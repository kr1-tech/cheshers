package app.cheshers.core.data.repository

import app.cheshers.core.ai.GameAnalysis
import app.cheshers.core.chess.*
import app.cheshers.core.data.local.CheshersDatabase
import app.cheshers.core.data.local.entity.GameEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveGameConfig(
    val id: String,
    val mode: GameMode,
    val initialFen: String = START_FEN,
    val playerColor: ChessColor? = null,
    val difficulty: Difficulty? = null,
    val personality: Personality = Personality.BALANCED,
    val timeControl: TimeControl = TimeControl.UNLIMITED,
    val whiteName: String = "White",
    val blackName: String = "Black",
    val hintsRemaining: Int = 3,
)

data class RestoredGame(
    val config: ActiveGameConfig,
    val game: ChessGame,
    val whiteMs: Long,
    val blackMs: Long,
    val entity: GameEntity,
)

@Singleton
class GameRepository @Inject constructor(
    private val database: CheshersDatabase,
    private val json: Json,
    private val gson: Gson,
) {
    private val dao = database.games()

    fun observeHistory(): Flow<List<GameEntity>> = dao.observeHistory()
    fun observeLatestActive(): Flow<GameEntity?> = dao.observeLatestActive()
    suspend fun latestActive(): GameEntity? = dao.latestActive()
    suspend fun gameById(id: String): GameEntity? = dao.byId(id)
    suspend fun deleteGame(id: String) = dao.delete(id)
    suspend fun exportPgn(id: String): String? = dao.byId(id)?.pgn

    suspend fun saveActive(
        config: ActiveGameConfig,
        game: ChessGame,
        whiteMs: Long,
        blackMs: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        // Capture immutable move/PGN data before the first suspending DAO call.
        val movesJson = json.encodeToString(ListSerializer(String.serializer()), game.history.map { it.uci })
        val pgn = game.toPgn(config.whiteName, config.blackName)
        val previous = dao.byId(config.id)
        dao.upsert(
            GameEntity(
                id = config.id,
                active = true,
                mode = config.mode.name,
                initialFen = config.initialFen,
                movesJson = movesJson,
                pgn = pgn,
                whiteName = config.whiteName,
                blackName = config.blackName,
                playerColor = config.playerColor?.name,
                difficulty = config.difficulty?.name,
                personality = config.personality.name,
                timeControlId = config.timeControl.id,
                whiteMs = whiteMs,
                blackMs = blackMs,
                hintsRemaining = config.hintsRemaining,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }

    suspend fun restoreActive(id: String): RestoredGame? {
        val entity = dao.byId(id)?.takeIf { it.active } ?: return null
        return restore(entity)
    }

    suspend fun restore(entity: GameEntity): RestoredGame? = runCatching {
        val moves = json.decodeFromString(ListSerializer(String.serializer()), entity.movesJson)
        val game = ChessGame(entity.initialFen)
        moves.forEach(game::move)
        val config = ActiveGameConfig(
            id = entity.id,
            mode = GameMode.valueOf(entity.mode),
            initialFen = entity.initialFen,
            playerColor = entity.playerColor?.let(ChessColor::valueOf),
            difficulty = entity.difficulty?.let(Difficulty::valueOf),
            personality = runCatching { Personality.valueOf(entity.personality) }.getOrDefault(Personality.BALANCED),
            timeControl = TimeControl.fromId(entity.timeControlId),
            whiteName = entity.whiteName,
            blackName = entity.blackName,
            hintsRemaining = entity.hintsRemaining,
        )
        RestoredGame(config, game, entity.whiteMs, entity.blackMs, entity)
    }.getOrNull()

    suspend fun completeActive(
        config: ActiveGameConfig,
        game: ChessGame,
        result: String,
        reason: String,
        ratingDelta: Int = 0,
        now: Long = System.currentTimeMillis(),
    ): GameEntity? {
        val active = dao.byId(config.id) ?: return null
        val completed = active.copy(
            id = UUID.randomUUID().toString(),
            active = false,
            movesJson = json.encodeToString(ListSerializer(String.serializer()), game.history.map { it.uci }),
            pgn = game.toPgn(config.whiteName, config.blackName, result),
            result = result,
            resultReason = reason,
            ratingDelta = ratingDelta,
            updatedAt = now,
        )
        dao.upsert(completed)
        dao.delete(config.id)
        return completed
    }

    suspend fun saveAnalysis(gameId: String, analysis: GameAnalysis) {
        dao.saveAnalysis(gameId, gson.toJson(analysis), analysis.accuracy)
    }

    fun decodeAnalysis(entity: GameEntity): GameAnalysis? = entity.analysisJson?.let { raw ->
        runCatching { gson.fromJson(raw, GameAnalysis::class.java) }.getOrNull()
    }
}
