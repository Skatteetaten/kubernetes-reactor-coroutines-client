package no.skatteetaten.aurora.kubernetes

import io.fabric8.kubernetes.api.model.DeleteOptions
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Status
import io.fabric8.kubernetes.api.model.v1.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull

/*
  A version of the client that uses Kotlin Coroutines

  Note that all the methods in this class are inlined so it _cannot_ be mocked.
  If you need to mock calls using this client create a wrapper class in your code and mock that.
 */
class KubernetesCoroutinesClient(val client: KubernetesReactiveClient) {

    /*
     Get a single resource resource with a given name or namepace

     @param metadata Fetch a given resource using the namespace and name on this metadata object
     @return         A resource of the type Kind that is fetched reified from the variable you assign the result too
     */
    suspend inline fun <reified Kind : HasMetadata> getOrNull(metadata: ObjectMeta): Kind? =
        client.get<Kind>(metadata).awaitFirstOrNull()

    suspend inline fun <reified Kind : HasMetadata> get(metadata: ObjectMeta): Kind =
        getOrNull(metadata)
            ?: throw ResourceNotFoundException("Resource with name=${metadata.name} namespace=${metadata.namespace} kind=${Kind::class.simpleName} was not found")

    suspend inline fun <reified Kind : HasMetadata> getOrNull(resource: Kind): Kind? =
        client.get(resource).awaitFirstOrNull()

    suspend inline fun <reified Kind : HasMetadata> get(resource: Kind): Kind =
        getOrNull(resource)
            ?: throw ResourceNotFoundException("Resource with name=${resource.metadata?.name} namespace=${resource.metadata?.namespace} kind=${resource.kind} was not found")

    suspend inline fun <reified Kind : HasMetadata> getMany(resource: Kind): List<Kind> {
        return client.getMany(resource).awaitFirst()
    }

    suspend inline fun <reified Kind : HasMetadata> getMany(metadata: ObjectMeta?): List<Kind> {
        return client.getMany<Kind>(metadata).awaitFirst()
    }

    suspend inline fun <reified Input : HasMetadata> post(resource: Input): Input =
        client.post(resource).awaitFirst()

    suspend inline fun <reified Input : HasMetadata> put(resource: Input): Input =
        client.put(resource).awaitFirst()

    suspend inline fun <reified Input : HasMetadata> delete(resource: Input, options: DeleteOptions? = null): Status =
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