package collectors

import Snapshot

class EmptyMetricsCollector: MetricsCollector {
    override fun record(value: Int) {
    }

    override fun snapshot(): Snapshot = Snapshot()
}
