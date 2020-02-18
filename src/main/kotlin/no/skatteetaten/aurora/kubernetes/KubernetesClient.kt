package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.v1.metadata
import com.fkorotkov.kubernetes.v1.newScale
import com.fkorotkov.kubernetes.v1.spec
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newUser
import io.fabric8.kubernetes.api.model.DeleteOptions
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Status
import io.fabric8.kubernetes.api.model.v1.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody

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

    suspend inline fun <reified Kind : HasMetadata, reified T : Any> getResource(resource: Kind): T {
        return webClient.get().kubernetesResource(resource)
    }

    suspend inline fun <reified Kind : HasMetadata> get(resource: Kind): Kind {
        return getResource(resource)
    }

    suspend inline fun <reified Kind : HasMetadata, reified ListKind : KubernetesResourceList<Kind>> getList(resource: Kind): ListKind {
        return getResource(resource)
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

    suspend inline fun <reified Kind : HasMetadata> delete(resource: Kind, options: DeleteOptions? = null): Boolean {
        return try {
            options?.let {
                webClient.method(HttpMethod.DELETE).kubernetesResource<Kind, Status>(resource, it)
            } ?: webClient.delete().kubernetesResource(resource)
            true
        } catch (t: Throwable) {
            val logger = KotlinLogging.logger {}
            if (t is WebClientResponseException && t.statusCode == HttpStatus.NOT_FOUND) {
                logger.debug("Resource not found, ${resource.metadata.namespace} ${resource.metadata.name}. ${t.message}")
                false
            } else {
                logger.warn("Unable to delete resource, ${resource.metadata.namespace} ${resource.metadata.name}. ${t.message}")
                throw t
            }
        }
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

    suspend inline fun <reified Kind : HasMetadata, reified T : Any> WebClient.RequestHeadersUriSpec<*>.kubernetesResource(
        resource: Kind
    ): T {
        val labels = resource.metadata?.labels
        val spec = if (labels.isNullOrEmpty()) {
            this.uri(resource.uri(), resource.uriVariables())
        } else {
            this.uri { builder ->
                builder.path(resource.uri())
                    .queryParam("labelSelector", labels.map {
                        if (it.value.isNullOrEmpty()) {
                            it.key
                        } else {
                            "${it.key}=${it.value}"
                        }
                    }.joinToString(","))
                    .build(resource.uriVariables())
            }
        }

        return spec.bearerToken(tokenFetcher.token()).retrieve().awaitBody()
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