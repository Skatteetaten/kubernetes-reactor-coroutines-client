package no.skatteetaten.aurora.kubernetes.reactor

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import no.skatteetaten.aurora.kubernetes.AbstractKubernetesClient
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class KubernetesClientReactor(webClient: WebClient, tokenFetcher: TokenFetcher) :
    AbstractKubernetesClient(webClient, tokenFetcher) {

    constructor(webClient: WebClient, token: String) : this(webClient, object : TokenFetcher {
        override fun token() = token
    })

    /*
    Get a single resource given a resource template.

    The fields metadata.namespace and metadata.name are required. Labels in the resource are ignored for this operation

    @param resource:Kind a KubernetesResources implementing HasMetadata
    @return Mono<Kind>: A mono that can either be a result, empty (if the resource is not found) or an exception if it fails
    */
    inline fun <reified Kind : HasMetadata> get(resource: Kind): Mono<Kind> {
        return webClient.get().kubernetesUri(resource, labels = emptyMap()).perform()
    }

    //TODO: if the resource that we get in here has a name the wrong url will be generated
    inline fun <reified Kind : HasMetadata> getList(resource: Kind): Mono<List<Kind>> {
        return webClient.get()
            .kubernetesUri(resource)
            .perform<KubernetesResourceList<Kind>>()
            .map { it.items }
    }
}