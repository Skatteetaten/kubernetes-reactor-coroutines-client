package no.skatteetaten.aurora.kubernetes

import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import mu.KotlinLogging
import no.skatteetaten.aurora.kubernetes.config.defaultHeaders
import no.skatteetaten.aurora.kubernetes.config.kubernetesToken
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import reactor.netty.tcp.SslProvider
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory

private val logger = KotlinLogging.logger {}

@Component
@ConfigurationProperties("kubernetes")
data class KubernetesConfiguration(
    var url: String = "https://kubernetes.default.svc.cluster.local",
    var retry: RetryConfiguration,
    var timeout: HttpClientTimeoutConfiguration,
    var tokenLocation: String = "/var/run/secrets/kubernetes.io/serviceaccount/token",
    @Value("\${spring.application.name:}") val name: String? = null
) {

    fun createTestClient(token: String, userAgent: String = "test-client") =
        createReactorClient(
            builder = WebClient.builder(),
            trustStore = null,
            tokenFetcher = StringTokenFetcher(token)
        ).apply {
            webClientBuilder.defaultHeaders(userAgent)
        }.build()

    fun createReactorClient(
        builder: WebClient.Builder,
        trustStore: KeyStore?,
        tokenFetcher: TokenFetcher
    ): KubernetesReactorClient.Builder {
        val webClient = kubernetesWebClient(builder, httpClient(trustStore))
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
        val webClient = kubernetesWebClient(builder, httpClient(trustStore))
        return KubernetesReactorClient.Builder(
            webClient,
            StringTokenFetcher(kubernetesToken(tokenLocation)),
            retry
        )
    }

    fun kubernetesWebClient(
        builder: WebClient.Builder,
        httpClient: HttpClient
    ): WebClient.Builder {
        logger.debug("Kubernetes url=$url")
        return builder
            .baseUrl(url)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs {
                        it.defaultCodecs().apply {
                            maxInMemorySize(-1) // unlimited
                        }
                    }.build()
            )
    }

    fun httpClient(
        trustStore: KeyStore?
    ): HttpClient {
        val trustFactory = TrustManagerFactory.getInstance("X509")
        trustFactory.init(trustStore)

        val sslProvider = SslProvider.builder().sslContext(
            SslContextBuilder
                .forClient()
                .trustManager(trustFactory)
                .build()
        ).build()
        return HttpClient.create(ConnectionProvider.builder("$name-connection-provider").metrics(true).build())
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
