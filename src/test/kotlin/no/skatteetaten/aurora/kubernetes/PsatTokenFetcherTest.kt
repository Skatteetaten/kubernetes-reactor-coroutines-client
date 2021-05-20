package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable

class PsatTokenFetcherTest {

    @SetEnvironmentVariable(key = "VOLUME_PSAT_TOKEN_MOUNT", value = "src/test/resources")
    @Test
    fun `Get token with audience`() {
        val token = PsatTokenFetcher().token("test-token.txt")
        assertThat(token).isEqualTo("abc123")
    }
}