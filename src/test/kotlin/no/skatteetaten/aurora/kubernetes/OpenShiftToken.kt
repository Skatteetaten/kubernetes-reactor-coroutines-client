package no.skatteetaten.aurora.kubernetes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

const val OPENSHIFT_URL = "https://utv-master.paas.skead.no:8443"
const val NAMESPACE = "aurora"
const val NAME = "boober"

fun openShiftToken(environment: String = "utv-master"): String {
    val content = File("${System.getProperty("user.home")}/.kube/config").readText()
    val values = ObjectMapper(YAMLFactory()).readTree(content)
    return values.at("/users").iterator().asSequence()
        .firstOrNull { it.at("/name").textValue().contains("$environment-paas-skead-no") }
        ?.at("/user/token")?.textValue()
        ?: throw IllegalArgumentException("No openshift token found for environment $environment")
}

fun testWebClient() = WebClient.builder().baseUrl(OPENSHIFT_URL).exchangeStrategies(
    ExchangeStrategies.builder()
        .codecs {
            it.defaultCodecs().apply {
                maxInMemorySize(-1) // unlimited
            }
        }.build()
).build()

@Target(AnnotationTarget.CLASS)
@Retention
@ExtendWith(EnabledIfOpenShiftTokenCondition::class)
annotation class EnabledIfOpenShiftToken

class EnabledIfOpenShiftTokenCondition : ExecutionCondition {

    override fun evaluateExecutionCondition(context: ExtensionContext?): ConditionEvaluationResult {
        return try {
            openShiftToken()
            ConditionEvaluationResult.enabled("OpenShift token found")
        } catch (ignored: IllegalArgumentException) {
            ConditionEvaluationResult.disabled("No OpenShift token")
        }
    }
}
