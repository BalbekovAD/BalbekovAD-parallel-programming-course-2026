import collectors.BaseMetricsCollector
import collectors.DoubleBufferMetricsCollector
import collectors.EmptyMetricsCollector
import collectors.MetricsCollector
import collectors.ShardMetricsCollector
import collectors.ThreadLocalMetricsCollector

enum class CollectorType {
    NOT_CONCURRENT,
    NAIVELY_SYNCHRONIZED,
    NAIVELY_SYNCHRONIZED_EMPTY_BODY,
    LOCK_SHARDING,
    THREAD_LOCAL,
    DOUBLE_BUFFER;
    fun createMetricsCollector(): MetricsCollector = when (this) {
        NOT_CONCURRENT -> BaseMetricsCollector()
        NAIVELY_SYNCHRONIZED -> BaseMetricsCollector().synchronify()
        NAIVELY_SYNCHRONIZED_EMPTY_BODY -> EmptyMetricsCollector().synchronify()
        LOCK_SHARDING -> ShardMetricsCollector()
        THREAD_LOCAL -> ThreadLocalMetricsCollector()
        DOUBLE_BUFFER -> DoubleBufferMetricsCollector()
    }
}
