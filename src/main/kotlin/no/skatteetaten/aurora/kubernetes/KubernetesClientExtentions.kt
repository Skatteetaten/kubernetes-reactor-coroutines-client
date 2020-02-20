package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newUser
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Mono
import reactor.retry.Retry

class ResourceNotFoundException(m: String) : RuntimeException(m)

private val logger = KotlinLogging.logger {}

fun <Kind : HasMetadata> WebClient.RequestBodyUriSpec.kubernetesBodyUri(
    resource: Kind,
    body: Any,
    uriSuffix: String = ""
): WebClient.RequestHeadersSpec<*> {
    return this.uri(resource.uri() + uriSuffix, resource.uriVariables())
        .body(BodyInserters.fromValue(body))
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

    return this.uri {
        it.path(createUrl(metadata, resource.apiVersion))
            .addQueryParamIfExist(labels)
            .build(resource.uriVariables())
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

@FunctionalInterface
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

fun <T> Mono<T>.retryWithLog(retryConfiguration: KubernetesRetryConfiguration): Mono<T> {
    if (retryConfiguration.times == 0L) {
        return this
    }

    return this.retryWhen(Retry.onlyIf<Mono<T>> {
        it.exception() is WebClientResponseException && (it.exception() as WebClientResponseException).statusCode.is5xxServerError
    }
        .exponentialBackoff(retryConfiguration.min, retryConfiguration.max)
        .retryMax(retryConfiguration.times)
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

fun UriBuilder.addQueryParamIfExist(label: Map<String, String?>?): UriBuilder {
    if (label.isNullOrEmpty()) return this
    return this.queryParam(label.toLabelSelector())
}

fun Map<String, String?>.toLabelSelector(): String {
    return this.map {
        if (it.value.isNullOrEmpty()) {
            it.key
        } else {
            "${it.key}=${it.value}"
        }
    }.joinToString(",")
}
