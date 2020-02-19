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
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.v1.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.retry.Retry
import java.time.Duration

class ResourceNotFoundException(m: String) : RuntimeException(m)

private val logger = KotlinLogging.logger {}

class KubernetesCoroutinesClient(val client: KubernetesClient) {
    suspend inline fun <reified Input : HasMetadata, reified Output : HasMetadata> getOrNullWithQueryResource(resource: Input): Output? =
        client.getWithQueryResource<Input, Output>(resource).awaitFirstOrNull()

    suspend inline fun <reified Input : HasMetadata, reified Output : HasMetadata> getWithQueryResource(resource: Input): Output {
        return getOrNullWithQueryResource(resource)
            ?: throw ResourceNotFoundException("Resource with name=${resource.metadata?.name} namespace=${resource.metadata?.namespace} kind=${resource.kind} was not found")
    }
    
    suspend inline fun <reified Kind : HasMetadata> getOrNull(resource: Kind): Kind? =
        client.get(resource).awaitFirstOrNull()

    suspend inline fun <reified Kind : HasMetadata> get(resource: Kind): Kind =
        getOrNull(resource) ?: throw ResourceNotFoundException("Resource with name=${resource.metadata?.name} namespace=${resource.metadata?.namespace} kind=${resource.kind} was not found")

    suspend inline fun <reified Input : HasMetadata, reified Output : HasMetadata> getMany(resource: Input): List<Output> {
        return client.getList<Input, Output>(resource).awaitFirst()
    }

    suspend inline fun <reified Input : HasMetadata> post(resource: Input): Input =
        client.post(resource).awaitFirst()


    suspend inline fun <reified Input : HasMetadata> put(resource: Input): Input =
        client.put(resource).awaitFirst()


    suspend inline fun <reified Input : HasMetadata> delete(resource: Input, options: DeleteOptions? = null): Boolean =
        client.delete(resource, options).awaitFirst()

    suspend inline fun <reified T : Any> proxyGet(pod: Pod, port: Int, path: String): T {
        return client.proxyGet<T>(pod, port, path).awaitFirst()
    }

    suspend inline fun scaleDeploymentConfig(namespace: String, name: String, count: Int): Scale {
        return client.scaleDeploymentConfig(namespace, name, count).awaitFirst()
    }

    suspend fun rolloutDeploymentConfig(namespace: String, name: String): DeploymentConfig {
        return client.rolloutDeploymentConfig(namespace, name).awaitFirst()
    }

}

class KubernetesClient(val webClient: WebClient, val tokenFetcher: TokenFetcher) {

    companion object {
        fun create(webClient: WebClient, tokenFetcher: TokenFetcher) = KubernetesClient(webClient, tokenFetcher)

        fun create(webClient: WebClient, token: String) = KubernetesClient(webClient, object : TokenFetcher {
            override fun token() = token
        })
    }

    fun scaleDeploymentConfig(namespace: String, name: String, count: Int): Mono<Scale> {
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

        return webClient.put()
            .kubernetesBodyUri(
                resource = dc, 
                body = scale,
                uriSuffix = "/scale"
            ).perform()
        
        /*
        return webClient
            .put()
            .uri("${dc.uri()}/scale", dc.uriVariables())
            .body(BodyInserters.fromValue(scale))
            .perform()
            
         */
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
        /*
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
            .perform()
            
         */
    }


    inline fun <reified T : Any> proxyGet(pod: Pod, port: Int, path: String): Mono<T> {
        return webClient.get()
            .kubernetesUri(
                resource = pod,
                uriSuffix = ":{port}/proxy{path}",
                additionalUriVariables = mapOf(
                    "port" to port.toString(),
                    "path" to if (path.startsWith("/")) path else "/$path"
                )
            )
            .perform()
    }


    inline fun <reified Input : HasMetadata, reified Output : HasMetadata> getWithQueryResource(resource: Input): Mono<Output> {
        return webClient.get().kubernetesUri(resource).perform()
    }

    inline fun <reified Kind : HasMetadata> get(resource: Kind): Mono<Kind> {
        return webClient.get().kubernetesUri(resource).perform()
    }


    inline fun <reified Input : HasMetadata, reified Output : HasMetadata> getList(resource: Input): Mono<List<Output>> {
        return webClient.get()
            .kubernetesListUri(resource)
            .perform<KubernetesResourceList<Output>>()
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

    inline fun <reified Kind : HasMetadata> deleteMany(resource: Kind, options: DeleteOptions? = null): Mono<Boolean> {
        val request = options?.let {
            webClient.method(HttpMethod.DELETE)
                .kubernetesListBodyUri(resource, it)
        } ?: webClient.delete().kubernetesUri(resource)
        val response = request.perform<Any>()
        return response
            .map { true }
            .or(Mono.just(false))
    }

    inline fun <reified Kind : HasMetadata> delete(resource: Kind, options: DeleteOptions? = null): Mono<Boolean> {
        val request = options?.let {
            webClient.method(HttpMethod.DELETE)
                .kubernetesBodyUri(resource, it)
        } ?: webClient.delete().kubernetesUri(resource)
        val response = request.perform<Any>()
        return response
            .map { true }
            .or(Mono.just(false))
    }

    inline fun <reified T : Any> WebClient.RequestHeadersSpec<*>.perform() =
        this.bearerToken(tokenFetcher.token())
            .retrieve()
            .bodyToMono<T>()
            .notFoundAsEmpty()
            .retryWithLog(3L, 100L, 1000L)

    fun <Kind : HasMetadata> WebClient.RequestBodyUriSpec.kubernetesBodyUri(
        resource: Kind,
        body: Any,
        uriSuffix: String = ""
    ): WebClient.RequestHeadersSpec<*> {
        return this.uri(resource.uri() + uriSuffix, resource.uriVariables())
            .body(BodyInserters.fromValue(body))
    }

    fun <Kind : HasMetadata> WebClient.RequestBodyUriSpec.kubernetesListBodyUri(
        resource: Kind,
        body: Any,
        labels: Map<String, String?> = resource.metadata?.labels ?: emptyMap()
    ): WebClient.RequestHeadersSpec<*> {

        val metadata = if (resource.metadata == null) {
            null
        } else {
            newObjectMeta {
                namespace = resource.metadata?.namespace
            }
        }

        val baseUri = createUrl(metadata, resource.apiVersion)
        val urlVariables = resource.uriVariables()

        val req = if (labels.isNullOrEmpty()) {
            this.uri(baseUri, urlVariables)
        } else {
            this.uri { builder ->
                builder.path(baseUri)
                    .queryParam("labelSelector", labels.map {
                        if (it.value.isNullOrEmpty()) {
                            it.key
                        } else {
                            "${it.key}=${it.value}"
                        }
                    }.joinToString(","))
                    .build(urlVariables)
            }
        }

        return req.body(BodyInserters.fromValue(body))
    }


    fun <Kind : HasMetadata> WebClient.RequestHeadersUriSpec<*>.kubernetesListUri(
        resource: Kind,
        labels: Map<String, String?> = resource.metadata?.labels ?: emptyMap()
    ): WebClient.RequestHeadersSpec<*> {

        val metadata = if (resource.metadata == null) {
            null
        } else {
            newObjectMeta {
                namespace = resource.metadata?.namespace
            }
        }

        val baseUri = createUrl(metadata, resource.apiVersion)
        val urlVariables = resource.uriVariables()

        if (labels.isNullOrEmpty()) {
            return this.uri(baseUri, urlVariables)
        }
        return this.uri { builder ->
            builder.path(baseUri)
                .queryParam("labelSelector", labels.map {
                    if (it.value.isNullOrEmpty()) {
                        it.key
                    } else {
                        "${it.key}=${it.value}"
                    }
                }.joinToString(","))
                .build(urlVariables)
        }
    }

    fun <Kind : HasMetadata> WebClient.RequestHeadersUriSpec<*>.kubernetesUri(
        resource: Kind,
        uriSuffix: String = "",
        additionalUriVariables: Map<String, String> = emptyMap()
    ): WebClient.RequestHeadersSpec<*> {
        return this.uri("${resource.uri()}$uriSuffix", resource.uriVariables() + additionalUriVariables)
    }

    fun WebClient.RequestHeadersSpec<*>.bearerToken(token: String?) =
        token?.let {
            this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        } ?: this

    fun createUrl(metadata: ObjectMeta?, apiVersion: String): String {
        val contextRoot = if (apiVersion == "v1") {
            "/api"
        } else {
            "/apis"
        }

        return if (metadata == null) {
            "$contextRoot/${apiVersion}/{kind}"
        } else {
            metadata.namespace?.let {
                "$contextRoot/${apiVersion}/namespaces/{namespace}/{kind}/{name}"
            } ?: "$contextRoot/${apiVersion}/{kind}/{name}"
        }
    }
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

