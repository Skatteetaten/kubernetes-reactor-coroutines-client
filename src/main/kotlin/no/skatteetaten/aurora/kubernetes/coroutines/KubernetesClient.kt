package no.skatteetaten.aurora.kubernetes.coroutines

import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.v1.metadata
import com.fkorotkov.kubernetes.v1.newScale
import com.fkorotkov.kubernetes.v1.spec
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
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
import no.skatteetaten.aurora.kubernetes.AbstractKubernetesClient
import no.skatteetaten.aurora.kubernetes.ResourceNotFoundException
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.kubernetes.uri
import no.skatteetaten.aurora.kubernetes.uriVariables
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

class KubernetesClient(
    webClient: WebClient,
    tokenFetcher: TokenFetcher
) : AbstractKubernetesClient(webClient, tokenFetcher) {

    constructor(webClient: WebClient, token: String): this(webClient, object: TokenFetcher {
        override fun token() = token
    })

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

     @param resource:Kind a KubernetesResources implementing HasMetadata
     @return Kind?: Either the result or null, if an error occurs and Exception will be thrown
     */
    suspend inline fun <reified Kind : HasMetadata> getOrNull(resource: Kind): Kind? =
        webClient.get().kubernetesUri(resource).perform<Kind>().awaitFirstOrNull()

    /*
    Get a single resource given a resource template.

    The fields metadata.namespace and metadata.name are required. Labels in the resource are ignored for this operation

    @param resource:Kind a KubernetesResources implementing HasMetadata
    @return Kind: The result
    @throws ResourceNotFoundException if the given resource was not found
    */
    suspend inline fun <reified Kind : HasMetadata> get(resource: Kind): Kind {
        return getOrNull(resource)
            ?: throw ResourceNotFoundException("Resource with name=${resource.metadata?.name} namespace=${resource.metadata?.namespace} kind=${resource.kind} was not found")
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

    suspend inline fun <reified Kind : HasMetadata> getList(resource: Kind): List<Kind> {
        return webClient.get()
            .kubernetesUri(resource)
            .perform<KubernetesResourceList<Kind>>()
            .map { it.items }.awaitSingle()
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
}