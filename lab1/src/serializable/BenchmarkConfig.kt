package serializable

import CollectorType
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class BenchmarkConfig(
    val collectorType: CollectorType,
    val inputSize: Int,
    val singleRunTime: Duration,
    val maxThreads: Int,
    val consistencyTestThreads: Int,
    val consistencyIterations: Int,
    val repeats: Int,
    val seed: Long,
) {
    init {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        require(inputSize > 0)
        require(maxThreads in 1..availableProcessors)
        require(repeats > 0)
    }
}
