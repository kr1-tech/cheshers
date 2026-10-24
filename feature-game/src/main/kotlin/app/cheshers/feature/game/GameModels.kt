package app.cheshers.feature.game

import app.cheshers.core.chess.*
import app.cheshers.core.data.repository.CheshersSettings
import app.cheshers.core.data.repository.ProgressUpdate

enum class SideChoice { WHITE, BLACK, RANDOM }

data class GameOverState(
    val result: String,
    val title: String,
    val reason: String,
    val completedGameId: String? = null,
    val progress: ProgressUpdate? = null,
)

data class GameUiState(
    val initialized: Boolean = false,
    val snapshot: PositionSnapshot = ChessGame().snapshot(),
    val history: List<MoveRecord> = emptyList(),
    val mode: GameMode = GameMode.BOT,
    val playerColor: ChessColor = ChessColor.WHITE,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val personality: Personality = Personality.BALANCED,
    val timeControl: TimeControl = TimeControl.UNLIMITED,
    val whiteMs: Long = Long.MAX_VALUE,
    val blackMs: Long = Long.MAX_VALUE,
    val thinking: Boolean = false,
    val hintThinking: Boolean = false,
    val aiReasoning: String = "",
    val hintsRemaining: Int = 3,
    val hintMove: ChessMove? = null,
    val gameOver: GameOverState? = null,
    val flipped: Boolean = false,
    val drawOfferPending: Boolean = false,
    val message: String? = null,
    val settings: CheshersSettings = CheshersSettings(),
)

enum class GameSound { MOVE, CAPTURE, CHECK, CASTLE, END }
sealed interface GameEvent {
    data class PlaySound(val sound: GameSound) : GameEvent
    data class Toast(val message: String) : GameEvent
}
