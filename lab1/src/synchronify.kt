import collectors.MetricsCollector

fun MetricsCollector.synchronify(): MetricsCollector = object : MetricsCollector {
    @Synchronized
    override fun record(value: Int) = this@synchronify.record(value)

    @Synchronized
    override fun snapshot(): Snapshot = this@synchronify.snapshot()
}
