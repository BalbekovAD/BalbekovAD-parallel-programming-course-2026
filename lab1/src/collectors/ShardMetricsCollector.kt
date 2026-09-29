package collectors

import Snapshot
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.update
import kotlin.math.min

private const val LOCKS_COUNT = 16

@OptIn(ExperimentalAtomicApi::class)
class ShardMetricsCollector : MetricsCollector {
    private val bucketsLocks = List(BUCKETS_COUNT / LOCKS_COUNT) {
        Any()
    }
    private val buckets = LongArray(BUCKETS_COUNT)
    private val count = AtomicLong(0)
    private val sum = AtomicLong(0)
    private val min = AtomicLong(Long.MAX_VALUE)
    private val max = AtomicLong(Long.MIN_VALUE)
    override fun record(value: Int) {
        val bucketIndex = min(value / 4, buckets.lastIndex)
        synchronized(bucketsLocks[bucketIndex % LOCKS_COUNT]) {
            buckets[bucketIndex]++
        }
        count.incrementAndFetch()
        sum.addAndFetch(value.toLong())
        min.update { minOf(it, value.toLong()) }
        max.update { maxOf(it, value.toLong()) }
    }

    private fun copyBuckets(): LongArray {
        val result = LongArray(buckets.size)
        bucketsLocks.forEachIndexed { lockIndex, lock ->
            synchronized(lock) {
                for (i in lockIndex..<BUCKETS_COUNT step LOCKS_COUNT) {
                    result[i] = buckets[i]
                }
            }
        }
        return result
    }

    override fun snapshot(): Snapshot {
        val copiedBuckets = copyBuckets()
        return Snapshot(copiedBuckets, count.load(), sum.load(), min.load(), max.load())
    }
}
