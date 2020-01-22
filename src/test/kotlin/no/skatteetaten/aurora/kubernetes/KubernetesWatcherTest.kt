package no.skatteetaten.aurora.kubernetes

import assertk.Assert
import assertk.assertThat
import assertk.assertions.support.expected
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.util.concurrent.TimeUnit
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono

@Disabled("test not finished")
@ActiveProfiles("test")
@SpringBootTest(classes = [KubernetesClientConfig::class, TestConfig::class])
class KubernetesWatcherTest {
    @Autowired
    private lateinit var kubernetesWatcher: KubernetesWatcher

    private val kubernetes = MockWebServer()
    private val kubernetesListener = object : WebSocketListener() {
        var webSocket: WebSocket? = null

        override fun onOpen(ws: WebSocket, response: Response) {
            webSocket = ws
        }
    }

    init {
        kubernetes.enqueue(MockResponse().withWebSocketUpgrade(kubernetesListener))
        kubernetes.start("kubernetes".port())
    }

    @AfterEach
    fun tearDown() {
        kubernetes.shutdown()
    }

    @Test
    fun `Receive deleted event and call dbh`() {
        val webSocket = await untilNotNull { kubernetesListener.webSocket }
        val json = """{ "type" : "MY_EVENT" }"""
        webSocket.send(json)
        kubernetesWatcher.watch(kubernetes.url("/").toString(), listOf("MY_EVENT")) {

            Mono.empty()
        }
    }

    private fun String.port(): Int {
        val yaml = ClassPathResource("application.yaml").file.readText()
        val values = ObjectMapper(YAMLFactory()).readTree(yaml)
        return values.at("/integrations/$this/port").asInt()
    }

    private fun MockWebServer.enqueueJson(vararg responses: MockResponse) {
        responses.forEach {
            it.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            this.enqueue(it)
        }
    }

    private fun MockWebServer.assertThat(): Assert<List<RecordedRequest>> {
        val requests = mutableListOf<RecordedRequest>()
        do {
            val request = this.takeRequest(500, TimeUnit.MILLISECONDS)?.let {
                requests.add(it)
            }
        } while (request != null)
        return assertThat(requests)
    }

    private fun Assert<List<RecordedRequest>>.containsRequest(
        method: HttpMethod,
        path: String
    ): Assert<List<RecordedRequest>> =
        transform { requests ->
            if (requests.any { it.method == method.name && it.path == path }) {
                requests
            } else {
                expected("${method.name} request with $path but was $requests")
            }
        }
}

fun createGetSchemaResultJson(id: String, type: String = "MANAGED"): String {
    return """
            {
              "items": [
                {
                 "type" : "$type",
                 "id" : "$id",
                 "labels" : {
                   "userId" : "hero",
                   "name" : "application-database", 
                   "application" : "test-app",
                   "environment" : "test-utv", 
                   "affiliation" : "test"
                 }
               }
               ]
            }
        """.trimIndent()
}
