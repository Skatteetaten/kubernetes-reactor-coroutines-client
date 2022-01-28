package no.skatteetaten.aurora.kubernetes.testutils

import no.skatteetaten.aurora.kubernetes.config.kubernetesToken
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

const val KUBERNETES_URL = "https://api.utv04.paas.skead.no:6443"
const val NAMESPACE = "aup"
const val NAMESPACE_DEV = "aup-dev"
const val NAME = "boober"

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
