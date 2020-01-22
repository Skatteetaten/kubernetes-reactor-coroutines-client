package no.skatteetaten.aurora.kubernetes.crd

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta

inline fun <reified T : Any> newSkatteetatenKubernetesResource(block: SkatteetatenKubernetesResource.() -> Unit = {}): SkatteetatenKubernetesResource {
    val instance = SkatteetatenKubernetesResource(T::class.simpleName!!)
    instance.block()
    return instance
}

class SkatteetatenKubernetesResource(private val kind: String) : HasMetadata {
    private lateinit var metadata: ObjectMeta

    override fun getMetadata() = metadata

    fun metadata(block: ObjectMeta.() -> Unit) {
        metadata = ObjectMeta()
        metadata.block()
    }

    override fun getKind() = kind

    override fun getApiVersion() = "skatteetaten.no/v1"

    override fun setMetadata(metadata: ObjectMeta) {
        this.metadata = metadata
    }

    override fun setApiVersion(version: String) =
        throw UnsupportedOperationException("Cannot set apiVersion on custom resource")
}
