package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fkorotkov.kubernetes.authorization.newSelfSubjectAccessReview
import com.fkorotkov.kubernetes.extensions.newIngress
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.openshift.newDeploymentConfig
import org.junit.jupiter.api.Test

class KubernetesClientTest {

    @Test
    fun `Build kubernetes uri with apiVersion and namespace for ingress`() {
        val ingress = newIngress {
            metadata = newObjectMeta {
                namespace = "aurora"
                name = "boober"
            }
        }

        assertThat(ingress.uri()).isEqualTo("/apis/extensions/v1beta1/namespaces/{namespace}/ingresses/{name}")
        assertThat(ingress.uriVariables()["name"]).isEqualTo("boober")
    }

    @Test
    fun `Build kubernetes uri with apiVersion and namespace`() {
        val dc = newDeploymentConfig {
            metadata = newObjectMeta {
                namespace = "aurora"
                name = "boober"
            }
        }

        assertThat(dc.uri()).isEqualTo("/apis/apps.openshift.io/v1/namespaces/{namespace}/deploymentconfigs/{name}")
        assertThat(dc.uriVariables()["name"]).isEqualTo("boober")
    }

    @Test
    fun `Build kubernetes uri with v1 apiVersion and no namespace`() {
        val p = newPod {
            metadata = newObjectMeta {
                name = "name"
            }
        }

        assertThat(p.uri()).isEqualTo("/api/v1/pods/{name}")
    }

    @Test
    fun `Build Kubernetes uri without namespace and name`() {
        val s = newSelfSubjectAccessReview {}
        assertThat(s.uri()).isEqualTo("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")
    }
}
