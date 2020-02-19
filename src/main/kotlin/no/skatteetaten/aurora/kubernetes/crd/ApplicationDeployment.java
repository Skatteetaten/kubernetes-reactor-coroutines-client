package no.skatteetaten.aurora.kubernetes.crd;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import no.skatteetaten.aurora.kubernetes.ApplicationDeploymentSpec;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "apiVersion", "kind", "metadata", "spec", "status" })
@JsonDeserialize(
    using = JsonDeserializer.None.class
)
public class ApplicationDeployment implements HasMetadata {
    @JsonProperty("apiVersion")
    private String apiVersion = "skatteetaten.no/v1";
    @JsonProperty("kind")
    private String kind = "ApplicationDeployment";
    @JsonProperty("metadata")
    private ObjectMeta metadata;
    @JsonProperty("spec")
    private ApplicationDeploymentSpec spec;

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Override
    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    @Override
    public ObjectMeta getMetadata() {
        return metadata;
    }

    @Override
    public void setMetadata(ObjectMeta metadata) {
        this.metadata = metadata;
    }

    public ApplicationDeploymentSpec getSpec() {
        return spec;
    }

    public void setSpec(ApplicationDeploymentSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApplicationDeployment that = (ApplicationDeployment) o;
        return Objects.equals(apiVersion, that.apiVersion) &&
            Objects.equals(kind, that.kind) &&
            Objects.equals(metadata, that.metadata) &&
            Objects.equals(spec, that.spec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiVersion, kind, metadata, spec);
    }
}


