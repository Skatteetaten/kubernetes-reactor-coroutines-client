package no.skatteetaten.aurora.kubernetes

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import no.skatteetaten.aurora.kubernetes.crd.SkatteetatenCRD

/*
Since our example CRD has both required and optional fields we need to create a valid bottom type for it in the
generator method

 */
fun newApplicationDeployment(block: ApplicationDeployment.() -> Unit = {}): ApplicationDeployment {
    val instance = ApplicationDeployment(
        ApplicationDeploymentSpec(
            applicationId = ""
        )
    )
    instance.block()
    return instance
}

/*
  An example CRD called ApplicationDeployment.
  If you want to run the tests in your own cluster feel free to add it from the file test/resources/ApplicationDeployment.json  file

  It is very important to add the JsonDeserialize annotation to your CRDs if not jackson will not be able to marshall them.
 */
@JsonPropertyOrder(value = ["apiVersion", "kind", "metadata", "spec"])
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(using = JsonDeserializer.None::class)
data class ApplicationDeployment(
    var spec: ApplicationDeploymentSpec
) : SkatteetatenCRD("ApplicationDeployment")

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentSpec(
    val applicationId: String,
    val applicationName: String? = null
)
