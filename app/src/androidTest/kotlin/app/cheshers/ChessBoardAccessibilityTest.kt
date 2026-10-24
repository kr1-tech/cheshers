package app.cheshers

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cheshers.core.chess.ChessGame
import app.cheshers.core.chess.ChessMove
import app.cheshers.core.chess.PositionSnapshot
import app.cheshers.core.data.repository.BoardTheme
import app.cheshers.core.data.repository.PieceSet
import app.cheshers.feature.game.board.ChessBoard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChessBoardAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun semanticSquareActionsSubmitLegalMove() {
        var played: ChessMove? = null
        compose.setContent { TestBoard(ChessGame().snapshot(), onMove = { played = it }) }
        compose.onNodeWithContentDescription("e2, white pawn").performClick()
        compose.onNodeWithContentDescription("e4, empty, legal destination").performClick()
        compose.runOnIdle { assertEquals("e2e4", played?.uci) }
    }

    @Test
    fun confirmationRequiresExplicitApproval() {
        var played: ChessMove? = null
        compose.setContent { TestBoard(ChessGame().snapshot(), confirm = true, onMove = { played = it }) }
        compose.onNodeWithContentDescription("e2, white pawn").performClick()
        compose.onNodeWithContentDescription("e4, empty, legal destination").performClick()
        compose.onNodeWithText("Confirm move").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, played) }
        compose.onNodeWithText("Play move").performClick()
        compose.runOnIdle { assertEquals("e2e4", played?.uci) }
    }

    @Test
    fun promotionCanChooseKnight() {
        var played: ChessMove? = null
        val snapshot = ChessGame("7k/P7/8/8/8/8/8/7K w - - 0 1").snapshot()
        compose.setContent { TestBoard(snapshot, onMove = { played = it }) }
        compose.onNodeWithContentDescription("a7, white pawn").performClick()
        compose.onNodeWithContentDescription("a8, empty, legal destination").performClick()
        compose.onNodeWithText("Choose promotion").assertIsDisplayed()
        compose.onNodeWithText("Knight").performClick()
        compose.runOnIdle { assertEquals("a7a8n", played?.uci) }
    }

    @Composable
    private fun TestBoard(snapshot: PositionSnapshot, confirm: Boolean = false, onMove: (ChessMove) -> Unit) {
        MaterialTheme {
            ChessBoard(
                snapshot = snapshot, flipped = false, boardTheme = BoardTheme.WOOD, pieceSet = PieceSet.CLASSIC,
                colorBlind = false, showCoordinates = true, showLegalMoves = true, autoPromote = false,
                confirmMove = confirm, animationSpeed = 2f, enabled = true, hintMove = null, onMove = onMove,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        }
    }
}
