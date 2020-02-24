package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newProject
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.execute
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class KubernetesClientNetworkTest {

    private val server = MockWebServer()
    private val url = server.url("/")

    private val client = KubernetesCoroutinesClient(
        KubernetesReactorClient.create(WebClient.create(url.toString()), "test-token", KubernetesRetryConfiguration())
    )

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 503])
    fun `Retry request on 5xx responses`(statusCode: Int) {
        val errorResponse = MockResponse().json().setResponseCode(statusCode)
        val okResponse = MockResponse().json(newDeploymentConfig { })

        server.execute(errorResponse, okResponse) {
            runBlocking {
                val dc = client.get(newDeploymentConfig { })
                assertThat(dc).isNotNull()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403])
    fun `Do not retry request on 4xx responses`(statusCode: Int) {
        val errorResponse = MockResponse()
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setResponseCode(statusCode)

        server.execute(errorResponse) {
            runBlocking {
                assertThat {
                    client.get(newDeploymentConfig { })
                }.isFailure().isInstanceOf(WebClientResponseException::class)
            }
        }
    }

    @Test
    fun `Return empty response for 404`() {
        val errorResponse = MockResponse().setResponseCode(404)

        server.execute(errorResponse) {
            runBlocking {
                val project = client.getOrNull(newProject { })
                assertThat(project).isNull()
            }
        }
    }

    @Test
    fun `Throw ResourceNotFoundException when resource not found`() {
        val errorResponse = MockResponse().setResponseCode(404)

        server.execute(errorResponse) {
            runBlocking {
                assertThat {
                    client.deleteForeground(newDeploymentConfig { })
                }.isFailure().isInstanceOf(ResourceNotFoundException::class)
            }
        }
    }

    @Test
    fun `Return empty list for 404`() {
        val response = MockResponse().setResponseCode(404)

        server.execute(response) {
            runBlocking {
                val projects = client.getMany(newProject { })
                assertThat(projects).hasSize(0)
            }
        }
    }

    private fun MockResponse.json(body: Any? = null): MockResponse {
        this.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        if (body != null) {
            this.setBody(jacksonObjectMapper().writeValueAsString(body))
        }
        return this
    }
}