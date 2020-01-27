package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(classes = [KubernetesClientConfig::class, TestConfig::class])
class KubernetesWatcherTest {
    @Autowired
    private lateinit var kubernetesWatcher: KubernetesWatcher

    @MockkBean
    private lateinit var closeableWatcher: CloseableWatcher

    private val kubernetes = MockWebServer()
    private val kubernetesListener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            val json = """{ "type" : "MY_EVENT" }"""
            ws.send(json)
        }
    }

    init {
        kubernetes.enqueue(MockResponse().withWebSocketUpgrade(kubernetesListener))
        kubernetes.start()
    }

    @BeforeEach
    fun setUp() {
        every { closeableWatcher.stop(any()) } returns true
    }

    @Test
    fun `Receive deleted event and call dbh`() {
        kubernetesWatcher.watch(kubernetes.url("/").toString(), listOf("MY_EVENT")) {
            assertThat(it.at("/type").textValue()).isEqualTo("MY_EVENT")

            throw RuntimeException("stop watcher")
        }
    }
}
