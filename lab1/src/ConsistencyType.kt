enum class ConsistencyType {
    CONSISTENT, COUNT_IS_GREATER, COUNT_IS_LESS
}

fun consistencyType(bucketsSum: Long, atomicCount: Long): ConsistencyType = when {
    atomicCount < bucketsSum -> ConsistencyType.COUNT_IS_LESS
    bucketsSum < atomicCount -> ConsistencyType.COUNT_IS_GREATER
    else -> ConsistencyType.CONSISTENT
}
