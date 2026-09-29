import arrow.fx.coroutines.CountDownLatch
import collectors.MetricsCollector
import kotlinx.coroutines.*
import org.apache.commons.math3.distribution.ZipfDistribution
import org.apache.commons.math3.random.RandomGeneratorFactory
import serializable.BenchmarkPoint
import serializable.ConsistencyTestResults
import java.util.*
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private const val MAX_METRIC = 1023
private const val ZIPF_EXPONENT = 1.15

fun Random.metricsDistribution(): ZipfDistribution {
    val randomGenerator = RandomGeneratorFactory.createRandomGenerator(asJavaRandom())!!
    return ZipfDistribution(randomGenerator, MAX_METRIC, ZIPF_EXPONENT)
}

fun Random.generateMetrics(size: Int): IntArray = metricsDistribution().sample(size)!!

@OptIn(ExperimentalAtomicApi::class)
data class ParallelRun<T>(
    private val beActive: AtomicBoolean,
    private val runs: List<Deferred<T>>
) {
    fun stop() = beActive.store(false)
    suspend fun awaitAll(): List<T> {
        stop()
        return runs.awaitAll()
    }
}

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
private fun <T> CoroutineScope.runParallel(
    threads: Int,
    action: suspend CoroutineScope.(threadId: Int, beActive: AtomicBoolean) -> T
): ParallelRun<T> {
    val beActive = AtomicBoolean(true)
    return ParallelRun(
        beActive = beActive,
        runs = (0..<threads).map {
            async(newSingleThreadContext("worker$it")) {
                action(it, beActive)
            }
        }
    )
}

@OptIn(ExperimentalAtomicApi::class)
private fun singleBenchmarkRun(
    collector: MetricsCollector,
    values: IntArray,
    threads: Int,
    time: Duration
): Double = runBlocking {
    val latch = CountDownLatch(1)
    val run = runParallel(threads) { thread, beActive ->
        var localCount = 0L
        var i = thread * 1000
        latch.await()
        while (beActive.load()) {
            collector.record(values[i])
            localCount++
            i++
            if (i == values.size) i = 0
        }
        localCount
    }
    val timeSeconds = measureTime {
        latch.countDown()
        delay(time)
        run.stop()
    }.toDouble(DurationUnit.MILLISECONDS)
    run.awaitAll().sum() / timeSeconds
}

fun MetricsCollector.benchmark(
    values: IntArray,
    threads: Int,
    time: Duration,
    repeats: Int,
    onWarmupStart: () -> Unit,
    onWarmupEnd: (Duration) -> Unit,
    onIterationStart: (Int) -> Unit,
    onIterationEnd: (Int, Duration) -> Unit
): BenchmarkPoint {
    require(repeats > 0)
    onWarmupStart()

    val (warmupOps, duration) = measureTimedValue { singleBenchmarkRun(this, values, threads, time) }
    onWarmupEnd(duration)
    val ops = DoubleArray(repeats) {
        onIterationStart(it)
        val (res, iterDuration) = measureTimedValue { singleBenchmarkRun(this, values, threads, time) }
        onIterationEnd(it, iterDuration)
        res
    }
    return BenchmarkPoint(warmupOps = warmupOps, ops = ops)
}

@OptIn(ExperimentalAtomicApi::class)
fun measureConsistency(
    collector: MetricsCollector,
    iterations: Int,
    threads: Int,
    seed: Long,
    onNewMeasurement: (Int, ConsistencyType) -> Unit
): ConsistencyTestResults {
    require(iterations > 0)
    require(threads > 0)
    return runBlocking {
        val metricsDistribution = Random(seed).metricsDistribution()
        val runs = runParallel(threads) { _, beActive ->
            var ops = 0L
            while (beActive.load()) {
                collector.record(metricsDistribution.sample())
                ops++
            }
            ops
        }
        val counts = try {
            (0..<iterations).countsMap {
                val res = collector.snapshot().measureConsistency()
                onNewMeasurement(it, res)
                res
            }
        } finally {
            runs.stop()
        }
        val finalCount = runs.awaitAll().sum()

        ConsistencyTestResults(counts, finalCount, collector.snapshot().count)
    }
}

private inline fun <T, reified R : Enum<R>> Iterable<T>.countsMap(transform: (T) -> R): Map<R, Int> {
    val enumMap: MutableMap<R, Int> = EnumMap(R::class.java)
    forEach {
        enumMap.merge(transform(it), 1, Int::plus)
    }
    return enumMap
}
