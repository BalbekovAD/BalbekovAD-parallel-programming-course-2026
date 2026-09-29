package collectors

import Snapshot
import kotlin.math.min


class BaseMetricsCollector : MetricsCollector {
    private val buckets = LongArray(BUCKETS_COUNT)
    private var count: Long = 0
    private var sum: Long = 0
    private var min: Long = Long.MAX_VALUE
    private var max: Long = Long.MIN_VALUE
    override fun record(value: Int) {
        val bucketIndex = min(value / 4, buckets.lastIndex)
        buckets[bucketIndex]++
        count++
        sum += value
        min = min(min, value.toLong())
        max = min(max, value.toLong())
    }

    override fun snapshot(): Snapshot = Snapshot(
        buckets = buckets.copyOf(),
        count = count,
        sum = sum,
        min = min,
        max = max
    )
}
