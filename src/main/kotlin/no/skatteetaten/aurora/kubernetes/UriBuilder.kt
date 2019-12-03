package no.skatteetaten.aurora.kubernetes

import mu.KotlinLogging
import org.springframework.web.util.UriComponentsBuilder

private val logger = KotlinLogging.logger {}

data class OpenShiftUri(val template: String, val variables: Map<String, String?>) {
    fun expand() = UriComponentsBuilder.fromPath(template).buildAndExpand(variables).toUriString()
}

interface ApiGroup {
    fun uri(namespace: String? = null, name: String? = null): OpenShiftUri
    fun labelSelector(labels: Map<String, String> = emptyMap()) =
        labels.map { "${it.key}=${it.value}" }.joinToString(",")
}

enum class OpenShiftApiGroup(
    private val label: String,
    private val suffix: String = "",
    private val alternateName: String? = null
) : ApiGroup {
    APPLICATIONDEPLOYMENT("skatteetaten.no/v1"),
    DEPLOYMENTCONFIG("apps"),
    DEPLOYMENTCONFIGSCALE("apps", "/scale", alternateName = "deploymentconfig"),
    DEPLOYMENTREQUEST("apps", "/instantiate", alternateName = "deploymentconfig"),
    ROUTE("route"),
    USER("user", "/~"),
    PROJECT("project"),
    IMAGESTREAMTAG("image");

    override fun uri(namespace: String?, name: String?): OpenShiftUri {
        val path = if (label.contains(".")) {
            "/apis/$label"
        } else {
            "/apis/$label.openshift.io/v1"
        }

        val ns = ns(namespace)
        val n = n(name)
        val uriTemplate = "$path$ns$kind$n$suffix"
        val variables = mapOf(
            "namespace" to namespace,
            "kind" to "${(alternateName ?: this.name).toLowerCase()}s",
            "name" to name
        )
        logger.debug { "uri template=$uriTemplate variables=$variables" }
        return OpenShiftUri(
            uriTemplate, variables
        )
    }
}

enum class KubernetesApiGroup(private val label: String) : ApiGroup {
    SERVICE("services"),
    POD("pods"),
    REPLICATIONCONTROLLER("replicationcontrollers"),
    IMAGESTREAMTAG("imagestreamtags"),
    SELFSUBJECTACCESSREVIEW("authorization.k8s.io");

    override fun uri(namespace: String?, name: String?): OpenShiftUri {
        val path = if (label.contains(".")) {
            "/apis/$label/v1"
        } else {
            "/api/v1"
        }

        val ns = ns(namespace)
        val n = n(name)
        val uriTemplate = "$path$ns$kind$n"
        return OpenShiftUri(
            uriTemplate, mapOf(
                "namespace" to namespace,
                "kind" to "${this.name.toLowerCase()}s",
                "name" to name
            )
        )
    }
}

fun ns(namespace: String?) = namespace?.let { "/namespaces/{namespace}" } ?: ""
fun n(name: String?) = name?.let { "/{name}" } ?: ""
private const val kind = "/{kind}"
