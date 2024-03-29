package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.JsonNode
import com.fkorotkov.kubernetes.authentication.newTokenReview
import com.fkorotkov.kubernetes.authentication.spec
import com.fkorotkov.kubernetes.authorization.newSelfSubjectAccessReview
import com.fkorotkov.kubernetes.authorization.resourceAttributes
import com.fkorotkov.kubernetes.authorization.spec
import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newDeleteOptions
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newReplicationController
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newImageStreamTag
import com.fkorotkov.openshift.newProject
import com.fkorotkov.openshift.newRoute
import io.fabric8.kubernetes.api.model.KubernetesList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.internal.KubernetesDeserializer
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.Route
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.config.kubernetesToken
import no.skatteetaten.aurora.kubernetes.testutils.DisableIfJenkins
import no.skatteetaten.aurora.kubernetes.testutils.EnabledIfKubernetesToken
import no.skatteetaten.aurora.kubernetes.testutils.KUBERNETES_URL
import no.skatteetaten.aurora.kubernetes.testutils.NAME
import no.skatteetaten.aurora.kubernetes.testutils.NAMESPACE
import no.skatteetaten.aurora.kubernetes.testutils.NAMESPACE_DEV
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@DisableIfJenkins
@EnabledIfKubernetesToken
class KubernetesUserTokenClientIntegrationTest {

    private val config = KubernetesConfiguration(
        retry = RetryConfiguration(0),
        timeout = HttpClientTimeoutConfiguration(),
        url = KUBERNETES_URL
    )

    private val client = config.createTestClient(kubernetesToken())

    private val kubernetesClient = KubernetesCoroutinesClient(client, null)

    @Test
    fun `Get projects`() {
        runBlocking {
            val projects: List<Project> = kubernetesClient.getMany(newProject { })
            val project = kubernetesClient.get(newProject { metadata { name = NAMESPACE } })

            assertThat(projects).isNotNull()
            assertThat(project).isNotNull()
        }
    }

    @Test
    fun `Get projects with label`() {
        runBlocking {
            val projects: List<Project> =
                kubernetesClient.getMany(newProject { metadata { labels = newLabel("removeAfter") } })

            projects.forEach {
                assertThat(it.metadata.labels.containsKey("removeAfter")).isTrue()
            }
            assertThat(projects).isNotNull()
        }
    }

    @Test
    fun `Get routes`() {
        runBlocking {
            val routes: List<Route> = kubernetesClient.getMany(newRoute { metadata { namespace = NAMESPACE } })
            val route = kubernetesClient.get(
                newRoute {
                    metadata {
                        namespace = NAMESPACE
                        name = NAME
                    }
                }
            )

            assertThat(routes).isNotNull()
            assertThat(route).isNotNull()
        }
    }

    @Test
    fun `Get deployment config`() {
        runBlocking {
            val dc = kubernetesClient.get(
                newDeploymentConfig {
                    metadata {
                        namespace = NAMESPACE
                        name = NAME
                    }
                }
            )
            assertThat(dc).isNotNull()
        }
    }

    @Test
    fun `Get application deployments`() {

        KubernetesDeserializer.registerCustomKind(
            "skatteetaten.no/v1",
            "ApplicationDeploymentList",
            KubernetesList::class.java
        )

        KubernetesDeserializer.registerCustomKind(
            "skatteetaten.no/v1",
            "ApplicationDeployment",
            ApplicationDeployment::class.java
        )
        val ad = newApplicationDeployment {
            metadata {
                this.namespace = NAMESPACE
            }
        }

        runBlocking {
            val ads = kubernetesClient.getMany(ad)
            assertThat(ads).isNotNull()
        }
    }

    @Test
    fun `Get services`() {
        runBlocking {
            val services: List<Service> = kubernetesClient.getMany(
                newService {
                    metadata = newObjectMeta {
                        namespace = NAMESPACE
                    }
                }
            )
            assertThat(services).isNotNull()
        }
    }

    @Test
    fun `Get pods`() {
        runBlocking {
            val pods: List<Pod> = kubernetesClient.getMany(
                newPod {
                    metadata {
                        namespace = NAMESPACE
                    }
                }
            )

            assertThat(pods).isNotNull()
        }
    }

    @Test
    fun `Get pods with metadata`() {
        runBlocking {
            val pods: List<Pod> = kubernetesClient.getMany(newObjectMeta { namespace = NAMESPACE })
            assertThat(pods).isNotNull()
        }
    }

    @Test
    fun `Get replication controllers`() {
        runBlocking {
            val rcs: List<ReplicationController> = kubernetesClient.getMany(
                newReplicationController {
                    metadata {
                        namespace = NAMESPACE
                    }
                }
            )

            val rc = kubernetesClient.get(
                newReplicationController {
                    metadata {
                        namespace = NAMESPACE
                        name = rcs.first().metadata.name
                    }
                }
            )

            assertThat(rcs).isNotNull()
            assertThat(rc).isNotNull()
        }
    }

    @Test
    fun `Get image stream tag`() {
        runBlocking {
            val ist = kubernetesClient.get(
                newImageStreamTag {
                    metadata {
                        namespace = NAMESPACE
                        name = "$NAME:latest"
                    }
                }
            )

            assertThat(ist).isNotNull()
        }
    }

    @Test
    fun `Get user`() {
        runBlocking {
            val u = kubernetesClient.get(newCurrentUser())
            assertThat(u).isNotNull()
        }
    }

    @Test
    fun `Get current user`() {
        runBlocking {
            val u = kubernetesClient.currentUser(kubernetesToken())
            assertThat(u).isNotNull()
        }
    }

    @Test
    fun `Get token review`() {
        runBlocking {
            val tokenReview = newTokenReview {
                spec {
                    token = kubernetesToken()
                }
            }
            val result = kubernetesClient.post(tokenReview, kubernetesToken())
            assertThat(result).isNotNull()
            assertThat(result.hasError()).isFalse()
            assertThat(result.errorMessage()).isNull()
            assertThat(result.isAuthenticated()).isTrue()
        }
    }

    @Test
    fun `Get token review with PSAT`(@TempDir tempDir: Path) {
        val path = tempDir.resolve("psat-token.txt").apply {
            Files.write(this, kubernetesToken().toByteArray())
        }
        val client = KubernetesCoroutinesClient(client, PsatTokenFetcher(path.toString()))

        runBlocking {
            val tokenReview = newTokenReview {
                spec {
                    token = kubernetesToken()
                }
            }
            val result = client.post(tokenReview, kubernetesToken())
            assertThat(result).isNotNull()
            assertThat(result.hasError()).isFalse()
            assertThat(result.errorMessage()).isNull()
            assertThat(result.isAuthenticated()).isTrue()
        }
    }

    @Test
    fun `Get current user null on invalid token`() {
        runBlocking {
            val u = kubernetesClient.currentUser("fail")
            assertThat(u).isNull()
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
            val selfSubjectAccessView = kubernetesClient.post(s)
            assertThat(selfSubjectAccessView).isNotNull()
        }
    }

    @Test
    fun `get proxy pod`() {
        runBlocking {
            val pod: Pod = kubernetesClient.getMany(
                newPod {
                    metadata {
                        namespace = NAMESPACE
                        labels = mapOf("app" to NAME)
                    }
                }
            ).first()

            val result: JsonNode = kubernetesClient.proxyGet(
                pod = pod,
                port = 8081,
                path = "actuator"
            )

            assertThat(result).isNotNull()
        }
    }

    @Test
    fun `post proxy pod`() {
        runBlocking {
            val pod: Pod = kubernetesClient.getMany(
                newPod {
                    metadata {
                        namespace = NAMESPACE
                        labels = mapOf("app" to "gobo")
                    }
                }
            ).first()

            val result = kubernetesClient.proxyPost<JsonNode>(
                pod = pod,
                port = 8080,
                path = "graphql",
                body = """{ "query":"{ affiliations { edges { node { name  } } } }" }"""
            )

            assertThat(result).isNotNull()
        }
    }

    @Disabled("add namespace and name, and update path before running test")
    @Test
    fun `delete proxy pod`() {
        runBlocking {
            val pod = newPod {
                metadata {
                    namespace = ""
                    name = ""
                }
            }

            val result = kubernetesClient.proxyDelete<Unit>(
                pod = pod,
                port = 8474,
                path = "/proxies/{proxy}/toxics/{toxic}"
            )

            assertThat(result).isNull()
        }
    }

    @Disabled("add name before running test")
    @Test
    fun `Roll out deployment config`() {
        runBlocking {
            val dc = kubernetesClient.rolloutDeploymentConfig(NAMESPACE_DEV, "")
            assertThat(dc).isNotNull()
        }
    }

    @Disabled("add name before running test")
    @Test
    fun `Scale deployment config`() {
        runBlocking {
            val s = kubernetesClient.scaleDeploymentConfig(NAMESPACE_DEV, "", 2)
            assertThat(s).isNotNull()
        }
    }

    @Disabled("add name before running test")
    @Test
    fun `Delete application deployment`() {
        runBlocking {
            val deleted = kubernetesClient.deleteForeground(
                newApplicationDeployment {
                    metadata {
                        name = ""
                        namespace = NAMESPACE_DEV
                    }
                },
                newDeleteOptions { }
            )

            assertThat(deleted).isNotNull()
        }
    }

    @Disabled("add name before running test")
    @Test
    fun `Delete application deployment without options`() {
        runBlocking {
            val deleted = kubernetesClient.deleteBackground(
                newApplicationDeployment {
                    metadata {
                        name = ""
                        namespace = NAMESPACE_DEV
                    }
                }
            )

            assertThat(deleted.status).isEqualTo("Success")
        }
    }
}
