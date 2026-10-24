package app.cheshers.core.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-confinement is intentional: a ViewModel or server owns one instance.
 * Chesslib is always the authority; every external move is matched against legalMoves().
 */
class ChessGame(initialFen: String = START_FEN) {
    private val initialPosition: String = initialFen
    private var board = Board().apply { loadFromFen(initialFen) }
    private val records = mutableListOf<MoveRecord>()

    val fen: String get() = board.fen
    val history: List<MoveRecord> get() = records.toList()
    val turn: ChessColor get() = board.sideToMove.toModel()

    fun legalMoves(): List<ChessMove> = board.legalMoves().map { it.toModel() }
    fun legalMovesFrom(square: String): List<ChessMove> = legalMoves().filter { it.from == square.lowercase() }
    fun isLegal(move: ChessMove): Boolean = legalMoves().any { it.uci == move.uci }

    fun pieceAt(square: String): BoardPiece? {
        val libSquare = runCatching { Square.valueOf(square.uppercase()) }.getOrNull() ?: return null
        return board.getPiece(libSquare).takeIf { it != Piece.NONE }?.toModel(libSquare)
    }

    fun snapshot(): PositionSnapshot {
        val pieces = Square.entries.mapNotNull { square ->
            if (square == Square.NONE) return@mapNotNull null
            board.getPiece(square).takeIf { it != Piece.NONE }?.toModel(square)
        }
        val status = ruleStatus()
        val checkedKing = if (board.isKingAttacked) {
            pieces.firstOrNull { it.type == PieceType.KING && it.color == turn }?.square
        } else null
        return PositionSnapshot(
            fen = fen,
            pieces = pieces,
            turn = turn,
            legalMoves = if (status.isOver) emptyList() else legalMoves(),
            lastMove = records.lastOrNull()?.uci?.let(ChessMove::parse),
            checkedKing = checkedKing,
            status = status,
            ply = records.size,
        )
    }

    fun move(uci: String): MoveRecord = move(ChessMove.parse(uci) ?: throw IllegalChessMoveException(uci))

    fun move(modelMove: ChessMove): MoveRecord {
        if (ruleStatus().isOver) throw IllegalChessMoveException(modelMove.uci)
        val libMove = board.legalMoves().firstOrNull { it.toString().equals(modelMove.uci, true) }
            ?: throw IllegalChessMoveException(modelMove.uci)
        val fenBefore = board.fen
        val movingColor = turn
        val movingPiece = board.getPiece(libMove.from)
        val targetPiece = board.getPiece(libMove.to)
        val isEnPassant = movingPiece.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN &&
            libMove.to == board.enPassant && targetPiece == Piece.NONE
        val san = buildSan(libMove, movingPiece, isEnPassant)
        if (!board.doMove(libMove, true)) throw IllegalChessMoveException(modelMove.uci)
        libMove.san = san
        val record = MoveRecord(
            uci = libMove.toString().lowercase(),
            san = san,
            fenBefore = fenBefore,
            fenAfter = board.fen,
            color = movingColor,
            piece = movingPiece.pieceType.toModel(),
            captured = when {
                isEnPassant -> PieceType.PAWN
                targetPiece != Piece.NONE -> targetPiece.pieceType.toModel()
                else -> null
            },
            check = san.endsWith("+") || san.endsWith("#"),
            mate = san.endsWith("#"),
        )
        records += record
        return record
    }

    fun undo(): MoveRecord? {
        if (records.isEmpty()) return null
        board.undoMove()
        return records.removeAt(records.lastIndex)
    }

    fun ruleStatus(): RuleStatus = when {
        board.isMated -> RuleStatus.CHECKMATE
        board.isStaleMate -> RuleStatus.STALEMATE
        board.isInsufficientMaterial -> RuleStatus.DRAW_INSUFFICIENT_MATERIAL
        board.halfMoveCounter >= 100 -> RuleStatus.DRAW_FIFTY_MOVE
        board.isRepetition(3) -> RuleStatus.DRAW_REPETITION
        board.isKingAttacked -> RuleStatus.CHECK
        else -> RuleStatus.ONGOING
    }

    fun isOver(): Boolean = ruleStatus().isOver

    fun toPgn(
        whiteName: String = "White",
        blackName: String = "Black",
        result: String = "*",
        event: String = "Cheshers casual game",
        date: Date = Date(),
    ): String {
        fun header(name: String, value: String) = "[$name \"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"]"
        val headers = buildList {
            add(header("Event", event))
            add(header("Site", "Cheshers for Android"))
            add(header("Date", SimpleDateFormat("yyyy.MM.dd", Locale.US).format(date)))
            add(header("Round", "-"))
            add(header("White", whiteName))
            add(header("Black", blackName))
            add(header("Result", result))
            if (initialPosition != START_FEN) {
                add(header("SetUp", "1"))
                add(header("FEN", initialPosition))
            }
        }
        val tokens = mutableListOf<String>()
        val fenParts = initialPosition.split(" ")
        var fullMove = fenParts.getOrNull(5)?.toIntOrNull() ?: 1
        var currentSide = fenParts.getOrNull(1) ?: "w"
        records.forEachIndexed { index, record ->
            if (currentSide == "w") {
                tokens += "$fullMove."
            } else if (index == 0) {
                tokens += "$fullMove..."
            }
            tokens += record.san
            if (currentSide == "b") fullMove++
            currentSide = if (currentSide == "w") "b" else "w"
        }
        tokens += result
        val moveText = tokens.joinToString(" ").foldLine(88)
        return headers.joinToString("\n") + "\n\n" + moveText + "\n"
    }

    private fun buildSan(move: Move, movingPiece: Piece, isEnPassant: Boolean): String {
        val from = move.from.toString().lowercase()
        val to = move.to.toString().lowercase()
        val target = board.getPiece(move.to)
        val capture = target != Piece.NONE || isEnPassant
        val type = movingPiece.pieceType.toModel()
        val base = if (type == PieceType.KING && kotlin.math.abs(from[0] - to[0]) == 2) {
            if (to[0] == 'g') "O-O" else "O-O-O"
        } else {
            buildString {
                if (type != PieceType.PAWN) {
                    append(type.san)
                    val alternatives = board.legalMoves().filter { other ->
                        other != move && other.to == move.to &&
                            board.getPiece(other.from).pieceType == movingPiece.pieceType
                    }
                    if (alternatives.isNotEmpty()) {
                        val sameFile = alternatives.any { it.from.toString()[1] == move.from.toString()[1] }
                        val sameRank = alternatives.any { it.from.toString()[2] == move.from.toString()[2] }
                        if (!sameFile) append(from[0])
                        else if (!sameRank) append(from[1])
                        else append(from)
                    }
                } else if (capture) append(from[0])
                if (capture) append('x')
                append(to)
                if (move.promotion != Piece.NONE) append('=').append(move.promotion.pieceType.toModel().san)
            }
        }
        val next = Board().apply { loadFromFen(board.fen); doMove(move) }
        return base + when {
            next.isMated -> "#"
            next.isKingAttacked -> "+"
            else -> ""
        }
    }
}

fun randomLegalMove(legalMoves: List<ChessMove>, random: kotlin.random.Random = kotlin.random.Random.Default): ChessMove? =
    if (legalMoves.isEmpty()) null else legalMoves[random.nextInt(legalMoves.size)]

private fun Move.toModel(): ChessMove = ChessMove.parse(toString())
    ?: throw IllegalStateException("Chesslib emitted an invalid UCI move")

private fun Piece.toModel(square: Square): BoardPiece = BoardPiece(
    square = square.toString().lowercase(),
    color = pieceSide.toModel(),
    type = pieceType.toModel(),
)

private fun Side.toModel(): ChessColor = if (this == Side.WHITE) ChessColor.WHITE else ChessColor.BLACK

private fun com.github.bhlangonijr.chesslib.PieceType.toModel(): PieceType = when (this) {
    com.github.bhlangonijr.chesslib.PieceType.PAWN -> PieceType.PAWN
    com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> PieceType.KNIGHT
    com.github.bhlangonijr.chesslib.PieceType.BISHOP -> PieceType.BISHOP
    com.github.bhlangonijr.chesslib.PieceType.ROOK -> PieceType.ROOK
    com.github.bhlangonijr.chesslib.PieceType.QUEEN -> PieceType.QUEEN
    com.github.bhlangonijr.chesslib.PieceType.KING -> PieceType.KING
    else -> throw IllegalArgumentException("Unsupported piece type: $this")
}

private fun String.foldLine(width: Int): String {
    val lines = mutableListOf<String>()
    var line = ""
    split(" ").forEach { token ->
        if (line.isNotEmpty() && line.length + token.length + 1 > width) {
            lines += line
            line = ""
        }
        line = if (line.isEmpty()) token else "$line $token"
    }
    if (line.isNotEmpty()) lines += line
    return lines.joinToString("\n")
}
