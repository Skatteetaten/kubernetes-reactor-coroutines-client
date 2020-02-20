package no.skatteetaten.aurora.kubernetes.crd

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta

/*
  An example CRD base class that can be extended for your own organizations CRD resources
  This is here as a convenience since extending the HasMetadata interface in kotlin is kind of a PITA

  You should make sure that classes extending this class should not have any required fields
 */
abstract class SkatteetatenCRD(private val _kind: String) : HasMetadata {

    private lateinit var metadata: ObjectMeta

    override fun getMetadata() = metadata

    fun metadata(block: ObjectMeta.() -> Unit) {
        metadata = ObjectMeta()
        metadata.block()
    }

    override fun getKind():String = _kind

    override fun getApiVersion() = "skatteetaten.no/v1"

    override fun setMetadata(data: ObjectMeta) {
        metadata = data
    }

    override fun setApiVersion(version: String?) {
        throw UnsupportedOperationException("Cannot set apiVersion on custom resource")
    }
}
