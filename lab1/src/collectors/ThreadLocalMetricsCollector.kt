package collectors

import Snapshot
import kotlin.concurrent.atomics.*

class ThreadLocalMetricsCollector : MetricsCollector {
    private val allStates = mutableListOf<ThreadState>()
    private val allStatesLock = Any()
    private val myState = ThreadLocal.withInitial {
        val result = ThreadState()
        synchronized(allStatesLock) {
            allStates += result
        }
        result
    }!!
    override fun record(value: Int) = myState.get()!!.record(value)

    override fun snapshot(): Snapshot {
        val out = LongArray(BUCKETS_COUNT)
        var count = 0L
        var sum = 0L
        var min = Long.MAX_VALUE
        var max = Long.MIN_VALUE
        val states = synchronized(allStatesLock) { allStates.toList() }
        states.forEach { s ->
            for (i in out.indices) out[i] = s.buckets.get(i)
            count += s.count.get()
            sum += s.sum.get()
            min = minOf(min, s.min.get())
            max = maxOf(max, s.max.get())
        }
        return Snapshot(out, count, sum, min, max)
    }
}


@OptIn(ExperimentalAtomicApi::class)
private class ThreadState {
    val buckets = AtomicLongArray(BUCKETS_COUNT).asJavaAtomicArray()
    val count = AtomicLong(0).asJavaAtomic()
    val sum = AtomicLong(0).asJavaAtomic()
    val min = AtomicLong(Long.MAX_VALUE).asJavaAtomic()
    val max = AtomicLong(Long.MIN_VALUE).asJavaAtomic()
    fun record(value: Int) {
        val i = minOf(value / 4, buckets.length() - 1)
        buckets.setRelease(i, buckets.getPlain(i))
        count.setRelease(count.plain)
        sum.setRelease(sum.plain + value)
        if (value < min.plain) min.setRelease(value.toLong())
        if (value > max.plain) max.setRelease(value.toLong())
    }
}
