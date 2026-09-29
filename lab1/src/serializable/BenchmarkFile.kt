package serializable

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkFile(
    val config: BenchmarkConfig,
    val results: Map<Int, BenchmarkPoint>,
    val consistencyTestResults: ConsistencyTestResults
)
