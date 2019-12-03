package no.skatteetaten.aurora.kubernetes

import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import mu.KotlinLogging
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.util.StreamUtils
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.SslProvider
import reactor.netty.tcp.TcpClient
import java.io.IOException

const val HEADER_KLIENTID = "KlientID"

private val logger = KotlinLogging.logger{}
@Configuration
@EnableAsync
class WebclientKubernetesConfig(
    @Value("\${spring.application.name}") val applicationName: String,
    @Value("\${kubernetes.url}") val kubernetesUrl: String,
    @Value("\${kubernetes.tokenLocation:file:/var/run/secrets/kubernetes.io/serviceaccount/token}") val token: Resource
) : BeanPostProcessor {


    @Bean
    fun kubernetesClient(
        @Qualifier("kubernetes") webClient: WebClient,
        userTokenFetcher: UserTokenFetcher
    ) = KubernetesClient(webClient, userTokenFetcher)

    @Qualifier("kubernetest")
    @Bean
    fun webClient(
        builder: WebClient.Builder,
        @Qualifier("openshift") tcpClient: TcpClient
    ): WebClient {
        logger.debug("OpenshiftUrl=$kubernetesUrl")
        val b = builder
            .baseUrl(kubernetesUrl)
            .defaultHeaders(applicationName)
            .clientConnector(ReactorClientHttpConnector(HttpClient.from(tcpClient).compress(true)))

        try {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${token.readContent()}")
        } catch (e: IOException) {
            logger.info("No token file found, will not add Authorization header to WebClient")
        }

        return b.build()
    }

    @Bean
    @Qualifier("kubernetes")
    fun websocketClient(
        @Qualifier("kubernetes") tcpClient: TcpClient,
        @Value("\${kubernetes.url}") openshiftUrl: String,
        @Value("\${kubernetes.tokenLocation:file:/var/run/secrets/kubernetes.io/serviceaccount/token}") token: Resource
    ): ReactorNettyWebSocketClient {
        return ReactorNettyWebSocketClient(
            HttpClient.create()
                .baseUrl(openshiftUrl)
                .headers {
                    it.add(HttpHeaders.AUTHORIZATION, "Bearer ${token.readContent()}")
                    it.add("User-Agent", applicationName)
                }
        )
    }

    @Bean
    @Qualifier("kubernetes")
    fun openshiftTcpClientWrapper(
        @Value("\${kubernetes.readTimeout:5000}") readTimeout: Long,
        @Value("\${kubernetes.writeTimeout:5000}") writeTimeout: Long,
        @Value("\${kubernetes.connectTimeout:5000}") connectTimeout: Int,
        trustStore: KeyStore?
    ): TcpClient = tcpClient(readTimeout, writeTimeout, connectTimeout, trustStore)

    fun tcpClient(
        readTimeout: Long,
        writeTimeout: Long,
        connectTimeout: Int,
        trustStore: KeyStore?
    ): TcpClient {
        val trustFactory = TrustManagerFactory.getInstance("X509")
        trustFactory.init(trustStore)

        val sslProvider = SslProvider.builder().sslContext(
            SslContextBuilder
                .forClient()
                .trustManager(trustFactory)
                .build()
        ).build()
        return TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
            .secure(sslProvider)
            .doOnConnected { connection ->
                connection
                    .addHandlerLast(ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS))
            }
    }

    @Bean
    @Profile("local")
    fun localKeyStore(): KeyStore? = null

    @Profile("kubernetes")
    @Bean
    fun openshiftSSLContext(@Value("\${trust.store}") trustStoreLocation: String): KeyStore =
        KeyStore.getInstance(KeyStore.getDefaultType())?.let { ks ->
            ks.load(FileInputStream(trustStoreLocation), "changeit".toCharArray())
            val fis = FileInputStream("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt")
            CertificateFactory.getInstance("X509").generateCertificates(fis).forEach {
                ks.setCertificateEntry((it as X509Certificate).subjectX500Principal.name, it)
            }
            ks
        } ?: throw Exception("KeyStore getInstance did not return KeyStore")
}

private fun WebClient.Builder.defaultHeaders(applicationName: String) = this
    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
    .defaultHeader("User-Agent", applicationName)


fun Resource.readContent() = StreamUtils.copyToString(this.inputStream, StandardCharsets.UTF_8)
