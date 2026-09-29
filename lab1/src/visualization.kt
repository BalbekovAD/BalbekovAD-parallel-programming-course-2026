import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import serializable.BenchmarkFile


fun BenchmarkFile.visualize(): Plot {
    require(results.isNotEmpty()) { "Benchmark results must not be empty" }

    val measurements = results.entries
        .sortedBy { it.key }
        .flatMap { (threads, point) ->
            point.ops.map { ops -> threads to ops }
        }
    val threads = measurements.map { it.first }
    val operationsPerMillisecond = measurements.map { it.second }

    return plot(mapOf("Threads" to threads, "ops/ms" to operationsPerMillisecond)) {
        points {
            x("Threads") {
                axis.name = "Threads"
                axis.breaks(results.keys.sorted())
            }
            y("ops/ms") { axis.name = "Operations per millisecond" }
            size = 4.0
        }
        layout.title = config.collectorType.name
    }
}
