package lab2.solution

import java.util.concurrent.Semaphore
import java.util.function.BooleanSupplier

internal data class ThreadContext(val scheduler: Scheduler, val worker: Worker)

internal val threadLocal = ThreadLocal<ThreadContext>()

/**
 * Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами.
 */
class Scheduler {
    companion {
        /** window() из тестовых программ: требует паузу перед следующей общей операцией. */
        fun point(): Unit = logOperation("window")

        /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
        fun await(cond: BooleanSupplier) {
            val (scheduler, worker) = threadLocal.get()!!
            worker.waitingCondition = cond
            scheduler.pause(worker)
            worker.waitingCondition = null
            scheduler.logOperation(worker, "await done")
        }
    }

    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner.  */
    var logging: Boolean = false

    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". </операция></поток> */
    val log: List<String>
        field = mutableListOf()

    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    var deadlock: String? = null

    private val workers = mutableListOf<Worker>()
    private val trace = mutableListOf<Int>()
    private val locks = mutableListOf<SchedLock>()
    internal var aborted = false
        private set

    internal val semaphore = Semaphore(0)

    // Вызывается после подтверждения через семафор: все живые потоки уже на паузе.
    private fun collectFreeWorkers(): List<Int> = workers.filter {
        val waitsFreeLock = (it.waitingLock?.owner ?: -1) == -1
        val waitsTrueCondition = it.waitingCondition?.invoke() ?: true
        !it.finished && waitsFreeLock && waitsTrueCondition
    }.map(Worker::id)

    internal fun registerLock(lock: SchedLock) {
        locks += lock
    }

    internal fun ownsLock(lock: SchedLock): Boolean = lock in locks

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn.  */
    fun spawn(body: Runnable) {
        workers += Worker(workers.size, this, body)
    }

    fun wakeUp() = semaphore.release()

    // Каждая общая операция сама делает одну паузу перед выполнением.
    internal fun pause(worker: Worker) {
        ensureActive()
        wakeUp()
        worker.semaphore.acquire()
        ensureActive()
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last).  */
    fun run(strategy: Strategy): Unit = try {
        workers.forEach(Worker::start)
        semaphore.acquire(workers.size)
        while (true) {
            workers.firstNotNullOfOrNull { it.failure }?.let { throw it }
            if (workers.all { it.finished }) break
            val enabled = collectFreeWorkers()
            if (enabled.isEmpty()) {
                deadlock = locks.filter { it.owner >= 0 }.joinToString(
                    prefix = "held={",
                    postfix = "}",
                    separator = ", "
                ) { "${it.name}:${workerName(it.owner)}" }
                break
            }
            val choice = strategy.pick(enabled, trace.lastOrNull() ?: -1)
            require(choice in enabled) { "Strategy selected disabled thread ${workerName(choice)}" }
            trace += choice
            workers[choice].wakeUp()
            semaphore.acquire()
        }
    } finally {
        aborted = true
        workers.forEach(Worker::wakeUp)
        workers.forEach(Worker::join)
    }

    internal fun ensureActive() {
        if (aborted) throw RunAborted()
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB".  */
    fun traceString(): String = trace.joinToString(separator = "") { workers[it].name }

    internal fun logOperation(worker: Worker, operation: String) {
        require(operation.isNotBlank())
        if (logging && !aborted) {
            log += "${worker.name}  $operation"
        }
    }
}

internal fun logOperation(operation: String) {
    val (scheduler, worker) = threadLocal.get()!!
    scheduler.logOperation(worker, operation)
}

internal fun beforeSharedAccess() {
    val (scheduler, worker) = threadLocal.get()!!
    scheduler.pause(worker)
}

