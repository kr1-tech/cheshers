package app.cheshers.core.chess

import kotlinx.serialization.Serializable

const val START_FEN: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

@Serializable
enum class ChessColor { WHITE, BLACK;
    fun opposite(): ChessColor = if (this == WHITE) BLACK else WHITE
}

@Serializable
enum class PieceType(val uci: String, val san: String) {
    PAWN("p", ""), KNIGHT("n", "N"), BISHOP("b", "B"),
    ROOK("r", "R"), QUEEN("q", "Q"), KING("k", "K");

    companion object {
        fun fromUci(value: Char): PieceType? = entries.firstOrNull { it.uci.equals(value.toString(), true) }
    }
}

@Serializable
data class BoardPiece(val square: String, val color: ChessColor, val type: PieceType)

@Serializable
data class ChessMove(val from: String, val to: String, val promotion: PieceType? = null) {
    val uci: String get() = from + to + (promotion?.uci ?: "")

    companion object {
        private val pattern = Regex("^[a-h][1-8][a-h][1-8][qrbn]?$")
        fun parse(value: String): ChessMove? {
            val normalized = value.trim().lowercase()
            if (!pattern.matches(normalized)) return null
            return ChessMove(
                from = normalized.substring(0, 2),
                to = normalized.substring(2, 4),
                promotion = normalized.getOrNull(4)?.let(PieceType::fromUci),
            )
        }
    }
}

@Serializable
data class MoveRecord(
    val uci: String,
    val san: String,
    val fenBefore: String,
    val fenAfter: String,
    val color: ChessColor,
    val piece: PieceType,
    val captured: PieceType? = null,
    val check: Boolean = false,
    val mate: Boolean = false,
)

@Serializable
enum class RuleStatus {
    ONGOING, CHECK, CHECKMATE, STALEMATE, DRAW_INSUFFICIENT_MATERIAL,
    DRAW_REPETITION, DRAW_FIFTY_MOVE;
    val isOver: Boolean get() = this == CHECKMATE || this == STALEMATE ||
        this == DRAW_INSUFFICIENT_MATERIAL || this == DRAW_REPETITION || this == DRAW_FIFTY_MOVE
}

@Serializable
data class PositionSnapshot(
    val fen: String,
    val pieces: List<BoardPiece>,
    val turn: ChessColor,
    val legalMoves: List<ChessMove>,
    val lastMove: ChessMove?,
    val checkedKing: String?,
    val status: RuleStatus,
    val ply: Int,
)

@Serializable
enum class GameMode { BOT, PASS_AND_PLAY, ONLINE, PUZZLE, ANALYSIS }

@Serializable
enum class Difficulty(val displayName: String) {
    BEGINNER("Beginner"), EASY("Easy"), INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"), EXPERT("Expert"), MASTER("Master")
}

@Serializable
enum class Personality(val displayName: String) { BALANCED("Balanced"), AGGRESSIVE("Aggressive"), POSITIONAL("Positional") }

@Serializable
enum class RatingCategory { BULLET, BLITZ, RAPID, CLASSICAL, UNLIMITED }

@Serializable
enum class TimeControl(
    val id: String,
    val label: String,
    val initialMs: Long,
    val incrementMs: Long,
    val category: RatingCategory,
) {
    BULLET_1_0("1+0", "1 + 0", 60_000, 0, RatingCategory.BULLET),
    BULLET_2_1("2+1", "2 + 1", 120_000, 1_000, RatingCategory.BULLET),
    BLITZ_3_0("3+0", "3 + 0", 180_000, 0, RatingCategory.BLITZ),
    BLITZ_3_2("3+2", "3 + 2", 180_000, 2_000, RatingCategory.BLITZ),
    BLITZ_5_0("5+0", "5 + 0", 300_000, 0, RatingCategory.BLITZ),
    BLITZ_5_3("5+3", "5 + 3", 300_000, 3_000, RatingCategory.BLITZ),
    RAPID_10_0("10+0", "10 + 0", 600_000, 0, RatingCategory.RAPID),
    RAPID_10_5("10+5", "10 + 5", 600_000, 5_000, RatingCategory.RAPID),
    RAPID_15_10("15+10", "15 + 10", 900_000, 10_000, RatingCategory.RAPID),
    CLASSICAL_30_0("30+0", "30 + 0", 1_800_000, 0, RatingCategory.CLASSICAL),
    UNLIMITED("unlimited", "Unlimited", Long.MAX_VALUE, 0, RatingCategory.UNLIMITED);

    val isTimed: Boolean get() = this != UNLIMITED
    companion object {
        fun fromId(id: String): TimeControl = entries.firstOrNull { it.id == id } ?: BLITZ_5_0
    }
}

class IllegalChessMoveException(move: String) : IllegalArgumentException("Illegal chess move: $move")
