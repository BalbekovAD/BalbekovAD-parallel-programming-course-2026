import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toSVG
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import serializable.BenchmarkFile
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

class RedrawBenchmarkCommand : CliktCommand(name = "redraw") {
    private val inputDir: Path by option("--input-dir")
        .path(canBeFile = false, mustExist = true, mustBeReadable = true)
        .default(Path.of("results"))

    private val outputDir: Path by option("--output-dir")
        .path(canBeFile = false)
        .defaultLazy { inputDir }

    @OptIn(ExperimentalSerializationApi::class)
    override fun run() {
        val inputs = inputDir.listDirectoryEntries("benchmark_*.json").sorted()
        require(inputs.isNotEmpty()) { "No benchmark_*.json files in $inputDir" }

        val benchmarks = inputs.map { input ->
            input.inputStream().use { Json.decodeFromStream<BenchmarkFile>(it) }
        }
        require(benchmarks.map { it.config.collectorType }.distinct().size == benchmarks.size) {
            "Multiple benchmark files have the same collector type in $inputDir"
        }
        for (benchmark in benchmarks) {
            for ((threads, point) in benchmark.results.toSortedMap()) {
                echo("${benchmark.config.collectorType}: threads $threads: median = ${point.ops.median() / 1000} million ops/s")
            }
        }

        val svg = benchmarks.visualizeMedians().toSVG()
        outputDir.createDirectories()
        val output = outputDir.resolve("benchmark_combined.svg")
        output.writeText(svg)
        echo("Updated $output")
    }
}

private fun List<BenchmarkFile>.visualizeMedians(): Plot {
    val measurements = flatMap { benchmark ->
        require(benchmark.results.isNotEmpty()) { "Benchmark results for ${benchmark.config.collectorType} must not be empty" }
        // Lets-Plot truncates long legend labels unless they wrap.
        val collectorLabel = benchmark.config.collectorType.name.replace("_EMPTY_BODY", "_\nEMPTY_BODY")
        benchmark.results.toSortedMap().map { (threads, point) ->
            Triple(threads, point.ops.median(), collectorLabel)
        }
    }
    val threadCounts = measurements.map { it.first }.distinct().sorted()
    return plot(mapOf(
        "Threads" to measurements.map { it.first },
        "Median ops/ms" to measurements.map { it.second },
        "Collector" to measurements.map { it.third },
    )) {
        groupBy("Collector") {
            line {
                x("Threads") {
                    axis.name = "Threads"
                    axis.breaks(threadCounts)
                }
                y("Median ops/ms") { axis.name = "Median operations per millisecond" }
                color("Collector")
            }
            points {
                x("Threads")
                y("Median ops/ms")
                color("Collector")
                size = 4.0
            }
        }
        layout.title = "Benchmark median throughput by collector"
        layout.size = 1100 to 550
    }
}

private fun DoubleArray.median(): Double {
    require(isNotEmpty()) { "Cannot calculate the median of an empty sample" }
    val sorted = sortedArray()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2
    } else {
        sorted[middle]
    }
}
