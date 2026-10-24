package app.cheshers.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val active: Boolean,
    val mode: String,
    val initialFen: String,
    val movesJson: String = "[]",
    val pgn: String = "",
    val whiteName: String = "White",
    val blackName: String = "Black",
    val playerColor: String? = null,
    val difficulty: String? = null,
    val personality: String = "BALANCED",
    val timeControlId: String = "unlimited",
    val whiteMs: Long = Long.MAX_VALUE,
    val blackMs: Long = Long.MAX_VALUE,
    val hintsRemaining: Int = 3,
    val result: String = "*",
    val resultReason: String = "",
    val ratingDelta: Int = 0,
    val accuracy: Double? = null,
    val analysisJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val username: String = "Player",
    val avatarId: String = "king",
    val country: String = "",
    val xp: Int = 0,
    val level: Int = 1,
    val coins: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val gamesPlayed: Int = 0,
    val checkmates: Int = 0,
    val queenCheckmates: Int = 0,
    val puzzlesSolved: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val dailyPuzzleStreak: Int = 0,
    val lastDailyPuzzleDay: Long = -1,
    val bulletRating: Int = 1200,
    val blitzRating: Int = 1200,
    val rapidRating: Int = 1200,
    val classicalRating: Int = 1200,
    val bulletGames: Int = 0,
    val blitzGames: Int = 0,
    val rapidGames: Int = 0,
    val classicalGames: Int = 0,
    val accuracyTotal: Double = 0.0,
    val accuracySamples: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,
    val period: String,
    val metric: String,
    val title: String,
    val description: String,
    val target: Int,
    val progress: Int = 0,
    val rewardXp: Int,
    val rewardCoins: Int,
    val expiresAt: Long,
    val completedAt: Long? = null,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val target: Int,
    val progress: Int = 0,
    val unlockedAt: Long? = null,
)

@Entity(tableName = "cosmetics")
data class CosmeticEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val cost: Int,
    val unlocked: Boolean = false,
)

@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey val key: String,
    val content: String,
    val createdAt: Long,
)

@Entity(tableName = "puzzle_results")
data class PuzzleResultEntity(
    @PrimaryKey val puzzleId: String,
    val solvedAt: Long,
    val attempts: Int,
    val hintsUsed: Int,
    val daily: Boolean,
)

enum class QuestMetric { PLAY_GAMES, WIN_GAMES, WIN_ONLINE, SOLVE_PUZZLES, QUEEN_CHECKMATE, CHECKMATE }
enum class CosmeticType { BOARD, PIECES, AVATAR, BADGE }
