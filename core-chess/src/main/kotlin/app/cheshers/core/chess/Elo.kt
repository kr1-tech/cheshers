package app.cheshers.core.chess

import kotlin.math.pow
import kotlin.math.roundToInt

/** Conventional Elo updates; unlimited games are deliberately unrated. */
object Elo {
    fun expectedScore(rating: Int, opponentRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponentRating - rating) / 400.0))

    fun updatedRating(rating: Int, opponentRating: Int, score: Double, gamesPlayed: Int): Int {
        require(score in 0.0..1.0) { "Score must be between zero and one" }
        val k = when {
            gamesPlayed < 30 -> 40.0
            rating >= 2400 -> 10.0
            else -> 20.0
        }
        return (rating + k * (score - expectedScore(rating, opponentRating))).roundToInt()
    }
}

data class ClockState(val whiteMs: Long, val blackMs: Long, val running: Boolean = true) {
    fun remaining(color: ChessColor, now: Long, turnStartedAt: Long, turn: ChessColor): Long {
        val base = if (color == ChessColor.WHITE) whiteMs else blackMs
        if (!running || turn != color || base == Long.MAX_VALUE) return base
        return (base - (now - turnStartedAt)).coerceAtLeast(0)
    }

    fun afterMove(color: ChessColor, now: Long, turnStartedAt: Long, incrementMs: Long): ClockState {
        if (color == ChessColor.WHITE) {
            val remaining = remaining(ChessColor.WHITE, now, turnStartedAt, ChessColor.WHITE)
            return copy(whiteMs = if (remaining == Long.MAX_VALUE) remaining else remaining + incrementMs)
        }
        val remaining = remaining(ChessColor.BLACK, now, turnStartedAt, ChessColor.BLACK)
        return copy(blackMs = if (remaining == Long.MAX_VALUE) remaining else remaining + incrementMs)
    }
}
