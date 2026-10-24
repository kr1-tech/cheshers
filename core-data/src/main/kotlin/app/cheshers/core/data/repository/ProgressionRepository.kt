package app.cheshers.core.data.repository

import androidx.room.withTransaction
import app.cheshers.core.chess.Elo
import app.cheshers.core.chess.RatingCategory
import app.cheshers.core.data.local.CheshersDatabase
import app.cheshers.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ProgressionRepository @Inject constructor(private val database: CheshersDatabase) {
    private val profiles = database.profiles()
    private val quests = database.quests()
    private val achievements = database.achievements()
    private val cosmetics = database.cosmetics()
    private val puzzleResults = database.puzzleResults()

    fun observeProfile(): Flow<ProfileEntity?> = profiles.observe()
    fun observeQuests(now: Long = System.currentTimeMillis()): Flow<List<QuestEntity>> = quests.observeActive(now)
    fun observeAchievements(): Flow<List<AchievementEntity>> = achievements.observe()
    fun observeCosmetics(): Flow<List<CosmeticEntity>> = cosmetics.observe()
    fun observeSolvedPuzzles(): Flow<List<String>> = puzzleResults.observeSolvedIds()
    suspend fun profile(): ProfileEntity = ensureProfile()

    suspend fun ensureInitialized(now: Long = System.currentTimeMillis()) {
        database.withTransaction {
            ensureProfile()
            ensureQuestDefinitions(now)
            ensureAchievementDefinitions()
            ensureCosmeticDefinitions()
        }
    }

    suspend fun ensureQuests(now: Long = System.currentTimeMillis()) = database.withTransaction {
        quests.deleteExpired(now)
        ensureQuestDefinitions(now)
    }

    suspend fun updateProfile(username: String, avatarId: String, country: String): Result<Unit> = runCatching {
        val cleanName = username.trim()
        require(cleanName.length in 3..20 && USERNAME.matches(cleanName)) { "Use 3–20 letters, numbers, spaces, _ . or -" }
        val cleanCountry = country.trim().uppercase()
        require(cleanCountry.isEmpty() || Regex("[A-Z]{2}").matches(cleanCountry)) { "Use a two-letter country code" }
        database.withTransaction {
            val current = ensureProfile()
            val avatar = cosmetics.byId(avatarId)
            require(avatar == null || avatar.unlocked || avatar.id == current.avatarId) { "Avatar is still locked" }
            profiles.upsert(current.copy(username = cleanName, avatarId = avatarId, country = cleanCountry))
        }
    }

    suspend fun purchaseCosmetic(id: String): Result<Unit> = runCatching {
        database.withTransaction {
            val item = cosmetics.byId(id) ?: throw IllegalArgumentException("Unknown cosmetic")
            if (!item.unlocked) {
                val profile = ensureProfile()
                check(profile.coins >= item.cost) { "Not enough coins" }
                profiles.upsert(profile.copy(coins = profile.coins - item.cost))
                cosmetics.upsert(item.copy(unlocked = true))
            }
        }
    }

    suspend fun recordGame(
        outcome: GameOutcome,
        online: Boolean,
        category: RatingCategory,
        opponentRating: Int = 1200,
        rated: Boolean = !online || category != RatingCategory.UNLIMITED,
        checkmate: Boolean = false,
        queenCheckmate: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): ProgressUpdate = database.withTransaction {
        val before = ensureProfile()
        val baseXp = ProgressionRules.gameXp(outcome, online)
        var profile = before.copy(
            xp = before.xp + baseXp,
            wins = before.wins + if (outcome == GameOutcome.WIN) 1 else 0,
            losses = before.losses + if (outcome == GameOutcome.LOSS) 1 else 0,
            draws = before.draws + if (outcome == GameOutcome.DRAW) 1 else 0,
            gamesPlayed = before.gamesPlayed + 1,
            checkmates = before.checkmates + if (checkmate) 1 else 0,
            queenCheckmates = before.queenCheckmates + if (queenCheckmate) 1 else 0,
            currentWinStreak = if (outcome == GameOutcome.WIN) before.currentWinStreak + 1 else 0,
        )
        profile = profile.copy(bestWinStreak = max(profile.bestWinStreak, profile.currentWinStreak))

        var ratingDelta = 0
        if (rated && category != RatingCategory.UNLIMITED) {
            val oldRating = ratingOf(profile, category)
            val games = ratingGamesOf(profile, category)
            val newRating = Elo.updatedRating(oldRating, opponentRating, outcome.score, games)
            ratingDelta = newRating - oldRating
            profile = withRating(profile, category, newRating, games + 1)
        }
        val metrics = buildMap {
            put(QuestMetric.PLAY_GAMES, 1)
            if (outcome == GameOutcome.WIN) put(QuestMetric.WIN_GAMES, 1)
            if (online && outcome == GameOutcome.WIN) put(QuestMetric.WIN_ONLINE, 1)
            if (checkmate) put(QuestMetric.CHECKMATE, 1)
            if (queenCheckmate) put(QuestMetric.QUEEN_CHECKMATE, 1)
        }
        val questResult = applyQuestProgress(profile, metrics, now)
        profile = questResult.profile
        profile = profile.copy(level = ProgressionRules.levelForXp(profile.xp))
        profiles.upsert(profile)
        val unlocked = updateAchievements(profile, now)
        ProgressUpdate(
            xpEarned = profile.xp - before.xp,
            coinsEarned = profile.coins - before.coins,
            oldLevel = before.level,
            newLevel = profile.level,
            completedQuests = questResult.completedTitles,
            unlockedAchievements = unlocked,
            ratingDelta = ratingDelta,
        )
    }

    suspend fun recordPuzzle(
        puzzleId: String,
        attempts: Int,
        hintsUsed: Int,
        daily: Boolean,
        now: Long = System.currentTimeMillis(),
    ): ProgressUpdate = database.withTransaction {
        if (puzzleResults.byId(puzzleId) != null) return@withTransaction ProgressUpdate()
        val before = ensureProfile()
        val day = utcDay(now)
        val previousDay = before.lastDailyPuzzleDay
        var profile = before.copy(
            xp = before.xp + 20,
            puzzlesSolved = before.puzzlesSolved + 1,
            dailyPuzzleStreak = if (!daily) before.dailyPuzzleStreak else when (previousDay) {
                day -> before.dailyPuzzleStreak
                day - 1 -> before.dailyPuzzleStreak + 1
                else -> 1
            },
            lastDailyPuzzleDay = if (daily) day else previousDay,
        )
        puzzleResults.upsert(PuzzleResultEntity(puzzleId, now, attempts, hintsUsed, daily))
        val questResult = applyQuestProgress(profile, mapOf(QuestMetric.SOLVE_PUZZLES to 1), now)
        profile = questResult.profile.copy(level = ProgressionRules.levelForXp(questResult.profile.xp))
        profiles.upsert(profile)
        val unlocked = updateAchievements(profile, now)
        ProgressUpdate(
            xpEarned = profile.xp - before.xp,
            coinsEarned = profile.coins - before.coins,
            oldLevel = before.level,
            newLevel = profile.level,
            completedQuests = questResult.completedTitles,
            unlockedAchievements = unlocked,
        )
    }

    suspend fun recordAccuracy(accuracy: Double) {
        database.withTransaction {
            val profile = ensureProfile()
            profiles.upsert(profile.copy(
                accuracyTotal = profile.accuracyTotal + accuracy.coerceIn(0.0, 100.0),
                accuracySamples = profile.accuracySamples + 1,
            ))
        }
    }

    suspend fun setOnlineRating(category: RatingCategory, rating: Int) {
        if (category == RatingCategory.UNLIMITED) return
        database.withTransaction {
            val profile = ensureProfile()
            profiles.upsert(withRating(profile, category, rating, ratingGamesOf(profile, category)))
        }
    }

    private suspend fun ensureProfile(): ProfileEntity {
        profiles.get()?.let { return it }
        val created = ProfileEntity()
        profiles.upsert(created)
        return created
    }

    private suspend fun ensureQuestDefinitions(now: Long) {
        val existing = quests.all().associateBy { it.id }
        val day = utcDay(now)
        val dailyEnd = (day + 1) * DAY_MS
        val week = Math.floorDiv(now, WEEK_MS)
        val weeklyEnd = (week + 1) * WEEK_MS
        val dailyTag = utcDate(day * DAY_MS)
        val special = when ((day % 3).toInt()) {
            0 -> QuestDefinition(QuestMetric.QUEEN_CHECKMATE, "Royal finish", "Checkmate with a queen", 1, 90, 35)
            1 -> QuestDefinition(QuestMetric.PLAY_GAMES, "Stay in the game", "Play 3 games", 3, 60, 20)
            else -> QuestDefinition(QuestMetric.CHECKMATE, "Find the finish", "Deliver 1 checkmate", 1, 80, 30)
        }
        val definitions = listOf(
            QuestDefinition(QuestMetric.WIN_GAMES, "Daily victories", "Win 2 games", 2, 100, 40),
            QuestDefinition(QuestMetric.SOLVE_PUZZLES, "Tactical training", "Solve 5 puzzles", 5, 80, 30),
            special,
        ).map { definition ->
            definition.entity("d-$dailyTag-${definition.metric.name}", "DAILY", dailyEnd)
        } + listOf(
            QuestDefinition(QuestMetric.WIN_ONLINE, "Weekly contender", "Win 10 online games", 10, 500, 200),
            QuestDefinition(QuestMetric.PLAY_GAMES, "Weekly regular", "Play 25 games", 25, 350, 150),
        ).map { definition -> definition.entity("w-$week-${definition.metric.name}", "WEEKLY", weeklyEnd) }
        quests.upsertAll(definitions.filterNot { existing.containsKey(it.id) })
    }

    private suspend fun ensureAchievementDefinitions() {
        val existing = achievements.all().map { it.id }.toSet()
        achievements.upsertAll(achievementDefinitions().filterNot { existing.contains(it.id) })
    }

    private suspend fun ensureCosmeticDefinitions() {
        val defaults = listOf(
            CosmeticEntity("board_green", "BOARD", "Emerald", 0, true),
            CosmeticEntity("board_light", "BOARD", "Classic light", 0, true),
            CosmeticEntity("board_dark", "BOARD", "Midnight", 150),
            CosmeticEntity("board_wood", "BOARD", "Walnut", 200),
            CosmeticEntity("board_blue", "BOARD", "Ocean", 250),
            CosmeticEntity("pieces_classic", "PIECES", "Classic", 0, true),
            CosmeticEntity("pieces_neo", "PIECES", "Neo", 300),
            CosmeticEntity("pieces_alpha", "PIECES", "Alpha", 300),
            CosmeticEntity("avatar_king", "AVATAR", "King", 0, true),
            CosmeticEntity("avatar_cat", "AVATAR", "Cheshire cat", 150),
            CosmeticEntity("avatar_fox", "AVATAR", "Fox", 200),
            CosmeticEntity("avatar_rook", "AVATAR", "Rook", 250),
            CosmeticEntity("avatar_crown", "AVATAR", "Crown", 500),
            CosmeticEntity("badge_strategist", "BADGE", "Strategist badge", 400),
        )
        val existing = cosmetics.all().map { it.id }.toSet()
        cosmetics.upsertAll(defaults.filterNot { existing.contains(it.id) })
    }

    private suspend fun applyQuestProgress(
        profile: ProfileEntity,
        metrics: Map<QuestMetric, Int>,
        now: Long,
    ): QuestApplication {
        var xp = 0
        var coins = 0
        val completed = mutableListOf<String>()
        quests.active(now).forEach { quest ->
            if (quest.completedAt != null) return@forEach
            val amount = metrics[QuestMetric.valueOf(quest.metric)] ?: return@forEach
            val progress = (quest.progress + amount).coerceAtMost(quest.target)
            val isComplete = progress >= quest.target
            quests.upsert(quest.copy(progress = progress, completedAt = if (isComplete) now else null))
            if (isComplete) {
                xp += quest.rewardXp
                coins += quest.rewardCoins
                completed += quest.title
            }
        }
        return QuestApplication(profile.copy(xp = profile.xp + xp, coins = profile.coins + coins), completed)
    }

    private suspend fun updateAchievements(profile: ProfileEntity, now: Long): List<String> {
        val unlocked = mutableListOf<String>()
        val existing = achievements.all().associateBy { it.id }
        val updated = achievementDefinitions().map { definition ->
            val current = existing[definition.id]
            val progress = when (definition.id) {
                "first_win" -> profile.wins
                "streak_10" -> profile.bestWinStreak
                "puzzles_100" -> profile.puzzlesSolved
                "first_mate" -> profile.checkmates
                "queen_mate" -> profile.queenCheckmates
                "games_50" -> profile.gamesPlayed
                "level_5" -> profile.level
                else -> 0
            }.coerceAtMost(definition.target)
            val unlockNow = progress >= definition.target && current?.unlockedAt == null
            if (unlockNow) unlocked += definition.title
            definition.copy(progress = progress, unlockedAt = current?.unlockedAt ?: if (unlockNow) now else null)
        }
        achievements.upsertAll(updated)
        return unlocked
    }

    private data class QuestApplication(val profile: ProfileEntity, val completedTitles: List<String>)
    private data class QuestDefinition(val metric: QuestMetric, val title: String, val description: String, val target: Int, val xp: Int, val coins: Int) {
        fun entity(id: String, period: String, expiresAt: Long) = QuestEntity(id, period, metric.name, title, description, target, 0, xp, coins, expiresAt)
    }

    private fun achievementDefinitions() = listOf(
        AchievementEntity("first_win", "First victory", "Win your first game", 1),
        AchievementEntity("streak_10", "Unstoppable", "Win 10 games in a row", 10),
        AchievementEntity("puzzles_100", "Tactician", "Solve 100 puzzles", 100),
        AchievementEntity("first_mate", "Checkmate!", "Deliver your first checkmate", 1),
        AchievementEntity("queen_mate", "Royal finish", "Checkmate with a queen", 1),
        AchievementEntity("games_50", "Regular", "Play 50 games", 50),
        AchievementEntity("level_5", "Rising star", "Reach level 5", 5),
    )

    private fun ratingOf(profile: ProfileEntity, category: RatingCategory): Int = when (category) {
        RatingCategory.BULLET -> profile.bulletRating
        RatingCategory.BLITZ -> profile.blitzRating
        RatingCategory.RAPID -> profile.rapidRating
        RatingCategory.CLASSICAL -> profile.classicalRating
        RatingCategory.UNLIMITED -> 1200
    }
    private fun ratingGamesOf(profile: ProfileEntity, category: RatingCategory): Int = when (category) {
        RatingCategory.BULLET -> profile.bulletGames
        RatingCategory.BLITZ -> profile.blitzGames
        RatingCategory.RAPID -> profile.rapidGames
        RatingCategory.CLASSICAL -> profile.classicalGames
        RatingCategory.UNLIMITED -> 0
    }
    private fun withRating(profile: ProfileEntity, category: RatingCategory, rating: Int, games: Int): ProfileEntity = when (category) {
        RatingCategory.BULLET -> profile.copy(bulletRating = rating, bulletGames = games)
        RatingCategory.BLITZ -> profile.copy(blitzRating = rating, blitzGames = games)
        RatingCategory.RAPID -> profile.copy(rapidRating = rating, rapidGames = games)
        RatingCategory.CLASSICAL -> profile.copy(classicalRating = rating, classicalGames = games)
        RatingCategory.UNLIMITED -> profile
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val WEEK_MS = 7 * DAY_MS
        val USERNAME = Regex("^[\\p{L}\\p{N}_ .-]+$")
        fun utcDay(now: Long): Long = Math.floorDiv(now, DAY_MS)
        fun utcDate(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
    }
}
