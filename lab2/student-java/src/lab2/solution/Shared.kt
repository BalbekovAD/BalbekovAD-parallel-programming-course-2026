package lab2.solution

/* Этап 1: общая переменная. Перед каждым get/set — точка переключения (пауза планировщика),
 * после — строка в лог: чтение "<имя> -> <значение>", запись "<имя> = <значение>".
 * getAcquire/setRelease для планировщика ведут себя так же, как get/set. */
class Shared<T>(private val name: String, private var value: T) {
    /** Чтение с паузой перед ним.  */
    fun get(): T {
        beforeSharedAccess()
        val res = peek()
        logOperation("$name -> $res")
        return res
    }

    /** Запись с паузой перед ней.  */
    fun set(v: T) {
        beforeSharedAccess()
        value = v
        logOperation("$name = $v")
    }

    fun getAcquire(): T = get()

    /** То же, что set(v).  */
    fun setRelease(v: T): Unit = set(v)

    /** Чтение без паузы и без лога: для проверок после прогона и условий await.  */
    fun peek(): T = value
}
