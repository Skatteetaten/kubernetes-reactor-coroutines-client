package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isNotNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

@EnabledIfOpenShiftToken
class KubernetesUserTokenClientIntegrationTest {

    private val kubernetesClient = KubernetesUserTokenClient(openshiftToken(), testWebClient())

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
            assertThat(applicationDeployments).isNotNull()
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
            assertThat(pods).isNotNull()
        }
    }
}
