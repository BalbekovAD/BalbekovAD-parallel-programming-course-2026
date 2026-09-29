package serializable

import INDENT
import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkPoint(
    val warmupOps: Double,
    val ops: DoubleArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BenchmarkPoint

        if (warmupOps != other.warmupOps) return false
        if (!ops.contentEquals(other.ops)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = warmupOps.hashCode()
        result = 31 * result + ops.contentHashCode()
        return result
    }
    fun toPrettyString(title: String = "BenchmarkPoint", perLinePrefix: String = ""): String {
        val average = ops.average()
        return listOf(
            "ops: ${ops.joinToString(prefix = "[", postfix = "]")}",
            "average: $average",
            "min: average + ${ops.min() - average}",
            "max: average + ${ops.max() - average}"
        ).joinToString(prefix = "$perLinePrefix$title:\n", separator = "") {
            "$perLinePrefix$INDENT$it\n"
        }
    }
}