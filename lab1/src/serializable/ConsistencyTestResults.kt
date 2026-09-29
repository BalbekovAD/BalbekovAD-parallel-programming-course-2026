package serializable

import ConsistencyType
import ConsistencyType.CONSISTENT
import kotlinx.serialization.Serializable

@Serializable
data class ConsistencyTestResults(
    val counts: Map<ConsistencyType, Int>,
    val finalExpectedCount: Long,
    val finalActualCount: Long
) {
    fun toPrettyString(perLinePrefix: String = ""): String {
        val total = counts.values.sum()
        val mapValues = counts.mapValues { (_, count) ->
            (count.toDouble() / total).toFloat() * 100
        }
        return listOf(
            "consistent: ${mapValues[CONSISTENT] ?: 0}%",
            "> count: ${mapValues[ConsistencyType.COUNT_IS_LESS] ?: 0}%",
            "< count: ${mapValues[ConsistencyType.COUNT_IS_GREATER] ?: 0}%"
        ).joinToString(
            separator = "\n",
            prefix = "$perLinePrefix${ConsistencyTestResults::class.simpleName}:\n"
        ) { "$perLinePrefix    $it" }
    }
}