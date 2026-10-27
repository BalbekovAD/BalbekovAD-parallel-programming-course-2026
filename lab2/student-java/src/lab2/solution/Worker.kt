package lab2.solution

import java.util.concurrent.Semaphore
import java.util.function.BooleanSupplier
import kotlin.concurrent.thread

internal fun workerName(id: Int) = ('A' + id).toString()

internal class RunAborted : RuntimeException()

internal class Worker(val id: Int, scheduler: Scheduler, body: Runnable) {
    var finished = false
        private set
    val name: String get() = workerName(id)
    val semaphore = Semaphore(0)
    var waitingLock: SchedLock? = null
    var waitingCondition: BooleanSupplier? = null
    var failure: Throwable? = null

    private val thread = thread(start = false, name = name) {
        threadLocal.set(ThreadContext(scheduler, this))
        try {
            body()
        } catch (_: RunAborted) {
            // Планировщик завершает прогон и будит оставшиеся потоки.
        } catch (error: Throwable) {
            failure = error
        } finally {
            finished = true
            scheduler.wakeUp()
            threadLocal.remove()
        }
    }

    fun start() = thread.start()
    fun wakeUp() = semaphore.release()
    fun join() = thread.join()
}
