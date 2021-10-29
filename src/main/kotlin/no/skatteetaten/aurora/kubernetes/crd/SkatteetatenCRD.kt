package no.skatteetaten.aurora.kubernetes.crd

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta

/**
 An example CRD base class that can be extended for your own organizations CRD resources
 This is here as a convenience since extending the HasMetadata interface in kotlin is kind of a PITA
 */
abstract class SkatteetatenCRD(private val kind: String) : HasMetadata {

    private lateinit var metadata: ObjectMeta
    private var apiVersion: String = "skatteetaten.no/v1"

    override fun getMetadata() = metadata

    fun metadata(block: ObjectMeta.() -> Unit) {
        metadata = ObjectMeta()
        metadata.block()
    }

    override fun getKind(): String = kind

    override fun getApiVersion() = apiVersion

    override fun setMetadata(data: ObjectMeta) {
        metadata = data
    }

    override fun setApiVersion(version: String) {
        this.apiVersion = version
    }
}
