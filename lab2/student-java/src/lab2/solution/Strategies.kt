package lab2.solution

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */

/** Всегда первый поток из enabled.  */
object FirstAlive : Strategy {
    override fun pick(enabled: List<Int>, last: Int): Int = enabled.first()
}

/** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1).  */
class Replay(private val trace: String) : Strategy {
    private var iterator = trace.toList().listIterator()

    /** Сбросить позицию в трассе перед прогоном.  */
    override fun beginRun() {
        iterator = trace.toList().listIterator()
    }

    override fun pick(enabled: List<Int>, last: Int): Int {
        val decision = iterator.nextIndex() + 1
        require(iterator.hasNext()) { "Replay trace ended at decision $decision" }
        val name = iterator.next()
        val choice = name - 'A'
        require(choice in enabled) {
            "Replay thread $name is not enabled at decision $decision"
        }
        return choice
    }
}

/** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed).  */
class RandomPick(seed: Long) : Strategy {
    private val random = java.util.Random(seed)

    override fun pick(enabled: List<Int>, last: Int): Int = enabled[random.nextInt(enabled.size)]
}

/** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
 * bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE.  */
class Dfs(private val bound: Int) : Strategy {
    private val path = mutableListOf<DfsDecision>()
    private var position = 0
    private var preemptions = 0

    init {
        require(bound >= 0) { "Preemption bound must be non-negative" }
    }

    /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений).  */
    override fun beginRun() {
        position = 0
        preemptions = 0
    }

    override fun pick(enabled: List<Int>, last: Int): Int {
        if (position == path.size) {
            path += DfsDecision(
                choices = if (preemptions >= bound && last in enabled) {
                    [last]
                } else {
                    enabled
                }
            )
        }
        val choice = path[position++].choice
        check(choice in enabled) { "DFS prefix no longer matches enabled threads" }
        if (choice != last && last in enabled) preemptions++
        return choice
    }

    /** Выбрать следующий путь; false — дерево обойдено.  */
    override fun endRun(): Boolean {
        // A completed/deadlocked run has no decisions beyond its actual length.
        path.subList(position, path.size).clear()
        while (path.isNotEmpty()) {
            val decision = path.last()
            if (decision.advance()) return true
            path.removeLast()
        }
        return false
    }
}

private class DfsDecision(choices: List<Int>) {
    private val iterator = choices.listIterator()
    var choice: Int = iterator.next()
        private set

    fun advance(): Boolean {
        if (!iterator.hasNext()) return false
        choice = iterator.next()
        return true
    }
}
