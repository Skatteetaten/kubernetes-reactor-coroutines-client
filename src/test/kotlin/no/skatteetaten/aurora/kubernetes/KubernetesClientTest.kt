package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fkorotkov.kubernetes.authorization.newSelfSubjectAccessReview
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.openshift.newDeploymentConfig
import org.junit.jupiter.api.Test

class KubernetesClientTest {

    @Test
    fun `Build kubernetes uri with apiVersion and namespace`() {
        val dc = newDeploymentConfig {
            metadata = newObjectMeta {
                namespace = "aurora"
                name = "boober"
            }
        }

        assertThat(dc.uri()).isEqualTo("/apis/apps.openshift.io/v1/namespaces/{namespace}/{kind}/{name}")
    }

    @Test
    fun `Build kubernetes uri with v1 apiVersion and no namespace`() {
        val p = newPod {
            metadata = newObjectMeta {
                name = "name"
            }
        }

        assertThat(p.uri()).isEqualTo("/api/v1/{kind}/{name}")
    }

    @Test
    fun `Build Kubernetes uri without namespace and name`() {
        val s = newSelfSubjectAccessReview {}
        assertThat(s.uri()).isEqualTo("/apis/authorization.k8s.io/v1/{kind}")
    }
}
