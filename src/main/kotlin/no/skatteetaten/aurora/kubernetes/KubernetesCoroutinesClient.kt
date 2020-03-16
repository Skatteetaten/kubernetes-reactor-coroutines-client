package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.newObjectMeta
import io.fabric8.kubernetes.api.model.DeleteOptions
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Status
import io.fabric8.kubernetes.api.model.v1.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.web.reactive.function.client.WebClient

/**
 * A version of the client that uses Kotlin Coroutines
 *
 * Note that all the methods in this class are inlined so it _cannot_ be mocked.
 * If you need to mock calls using this client create a wrapper class in your code and mock that.
 *
 * This class is a simple wrapper around the Reactive Class that does all the heavy lifting
 *
 * @param client An instance of the reactive client that is used to back this client.
 **/
class KubernetesCoroutinesClient(val client: KubernetesReactorClient) {

    /**
     * Simplifies creation to client, mainly useful for tests
     */
    constructor(webClient: WebClient, tokenFetcher: TokenFetcher, retryConfiguration: RetryConfiguration) : this(
        KubernetesReactorClient(webClient, tokenFetcher, retryConfiguration)
    )

    /**
     * Simplifies creation to client, mainly useful for tests
     */
    constructor(
        webClient: WebClient,
        token: String,
        retryConfiguration: RetryConfiguration = RetryConfiguration()
    ) : this(
        KubernetesReactorClient(webClient, object : TokenFetcher {
            override fun token() = token
        }, retryConfiguration)
    )

    /**
     * Get a single resource with a given name or namespace
     *
     * @param metadata Fetch a given resource using the namespace and name on this metadata object
     * @return         A resource of the type Kind that is fetched reified from the variable you assign the result too
     **/
    suspend inline fun <reified Kind : HasMetadata> getOrNull(metadata: ObjectMeta): Kind? =
        client.get<Kind>(metadata).awaitFirstOrNull()

    suspend inline fun <reified Kind : HasMetadata> get(metadata: ObjectMeta): Kind =
        getOrNull(metadata) ?: throwResourceNotFoundException(metadata)

    suspend inline fun <reified Kind : HasMetadata> getOrNull(resource: Kind): Kind? =
        client.get(resource).awaitFirstOrNull()

    suspend inline fun <reified Kind : HasMetadata> get(resource: Kind): Kind =
        getOrNull(resource) ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified Kind : HasMetadata> getMany(resource: Kind): List<Kind> {
        return client.getMany(resource).awaitFirstOrNull() ?: emptyList()
    }

    suspend inline fun <reified Kind : HasMetadata> getMany(metadata: ObjectMeta? = null): List<Kind> {
        return client.getMany<Kind>(metadata).awaitFirstOrNull() ?: emptyList()
    }

    suspend inline fun <reified Input : HasMetadata> post(resource: Input): Input =
        client.post(resource).awaitFirstOrNull() ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified Input : HasMetadata> put(resource: Input): Input =
        client.put(resource).awaitFirstOrNull() ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified Input : HasMetadata> deleteBackground(
        resource: Input,
        options: DeleteOptions? = null
    ): Status =
        client.deleteBackground(resource, options).awaitFirstOrNull()
            ?: throw ResourceNotFoundException(notFoundMsg<Input>(resource.metadata))

    suspend inline fun <reified Input : HasMetadata> deleteForeground(
        resource: Input,
        options: DeleteOptions? = null
    ): Input =
        client.deleteForeground(resource, options).awaitFirstOrNull()
            ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified Input : HasMetadata> deleteOrphan(
        resource: Input,
        options: DeleteOptions? = null
    ): Input =
        client.deleteOrphan(resource, options).awaitFirstOrNull() ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified T : Any> proxyGet(
        pod: Pod,
        port: Int,
        path: String,
        headers: Map<String, String> = emptyMap()
    ): T {
        return client.proxyGet<T>(pod, port, path, headers).awaitFirstOrNull()
            ?: throw ResourceNotFoundException(notFoundMsg<T>(pod.metadata))
    }

    suspend inline fun scaleDeploymentConfig(namespace: String, name: String, count: Int): Scale {
        return client.scaleDeploymentConfig(namespace, name, count).awaitFirstOrNull()
            ?: throw ResourceNotFoundException(notFoundMsg<Scale>(namespace, name))
    }

    suspend fun rolloutDeploymentConfig(namespace: String, name: String): DeploymentConfig {
        return client.rolloutDeploymentConfig(namespace, name).awaitFirstOrNull()
            ?: throw ResourceNotFoundException(notFoundMsg<Scale>(namespace, name))
    }

    inline fun <reified Kind> throwResourceNotFoundException(metadata: ObjectMeta?): Kind {
        throw ResourceNotFoundException(notFoundMsg<Kind>(metadata))
    }

    inline fun <reified Kind> notFoundMsg(namespace: String, name: String) =
        notFoundMsg<Kind>(newObjectMeta {
            this.namespace = namespace
            this.name = name
        })

    inline fun <reified Kind> notFoundMsg(metadata: ObjectMeta?) =
        "Resource with name=${metadata?.name} namespace=${metadata?.namespace} kind=${Kind::class.simpleName} was not found"
}