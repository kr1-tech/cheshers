package app.cheshers.feature.game.puzzle

import app.cheshers.core.chess.ChessColor
import app.cheshers.core.chess.ChessGame

data class Puzzle(
    val id: String,
    val title: String,
    val theme: String,
    val objective: String,
    val fen: String,
    val solution: List<String>,
)

object PuzzleCatalog {
    val puzzles = listOf(
        Puzzle("fools-mate", "The shortest finish", "Mate in 1", "Black to move: deliver checkmate.", "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2", listOf("d8h4")),
        Puzzle("scholars-mate", "The f7 weakness", "Mate in 1", "White to move: use the queen and bishop battery.", "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", listOf("h5f7")),
        Puzzle("back-rank-rook", "No escape squares", "Mate in 1", "White to move: exploit the back rank.", "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", listOf("a1a8")),
        Puzzle("two-rook-mate", "Rooks in harmony", "Mate in 1", "White to move: one rook checks while the other covers escape.", "7k/1R6/8/8/8/8/8/R5K1 w - - 0 1", listOf("a1a8")),
        Puzzle("smothered-mate", "Smothered king", "Mate in 1", "White to move: the knight has a royal finish.", "6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1", listOf("g5f7")),
        Puzzle("queen-mate", "A royal net", "Mate in 1", "White to move: coordinate queen and king.", "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1", listOf("f7f8")),
        Puzzle("queen-back-rank", "Queen on the back rank", "Mate in 1", "White to move: find the checking route.", "6k1/5ppp/8/8/8/8/8/4Q1K1 w - - 0 1", listOf("e1e8")),
        Puzzle("knight-fork", "Royal fork", "Fork", "White to move: attack the king and queen with one move.", "3q3k/8/8/4N3/8/8/8/6K1 w - - 0 1", listOf("e5f7")),
        Puzzle("bishop-pin", "A pinning diagonal", "Pin", "White to move: pin the knight to its king.", "r3k3/8/2n5/8/8/8/P7/4KB2 w - - 0 1", listOf("f1b5")),
        Puzzle("en-passant", "A passing capture", "En passant", "White to move: use the special pawn capture.", "rnbqkbnr/1pp1pppp/p7/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3", listOf("e5d6")),
        Puzzle("promotion", "A new queen", "Promotion", "White to move: promote the pawn.", "8/P7/8/8/8/8/7k/4K3 w - - 0 1", listOf("a7a8q")),
        Puzzle("castling", "Safety first", "Castling", "White to move: castle king-side.", "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", listOf("e1g1")),
    )

    fun byId(id: String): Puzzle = puzzles.firstOrNull { it.id == id } ?: puzzles.first()
    fun daily(timestamp: Long): Puzzle = puzzles[(Math.floorDiv(timestamp, 86_400_000L) % puzzles.size).toInt()]
    fun next(id: String): Puzzle = puzzles[(puzzles.indexOfFirst { it.id == id }.coerceAtLeast(0) + 1) % puzzles.size]
    fun playerColor(puzzle: Puzzle): ChessColor = ChessGame(puzzle.fen).turn
}
