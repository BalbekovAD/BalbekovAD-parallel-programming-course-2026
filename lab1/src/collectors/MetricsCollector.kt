package collectors

import Snapshot

const val BUCKETS_COUNT = 256

interface MetricsCollector {
    fun record(value: Int)
    fun snapshot(): Snapshot
}
