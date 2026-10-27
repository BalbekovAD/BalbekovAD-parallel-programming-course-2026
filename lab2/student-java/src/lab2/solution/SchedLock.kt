package lab2.solution

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}.
 * Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s.
 */
class SchedLock(scheduler: Scheduler, val name: String?) {
    internal var owner: Int = -1

    init {
        scheduler.registerLock(this)
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем.  */
    fun lock() {
        val (scheduler, worker) = threadLocal.get()!!
        check(scheduler.ownsLock(this)) { "Lock belongs to another scheduler" }
        worker.waitingLock = this
        scheduler.pause(worker)
        worker.waitingLock = null
        check(owner < 0) { "Lock $name is already held" }
        owner = worker.id
        scheduler.logOperation(worker, "lock $name")
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны.  */
    fun unlock() {
        val (scheduler, worker) = threadLocal.get()!!

        if (scheduler.aborted) return
        check(scheduler.ownsLock(this)) { "Lock belongs to another scheduler" }

        scheduler.pause(worker)
        check(owner == worker.id) { "Thread ${worker.name} does not own lock $name" }
        owner = -1
        scheduler.logOperation(worker, "unlock $name")
    }
}
