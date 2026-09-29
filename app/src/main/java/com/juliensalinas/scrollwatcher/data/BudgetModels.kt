package com.juliensalinas.scrollwatcher.data

/**
 * Snapshot of today's doom-scroll budget state.
 * Base budget is always 30 minutes; extras purchased today add more.
 * Used minutes accumulate only while an evil app is foreground and the screen is on.
 */
data class BudgetState(
    val usedMillis: Long = 0L,
    val extraMillis: Long = 0L,
    val lastResetDate: String = "",
    val evilPackages: Set<String> = emptySet(),
    val monitoringEnabled: Boolean = true,
) {
    val baseBudgetMillis: Long get() = BASE_BUDGET_MILLIS
    val totalBudgetMillis: Long get() = baseBudgetMillis + extraMillis
    val remainingMillis: Long get() = (totalBudgetMillis - usedMillis).coerceAtLeast(0L)
    val isExhausted: Boolean get() = remainingMillis <= 0L

    companion object {
        const val BASE_BUDGET_MILLIS: Long = 30L * 60L * 1000L
        const val EXTRA_PACKAGE_MILLIS: Long = 10L * 60L * 1000L
    }
}
