package lab2.solution

/** Decides who runs next. Multi-run strategies (DFS) also say whether another run is needed.  */
interface Strategy {
    fun pick(enabled: List<Int>, last: Int): Int
    fun beginRun() {}
    fun endRun(): Boolean {
        return false
    } // true = run again
}
