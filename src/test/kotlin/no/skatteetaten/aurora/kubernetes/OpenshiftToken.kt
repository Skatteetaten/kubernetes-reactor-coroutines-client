package no.skatteetaten.aurora.kubernetes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File

fun openshiftToken(environment: String = "utv-master"): String {
    val content = File("${System.getProperty("user.home")}/.kube/config").readText()
    val values = ObjectMapper(YAMLFactory()).readTree(content)
    return values.at("/users").iterator().asSequence()
        .firstOrNull { it.at("/name").textValue().contains("$environment-paas-skead-no") }
        ?.at("/user/token")?.textValue()
        ?: throw IllegalArgumentException("No openshift token found for environment $environment")
}

@Target(AnnotationTarget.CLASS)
@Retention
@ExtendWith(EnabledIfOpenShiftTokenCondition::class)
annotation class EnabledIfOpenShiftToken

class EnabledIfOpenShiftTokenCondition : ExecutionCondition {

    override fun evaluateExecutionCondition(context: ExtensionContext?): ConditionEvaluationResult {
        return try {
            openshiftToken()
            ConditionEvaluationResult.enabled("OpenShift token found")
        } catch (ignored: IllegalArgumentException) {
            ConditionEvaluationResult.disabled("No OpenShift token")
        }
    }
}
