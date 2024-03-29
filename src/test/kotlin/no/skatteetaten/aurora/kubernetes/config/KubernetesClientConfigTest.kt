package no.skatteetaten.aurora.kubernetes.config

import java.beans.Beans.isInstanceOf
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.kubernetes.HttpClientTimeoutConfiguration
import no.skatteetaten.aurora.kubernetes.KubernetesConfiguration
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.RetryConfiguration
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.kubernetes.testutils.DisableIfJenkins
import no.skatteetaten.aurora.kubernetes.testutils.KUBERNETES_URL
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.WebClient
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import org.junitpioneer.jupiter.SetEnvironmentVariable

@TestConfiguration
class TestConfig {
    @Bean
    fun webClientBuilder() = WebClient.builder()

    @Bean
    fun kubeConfig() =
        KubernetesConfiguration(
            KUBERNETES_URL,
            RetryConfiguration(times = 0),
            HttpClientTimeoutConfiguration(),
            tokenLocation = "src/test/resources/test-token.txt"
        )
}

@TestConfiguration
class TestKubeConfig {
    @Bean
    fun webClientBuilder() = WebClient.builder()

    @Bean
    fun kubeConfig() =
        KubernetesConfiguration(
            KUBERNETES_URL,
            RetryConfiguration(times = 0),
            HttpClientTimeoutConfiguration()
        )
}

@SpringBootTest(classes = [TestConfig::class, KubernetesClientConfig::class])
class KubernetesClientConfigTest {
    @Autowired
    private lateinit var client: KubernetesReactorClient

    @Test
    fun `Spring initialization`() {
        assertThat(client).isNotNull()
    }
}

@SpringBootTest(classes = [TestConfig::class, KubernetesClientConfig::class])
class ServiceAccountConfigTest {
    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    @Autowired
    private lateinit var client: KubernetesReactorClient

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
    private lateinit var client: KubernetesReactorClient

    @MockkBean
    private lateinit var tokenFetcher: TokenFetcher

    @Test
    fun `Spring initialization`() {
        every { tokenFetcher.token() } returns "123abc"

        assertThat(client).isNotNull()
        assertThat(client.tokenFetcher.token()).isEqualTo("123abc")
    }
}

@SetEnvironmentVariable(key = "VOLUME_PSAT_TOKEN_MOUNT", value = "src/test/resources")
@SpringBootTest(classes = [TestConfig::class, KubernetesClientConfig::class])
class PsatConfigTest {
    @TargetClient(ClientTypes.PSAT)
    @Autowired
    private lateinit var client: KubernetesReactorClient

    @Test
    fun `Spring initialization`() {
        assertThat(client).isNotNull()
        assertThat(client.tokenFetcher.token("test-token.txt")).isEqualTo("abc123")
    }
}

@DisableIfJenkins
@SetSystemProperty(key = "user.home", value = "src/test/resources/test-config/single")
@SpringBootTest(classes = [TestKubeConfig::class, KubernetesClientConfig::class])
class SingleUserConfigTest {

    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    @Autowired
    private lateinit var client: KubernetesReactorClient

    @Test
    fun `Load single user config`() {
        val token = client.tokenFetcher.token()
        assertThat(token).isEqualTo("test-token")
    }
}

@DisableIfJenkins
@SetSystemProperty(key = "user.home", value = "src/test/resources/test-config/multiple")
@SpringBootTest(classes = [TestKubeConfig::class, KubernetesClientConfig::class])
class MultipleUsersConfigTest {

    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    @Autowired
    private lateinit var client: KubernetesReactorClient

    @Test
    fun `Load single user config`() {
        val token = client.tokenFetcher.token()
        assertThat(token).isEqualTo("test-token")
    }
}

@DisableIfJenkins
@SetSystemProperty(key = "user.home", value = "src/test/resources/test-config/userWithoutCluster")
@SpringBootTest(classes = [TestKubeConfig::class, KubernetesClientConfig::class])
class SingleUserWithoutClusterConfigTest {

    @TargetClient(ClientTypes.SERVICE_ACCOUNT)
    @Autowired
    private lateinit var client: KubernetesReactorClient

    @Test
    fun `Load single user config without cluster in name`() {
        val token = client.tokenFetcher.token()
        assertThat(token).isEqualTo("test-token")
    }
}

@DisableIfJenkins
@SetSystemProperty(key = "user.home", value = "src/test/resources/test-config/tokenNotFound")
class TokenNotFoundConfigTest {

    @Test
    fun `Load user config with no token given`() {
        assertThat {
            val token = kubernetesToken()
        }.isFailure().isInstanceOf(IllegalArgumentException::class)
    }
}
