package app.cheshers.feature.game.puzzle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheshers.core.chess.ChessColor
import app.cheshers.feature.game.GameEvent
import app.cheshers.feature.game.GameSound
import app.cheshers.feature.game.board.ChessBoard
import app.cheshers.feature.game.board.rememberGameSoundPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    puzzleId: String? = null,
    daily: Boolean = false,
    onBack: () -> Unit,
    viewModel: PuzzleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val soundPlayer = rememberGameSoundPlayer()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(puzzleId, daily) { viewModel.start(puzzleId, daily) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GameEvent.PlaySound -> {
                    soundPlayer.play(event.sound, state.settings.sound)
                    if (state.settings.haptics && (event.sound == GameSound.END || event.sound == GameSound.CHECK)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is GameEvent.Toast -> snackbar.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.daily) "Daily challenge" else "Tactical puzzle") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val puzzle = state.puzzle
        if (!state.initialized || puzzle == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(puzzle.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(puzzle.theme, color = MaterialTheme.colorScheme.primary)
                }
                if (state.daily) Card {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.profile?.dailyPuzzleStreak ?: 0}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("day streak", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(puzzle.objective)
            ChessBoard(
                snapshot = state.snapshot,
                flipped = state.playerColor == ChessColor.BLACK,
                boardTheme = state.settings.boardTheme,
                pieceSet = state.settings.pieceSet,
                colorBlind = state.settings.colorBlind,
                showCoordinates = state.settings.showCoordinates,
                showLegalMoves = state.settings.showLegalMoves,
                autoPromote = state.settings.autoPromote,
                confirmMove = false,
                animationSpeed = state.settings.animationSpeed,
                enabled = state.status == PuzzleStatus.PLAYING && !state.thinking && state.snapshot.turn == state.playerColor,
                hintMove = state.hintMove,
                onMove = viewModel::playMove,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            if (state.thinking || state.hintThinking) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                Text(if (state.hintThinking) "OpenRouter is finding a hint…" else "Opponent replies…")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::requestHint, enabled = state.hintsUsed < 3 && !state.hintThinking && state.status == PuzzleStatus.PLAYING) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(4.dp)); Text("Hint (${3 - state.hintsUsed})")
                }
                OutlinedButton(onClick = viewModel::reset) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Reset") }
                Text("Attempts: ${state.attempts}", Modifier.align(Alignment.CenterVertically))
            }
            if (state.status == PuzzleStatus.SOLVED) Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Puzzle solved!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    state.progress?.let { Text("+${it.xpEarned} XP  •  +${it.coinsEarned} coins") }
                    Button(onClick = viewModel::next) { Text("Next puzzle") }
                }
            }
            Text(
                "Puzzle moves are checked against the curated solution. Hints are proposed by OpenRouter and must be legal in the current position.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
