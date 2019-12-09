package no.skatteetaten.aurora.kubernetes

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fkorotkov.kubernetes.apps.newDeployment
import com.fkorotkov.kubernetes.extensions.metadata
import com.fkorotkov.kubernetes.extensions.newScale
import com.fkorotkov.kubernetes.extensions.spec
import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newPod
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.kubernetes.api.model.authorization.SelfSubjectAccessReview
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.ProjectList
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.api.model.RouteList
import io.fabric8.openshift.api.model.User
import java.net.URI
import java.time.Duration
import mu.KotlinLogging
import no.skatteetaten.aurora.kubernetes.KubernetesApiGroup.POD
import no.skatteetaten.aurora.kubernetes.KubernetesApiGroup.REPLICATIONCONTROLLER
import no.skatteetaten.aurora.kubernetes.KubernetesApiGroup.SELFSUBJECTACCESSREVIEW
import no.skatteetaten.aurora.kubernetes.KubernetesApiGroup.SERVICE
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.APPLICATIONDEPLOYMENT
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.DEPLOYMENTCONFIG
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.IMAGESTREAMTAG
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.PROJECT
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.ROUTE
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.USER
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.retry.Retry
import java.lang.Exception

private val logger = KotlinLogging.logger {}

abstract class AbstractOpenShiftClient(val webClient: WebClient, val token: String? = null) {

    fun scale(ns: String, n: String, count: Int): Mono<JsonNode> {

        val scale = newScale {
            metadata {
                namespace = ns
                name = n
            }
            spec {
                replicas = count
            }
        }
        val uri = OpenShiftApiGroup.DEPLOYMENTCONFIGSCALE.uri(ns, n)
        logger.debug("URL=${uri.expand()} body=${jacksonObjectMapper().writeValueAsString(scale)}")
        return webClient
            .put()
            .uri(uri.template, uri.variables)
            .body(BodyInserters.fromValue(scale))
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun deploy(namespace: String, name: String): Mono<JsonNode> {
        val uri = OpenShiftApiGroup.DEPLOYMENTREQUEST.uri(namespace, name)
        return webClient
            .post()
            .uri(uri.template, uri.variables)
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
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    inline fun <reified Kind : HasMetadata> get(resource:Kind) : Mono<Kind> {
        val variables = mapOf(
            "namespace" to  resource.metadata.namespace,
            "kind" to "${resource.kind.toLowerCase()}s",
            "name" to resource.metadata.name
        )
        return webClient
            .get()
            .uri("/${resource.apiVersion}/{kind}/namespace/{namespace}/{name}", variables)
            .bearerToken(token)
            .retrieve()
            .bodyToMono<Kind>()
            .notFoundAsEmpty()
    }

    fun pods2(namespace:String, name:String) : Mono<Pod> {
        return get(newPod {
            metadata {
                this.name=name
                this.namespace=namespace
            }
        })
    }


    inline fun <reified T : HasMetadata> get(namespace:String, name:String): Mono<T> {

         val apiGroup :ApiGroup= try {
             KubernetesApiGroup.valueOf(T::class.java.simpleName)
         }  catch(e:Exception) {
             OpenShiftApiGroup.valueOf(T::class.java.simpleName)
         }
        return webClient
            .get()
            .openShiftResource(apiGroup, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono<T>()
    }

    fun deploymentConfig(namespace: String, name: String): Mono<DeploymentConfig> {
        return webClient
            .get()
            .openShiftResource(DEPLOYMENTCONFIG, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployment(namespace: String, name: String): Mono<ApplicationDeployment> {
        return webClient
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployments(namespace: String): Mono<ApplicationDeploymentList> {
        return webClient
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun route(namespace: String, name: String): Mono<Route> {
        return webClient
            .get()
            .openShiftResource(ROUTE, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun routes(namespace: String, labelMap: Map<String, String>): Mono<RouteList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = ROUTE, namespace = namespace, labels = labelMap)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun services(namespace: String?, labelMap: Map<String, String>): Mono<ServiceList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = SERVICE, namespace = namespace, labels = labelMap)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun pods(namespace: String, labelMap: Map<String, String>): Mono<PodList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = POD, namespace = namespace, labels = labelMap)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun replicationController(namespace: String, name: String): Mono<ReplicationController> {
        return webClient
            .get()
            .openShiftResource(REPLICATIONCONTROLLER, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun imageStreamTag(namespace: String, name: String, tag: String): Mono<ImageStreamTag> {
        return webClient
            .get()
            .openShiftResource(IMAGESTREAMTAG, namespace, "$name:$tag")
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun project(name: String): Mono<Project> {
        return webClient
            .get()
            .openShiftResource(apiGroup = PROJECT, name = name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun projects(): Mono<ProjectList> {
        return webClient
            .get()
            .openShiftResource(PROJECT)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun selfSubjectAccessView(review: SelfSubjectAccessReview): Mono<SelfSubjectAccessReview> {
        val uri = SELFSUBJECTACCESSREVIEW.uri()
        return webClient
            .post()
            .uri(uri.template, uri.variables)
            .body(BodyInserters.fromValue(review))
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun user(): Mono<User> {
        return webClient
            .get()
            .uri(USER.uri().expand())
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun WebClient.RequestHeadersSpec<*>.bearerToken(token: String?) =
        token?.let {
            this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        } ?: this

    fun WebClient.RequestHeadersUriSpec<*>.openShiftResource(
        apiGroup: ApiGroup,
        namespace: String? = null,
        name: String? = null,
        labels: Map<String, String> = emptyMap()
    ): WebClient.RequestHeadersSpec<*> {
        val uri = apiGroup.uri(namespace, name)
        return if (labels.isEmpty()) {
            this.uri(uri.template, uri.variables)
        } else {
            this.uri {
                it.path(uri.template).queryParam("labelSelector", apiGroup.labelSelector(labels)).build(uri.variables)
            }
        }
    }
}

interface UserTokenFetcher {
    fun getUserToken() : String
}

class KubernetesServiceAccountClient(webClient: WebClient) : AbstractOpenShiftClient(webClient)
class KubernetesUserTokenClient(token: String, webClient: WebClient) : AbstractOpenShiftClient(webClient, token)

class KubernetesClient(
    private val webClient: WebClient,
    private val userTokenFetcher: UserTokenFetcher
) {
    private val kubernetesServiceAccountClient = KubernetesServiceAccountClient(webClient)
    fun serviceAccount() = kubernetesServiceAccountClient

    fun userToken(token: String = getUserToken()) = KubernetesUserTokenClient(token, webClient)

    private fun getUserToken() = userTokenFetcher.getUserToken()
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

data class RequestedOpenShiftResource(val namespace: String?, val kind: String?, val name: String?)

fun URI.requestedOpenShiftResource() =
    "/namespaces/(.+)/(.+)/(.+)".toRegex()
        .find(this.path)
        ?.groupValues
        ?.takeIf { it.size == 4 }
        ?.let {
            RequestedOpenShiftResource(it[1], it[2], it[3])
        }

fun <T> Mono<T>.notFoundAsEmpty() = this.onErrorResume {
    when (it) {
        is WebClientResponseException.NotFound -> {
            val resource = it.request?.uri?.requestedOpenShiftResource()
            logger.info {
                "Resource not found, method=${it.request?.method} uri=${it.request?.uri} " +
                    "namespace=${resource?.namespace} kind=${resource?.kind} name=${resource?.name}"
            }
            Mono.empty()
        }
        else -> Mono.error(it)
    }
}

private const val defaultFirstRetryInMs: Long = 100
private const val defaultMaxRetryInMs: Long = 2000

fun <T> Mono<T>.blockForResource(
    retryFirstInMs: Long = defaultFirstRetryInMs,
    retryMaxInMs: Long = defaultMaxRetryInMs
) =
    this.notFoundAsEmpty().retryWithLog(retryFirstInMs, retryMaxInMs).block()

fun <T : HasMetadata?> Mono<out KubernetesResourceList<T>>.blockForList(
    retryFirstInMs: Long = defaultFirstRetryInMs,
    retryMaxInMs: Long = defaultMaxRetryInMs
): List<T> = this.blockForResource(retryFirstInMs, retryMaxInMs)?.items ?: emptyList()

