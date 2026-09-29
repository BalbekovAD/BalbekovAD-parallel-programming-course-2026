import collectors.BUCKETS_COUNT

class Snapshot(
    val buckets: LongArray,
    val count: Long,
    val sum: Long,
    val min: Long,
    val max: Long,
) {
    constructor(): this(buckets = LongArray(BUCKETS_COUNT), count = 0, sum = 0, min = -1, max = -1)
    val p50: Long = cumulativeSearch(0.5)
    val p99: Long = cumulativeSearch(0.99)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Snapshot

        if (count != other.count) return false
        if (sum != other.sum) return false
        if (min != other.min) return false
        if (max != other.max) return false
        if (p50 != other.p50) return false
        if (p99 != other.p99) return false
        if (!buckets.contentEquals(other.buckets)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = count.hashCode()
        result = 31 * result + sum.hashCode()
        result = 31 * result + min.hashCode()
        result = 31 * result + max.hashCode()
        result = 31 * result + p50.hashCode()
        result = 31 * result + p99.hashCode()
        result = 31 * result + buckets.contentHashCode()
        return result
    }

    private fun cumulativeSearch(fraction: Double): Long {
        require(0 < fraction && fraction <= 1)
        val border: Double = buckets.sum() * fraction
        var accumulator: Long = 0
        buckets.forEachIndexed { index, bucketCount ->
            accumulator += bucketCount
            if (accumulator >= border) return index * 4L
        }
        error("Code should be unreachable")
    }

    fun measureConsistency(): ConsistencyType = consistencyType(buckets.sum(), count)
}
