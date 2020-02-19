package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.JsonNode
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
import io.fabric8.openshift.api.model.*
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.crd.newSkatteetatenQueryResource
import no.skatteetaten.aurora.kubernetes.testutils.DisableIfJenkins
import no.skatteetaten.aurora.kubernetes.testutils.EnabledIfKubernetesToken
import no.skatteetaten.aurora.kubernetes.testutils.NAME
import no.skatteetaten.aurora.kubernetes.testutils.NAMESPACE
import no.skatteetaten.aurora.kubernetes.testutils.NAMESPACE_DEV
import no.skatteetaten.aurora.kubernetes.testutils.kubernetesToken
import no.skatteetaten.aurora.kubernetes.testutils.testWebClient
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@DisableIfJenkins
@EnabledIfKubernetesToken
class KubernetesUserTokenClientIntegrationTest {

    private val reactiveClient = KubernetesClient.create(testWebClient(), kubernetesToken())
    private val kubernetesClient = KubernetesCoroutinesClient(reactiveClient)

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
            val dc = kubernetesClient.get(newDeploymentConfig {
                metadata {
                    namespace = NAMESPACE
                    name = NAME
                }
            })
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

        runBlocking {
            val ads = kubernetesClient.getMany(newApplicationDeployment {
                metadata {
                    this.namespace = namespace
                }
            })

            assertThat(ads).isNotNull()
        }
    }

    @Test
    fun `Get services`() {
        runBlocking {
            val services: List<Service> = kubernetesClient.getMany(newService {
                metadata = newObjectMeta {
                    namespace = NAMESPACE
                }
            })
            assertThat(services).isNotNull()
        }
    }

    @Test
    fun `Get pods`() {
        runBlocking {
            val pods: List<Pod> = kubernetesClient.getMany(newPod {
                metadata {
                    namespace = NAMESPACE
                }
            })

            assertThat(pods).isNotNull()
        }
    }

    @Test
    fun `Get replication controllers`() {
        runBlocking {
            val rcs: List<ReplicationController> = kubernetesClient.getMany(newReplicationController {
                metadata {
                    namespace = NAMESPACE
                }
            })

            val rc = kubernetesClient.get(newReplicationController {
                metadata {
                    namespace = NAMESPACE
                    name = rcs.first().metadata.name
                }
            })

            assertThat(rcs).isNotNull()
            assertThat(rc).isNotNull()
        }
    }

    @Test
    fun `Get image stream tag`() {
        runBlocking {
            val ist = kubernetesClient.get(newImageStreamTag {
                metadata {
                    namespace = NAMESPACE
                    name = "$NAME:latest"
                }
            })

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
    fun `proxy pod`() {
        runBlocking {
            val pod: Pod = kubernetesClient.getMany(newPod {
                metadata {
                    namespace = NAMESPACE
                    labels = mapOf("app" to NAME)
                }
            }).first()

            val result: JsonNode = kubernetesClient.proxyGet(
                pod = pod,
                port = 8081,
                path = "actuator"
            )

            assertThat(result).isNotNull()
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
            val deleted = kubernetesClient.delete(newSkatteetatenQueryResource<ApplicationDeployment> {
                metadata {
                    name = ""
                    namespace = NAMESPACE_DEV
                }
            }, newDeleteOptions {
                propagationPolicy = "Background"
            })

            assertThat(deleted).isTrue()
        }
    }
}
