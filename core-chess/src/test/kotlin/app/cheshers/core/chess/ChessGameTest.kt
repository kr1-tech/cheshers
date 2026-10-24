package app.cheshers.core.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessGameTest {
    @Test fun initialPositionHasTwentyLegalMoves() {
        val game = ChessGame()
        assertEquals(20, game.legalMoves().size)
        assertEquals(32, game.snapshot().pieces.size)
        assertEquals(ChessColor.WHITE, game.turn)
    }

    @Test fun foolsMateIsDetectedAndExportedAsSan() {
        val game = ChessGame()
        game.move("f2f3"); game.move("e7e5"); game.move("g2g4")
        val mate = game.move("d8h4")
        assertEquals("Qh4#", mate.san)
        assertEquals(RuleStatus.CHECKMATE, game.ruleStatus())
        assertTrue(game.isOver())
        assertTrue(game.toPgn(result = "0-1").contains("1. f3 e5 2. g4 Qh4# 0-1"))
    }

    @Test fun castlingMovesBothPiecesAndUndoRestoresPosition() {
        val game = ChessGame("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val before = game.fen
        val move = game.move("e1g1")
        assertEquals("O-O", move.san)
        assertEquals(PieceType.ROOK, game.pieceAt("f1")?.type)
        assertEquals(PieceType.KING, game.pieceAt("g1")?.type)
        game.undo()
        assertEquals(before, game.fen)
        assertNull(game.pieceAt("f1"))
    }

    @Test fun enPassantIsLegalAndCapturedPawnIsRemoved() {
        val game = ChessGame()
        game.move("e2e4"); game.move("a7a6"); game.move("e4e5"); game.move("d7d5")
        val capture = game.move("e5d6")
        assertEquals("exd6", capture.san)
        assertEquals(PieceType.PAWN, capture.captured)
        assertNull(game.pieceAt("d5"))
        assertEquals(PieceType.PAWN, game.pieceAt("d6")?.type)
    }

    @Test fun promotionRequiresTheExactLegalPromotionPiece() {
        val game = ChessGame("8/P7/8/8/8/8/7k/4K3 w - - 0 1")
        assertTrue(game.isLegal(ChessMove.parse("a7a8q")!!))
        assertFalse(game.isLegal(ChessMove.parse("a7a8")!!))
        assertEquals("a8=Q", game.move("a7a8q").san)
        assertEquals(PieceType.QUEEN, game.pieceAt("a8")?.type)
    }

    @Test fun stalemateAndInsufficientMaterialAreDraws() {
        assertEquals(RuleStatus.STALEMATE, ChessGame("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").ruleStatus())
        assertEquals(RuleStatus.DRAW_INSUFFICIENT_MATERIAL, ChessGame("8/8/8/8/8/8/7k/4K3 w - - 0 1").ruleStatus())
    }

    @Test fun invalidMoveNeverChangesThePosition() {
        val game = ChessGame()
        val before = game.fen
        runCatching { game.move("e2e5") }
        assertEquals(before, game.fen)
        assertTrue(game.history.isEmpty())
    }

    @Test fun pgnEscapesNamesAndIncludesCustomFen() {
        val game = ChessGame("8/8/8/8/8/8/7k/4K3 w - - 0 1")
        val pgn = game.toPgn(whiteName = "A \"Player\"", result = "1/2-1/2")
        assertTrue(pgn.contains("[SetUp \"1\"]"))
        assertTrue(pgn.contains("A \\\"Player\\\""))
    }

    @Test fun legalMoveFallbackReturnsNullOnlyWhenNoMoveExists() {
        assertNotNull(randomLegalMove(ChessGame().legalMoves()))
        assertNull(randomLegalMove(emptyList()))
    }
}
