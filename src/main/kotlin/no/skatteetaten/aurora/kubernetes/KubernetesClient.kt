package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.authorization.newSelfSubjectAccessReview
import com.fkorotkov.kubernetes.extensions.metadata
import com.fkorotkov.kubernetes.extensions.newScale
import com.fkorotkov.kubernetes.extensions.spec
import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newReplicationController
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newImageStreamTag
import com.fkorotkov.openshift.newProject
import com.fkorotkov.openshift.newRoute
import com.fkorotkov.openshift.newUser
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerList
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.kubernetes.api.model.authorization.SelfSubjectAccessReview
import io.fabric8.kubernetes.api.model.extensions.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.ProjectList
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.api.model.RouteList
import io.fabric8.openshift.api.model.User
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

abstract class AbstractKubernetesClient(private val webClient: WebClient, private val token: String? = null) {

    suspend fun scale(namespace: String, name: String, count: Int): Scale {
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
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    suspend fun deploy(namespace: String, name: String): DeploymentConfig {
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
            metadata {
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
            metadata {
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
        val rc = newReplicationController {
            metadata {
                this.namespace = namespace
            }
        }

        return webClient.get().kubernetesResources(rc)
    }

    suspend fun replicationController(namespace: String, name: String): ReplicationController {
        val rc = newReplicationController {
            metadata {
                this.namespace = namespace
                this.name = name
            }
        }

        return webClient.get().kubernetesResource(rc)
    }

    suspend fun imageStreamTag(namespace: String, name: String, tag: String): ImageStreamTag {
        val ist = newImageStreamTag {
            metadata {
                this.namespace = namespace
                this.name = "$name:$tag"
            }
        }

        return webClient.get().kubernetesResource(ist)
    }

    suspend fun project(name: String): Project {
        val p = newProject {
            metadata {
                this.name = name
            }
        }

        return webClient.get().kubernetesResource(p)
    }

    suspend fun projects(): ProjectList {
        return webClient.get().kubernetesResources(newProject { metadata {} })
    }

    suspend fun selfSubjectAccessView(review: SelfSubjectAccessReview): SelfSubjectAccessReview {
        return webClient.post().kubernetesResource(newSelfSubjectAccessReview { }, review)
    }

    suspend fun user(): User {
        val u = newUser {
            metadata {
                this.name = "~"
            }
        }
        return webClient.get().kubernetesResource(u)
    }

    private suspend inline fun <reified Kind : HasMetadata, reified T : Any> WebClient.RequestBodyUriSpec.kubernetesResource(
        resource: Kind,
        body: Any
    ): T {
        return this.uri(resource.uri(), resource.uriVariables())
            .body(BodyInserters.fromValue(body))
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    private suspend inline fun <reified Kind : HasMetadata, reified T : Any> WebClient.RequestHeadersUriSpec<*>.kubernetesResource(
        resource: Kind
    ): T {
        return this.uri(resource.uri(), resource.uriVariables())
            .bearerToken(token)
            .retrieve()
            .awaitBody()
    }

    private suspend inline fun <reified Kind : HasMetadata, reified KindList : KubernetesResourceList<Kind>>
        WebClient.RequestHeadersUriSpec<*>.kubernetesResources(
            resource: Kind,
            labels: Map<String, String> = emptyMap()
        ): KindList {
        val spec = if (labels.isEmpty()) {
            this.uri(resource.uri(), resource.uriVariables())
        } else {
            this.uri { builder ->
                builder.path(resource.uri())
                    .queryParam("labelSelector", labels.map { "${it.key}=${it.value}" }.joinToString(","))
                    .build(resource.uriVariables())
            }
        }

        return spec.bearerToken(token).retrieve().awaitBody()
    }

    private fun WebClient.RequestHeadersSpec<*>.bearerToken(token: String?) =
        token?.let {
            this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        } ?: this
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

fun HasMetadata.uriVariables() = mapOf(
    "namespace" to this.metadata?.namespace,
    "kind" to "${this.kind.toLowerCase()}s",
    "name" to this.metadata?.name
)

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
        throw UnsupportedOperationException("Cannot set metadata on SkatteetatenKubernetesResource")

    override fun setApiVersion(version: String?) =
        throw UnsupportedOperationException("Cannot set apiVersion on SkatteetatenKubernetesResource")
}
