package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isNotNull
import com.fkorotkov.kubernetes.authorization.newSelfSubjectAccessReview
import com.fkorotkov.kubernetes.authorization.resourceAttributes
import com.fkorotkov.kubernetes.authorization.spec
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

@EnabledIfKubernetesToken
class KubernetesUserTokenClientIntegrationTest {

    private val kubernetesClient = KubernetesUserTokenClient(kubernetesToken(), testWebClient())

    @Test
    fun `Get projects`() {
        runBlocking {
            val projects = kubernetesClient.projects()
            val project = kubernetesClient.project(NAMESPACE)

            assertThat(projects).isNotNull()
            assertThat(project).isNotNull()
        }
    }

    @Test
    fun `Get routes`() {
        runBlocking {
            val routes = kubernetesClient.routes(NAMESPACE)
            val route = kubernetesClient.route(NAMESPACE, NAME)

            assertThat(routes).isNotNull()
            assertThat(route).isNotNull()
        }
    }

    @Test
    fun `Get deployment config`() {
        runBlocking {
            val dc = kubernetesClient.deploymentConfig(NAMESPACE, NAME)
            assertThat(dc).isNotNull()
        }
    }

    @Test
    fun `Get application deployments`() {
        runBlocking {
            val applicationDeployments = kubernetesClient.applicationDeployments(NAMESPACE)
            val ad = kubernetesClient.applicationDeployment(NAMESPACE, NAME)

            assertThat(applicationDeployments).isNotNull()
            assertThat(ad).isNotNull()
        }
    }

    @Test
    fun `Get services`() {
        runBlocking {
            val services = kubernetesClient.services(NAMESPACE)
            assertThat(services).isNotNull()
        }
    }

    @Test
    fun `Get pods`() {
        runBlocking {
            val pods = kubernetesClient.pods(NAMESPACE)
            val pods2 = kubernetesClient.pods2(NAMESPACE, pods.items.first().metadata.name)

            assertThat(pods).isNotNull()
            assertThat(pods2).isNotNull()
        }
    }

    @Test
    fun `Get replication controllers`() {
        runBlocking {
            val rcs = kubernetesClient.replicationControllers(NAMESPACE)
            val rc = kubernetesClient.replicationController(NAMESPACE, rcs.items.first().metadata.name)

            assertThat(rcs).isNotNull()
            assertThat(rc).isNotNull()
        }
    }

    @Test
    fun `Get image stream tag`() {
        runBlocking {
            val ist = kubernetesClient.imageStreamTag(NAMESPACE, NAME, "latest")
            assertThat(ist).isNotNull()
        }
    }

    @Test
    fun `Get user`() {
        runBlocking {
            val u = kubernetesClient.user()
            assertThat(u).isNotNull()
        }
    }

    @Test
    fun `Self subject access review`() {
        runBlocking {
            val s = newSelfSubjectAccessReview {
                spec {
                    resourceAttributes {
                        namespace = NAMESPACE
                        verb = "update"
                        resource = "deploymentconfigs"
                    }
                }
            }
            val selfSubjectAccessView = kubernetesClient.selfSubjectAccessView(s)
            assertThat(selfSubjectAccessView).isNotNull()
        }
    }
}
