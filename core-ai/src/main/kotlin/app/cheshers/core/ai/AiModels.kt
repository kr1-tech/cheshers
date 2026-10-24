package app.cheshers.core.ai

import app.cheshers.core.chess.ChessMove
import app.cheshers.core.chess.Difficulty
import app.cheshers.core.chess.Personality

enum class AiSource {
    OPENROUTER, CACHE, FALLBACK_NO_KEY, FALLBACK_NETWORK, FALLBACK_INVALID, NO_LEGAL_MOVES
}

data class MoveProposalRequest(
    val fen: String,
    val legalMoves: List<ChessMove>,
    val difficulty: Difficulty,
    val personality: Personality = Personality.BALANCED,
    val model: String,
    val apiKey: String,
    val purpose: MovePurpose = MovePurpose.BOT_MOVE,
    val puzzleObjective: String? = null,
)

enum class MovePurpose { BOT_MOVE, HINT, ANALYSIS }

data class AiMoveResult(
    val move: ChessMove?,
    val reasoning: String,
    val source: AiSource,
    val attempts: Int,
) {
    val isFallback: Boolean get() = source != AiSource.OPENROUTER && source != AiSource.CACHE
}

enum class AnnotationSeverity { BOOK, GOOD, INACCURACY, MISTAKE, BLUNDER }

data class MoveAnnotation(
    val ply: Int,
    val severity: AnnotationSeverity,
    val comment: String,
    val bestMove: String? = null,
)

data class GameAnalysis(
    val accuracy: Double,
    val summary: String,
    val coach: String,
    val annotations: List<MoveAnnotation>,
)

data class PositionAdvice(
    /** Normalized from White's perspective: -1 decisive Black, +1 decisive White. */
    val evaluation: Double,
    val bestMove: ChessMove?,
    val explanation: String,
    val source: AiSource,
)

data class DrawDecision(val accepted: Boolean, val message: String, val source: AiSource)

data class OpenRouterModel(val id: String, val label: String) {
    companion object {
        val defaults = listOf(
            OpenRouterModel("openai/gpt-4o-mini", "GPT-4o mini"),
            OpenRouterModel("openai/gpt-4.1-mini", "GPT-4.1 mini"),
            OpenRouterModel("google/gemini-2.0-flash-001", "Gemini 2.0 Flash"),
            OpenRouterModel("anthropic/claude-3.5-haiku", "Claude 3.5 Haiku"),
            OpenRouterModel("qwen/qwen-2.5-7b-instruct", "Qwen 2.5 7B"),
        )
        const val DEFAULT_ID = "openai/gpt-4o-mini"
    }
}

interface AiCache {
    /** Returns cached raw model content, or null. Implementations expire old entries. */
    suspend fun get(key: String): String?
    suspend fun put(key: String, content: String)
}
