package no.skatteetaten.aurora.kubernetes

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fkorotkov.kubernetes.newObjectMeta
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta

//TODO: Disse bør ut hverfra, men de kan ligge i test.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentSpec(
    val applicationId: String,
    val applicationName: String?,
    val applicationDeploymentId: String,
    val applicationDeploymentName: String?,
    val databases: List<String>?,
    val splunkIndex: String? = null,
    val managementPath: String?,
    val releaseTo: String?,
    val deployTag: String?,
    val selector: Map<String, String>,
    val command: ApplicationDeploymentCommand,
    val message: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentCommand(
    val overrideFiles: Map<String, String> = emptyMap(),
    val applicationDeploymentRef: ApplicationDeploymentRef,
    val auroraConfig: AuroraConfigRef
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuroraConfigRef(
    val name: String,
    val refName: String,
    val resolvedRef: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentList(
    val items: List<ApplicationDeployment> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentRef(val environment: String, val application: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = ["apiVersion", "kind", "metadata", "spec"])
@JsonDeserialize(using = JsonDeserializer.None::class)
data class ApplicationDeployment(
    val spec: ApplicationDeploymentSpec,
    @JsonIgnore
    var _metadata: ObjectMeta?,
    @JsonIgnore
    val _kind: String = "ApplicationDeployment",
    @JsonIgnore
    var _apiVersion: String = "skatteetaten.no/v1"
) : HasMetadata { // or just KubernetesResource?
    override fun getMetadata(): ObjectMeta {
        return _metadata ?: ObjectMeta()
    }

    override fun getKind(): String {
        return _kind
    }

    override fun getApiVersion(): String {
        return _apiVersion
    }

    override fun setMetadata(metadata: ObjectMeta?) {
        _metadata = metadata
    }

    override fun setApiVersion(version: String?) {
        _apiVersion = apiVersion
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = ["apiVersion", "kind", "metadata", "spec"])
@JsonDeserialize(using = JsonDeserializer.None::class)
data class ApplicationDeploymentStub(
    val name: String? = null,
    val namespace: String? = null,
    @JsonIgnore
    var _metadata: ObjectMeta? = newObjectMeta {
        this.namespace = namespace
        this.name = name
    },
    @JsonIgnore
    val _kind: String = "ApplicationDeployment",
    @JsonIgnore
    var _apiVersion: String = "skatteetaten.no/v1"
) : HasMetadata {
    override fun getMetadata(): ObjectMeta {
        return _metadata ?: ObjectMeta()
    }

    override fun getKind(): String {
        return _kind
    }

    override fun getApiVersion(): String {
        return _apiVersion
    }

    override fun setMetadata(metadata: ObjectMeta?) {
        _metadata = metadata
    }

    override fun setApiVersion(version: String?) {
        _apiVersion = apiVersion
    }
}
