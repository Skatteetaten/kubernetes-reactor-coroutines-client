package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.v1.metadata
import com.fkorotkov.kubernetes.v1.newScale
import com.fkorotkov.kubernetes.v1.spec
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newUser
import io.fabric8.kubernetes.api.model.DeleteOptions
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.v1.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.reactive.awaitFirstOrElse
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.retry.Retry
import java.time.Duration

class ResourceNotFoundException(m: String) : RuntimeException(m)

private val logger = KotlinLogging.logger {}

class KubernetesClient(val webClient: WebClient, val tokenFetcher: TokenFetcher) {

    companion object {
        fun create(webClient: WebClient, tokenFetcher: TokenFetcher) = KubernetesClient(webClient, tokenFetcher)

        fun create(webClient: WebClient, token: String) = KubernetesClient(webClient, object : TokenFetcher {
            override fun token() = token
        })
    }

    //TODO: check if this is the correct class
    suspend fun scaleDeploymentConfig(namespace: String, name: String, count: Int): Scale {
        val dc = newDeploymentConfig {
            metadata {
                this.namespace = namespace
                this.name = name
            }
        }

        val scale = newScale {
            metadata {
                this.namespace = namespace
                this.name = name
            }
            spec {
                replicas = count
            }
        }

        return webClient
            .put()
            .uri("${dc.uri()}/scale", dc.uriVariables())
            .body(BodyInserters.fromValue(scale))
            .perform<Scale>()
            .awaitSingle()
    }

    suspend fun rolloutDeploymentConfig(namespace: String, name: String): DeploymentConfig {
        val dc = newDeploymentConfig {
            metadata {
                this.namespace = namespace
                this.name = name
            }
        }

        return webClient
            .post()
            .uri("${dc.uri()}/instantiate", dc.uriVariables())
            .body(
                BodyInserters.fromValue(
                    mapOf(
                        "kind" to "DeploymentRequest",
                        "apiVersion" to "apps.openshift.io/v1",
                        "name" to name,
                        "latest" to true,
                        "force" to true
                    )
                )
            )
            .perform<DeploymentConfig>()
            .awaitSingle()
    }

    /*
     Get a single resource given a resource template.

     The fields metadata.namespace and metadata.name are required. Labels in the resource are ignored for this operation

     @param resource:Kind a KubernetesResrouces implementing HasMetadata
     @return Mono<Kind>: A mono that can either be a result, empty (if the resource is not found) or an exception if it fails
     */
    inline fun <reified Kind : HasMetadata> getMono(resource: Kind): Mono<Kind> {
        return webClient.get().kubernetesUri(resource, labels = emptyMap()).perform()
    }

    /*
     Get a single resource given a resource template.

     The fields metadata.namespace and metadata.name are required. Labels in the resource are ignored for this operation

     @param resource:Kind a KubernetesResrouces implementing HasMetadata
     @return Kind?: Either the result or null, if an error occurs and Exception will be thrown
     */
    suspend inline fun <reified Kind : HasMetadata> getOrNull(resource: Kind): Kind? =
        getMono(resource).awaitFirstOrNull()

    /*
     Get a single resource given a resource template.

     The fields metadata.namespace and metadata.name are required. Labels in the resource are ignored for this operation

     @param resource:Kind a KubernetesResrouces implementing HasMetadata
     @return Kind: The result
     @throws ResourceNotFoundException if the given resource was not found
     */
    suspend inline fun <reified Kind : HasMetadata> get(resource: Kind): Kind {
        return getOrNull(resource)
            ?: throw ResourceNotFoundException("Resource with name=${resource.metadata?.name} namespace=${resource.metadata?.namespace} kind=${resource.kind} was not found")
    }

    inline fun <reified T : Any> WebClient.RequestHeadersSpec<*>.perform() =
        this.bearerToken(tokenFetcher.token())
            .retrieve()
            .bodyToMono<T>()
            .notFoundAsEmpty()
            .retryWithLog(3L, 100L, 1000L)

    suspend inline fun <reified T : Any> proxyGetPod(name: String, namespace: String, port: Int, path: String): T {

        val pod = newPod {
            metadata = newObjectMeta {
                this.namespace = namespace
                this.name = name
            }
        }
        return proxyGet(pod, port, path)
    }

    suspend inline fun <reified T : Any> proxyGet(pod: Pod, port: Int, path: String): T {
        return webClient.get()
            .kubernetesUri(
                resource = pod,
                uriSuffix = ":{port}/proxy{path}",
                additionalUriVariables = mapOf(
                    "port" to port.toString(),
                    "path" to if (path.startsWith("/")) path else "/$path"
                ),
                labels = emptyMap()
            )
            .perform<T>()
            .awaitSingle()
    }

    //TODO: if the resource that we get in here has a name the wrong url will be generated
    inline fun <reified Kind : HasMetadata> getListMono(resource: Kind): Mono<List<Kind>> {
        return webClient.get()
            .kubernetesUri(resource)
            .perform<KubernetesResourceList<Kind>>()
            .map { it.items }
    }

    /*
      //TODO: if the resource that we get in here has a name the wrong url will be generated
    inline fun <reified Kind : HasMetadata> getListMono(resource: Kind): Mono<List<Kind>> {
        return webClient.get()
            .kubernetesUri(resource)
            .perform<HasMetadata>()
            .map {
                if (it.kind == "KubernetesResourceList") {
                    (it as KubernetesResourceList<Kind>).items as List<Kind>
                } else {
                    listOf(it) as List<Kind>
                }
            }
    }
     */
    suspend inline fun <reified Kind : HasMetadata> getList(resource: Kind): List<Kind> {
        return getListMono(resource).awaitSingle()
    }

    suspend inline fun <reified Kind : HasMetadata> postResource(resource: Kind, body: Any = resource): Kind {
        return webClient.post()
            .kubernetesBodyUri(resource, body)
            .perform<Kind>()
            .awaitSingle()
    }

    suspend inline fun <reified Kind : HasMetadata> post(body: Kind): Kind {
        return postResource(resource = body, body = body)
    }

    suspend inline fun <reified Kind : HasMetadata> put(resource: Kind, body: Any = resource): Kind {
        return webClient.put()
            .kubernetesBodyUri(resource, body)
            .perform<Kind>()
            .awaitSingle()
    }

    suspend inline fun <reified Kind : HasMetadata> delete(resource: Kind, options: DeleteOptions? = null): Boolean {
        val request = options?.let {
            webClient.method(HttpMethod.DELETE)
                .kubernetesBodyUri(resource, it)
        } ?: webClient.delete().kubernetesUri(resource)
        val response = request.perform<Any>()
        return response.map { true }
            .doOnError {
                val logger = KotlinLogging.logger {}
                logger.warn("Unable to delete resource, ${resource.metadata.namespace} ${resource.metadata.name}. ${it.message}")
            }
            .awaitFirstOrElse { false }
    }

    inline fun <reified Kind : HasMetadata> WebClient.RequestBodyUriSpec.kubernetesBodyUri(
        resource: Kind,
        body: Any,
        uriSuffix: String = ""
    ): WebClient.RequestHeadersSpec<*> {
        return this.uri(resource.uri() + uriSuffix, resource.uriVariables())
            .body(BodyInserters.fromValue(body))
    }

    fun <Kind : HasMetadata> WebClient.RequestHeadersUriSpec<*>.kubernetesUri(
        resource: Kind,
        uriSuffix: String = "",
        additionalUriVariables: Map<String, String> = emptyMap(),
        labels: Map<String, String?> = resource.metadata?.labels ?: emptyMap()
    ): WebClient.RequestHeadersSpec<*> {
        if (labels.isNullOrEmpty()) {
            return this.uri("${resource.uri()}$uriSuffix", resource.uriVariables() + additionalUriVariables)
        }
        return this.uri { builder ->
            builder.path("${resource.uri()}$uriSuffix")
                .queryParam("labelSelector", labels.map {
                    if (it.value.isNullOrEmpty()) {
                        it.key
                    } else {
                        "${it.key}=${it.value}"
                    }
                }.joinToString(","))
                .build(resource.uriVariables() + additionalUriVariables)
        }
    }

    fun WebClient.RequestHeadersSpec<*>.bearerToken(token: String?) =
        token?.let {
            this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        } ?: this
}

@FunctionalInterface
interface TokenFetcher {
    fun token(): String
}

// TODO: This has some issues if namespace is empty or name is empty. We need to assert earlier in the functions
fun HasMetadata.uriVariables() = mapOf(
    "namespace" to this.metadata?.namespace,
    "kind" to this.kind.toLowerCase().plurlize(),
    "name" to this.metadata?.name
)

fun String.plurlize() = if (this.endsWith("s")) {
    "${this}es"
} else {
    "${this}s"
}

fun HasMetadata.uri(): String {
    val contextRoot = if (this.apiVersion == "v1") {
        "/api"
    } else {
        "/apis"
    }

    return if (this.metadata == null) {
        "$contextRoot/${this.apiVersion}/{kind}"
    } else {
        this.metadata.namespace?.let {
            "$contextRoot/${this.apiVersion}/namespaces/{namespace}/{kind}/{name}"
        } ?: "$contextRoot/${this.apiVersion}/{kind}/{name}"
    }
}

fun newCurrentUser() = newUser { metadata { name = "~" } }

fun newLabel(key: String) = mapOf(key to "")
fun newLabel(key: String, value: String) = mapOf(key to value)

fun <T> Mono<T>.notFoundAsEmpty() = this.onErrorResume {
    when (it) {
        is WebClientResponseException.NotFound -> {
            logger.debug { "Resource not found, method=${it.request?.method} uri=${it.request?.uri} " }
            Mono.empty()
        }
        else -> Mono.error(it)
    }
}

fun <T> Mono<T>.retryWithLog(times: Long = 3, retryFirstInMs: Long, retryMaxInMs: Long): Mono<T> {
    if (times == 0L) {
        return this
    }

    return this.retryWhen(Retry.onlyIf<Mono<T>> {
        it.exception() is WebClientResponseException && (it.exception() as WebClientResponseException).statusCode.is5xxServerError
    }
        .exponentialBackoff(Duration.ofMillis(retryFirstInMs), Duration.ofMillis(retryMaxInMs))
        .retryMax(times)
        .doOnRetry {
            //TODO: Should we log differently?
            logger.debug {
                val e = it.exception()
                val msg = "Retrying failed request times=${it.iteration()}, ${e.message}"
                if (e is WebClientResponseException) {
                    "$msg, method=${e.request?.method} uri=${e.request?.uri}"
                } else {
                    msg
                }
            }
        }
    )
}

