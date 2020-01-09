package no.skatteetaten.aurora.mean.genie.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.ConnectException
import java.net.URI
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.PrematureCloseException

/**
 * The integration test will stop the mock openshift server, which will cause a PrematureCloseException or ConnectException.
 * This needs to be handled and stop the loop in the Watcher code.
 */
@Profile("test")
@Component
class TestCloseableWatcher : CloseableWatcher {
    override fun stop(t: Throwable) =
        t.cause is PrematureCloseException || t.cause?.cause is ConnectException
}

/**
 * A manual shutdown of the application will send either a SIGINT or a SIGTERM, which will cause a InterruptedException.
 * This needs to be handled and stop the loop in the Watcher code.
 */
@Profile("!test")
@Component
class OpenshiftCloseableWatcher : CloseableWatcher {
    override fun stop(t: Throwable) = t.cause is InterruptedException
}

interface CloseableWatcher {
    fun stop(t: Throwable): Boolean
}

private val logger = KotlinLogging.logger {}

@Service
class KubernetesWatcher(
    @Qualifier("kubernetes") val websocketClient: ReactorNettyWebSocketClient,
    val closeableWatcher: CloseableWatcher
) {

    // TODO: convert this to non blocking
    fun watch(url: String, types: List<String> = emptyList(), fn: (JsonNode) -> Mono<Void>) {
        var stopped = false
        while (!stopped) {
            logger.debug("Started watch on url={}", url)
            try {
                watchBlocking(url, types, fn)
            } catch (t: Throwable) {
                stopped = closeableWatcher.stop(t)
                if (!stopped) {
                    logger.error("error occurred in watch", t)
                }
            }
        }
    }

    private fun watchBlocking(url: String, types: List<String>, fn: (JsonNode) -> Mono<Void>) {
        websocketClient.execute(URI.create(url)) { session ->
            session.receive()
                .map { jacksonObjectMapper().readTree(it.payloadAsText) }
                .filter {
                    if (types.isEmpty()) {
                        true
                    } else {
                        it.at("/type").textValue() in types
                    }
                }.flatMap {
                    fn(it)
                }.then()
        }.block()
    }
}
