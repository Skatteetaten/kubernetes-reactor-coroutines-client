package no.skatteetaten.aurora.kubernetes

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fkorotkov.kubernetes.extensions.metadata
import com.fkorotkov.kubernetes.extensions.newScale
import com.fkorotkov.kubernetes.extensions.spec
import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newProject
import com.fkorotkov.openshift.newRoute
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerList
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.kubernetes.api.model.authorization.SelfSubjectAccessReview
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.ProjectList
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.api.model.RouteList
import io.fabric8.openshift.api.model.User
import mu.KotlinLogging
import no.skatteetaten.aurora.kubernetes.KubernetesApiGroup.REPLICATIONCONTROLLER
import no.skatteetaten.aurora.kubernetes.KubernetesApiGroup.SELFSUBJECTACCESSREVIEW
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.IMAGESTREAMTAG
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.PROJECT
import no.skatteetaten.aurora.kubernetes.OpenShiftApiGroup.USER
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

private val logger = KotlinLogging.logger {}

abstract class AbstractKubernetesClient(val webClient: WebClient, val token: String? = null) {

    suspend fun scale(ns: String, n: String, count: Int): JsonNode {
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
            .awaitBody()
    }

    suspend fun deploy(namespace: String, name: String): JsonNode {
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
            .awaitBody()
    }

    suspend fun pods2(namespace: String, name: String): Pod {
        val pod = newPod {
            metadata {
                this.name = name
                this.namespace = namespace
            }
        }
        return webClient.get().kubernetesResource(pod)
    }

    suspend fun deploymentConfig(namespace: String, name: String): DeploymentConfig {
        val dc = newDeploymentConfig {
            metadata {
                this.name = name
                this.namespace = namespace
            }
        }

        return webClient.get().kubernetesResource(dc)
    }

    suspend fun applicationDeployment(namespace: String, name: String): ApplicationDeployment {
        val r = SkatteetatenKubernetesResource("ApplicationDeployment", namespace, name)
        return webClient.get().kubernetesResource(r)
    }

    suspend fun applicationDeployments(namespace: String): ApplicationDeploymentList {
        val r = SkatteetatenKubernetesResource("ApplicationDeployment", namespace)
        return webClient.get().kubernetesResource(r)
    }

    suspend fun route(namespace: String, name: String): Route {
        val r = newRoute {
            metadata = newObjectMeta {
                this.namespace = namespace
                this.name = name
            }
        }
        return webClient.get().kubernetesResource(r)
    }

    suspend fun routes(namespace: String, labels: Map<String, String> = emptyMap()): RouteList {
        val r = newRoute {
            metadata {
                this.namespace = namespace
            }
        }
        return webClient.get().kubernetesResources(r, labels)
    }

    suspend fun services(namespace: String?, labels: Map<String, String> = emptyMap()): ServiceList {
        val s = newService {
            metadata = newObjectMeta {
                this.namespace = namespace
            }
        }

        return webClient.get().kubernetesResources(s, labels)
    }

    suspend fun pods(namespace: String, labels: Map<String, String> = emptyMap()): PodList {
        val p = newPod {
            metadata {
                this.namespace = namespace
            }
        }

        return webClient.get().kubernetesResources(p, labels)
    }

    suspend fun replicationControllers(namespace: String): ReplicationControllerList {
        return webClient
            .get()
            .kubernetesResource(apiGroup = REPLICATIONCONTROLLER, namespace = namespace)
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    suspend fun replicationController(namespace: String, rcName: String): ReplicationController {
        return webClient
            .get()
            .kubernetesResource(REPLICATIONCONTROLLER, namespace, rcName)
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    suspend fun imageStreamTag(namespace: String, name: String, tag: String): ImageStreamTag {
        return webClient
            .get()
            .kubernetesResource(IMAGESTREAMTAG, namespace, "$name:$tag")
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    suspend fun project(namespace: String): Project {
        return webClient
            .get()
            .kubernetesResource(apiGroup = PROJECT, name = namespace)
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    suspend fun projects(): ProjectList {
        val project = newProject {
            metadata {
                this.name = name
                this.namespace = namespace
            }
        }

        return webClient.get().kubernetesResources(project)
    }

    suspend fun selfSubjectAccessView(review: SelfSubjectAccessReview): SelfSubjectAccessReview {
        val uri = SELFSUBJECTACCESSREVIEW.uri()
        return webClient
            .post()
            .uri(uri.template, uri.variables)
            .body(BodyInserters.fromValue(review))
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    suspend fun user(): User {
        return webClient
            .get()
            .uri(USER.uri().expand())
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    private suspend inline fun <reified Kind : HasMetadata, reified T : Any> WebClient.RequestHeadersUriSpec<*>.kubernetesResource(
        resource: Kind
    ): T {
        val variables = mapOf(
            "namespace" to resource.metadata.namespace,
            "kind" to "${resource.kind.toLowerCase()}s",
            "name" to resource.metadata.name
        )

        return this.uri(resource.uri(), variables)
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    private suspend inline fun <reified Kind : HasMetadata, reified KindList : KubernetesResourceList<Kind>>
        WebClient.RequestHeadersUriSpec<*>.kubernetesResources(
            resource: Kind,
            labels: Map<String, String> = emptyMap()
        ): KindList {
        val variables = mapOf(
            "namespace" to resource.metadata.namespace,
            "kind" to "${resource.kind.toLowerCase()}s",
            "name" to resource.metadata.name
        )

        val spec = if (labels.isEmpty()) {
            this.uri(resource.uri(), variables)
        } else {
            this.uri { builder ->
                builder.path(resource.uri())
                    .queryParam("labelSelector", labels.map { "${it.key}=${it.value}" }.joinToString(","))
                    .build(variables)
            }
        }

        return spec.bearerToken(token).retrieve().awaitBody()
    }

    private fun WebClient.RequestHeadersSpec<*>.bearerToken(token: String?) =
        token?.let {
            this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        } ?: this

    fun WebClient.RequestHeadersUriSpec<*>.kubernetesResource(
        apiGroup: ApiGroup,
        namespace: String? = null,
        name: String? = null,
        labels: Map<String, String> = emptyMap()
    ): WebClient.RequestHeadersSpec<*> {
        val uri = apiGroup.uri(namespace, name)
        return if (labels.isEmpty()) {
            this.uri(uri.template, uri.variables)
        } else {
            this.uri { builder ->
                builder.path(uri.template)
                    .queryParam("labelSelector", labels.map { "${it.key}=${it.value}" }.joinToString(","))
                    .build(uri.variables)
            }
        }
    }
}

interface UserTokenFetcher {
    fun getUserToken(): String
}

class KubernetesServiceAccountClient(webClient: WebClient) : AbstractKubernetesClient(webClient)
class KubernetesUserTokenClient(token: String, webClient: WebClient) : AbstractKubernetesClient(webClient, token)

class KubernetesClient(
    private val webClient: WebClient,
    private val userTokenFetcher: UserTokenFetcher
) {
    private val kubernetesServiceAccountClient = KubernetesServiceAccountClient(webClient)
    fun serviceAccount() = kubernetesServiceAccountClient

    fun userToken(token: String = getUserToken()) = KubernetesUserTokenClient(token, webClient)

    private fun getUserToken() = userTokenFetcher.getUserToken()
}

fun HasMetadata.uri(): String {
    val contextRoot = if (this.apiVersion == "v1") {
        "/api"
    } else {
        "/apis"
    }

    return this.metadata.namespace?.let {
        "$contextRoot/${this.apiVersion}/namespaces/{namespace}/{kind}/{name}"
    } ?: "$contextRoot/${this.apiVersion}/{kind}/{name}"
}

private class SkatteetatenKubernetesResource(private val kind: String, namespace: String, name: String? = null) :
    HasMetadata {

    private val metadata = newObjectMeta {
        this.namespace = namespace
        this.name = name
    }

    override fun getMetadata() = metadata

    override fun getKind() = kind

    override fun getApiVersion() = "skatteetaten.no/v1"

    override fun setMetadata(metadata: ObjectMeta?) =
        throw UnsupportedOperationException("Cannot set metadata on SkatteetatenResource")

    override fun setApiVersion(version: String?) =
        throw UnsupportedOperationException("Cannot set apiVersion on SkatteetatenResource")
}
