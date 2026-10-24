package app.cheshers.feature.game.puzzle

import app.cheshers.core.chess.ChessGame
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleCatalogTest {
    @Test fun everyBundledSolutionIsLegal() {
        PuzzleCatalog.puzzles.forEach { puzzle ->
            val game = ChessGame(puzzle.fen)
            puzzle.solution.forEach { uci ->
                assertTrue("${puzzle.id}: $uci should be legal", game.legalMoves().any { it.uci == uci })
                game.move(uci)
            }
        }
    }

    @Test fun matePuzzlesActuallyEndInCheckmate() {
        PuzzleCatalog.puzzles.filter { it.theme.startsWith("Mate") }.forEach { puzzle ->
            val game = ChessGame(puzzle.fen)
            puzzle.solution.forEach(game::move)
            assertTrue("${puzzle.id} should be mate", game.ruleStatus() == app.cheshers.core.chess.RuleStatus.CHECKMATE)
        }
    }
}
