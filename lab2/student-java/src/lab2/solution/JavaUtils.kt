package lab2.solution

import java.util.function.BooleanSupplier

operator fun BooleanSupplier.invoke(): Boolean = asBoolean
operator fun Runnable.invoke(): Unit = run()
