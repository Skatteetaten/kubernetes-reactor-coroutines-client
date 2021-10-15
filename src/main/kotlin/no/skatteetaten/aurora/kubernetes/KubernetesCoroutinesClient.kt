package no.skatteetaten.aurora.kubernetes

import com.fkorotkov.kubernetes.newObjectMeta
import io.fabric8.kubernetes.api.model.DeleteOptions
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Status
import io.fabric8.kubernetes.api.model.autoscaling.v1.Scale
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.User
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
class KubernetesCoroutinesClient(val client: KubernetesReactorClient, val tokenFetcher: TokenFetcher?) {

    /**
     * Simplifies creation of client, mainly useful for tests
     */
    constructor(
        baseUrl: String,
        token: String,
        retryConfiguration: RetryConfiguration = RetryConfiguration()
    ) : this(
        KubernetesReactorClient(
            WebClient.create(baseUrl),
            object : TokenFetcher {
                override fun token(audience: String?) = token
            },
            retryConfiguration
        ),
        object : TokenFetcher {
            override fun token(audience: String?) = token
        }
    )

    /**
     * Get a single resource with a given name or namespace
     *
     * @param metadata Fetch a given resource using the namespace and name on this metadata object
     * @return         A resource of the type Kind that is fetched reified from the variable you assign the result to
     **/
    suspend inline fun <reified Kind : HasMetadata> getOrNull(metadata: ObjectMeta, token: String? = null, audience: String? = null): Kind? =
        client.get<Kind>(metadata, getToken(getToken(token, audience), null), null).awaitFirstOrNull()

    suspend inline fun <reified Kind : HasMetadata> get(metadata: ObjectMeta, token: String? = null, audience: String? = null): Kind =
        getOrNull(metadata, getToken(getToken(token, audience), null), null) ?: throwResourceNotFoundException(metadata)

    suspend inline fun <reified Kind : HasMetadata> getOrNull(resource: Kind, token: String? = null, audience: String? = null): Kind? =
        client.get(resource, getToken(getToken(token, audience), null), null).awaitFirstOrNull()

    suspend inline fun <reified Kind : HasMetadata> get(resource: Kind, token: String? = null, audience: String? = null): Kind =
        getOrNull(resource, getToken(token, audience), null) ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified Kind : HasMetadata> getMany(resource: Kind, token: String? = null, audience: String? = null): List<Kind> =
        client.getMany(resource, getToken(getToken(token, audience), null), audience).awaitFirstOrNull() ?: emptyList()


    suspend inline fun <reified Kind : HasMetadata> getMany(metadata: ObjectMeta? = null, token: String? = null, audience: String? = null): List<Kind> =
        client.getMany<Kind>(metadata, getToken(token, audience), null).awaitFirstOrNull() ?: emptyList()

    suspend fun currentUser(token: String): User? = client.currentUser(token).awaitFirstOrNull()

    suspend inline fun <reified Input : HasMetadata> post(resource: Input, token: String? = null, audience: String? = null): Input =
        client.post(resource = resource, token = getToken(token, audience), audience = null).awaitFirstOrNull() ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified Input : HasMetadata> put(resource: Input, token: String? = null, audience: String? = null): Input =
        client.put(resource = resource, token = getToken(token, audience), audience = null).awaitFirstOrNull() ?: throwResourceNotFoundException(resource.metadata)


    suspend inline fun <reified Input : HasMetadata> deleteBackground(
        resource: Input,
        options: DeleteOptions? = null,
        token: String? = null
    ): Status =
        client.deleteBackground(resource = resource, deleteOptions = options, token = token).awaitFirstOrNull()
            ?: throw ResourceNotFoundException(notFoundMsg<Input>(resource.metadata))

    suspend inline fun <reified Input : HasMetadata> deleteForeground(
        resource: Input,
        options: DeleteOptions? = null,
        token: String? = null
    ): Input =
        client.deleteForeground(resource = resource, deleteOptions = options, token = getToken(token)).awaitFirstOrNull()
            ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified Input : HasMetadata> deleteOrphan(
        resource: Input,
        options: DeleteOptions? = null,
        token: String? = null
    ): Input =
        client.deleteOrphan(resource = resource, deleteOptions = options, token = getToken(token)).awaitFirstOrNull() ?: throwResourceNotFoundException(resource.metadata)

    suspend inline fun <reified T : Any> proxyGet(
        pod: Pod,
        port: Int,
        path: String,
        headers: Map<String, String> = emptyMap(),
        token: String? = null
    ): T = client.proxyGet<T>(pod = pod, port = port, path = path, headers = headers, token = getToken(token)).awaitFirstOrNull()
            ?: throw ResourceNotFoundException(notFoundMsg<T>(pod.metadata))

    suspend inline fun scaleDeploymentConfig(namespace: String, name: String, count: Int, token: String? = null) : Scale =
        client.scaleDeploymentConfig(namespace = namespace, name = name, count = count, token = getToken(token)).awaitFirstOrNull()
            ?: throw ResourceNotFoundException(notFoundMsg<Scale>(namespace, name))

    suspend fun rolloutDeploymentConfig(namespace: String, name: String, token: String? = null): DeploymentConfig =
        client.rolloutDeploymentConfig(namespace = namespace, name = name, token = getToken(token)).awaitFirstOrNull()
            ?: throw ResourceNotFoundException(notFoundMsg<Scale>(namespace, name))

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

    suspend fun getToken(token: String?) = token ?: tokenFetcher?.coToken(null)
    suspend fun getToken(token: String?, audience: String?) = token ?: tokenFetcher?.coToken(audience)
}