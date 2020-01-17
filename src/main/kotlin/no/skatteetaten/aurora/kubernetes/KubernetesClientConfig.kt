package no.skatteetaten.aurora.kubernetes

import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.SslProvider
import reactor.netty.tcp.TcpClient

private val logger = KotlinLogging.logger {}

enum class ClientTypes {
    USER_TOKEN, SERVICE_ACCOUNT
}

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class TargetClient(val value: ClientTypes)

@Configuration
class KubernetesClientConfig(
    @Value("\${spring.application.name}") val applicationName: String,
    @Value("\${kubernetes.url}") val kubernetesUrl: String,
    @Value("\${kubernetes.tokenLocation:/var/run/secrets/kubernetes.io/serviceaccount/token}") val tokenLocation: String
) {

    @Lazy(true)
    @Bean
    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    fun kubernetesClientServiceAccount(webClient: WebClient) =
        KubernetesClient.create(webClient, File(tokenLocation).readText())

    @Lazy(true)
    @Bean
    @Primary
    @TargetClient(ClientTypes.USER_TOKEN)
    fun kubernetesClientUserToken(webClient: WebClient, tokenFetcher: TokenFetcher) =
        KubernetesClient.create(webClient, tokenFetcher)

    @Bean
    fun webClient(
        builder: WebClient.Builder,
        tcpClient: TcpClient
    ): WebClient {
        logger.debug("Kubernetes url=$kubernetesUrl")
        return builder
            .baseUrl(kubernetesUrl)
            .defaultHeaders(applicationName)
            .clientConnector(ReactorClientHttpConnector(HttpClient.from(tcpClient).compress(true)))
            .exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs {
                        it.defaultCodecs().apply {
                            maxInMemorySize(-1) // unlimited
                        }
                    }.build()
            )
            .build()
    }

    @Bean
    fun websocketClient(
        tcpClient: TcpClient,
        @Value("\${kubernetes.url}") kubernetesUrl: String
    ): ReactorNettyWebSocketClient {
        return ReactorNettyWebSocketClient(
            HttpClient.create()
                .baseUrl(kubernetesUrl)
                .headers { headers ->
                    File(tokenLocation).takeIf { it.isFile }?.let {
                        headers.add(HttpHeaders.AUTHORIZATION, "Bearer ${it.readText()}")
                    }

                    headers.add("User-Agent", applicationName)
                }
        )
    }

    @Bean
    fun kubernetesTcpClientWrapper(
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

    @Bean
    @Primary
    @Profile("kubernetes")
    fun kubernetesSSLContext(@Value("\${trust.store}") trustStoreLocation: String): KeyStore =
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
