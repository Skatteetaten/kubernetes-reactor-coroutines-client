package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.newDeleteOptions
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newUser
import io.fabric8.kubernetes.api.model.DeleteOptions
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.authentication.TokenReview
import io.fabric8.kubernetes.api.model.authentication.TokenReviewStatus
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.io.File

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
    return this.uri(resource.createUrl()) {
        it.addQueryParamIfExist(labels).build(resource.uriVariables())
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

fun HasMetadata.createUrl(): String {
    val metadata = if (metadata == null) {
        null
    } else {
        newObjectMeta {
            namespace = metadata?.namespace
        }
    }

    val contextRoot = if (apiVersion == "v1") {
        "/api"
    } else {
        "/apis"
    }

    return if (metadata == null) {
        "$contextRoot/$apiVersion/${kindUri()}"
    } else {
        metadata.namespace?.let {
            "$contextRoot/$apiVersion/namespaces/{namespace}/${kindUri()}/{name}"
        } ?: "$contextRoot/$apiVersion/${kindUri()}/{name}"
    }
}

@FunctionalInterface
interface TokenFetcher {
    fun token(audience: String? = null): String?
}

class PsatTokenFetcher : TokenFetcher {
    override fun token(audience: String?): String? {
        val tokenLocation = System.getenv("VOLUME_PSAT_TOKEN_MOUNT") ?: "/u01/secrets/app/psat-token"
        return File("$tokenLocation/$audience").readText().trim()
    }
}

class StringTokenFetcher(val token: String) : TokenFetcher {
    override fun token(audience: String?) = token
}

class NoopTokenFetcher : TokenFetcher {
    override fun token(audience: String?): String? {
        logger.debug("NoopTokenFetcher configured and no token sent in")
        return null
    }
}

fun HasMetadata.uriVariables() = mapOf(
    "namespace" to this.metadata?.namespace,
    "name" to this.metadata?.name
)

fun String.pluralize() = if (this.endsWith("s")) {
    "${this}es"
} else {
    "${this}s"
}

fun HasMetadata.kindUri() = this.kind.toLowerCase().pluralize()

fun HasMetadata.uri(): String {
    val contextRoot = if (this.apiVersion == "v1") {
        "/api"
    } else {
        "/apis"
    }

    return if (this.metadata == null) {
        "$contextRoot/$apiVersion/${kindUri()}"
    } else {
        this.metadata.namespace?.let {
            "$contextRoot/$apiVersion/namespaces/{namespace}/${kindUri()}/{name}"
        } ?: "$contextRoot/$apiVersion/${kindUri()}/{name}"
    }
}

fun newCurrentUser() = newUser { metadata { name = "~" } }

fun newLabel(key: String) = mapOf(key to "")
fun newLabel(key: String, value: String) = mapOf(key to value)

fun <T> Mono<T>.notFoundAsEmpty() = this.onErrorResume {
    when (it) {
        is WebClientResponseException.NotFound -> {
            logger.trace { "Resource not found, method=${it.request?.method} uri=${it.request?.uri} " }
            Mono.empty()
        }
        else -> Mono.error(it)
    }
}

fun <T> Mono<T>.unauthorizedAsEmpty() = this.onErrorResume {
    when (it) {
        is WebClientResponseException.Unauthorized -> {
            logger.trace { "Unauthorized, method=${it.request?.method} uri=${it.request?.uri} " }
            Mono.empty()
        }
        else -> Mono.error(it)
    }
}

fun <T> Mono<T>.retryWithLog(
    retryConfiguration: RetryConfiguration,
    ignoreAllWebClientResponseException: Boolean = false,
    context: String = ""
): Mono<T> {
    if (retryConfiguration.times == 0L) {
        return this
    }

    return this.retryWhen(Retry
        .backoff(retryConfiguration.times, retryConfiguration.min)
        .maxBackoff(retryConfiguration.max)
        .filter {
            logger.trace(it) {
                "retryWhen called with exception ${it?.javaClass?.simpleName}, message: ${it?.message}"
            }

            if (ignoreAllWebClientResponseException) {
                it !is WebClientResponseException
            } else {
                it.isServerError() || it !is WebClientResponseException
            }
        }.doAfterRetry {
            val e = it.failure()
            logger.debug {
                val msg =
                    "Retrying failed request times=${it.totalRetries()}, context=${context} errorType=${e.javaClass.simpleName} errorMessage=${e.message}"
                if (e is WebClientResponseException) {
                    "$msg, method=${e.request?.method} uri=${e.request?.uri}"
                } else {
                    msg
                }
            }
        }
    )
}

private fun Throwable.isServerError() =
    this is WebClientResponseException && this.statusCode.is5xxServerError

fun UriBuilder.addQueryParamIfExist(label: Map<String, String?>?): UriBuilder {
    if (label.isNullOrEmpty()) return this
    return this.queryParam("labelSelector", label.toLabelSelector())
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

fun DeleteOptions?.propagationPolicy(propagationPolicy: String): DeleteOptions =
    this?.let {
        this.propagationPolicy = propagationPolicy
        this
    } ?: newDeleteOptions { this.propagationPolicy = propagationPolicy }

fun TokenReview.hasError() = this.status?.error?.let { true } ?: false
fun TokenReview.errorMessage(): String? = this.status?.error
fun TokenReview.isAuthenticated() = this.status?.authenticated ?: false
