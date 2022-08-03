package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class PsatTokenFetcherTest {

    @Test
    fun `Get token with audience`() {
        val token = PsatTokenFetcher("src/test/resources").token("test-token.txt")
        assertThat(token).isEqualTo("abc123")
    }
}
