package no.skatteetaten.aurora.kubernetes.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import mu.KotlinLogging
import no.skatteetaten.aurora.kubernetes.CloseableWatcher
import no.skatteetaten.aurora.kubernetes.KubernetesCloseableWatcher
import no.skatteetaten.aurora.kubernetes.KubernetesConfiguration
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.KubernetesWatcher
import no.skatteetaten.aurora.kubernetes.NoopTokenFetcher
import no.skatteetaten.aurora.kubernetes.PsatTokenFetcher
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.netty.http.client.HttpClient
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private val logger = KotlinLogging.logger {}

enum class ClientTypes {
    USER_TOKEN, SERVICE_ACCOUNT, PSAT
}

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class TargetClient(val value: ClientTypes)

@Configuration
class KubernetesClientConfig(
    @Value("\${spring.application.name}") val applicationName: String,
    val config: KubernetesConfiguration
) {

    @Lazy
    @Bean
    fun kubernetesWatcher(
        websocketClient: ReactorNettyWebSocketClient,
        closeableWatcher: CloseableWatcher
    ) = KubernetesWatcher(websocketClient, closeableWatcher)

    @Lazy
    @Bean
    fun kubernetesCloseableWatcher() = KubernetesCloseableWatcher()

    @Lazy
    @Bean
    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    fun kubernetesCoroutineClientServiceAccount(@TargetClient(ClientTypes.SERVICE_ACCOUNT) client: KubernetesReactorClient) =
        KubernetesCoroutinesClient(client, null)

    @Lazy
    @Bean
    @Primary
    @TargetClient(ClientTypes.USER_TOKEN)
    fun kubernetesCoroutineClientUserToken(
        @TargetClient(ClientTypes.USER_TOKEN) client: KubernetesReactorClient,
        @Autowired(required = false) tokenFetcher: TokenFetcher?
    ) = KubernetesCoroutinesClient(client, tokenFetcher)

    @Lazy
    @Bean
    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    fun kubernetesClientServiceAccount(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?
    ) = config.createServiceAccountReactorClient(builder, trustStore).apply {
        webClientBuilder.defaultHeaders(applicationName)
    }.build()

    @Lazy
    @Bean
    @TargetClient(ClientTypes.PSAT)
    fun kubernetesClientPsat(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?
    ) = config.createReactorClient(builder, trustStore, PsatTokenFetcher()).apply {
        webClientBuilder.defaultHeaders(applicationName)
    }.build()

    @Lazy
    @Bean
    @Primary
    @TargetClient(ClientTypes.USER_TOKEN)
    fun kubernetesClientUserToken(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?,
        @Autowired(required = false) tokenFetcher: TokenFetcher?
    ) = config.createReactorClient(builder, trustStore, tokenFetcher ?: NoopTokenFetcher()).apply {
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

    // TODO: how to fix this for testing?
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
        val username = userHome.substringAfterLast("/")
        val path = "$userHome/.kube/config"
        logger.info("Token location ($tokenLocation) not found, reading token from $path")

        val kubeConfig = File(path)
        if (!kubeConfig.isFile) {
            throw IllegalStateException("$path not found, run 'oc login' and then try again")
        }

        kubeConfig.readText().let {
            val values = ObjectMapper(YAMLFactory()).readTree(it)
            val users = values.at("/users").iterator().asSequence().toList()
            val currentContext = values.at("/current-context").textValue()
            logger.info("Current kube context: $currentContext")

            val key = currentContext.substring(currentContext.indexOf("/") + 1, currentContext.lastIndexOf("/"))
            return users.getCurrentContextUser(key)
                ?: users.getUser(username)
                ?: throw IllegalArgumentException("Could not find token for $currentContext in $path")
        }
    }
}

private fun List<JsonNode>.getCurrentContextUser(key: String) =
    find { it.at("/name").textValue().endsWith(key) }
        ?.at("/user/token")?.textValue()

private fun List<JsonNode>.getUser(username: String) =
    find { it.at("/name").textValue() == username }
        ?.at("/user/token")?.textValue()
