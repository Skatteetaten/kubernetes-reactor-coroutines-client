package no.skatteetaten.aurora.kubernetes

import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.SslProvider
import reactor.netty.tcp.TcpClient
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory

private val logger = KotlinLogging.logger {}

enum class ClientTypes {
    USER_TOKEN, SERVICE_ACCOUNT
}

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class TargetClient(val value: ClientTypes)

@Component
@ConfigurationProperties(prefix = "kubernetes")
data class KubnernetesClientConfiguration(
    val url: String,
    val retry: KubernetesRetryConfiguration,
    val timeout: HttpClientTimeoutConfiguration,
    val tokenLoation: String = "/var/run/secrets/kubernetes.io/serviceaccount/token"
) {

    fun createUserAccountReactorClient(
        builder: WebClient.Builder,
        trustStore: KeyStore?,
        tokenFetcher: TokenFetcher,
        applicationName: String
    ): KubernetesReactorClient {
        val tcpClient = tcpClient(trustStore)
        val webClient = kubernetesWebClient(builder, tcpClient, applicationName)
        return KubernetesReactorClient(
            webClient,
            tokenFetcher,
            this.retry
        )
    }

    fun createServiceAccountReactorClient(
        builder: WebClient.Builder,
        trustStore: KeyStore?,
        applicationName: String
    ): KubernetesReactorClient {
        val tcpClient = tcpClient(trustStore)
        val webClient = kubernetesWebClient(builder, tcpClient, applicationName)
        val token = File(tokenLoation).readText().trim()
        return KubernetesReactorClient(
            webClient,
            object : TokenFetcher {
                override fun token() = token
            },
            retry
        )
    }

    fun kubernetesWebClient(
        builder: WebClient.Builder,
        tcpClient: TcpClient,
        applicationName: String
    ): WebClient {
        logger.debug("Kubernetes url=${url}")
        return builder
            .baseUrl(url)
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

    fun tcpClient(
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
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout.connect.toMillis().toInt())
            .secure(sslProvider)
            .doOnConnected { connection ->
                connection
                    .addHandlerLast(ReadTimeoutHandler(timeout.read.toMillis(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(timeout.write.toMillis(), TimeUnit.MILLISECONDS))
            }
    }
}

data class HttpClientTimeoutConfiguration(
    val connect: Duration = Duration.ofSeconds(1),
    val read: Duration = Duration.ofSeconds(2),
    val write: Duration = Duration.ofSeconds(2)
)

data class KubernetesRetryConfiguration(
    val times: Long = 3L,
    val min: Duration = Duration.ofMillis(100),
    val max: Duration = Duration.ofSeconds(1)
)

@Configuration
class KubernetesClientConfig(
    @Value("\${spring.application.name}") val applicationName: String,
    val config: KubnernetesClientConfiguration
) {

    @Bean
    fun kubernetesWatcher(
        websocketClient: ReactorNettyWebSocketClient,
        closeableWatcher: CloseableWatcher
    ) = KubernetesWatcher(websocketClient, closeableWatcher)

    @Bean
    fun kubernetesCloseableWatcher() = KubernetesCloseableWatcher()

    @Lazy(true)
    @Bean
    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    fun kubernetesCoroutineClientServiceAccount(@TargetClient(ClientTypes.SERVICE_ACCOUNT) client: KubernetesReactorClient) =
        KubernetesCoroutinesClient(client)

    @Lazy(true)
    @Bean
    @Primary
    @TargetClient(ClientTypes.USER_TOKEN)
    fun kubernetesCoroutineClientUserToken(@TargetClient(ClientTypes.USER_TOKEN) client: KubernetesReactorClient) =
        KubernetesCoroutinesClient(client)

    @Lazy(true)
    @Bean
    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    fun kubernetesClientServiceAccount(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?
    ): KubernetesReactorClient {
        return config.createServiceAccountReactorClient(builder, trustStore, applicationName)
    }

    @Lazy(true)
    @Bean
    @Primary
    @TargetClient(ClientTypes.USER_TOKEN)
    fun kubernetesClientUserToken(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?,
        tokenFetcher: TokenFetcher
    ) = config.createUserAccountReactorClient(builder, trustStore, tokenFetcher, applicationName)

    @Bean
    @Qualifier("kubernetesClientWebClient")
    fun kubernetesWebsocketClient(): ReactorNettyWebSocketClient {
        return ReactorNettyWebSocketClient(
            HttpClient.create()
                .baseUrl(config.url)
                .headers { headers ->
                    File(config.tokenLoation).takeIf { it.isFile }?.let {
                        headers.add(HttpHeaders.AUTHORIZATION, "Bearer ${it.readText()}")
                    }

                    headers.add("User-Agent", applicationName)
                }
        )
    }


    @Bean
    @Profile("local")
    @Qualifier("kubernetesClientWebClient")
    fun kuberntesLocalKeyStore(): KeyStore? = null

    //TODO: how to fix this for testing?
    @Bean
    @Primary
    @Profile("openshift")
    @Qualifier("kubernetesClientWebClient")
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


