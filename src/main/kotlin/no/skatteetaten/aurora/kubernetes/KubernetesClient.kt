package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.v1.metadata
import com.fkorotkov.kubernetes.v1.newScale
import com.fkorotkov.kubernetes.v1.spec
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newUser
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.v1.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.retry.Retry
import java.time.Duration

private val logger= KotlinLogging.logger{}

class KubernetesClient(val webClient: WebClient, val tokenFetcher: TokenFetcher) {

    companion object {
        fun create(webClient: WebClient, tokenFetcher: TokenFetcher) = KubernetesClient(webClient, tokenFetcher)

        fun create(webClient: WebClient, token: String) = KubernetesClient(webClient, object : TokenFetcher {
            override fun token() = token
        })
    }

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
            .bearerToken(tokenFetcher.token())
            .retrieve()
            .awaitBody()
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
            .bearerToken(tokenFetcher.token())
            .retrieve()
            .awaitBody()
    }



    suspend inline fun <reified Kind : HasMetadata> getResource(resource: Kind): Kind {
        return webClient.get().kubernetesResource<Kind, Kind>(resource).awaitSingle()
    }

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
        return webClient.get().kubernetesResource<Pod, T>(
            resource = pod,
            uriSuffix = ":{port}/proxy{path}",
            additionalUriVariables = mapOf(
                "port" to port.toString(),
                "path" to if (path.startsWith("/")) path else "/$path"
            )
        ).awaitSingle()
    }

    suspend inline fun <reified Kind : HasMetadata> getOrNull(resource: Kind): Kind? {
        return webClient.get().kubernetesResource<Kind, Kind>(resource).notFoundAsEmpty().awaitFirstOrNull()
    }


    suspend inline fun <reified Kind : HasMetadata> get(resource: Kind): Kind {
        return getResource(resource)
    }

    inline fun <reified Kind : HasMetadata> getListReactive(resource: Kind): Mono<List<Kind>> {
        return webClient.get().kubernetesResource<Kind, KubernetesResourceList<Kind>>(resource) .map { it.items }
    }

    suspend inline fun <reified Kind : HasMetadata> getList(resource: Kind): List<Kind> {
        return getListReactive(resource).awaitSingle()
    }

    suspend inline fun <reified Kind : HasMetadata> postResource(resource: Kind, body: Any = resource): Kind {
        return webClient.post().kubernetesResource(resource, body)
    }

    suspend inline fun <reified Kind : HasMetadata> post(body: Kind): Kind {
        return postResource(resource = body, body = body)
    }

    suspend inline fun <reified Kind : HasMetadata> put(resource: Kind, body: Any = resource): Kind {
        return webClient.put().kubernetesResource(resource, body)
    }

    suspend inline fun <reified Kind : HasMetadata> delete(resource: Kind): Kind {
        return webClient.delete().kubernetesResource<Kind, Kind>(resource).awaitSingle()
    }

    suspend inline fun <reified Kind : HasMetadata, reified T : Any> WebClient.RequestBodyUriSpec.kubernetesResource(
        resource: Kind,
        body: Any
    ): T {
        return this.uri(resource.uri(), resource.uriVariables())
            .body(BodyInserters.fromValue(body))
            .bearerToken(tokenFetcher.token())
            .retrieve()
            .awaitBody()
    }

    inline fun <Kind : HasMetadata, reified T: Any> WebClient.RequestHeadersUriSpec<*>.kubernetesResource(
        resource: Kind,
        uriSuffix: String = "",
        additionalUriVariables: Map<String, String> = emptyMap()
    ): Mono<T> {
        val labels = resource.metadata?.labels
        val spec = if (labels.isNullOrEmpty()) {
            this.uri("${resource.uri()}$uriSuffix", resource.uriVariables() + additionalUriVariables)
        } else {
            this.uri { builder ->
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

        return spec.bearerToken(tokenFetcher.token()).retrieve().bodyToMono()
    }


    fun WebClient.RequestHeadersSpec<*>.bearerToken(token: String?) =
        token?.let {
            this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        } ?: this
}

interface TokenFetcher {
    fun token(): String
}

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


fun <T> Mono<T>.retryWithLog(retryFirstInMs: Long, retryMaxInMs: Long) =
    this.retryWhen(Retry.onlyIf<Mono<T>> {
        if (it.iteration() == 3L) {
            logger.info {
                val e = it.exception()
                val msg = "Retrying failed request, ${e.message}"
                if (e is WebClientResponseException) {
                    "$msg, ${e.request?.method} ${e.request?.uri}"
                } else {
                    msg
                }
            }
        }

        it.exception() !is WebClientResponseException.Unauthorized
    }.exponentialBackoff(Duration.ofMillis(retryFirstInMs), Duration.ofMillis(retryMaxInMs)).retryMax(3))
