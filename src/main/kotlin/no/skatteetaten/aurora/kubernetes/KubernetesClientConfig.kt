package no.skatteetaten.aurora.kubernetes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
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
import java.lang.IllegalArgumentException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory

private val logger = KotlinLogging.logger {}

enum class ClientTypes {
    USER_TOKEN, SERVICE_ACCOUNT, PSAT
}

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class TargetClient(val value: ClientTypes)

@Component
@ConfigurationProperties("kubernetes")
data class KubnernetesClientConfiguration(
    var url: String = "https://kubernetes.default.svc.cluster.local",
    var retry: RetryConfiguration,
    var timeout: HttpClientTimeoutConfiguration,
    var tokenLocation: String = "/var/run/secrets/kubernetes.io/serviceaccount/token"
) {

    fun createTestClient(token: String, userAgent: String = "test-client") =
        createUserAccountReactorClient(
            builder = WebClient.builder(),
            trustStore = null,
            tokenFetcher = StringTokenFetcher(token)
        ).apply {
            webClientBuilder.defaultHeaders(userAgent)
        }.build()

    fun createUserAccountReactorClient(
        builder: WebClient.Builder,
        trustStore: KeyStore?,
        tokenFetcher: TokenFetcher
    ): KubernetesReactorClient.Builder {
        val tcpClient = tcpClient(trustStore)
        val webClient = kubernetesWebClient(builder, tcpClient)
        return KubernetesReactorClient.Builder(
            webClient,
            tokenFetcher,
            this.retry
        )
    }

    fun createServiceAccountReactorClient(
        builder: WebClient.Builder,
        trustStore: KeyStore?
    ): KubernetesReactorClient.Builder {
        val tcpClient = tcpClient(trustStore)
        val webClient = kubernetesWebClient(builder, tcpClient)
        return KubernetesReactorClient.Builder(
            webClient,
            StringTokenFetcher(kubernetesToken(tokenLocation)),
            retry
        )
    }

    fun createPsatReactorClient(
        builder: WebClient.Builder,
        trustStore: KeyStore?
    ): KubernetesReactorClient.Builder {
        val tcpClient = tcpClient(trustStore)
        val webClient = kubernetesWebClient(builder, tcpClient)
        return KubernetesReactorClient.Builder(
            webClient,
            PsatTokenFetcher(),
            retry
        )
    }

    fun kubernetesWebClient(
        builder: WebClient.Builder,
        tcpClient: TcpClient
    ): WebClient.Builder {
        logger.debug("Kubernetes url=${url}")
        return builder
            .baseUrl(url)
            .clientConnector(ReactorClientHttpConnector(HttpClient.from(tcpClient)))
            .exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs {
                        it.defaultCodecs().apply {
                            maxInMemorySize(-1) // unlimited
                        }
                    }.build()
            )
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
    var connect: Duration = Duration.ofSeconds(2),
    var read: Duration = Duration.ofSeconds(5),
    var write: Duration = Duration.ofSeconds(5)
)

data class RetryConfiguration(
    var times: Long = 3L,
    var min: Duration = Duration.ofMillis(100),
    var max: Duration = Duration.ofSeconds(1)
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
    ) = config.createServiceAccountReactorClient(builder, trustStore).apply {
        webClientBuilder.defaultHeaders(applicationName)
    }.build()

    @Lazy(true)
    @Bean
    @TargetClient(ClientTypes.PSAT)
    fun kubernetesClientPsat(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?
    ) = config.createPsatReactorClient(builder, trustStore).apply {
        webClientBuilder.defaultHeaders(applicationName)
    }.build()

    @Lazy(true)
    @Bean
    @Primary
    @TargetClient(ClientTypes.USER_TOKEN)
    fun kubernetesClientUserToken(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?,
        @Autowired(required = false) tokenFetcher: TokenFetcher?
    ) = config.createUserAccountReactorClient(builder, trustStore, tokenFetcher ?: NoopTokenFetcher()).apply {
        webClientBuilder.defaultHeaders(applicationName)
    }.build()

    @Bean
    @Qualifier("kubernetesClientWebClient")
    fun kubernetesWebsocketClient(): ReactorNettyWebSocketClient {
        return ReactorNettyWebSocketClient(
            HttpClient.create()
                .baseUrl(config.url)
                .headers { headers ->
                    File(kubernetesToken(config.tokenLocation)).takeIf { it.isFile }?.let {
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

fun WebClient.Builder.defaultHeaders(applicationName: String) = this
    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
    .defaultHeader("User-Agent", applicationName)

fun kubernetesToken(tokenLocation: String = ""): String {
    val tokenFile = File(tokenLocation)
    return if (tokenFile.isFile) {
        logger.debug("Reading token from: $tokenLocation")
        tokenFile.readText().trim()
    } else {
        val userHome = System.getProperty("user.home")
        val path = "$userHome/.kube/config"
        logger.info("Token location ($tokenLocation) not found, reading token from $path")
        File(path).readText().let {
            val values = ObjectMapper(YAMLFactory()).readTree(it)
            val users = values.at("/users").iterator().asSequence().toList()
            val name = users.first().at("/name").textValue()
            if (name == userHome.substringAfterLast("/")) {
                users.first().at("/user/token").textValue()
            } else {
                val currentContext = values.at("/current-context").textValue()
                val key = currentContext.substring(currentContext.indexOf("/") + 1, currentContext.lastIndexOf("/"))
                users.find { user ->
                    user.at("/name").textValue().endsWith(key)
                }?.at("/user/token")?.textValue() ?: throw IllegalArgumentException("Could not find token in $path")
            }
        }
    }
}

