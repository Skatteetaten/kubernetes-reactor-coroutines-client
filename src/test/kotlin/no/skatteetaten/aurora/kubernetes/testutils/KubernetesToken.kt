package no.skatteetaten.aurora.kubernetes.testutils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

const val KUBERNETES_URL = "https://utv-master.paas.skead.no:8443"
const val NAMESPACE = "aurora"
const val NAMESPACE_DEV = "aurora-dev"
const val NAME = "boober"

fun kubernetesToken(environment: String = "utv-master"): String {
    val kubernetesConfig = File("${System.getProperty("user.home")}/.kube/config")
    if (!kubernetesConfig.exists())
        throw IllegalArgumentException("No kubernetes config file found in ${kubernetesConfig.absolutePath}")

    val content = kubernetesConfig.readText()
    val values = ObjectMapper(YAMLFactory()).readTree(content)
    return values.at("/users").iterator().asSequence()
        .firstOrNull { it.at("/name").textValue().contains("$environment-paas-skead-no") }
        ?.at("/user/token")?.textValue()
        ?: throw IllegalArgumentException("No Kubernetes token found for environment $environment")
}

fun testWebClient() = WebClient.builder().baseUrl(KUBERNETES_URL)
    .exchangeStrategies(
        ExchangeStrategies.builder()
            .codecs {
                it.defaultCodecs().apply {
                    maxInMemorySize(-1) // unlimited
                }
            }.build()
    ).build()

@Target(AnnotationTarget.CLASS)
@Retention
@ExtendWith(EnabledIfKubernetesTokenCondition::class)
annotation class EnabledIfKubernetesToken

class EnabledIfKubernetesTokenCondition : ExecutionCondition {

    override fun evaluateExecutionCondition(context: ExtensionContext?): ConditionEvaluationResult {
        return try {
            kubernetesToken()
            ConditionEvaluationResult.enabled("Kubernetes token found")
        } catch (e: IllegalArgumentException) {
            ConditionEvaluationResult.disabled("Test is disabled, no Kubernetes token. ${e.message}")
        }
    }
}
