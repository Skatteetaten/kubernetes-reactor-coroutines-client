package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newUser
import io.fabric8.kubernetes.api.model.HasMetadata
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.retry.Retry
import java.time.Duration

class ResourceNotFoundException(m: String) : RuntimeException(m)

private val logger = KotlinLogging.logger {}

abstract class AbstractKubernetesClient(val webClient: WebClient, val tokenFetcher: TokenFetcher) {

    inline fun <reified T : Any> WebClient.RequestHeadersSpec<*>.perform() =
        this.bearerToken(tokenFetcher.token())
            .retrieve()
            .bodyToMono<T>()
            .notFoundAsEmpty()
            .retryWithLog(3L, 100L, 1000L)

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

