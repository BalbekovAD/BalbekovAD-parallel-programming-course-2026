package collectors

import Snapshot
import java.util.Arrays
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.math.max

private const val NOWHERE = -1

class DoubleBufferMetricsCollector : MetricsCollector {
    private val allStates = mutableListOf<ThreadBuffers>()
    private val allStatesLock = Any()
    private val myState = ThreadLocal.withInitial {
        val result = ThreadBuffers()
        synchronized(allStatesLock) {
            allStates += result
        }
        result
    }!!

    @Volatile
    private var active = 0
    private val global = Buf()

    override fun record(value: Int) {
        val my = myState.get()!!
        var b: Int
        while (true) {
            b = active
            my.inside.set(b)
            if (active == b)
                break
            my.inside.setRelease(NOWHERE)
        }
        try {
            my.record(b, value)
        } finally {
            my.inside.setRelease(NOWHERE)
        }
    }

    override fun snapshot(): Snapshot = synchronized(allStatesLock) {
        val old = active
        active = 1 - old
        for (s in allStates) while (s.inside.get() == old) Thread.onSpinWait()
        for (s in allStates) {
            val buf = s.buffers[old]

            for (i in global.buckets.indices) global.buckets[i] += buf.buckets[i]
            global.count += buf.count
            global.sum += buf.sum
            global.min = minOf(global.min, buf.min)
            global.max = maxOf(global.max, buf.max)
            buf.clear()
        }
        return Snapshot(global.buckets.copyOf(), global.count, global.sum, global.min, global.max)
    }
}

private class ThreadBuffers {
    val buffers: Array<Buf> = arrayOf(Buf(), Buf())
    val inside = AtomicInteger(NOWHERE)
    fun record(target: Int, value: Int) {
        buffers[target].record(value)
    }
}

private class Buf {
    val buckets = LongArray(BUCKETS_COUNT)
    var count = 0L
    var sum = 0L
    var min = Long.MAX_VALUE
    var max = 0L

    fun record(value: Int) {
        buckets[minOf(value / 4, buckets.lastIndex)]++
        count++
        sum += value
        min = min(min, value.toLong())
        max = max(max, value.toLong())
    }

    fun clear() {
        Arrays.fill(buckets, 0)
        count = 0
        sum = 0
        min = Long.MAX_VALUE
        max = 0L
    }
}
