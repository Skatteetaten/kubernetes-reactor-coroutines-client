package no.skatteetaten.aurora.kubernetes

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.WebClient

@TestConfiguration
class TestConfig {
    @Bean
    fun webClientBuilder() = WebClient.builder()
}

@SpringBootTest(classes = [TestConfig::class, KubernetesClientConfig::class])
class KubernetesClientConfigTest {
    @Autowired
    private lateinit var client: KubernetesClient

    @MockkBean
    private lateinit var tokenFetcher: TokenFetcher

    @Test
    fun `Spring initialization`() {
        assertThat(client).isNotNull()
    }
}

@SpringBootTest(classes = [TestConfig::class, KubernetesClientConfig::class])
class ServiceAccountConfigTest {
    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    @Autowired
    private lateinit var client: KubernetesClient

    @Test
    fun `Spring initialization`() {
        assertThat(client).isNotNull()
        assertThat(client.tokenFetcher.token()).isEqualTo("abc123")
    }
}

@SpringBootTest(classes = [TestConfig::class, KubernetesClientConfig::class])
class UserTokenConfigTest {
    @TargetClient(ClientTypes.USER_TOKEN)
    @Autowired
    private lateinit var client: KubernetesClient

    @MockkBean
    private lateinit var tokenFetcher: TokenFetcher

    @Test
    fun `Spring initialization`() {
        every { tokenFetcher.token() } returns "123abc"

        assertThat(client).isNotNull()
        assertThat(client.tokenFetcher.token()).isEqualTo("123abc")
    }
}
