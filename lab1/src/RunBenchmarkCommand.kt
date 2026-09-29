import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.kotlinx.kandy.letsplot.export.toSVG
import serializable.BenchmarkConfig
import serializable.BenchmarkFile
import serializable.BenchmarkPoint
import serializable.ConsistencyTestResults
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val maxProcs get() = Runtime.getRuntime().availableProcessors()
private const val RANDOM_SEED = 42L

class RunBenchmarkCommand : CliktCommand(name = "benchmark") {
    val collectorType: CollectorType by argument("collector-type")
        .enum { it.name.lowercase() }
    val resultsOutputDir: Path by option("--output-dir").path(
        canBeFile = false,
        canBeDir = true,
        mustBeWritable = true,
        mustBeReadable = true,
    ).default(Path.of("results"))

    val inputSize: Int by option("--input-size")
        .int()
        .default(1 shl 20)
        .check("Must be > 0") { it > 0 }

    val singleRunTime: Duration by option("--single-run-time")
        .convert { Duration.parse(it) }
        .default(5.seconds)

    val maxThreads: Int by option("--max-threads")
        .int()
        .default(maxProcs)
        .check("Must be in 1..$maxProcs") { it in 1..maxProcs }

    val consistencyThreads: Int by option("--consistency-threads")
        .int()
        .defaultLazy { minOf(maxThreads, 4) }
        .check("Must be in 1..(--max-threads)") { it in 1..maxThreads }

    val consistencyIterations: Int by option("--consistency-iterations")
        .int()
        .default(10000)
        .check("Must be > 0") { it > 0 }

    val repeats: Int by option("--repeats")
        .int()
        .default(5)
        .check("Must be > 0") { it > 0 }

    val seed: Long by option("--seed").long().default(RANDOM_SEED)

    @OptIn(ExperimentalSerializationApi::class)
    override fun run() {
        val config = BenchmarkConfig(
            collectorType,
            inputSize,
            singleRunTime,
            maxThreads,
            consistencyThreads,
            consistencyIterations,
            repeats,
            seed
        )
        resultsOutputDir.createDirectories()
        val benchmarkResults = createOutputFile(resultsOutputDir, collectorType, "json")
        val benchmarkPlot = createOutputFile(resultsOutputDir, collectorType, "svg")
        val bench = config.runBench()
        val consistency = config.runConsistencyTest()
        println(consistency.toPrettyString())
        val results = BenchmarkFile(config, bench, consistency)
        benchmarkResults.outputStream().use {
            Json.encodeToStream(results, it)
        }
        benchmarkPlot.bufferedWriter().use {
            it.write(results.visualize().toSVG())
        }
    }

    private fun createOutputFile(dir: Path, collectorType: CollectorType, extension: String): Path {
        val path = dir / "benchmark_${collectorType.name.lowercase()}.$extension"
        require(path.notExists() || path.isRegularFile()) { "$path should be file" }
        if (path.deleteIfExists()) {
            echo("Path $path is not empty, so I deleted it!")
        }
        return path.createFile()
    }
    private fun BenchmarkConfig.runConsistencyTest(): ConsistencyTestResults {
        echo("Consistency test for $collectorType with consistencyTestThreads = $consistencyTestThreads:")
        var oldMark = TimeSource.Monotonic.markNow()
        return measureConsistency(
            collector = collectorType.createMetricsCollector(),
            iterations = consistencyIterations,
            threads = consistencyTestThreads,
            seed = seed,
        ) { i, consistencyType ->
            val new = TimeSource.Monotonic.markNow()
            val diff = new - oldMark
            oldMark = new
            echo("${INDENT}Iteration $i, ${ConsistencyType::class.simpleName}: ${consistencyType.name}, $diff")
        }
    }

    private fun BenchmarkConfig.runBench(): Map<Int, BenchmarkPoint> {
        echo("Starting benchmark for $collectorType with maxThreads = $maxThreads")
        return (1..maxThreads).associateWith { threads ->
            echo("Benchmark ${collectorType.name} with threads = $threads:")
            val benchmarkPoint = collectorType.createMetricsCollector().benchmark(
                values = Random(seed).generateMetrics(inputSize),
                threads = threads,
                time = singleRunTime,
                repeats = repeats,
                onWarmupStart = { echo("${INDENT}Warmup started", trailingNewline = false) },
                onWarmupEnd = { echo(", finished: $it") },
                onIterationStart = { echo("${INDENT}Iteration $it started", trailingNewline = false) },
                onIterationEnd = { i, duration -> echo(", finished: $duration") }
            )
            echo(benchmarkPoint.toPrettyString(perLinePrefix = INDENT))
            benchmarkPoint
        }
    }
}
