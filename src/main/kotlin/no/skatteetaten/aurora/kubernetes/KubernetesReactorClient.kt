package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.v1.metadata
import com.fkorotkov.kubernetes.v1.newScale
import com.fkorotkov.kubernetes.v1.spec
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import io.fabric8.kubernetes.api.model.DeleteOptions
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Status
import io.fabric8.kubernetes.api.model.v1.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import mu.KotlinLogging
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class KubernetesReactorClient(
    val webClient: WebClient,
    val tokenFetcher: TokenFetcher,
    val retryConfiguration: RetryConfiguration
) {

    val logger = KotlinLogging.logger {}

    class Builder(
        val webClientBuilder: WebClient.Builder,
        val tokenFetcher: TokenFetcher,
        val retryConfiguration: RetryConfiguration
    ) {

        fun build() = KubernetesReactorClient(webClientBuilder.build(), tokenFetcher, retryConfiguration)
    }

    fun scaleDeploymentConfig(namespace: String, name: String, count: Int): Mono<Scale> {
        val dc = newDeploymentConfig {
            metadata {
                this.namespace = namespace
                this.name = name
            }
        }

        val scale = newScale {
            //TODO: openshift 3.11 uses this and not what is in this client as standard
            apiVersion = "extensions/v1beta1"
            metadata {
                this.namespace = namespace
                this.name = name
            }
            spec {
                replicas = count
            }
        }

        return webClient.put()
            .kubernetesBodyUri(
                resource = dc,
                body = scale,
                uriSuffix = "/scale"
            ).perform()
    }

    fun rolloutDeploymentConfig(namespace: String, name: String): Mono<DeploymentConfig> {
        val dc = newDeploymentConfig {
            metadata {
                this.namespace = namespace
                this.name = name
            }
        }

        return webClient.post().kubernetesBodyUri(
            resource = dc,
            body = mapOf(
                "kind" to "DeploymentRequest",
                "apiVersion" to "apps.openshift.io/v1",
                "name" to name,
                "latest" to true,
                "force" to true
            ),
            uriSuffix = "/instantiate"
        ).perform()
    }

    inline fun <reified Kind : HasMetadata> get(metadata: ObjectMeta): Mono<Kind> {
        return webClient.get().kubernetesUri(TypedHasMetadata(Kind::class, metadata)).perform()
    }

    inline fun <reified Kind : HasMetadata> getMany(metadata: ObjectMeta? = null): Mono<List<Kind>> {
        return webClient.get()
            .kubernetesListUri<HasMetadata>(TypedHasMetadata(Kind::class, metadata))
            .perform<KubernetesResourceList<Kind>>()
            .map { it.items }
    }

    inline fun <reified T : Any> proxyGet(pod: Pod, port: Int, path: String, headers:Map<String, String> = emptyMap()): Mono<T> {
        return webClient.get().kubernetesUri(
            resource = pod,
            uriSuffix = ":{port}/proxy{path}",
            additionalUriVariables = mapOf(
                "port" to port.toString(),
                "path" to if (path.startsWith("/")) path else "/$path"
            )
        ).headers { h ->
            headers.forEach {
                h.add(it.key, it.value)
            }
        }.perform<T>(true)
    }

    inline fun <reified Kind : HasMetadata> get(resource: Kind): Mono<Kind> {
        return webClient.get().kubernetesUri(resource).perform<Kind>().doOnError {
            logger.debug(
                "Error occurred for getting type=${it.javaClass.simpleName} kind=${resource.kind} namespace=${resource.metadata?.namespace} name=${resource.metadata?.name} message=${it.message}"
            )
        }
    }

    inline fun <reified Input : HasMetadata, reified Output : HasMetadata> getWithQueryResource(resource: Input): Mono<Output> {
        return webClient.get().kubernetesUri(resource).perform<Output>().doOnError {
            logger.debug(
                "Error occurred for getting type=${it.javaClass.simpleName} kind=${resource.kind} namespace=${resource.metadata?.namespace} name=${resource.metadata?.name} message=${it.message}"
            )
        }
    }

    inline fun <reified Kind : HasMetadata> getMany(resource: Kind): Mono<List<Kind>> {
        return webClient.get()
            .kubernetesListUri(resource)
            .perform<KubernetesResourceList<Kind>>().doOnError {
                logger.debug(
                    "Error occurred for getting type=${it.javaClass.simpleName} kind=${resource.kind} namespace=${resource.metadata?.namespace} name=${resource.metadata?.name} message=${it.message}"
                )
            }
            .map { it.items }
    }

    inline fun <reified Kind : HasMetadata> post(resource: Kind, body: Any = resource): Mono<Kind> {
        return webClient.post()
            .kubernetesBodyUri(resource, body)
            .perform<Kind>()
    }

    inline fun <reified Kind : HasMetadata> put(resource: Kind, body: Any = resource): Mono<Kind> {
        return webClient.put()
            .kubernetesBodyUri(resource, body)
            .perform()
    }

    //background=Status
    //Foreground=Kind
    inline fun <reified Kind : HasMetadata> deleteForeground(
        resource: Kind,
        deleteOptions: DeleteOptions? = null
    ): Mono<Kind> {
        return webClient.method(HttpMethod.DELETE)
            .kubernetesBodyUri(resource, deleteOptions.propagationPolicy("Foreground"))
            .perform()
    }

    inline fun <reified Kind : HasMetadata> deleteOrphan(
        resource: Kind,
        deleteOptions: DeleteOptions? = null
    ): Mono<Kind> {
        return webClient.method(HttpMethod.DELETE)
            .kubernetesBodyUri(resource, deleteOptions.propagationPolicy("Orphan"))
            .perform()
    }

    inline fun <reified Kind : HasMetadata> deleteBackground(
        resource: Kind,
        deleteOptions: DeleteOptions? = null
    ): Mono<Status> {
        return webClient.method(HttpMethod.DELETE)
            .kubernetesBodyUri(resource, deleteOptions.propagationPolicy("Background"))
            .perform()
    }

    inline fun <reified T : Any> WebClient.RequestHeadersSpec<*>.perform(proxy: Boolean = false) =
        this.bearerToken(tokenFetcher.token())
            .retrieve()
            .bodyToMono<T>()
            .notFoundAsEmpty()
            .retryWithLog(retryConfiguration, proxy)
}

class TypedHasMetadata<Kind : HasMetadata>(private val kind: KClass<Kind>, private val metadata: ObjectMeta?) :
    HasMetadata {
    override fun getMetadata() = metadata?.let {
        newObjectMeta {
            this.name = it.name
            this.namespace = it.namespace
        }
    }

    override fun getKind() = kind.simpleName!!
    override fun getApiVersion(): String = kind.createInstance().apiVersion
    override fun setMetadata(meta: ObjectMeta?) {
        throw UnsupportedOperationException("Cannot set apiVersion on custom resource")
    }

    override fun setApiVersion(version: String?) {
        throw UnsupportedOperationException("Cannot set apiVersion on custom resource")
    }
}

