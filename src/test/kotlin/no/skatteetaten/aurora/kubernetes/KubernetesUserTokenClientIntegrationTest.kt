package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isNotNull
import com.fkorotkov.kubernetes.authorization.newSelfSubjectAccessReview
import com.fkorotkov.kubernetes.authorization.resourceAttributes
import com.fkorotkov.kubernetes.authorization.spec
import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newReplicationController
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newImageStreamTag
import com.fkorotkov.openshift.newProject
import com.fkorotkov.openshift.newRoute
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ReplicationControllerList
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.openshift.api.model.ProjectList
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.crd.newSkatteetatenKubernetesResource
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

    private val kubernetesClient = KubernetesClient.create(testWebClient(), kubernetesToken())

    @Test
    fun `Get projects`() {
        runBlocking {
            val projects: ProjectList = kubernetesClient.getList(newProject { })
            val project = kubernetesClient.get(newProject { metadata { name = NAMESPACE } })

            assertThat(projects).isNotNull()
            assertThat(project).isNotNull()
        }
    }

    @Test
    fun `Get projects with label`() {
        runBlocking {
            val projects: ProjectList = kubernetesClient.getList(newProject { metadata { labels = newLabel("removeAfter") }})

            assertThat(projects).isNotNull()
        }
    }

    @Test
    fun `Get routes`() {
        runBlocking {
            val routes = kubernetesClient.getList(newRoute { metadata { namespace = NAMESPACE } })
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
        runBlocking {
            val ad: ApplicationDeployment =
                kubernetesClient.getResource(newSkatteetatenKubernetesResource<ApplicationDeployment> {
                    metadata {
                        namespace = NAMESPACE
                        name = NAME
                    }
                })

            val ads: ApplicationDeploymentList =
                kubernetesClient.getResource(newSkatteetatenKubernetesResource<ApplicationDeployment> {
                    metadata {
                        namespace = NAMESPACE
                    }
                })

            assertThat(ad).isNotNull()
            assertThat(ads).isNotNull()
        }
    }

    @Test
    fun `Get services`() {
        runBlocking {
            val services: ServiceList = kubernetesClient.getList(newService {
                metadata = newObjectMeta {
                    namespace = NAMESPACE
                    name = NAME
                }
            })
            assertThat(services).isNotNull()
        }
    }

    @Test
    fun `Get pods`() {
        runBlocking {
            val pods: PodList = kubernetesClient.getList(newPod {
                metadata {
                    namespace = NAMESPACE
                }
            })

            val pods2: PodList = kubernetesClient.getList(newPod {
                metadata {
                    namespace = NAMESPACE
                    name = pods.items.first().metadata.name
                }
            })

            assertThat(pods).isNotNull()
            assertThat(pods2).isNotNull()
        }
    }

    @Test
    fun `Get replication controllers`() {
        runBlocking {
            val rcs: ReplicationControllerList = kubernetesClient.getList(newReplicationController {
                metadata {
                    namespace = NAMESPACE
                }
            })

            val rc = kubernetesClient.get(newReplicationController {
                metadata {
                    namespace = NAMESPACE
                    name = rcs.items.first().metadata.name
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
}
