package app.cheshers.core.data.repository

import kotlin.math.sqrt

enum class GameOutcome(val score: Double) { WIN(1.0), LOSS(0.0), DRAW(0.5) }

object ProgressionRules {
    fun levelForXp(xp: Int): Int = (sqrt(xp.coerceAtLeast(0) / 100.0).toInt() + 1).coerceAtLeast(1)
    fun xpForLevel(level: Int): Int = (level - 1).coerceAtLeast(0) * (level - 1).coerceAtLeast(0) * 100
    fun nextLevelXp(level: Int): Int = level.coerceAtLeast(1) * level.coerceAtLeast(1) * 100
    fun gameXp(outcome: GameOutcome, online: Boolean): Int = when (outcome) {
        GameOutcome.WIN -> 50
        GameOutcome.DRAW -> 25
        GameOutcome.LOSS -> 10
    } + if (online) 10 else 0

    fun levelProgress(xp: Int, level: Int): Float {
        val start = xpForLevel(level)
        val end = nextLevelXp(level)
        return ((xp - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
}

data class ProgressUpdate(
    val xpEarned: Int = 0,
    val coinsEarned: Int = 0,
    val oldLevel: Int = 1,
    val newLevel: Int = 1,
    val completedQuests: List<String> = emptyList(),
    val unlockedAchievements: List<String> = emptyList(),
    val ratingDelta: Int = 0,
) {
    val leveledUp: Boolean get() = newLevel > oldLevel
}
