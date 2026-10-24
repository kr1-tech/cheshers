package app.cheshers.feature.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheshers.core.chess.*
import app.cheshers.feature.game.board.ChessBoard
import app.cheshers.feature.game.board.rememberGameSoundPlayer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    activeGameId: String? = null,
    mode: GameMode = GameMode.BOT,
    difficulty: Difficulty = Difficulty.INTERMEDIATE,
    sideChoice: SideChoice = SideChoice.WHITE,
    timeControl: TimeControl = TimeControl.UNLIMITED,
    personality: Personality = Personality.BALANCED,
    onBack: () -> Unit,
    onNewGame: () -> Unit,
    onReview: (String) -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val soundPlayer = rememberGameSoundPlayer()
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resignConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(activeGameId) {
        if (activeGameId != null) viewModel.resume(activeGameId)
        else viewModel.startNew(mode, difficulty, sideChoice, timeControl, personality)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.persist()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GameEvent.PlaySound -> {
                    soundPlayer.play(event.sound, state.settings.sound)
                    if (state.settings.haptics) haptics.performHapticFeedback(
                        if (event.sound == GameSound.END || event.sound == GameSound.CHECK) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                    )
                }
                is GameEvent.Toast -> snackbar.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.mode == GameMode.PASS_AND_PLAY) "Pass & play" else "Cheshers") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = viewModel::toggleFlip) { Icon(Icons.Default.Refresh, "Flip board"); Spacer(Modifier.width(4.dp)); Text("Flip") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.initialized) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val landscape = maxWidth > maxHeight && maxWidth > 600.dp
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        PlayerBar(state, topColor(state.flipped), Modifier.fillMaxWidth())
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            GameBoard(state, viewModel::playMove, Modifier.fillMaxHeight().aspectRatio(1f))
                        }
                        PlayerBar(state, bottomColor(state.flipped), Modifier.fillMaxWidth())
                    }
                    GameSidePanel(state, viewModel, onResign = { resignConfirmation = true }, modifier = Modifier.width(310.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                    PlayerBar(state, topColor(state.flipped), Modifier.fillMaxWidth())
                    GameBoard(state, viewModel::playMove, Modifier.fillMaxWidth().aspectRatio(1f))
                    PlayerBar(state, bottomColor(state.flipped), Modifier.fillMaxWidth())
                    GameSidePanel(state, viewModel, onResign = { resignConfirmation = true }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (resignConfirmation) AlertDialog(
        onDismissRequest = { resignConfirmation = false },
        title = { Text("Resign this game?") },
        text = { Text("This will end the game as a loss for the side to move.") },
        confirmButton = { TextButton(onClick = { resignConfirmation = false; viewModel.resign() }) { Text("Resign") } },
        dismissButton = { TextButton(onClick = { resignConfirmation = false }) { Text("Keep playing") } },
    )
    if (state.drawOfferPending) AlertDialog(
        onDismissRequest = { viewModel.respondToDraw(false) },
        title = { Text("Draw offer") },
        text = { Text("${state.snapshot.turn.name.lowercase().replaceFirstChar { it.uppercase() }}, do you accept a draw?") },
        confirmButton = { TextButton(onClick = { viewModel.respondToDraw(true) }) { Text("Accept") } },
        dismissButton = { TextButton(onClick = { viewModel.respondToDraw(false) }) { Text("Decline") } },
    )
    state.gameOver?.let { over ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(over.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(over.reason, style = MaterialTheme.typography.titleMedium)
                    over.progress?.let { progress ->
                        Text("+${progress.xpEarned} XP  •  +${progress.coinsEarned} coins")
                        if (progress.ratingDelta != 0) Text("Rating ${if (progress.ratingDelta > 0) "+" else ""}${progress.ratingDelta}")
                        if (progress.leveledUp) Text("Level ${progress.newLevel} reached!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (progress.completedQuests.isNotEmpty()) Text("Quest complete: ${progress.completedQuests.joinToString()}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = onNewGame) { Text("New game") } },
            dismissButton = {
                Row {
                    over.completedGameId?.let { id -> TextButton(onClick = { onReview(id) }) { Text("Review") } }
                    TextButton(onClick = onBack) { Text("Home") }
                }
            },
        )
    }
}

@Composable
private fun GameBoard(state: GameUiState, onMove: (ChessMove) -> Unit, modifier: Modifier) {
    ChessBoard(
        snapshot = state.snapshot,
        flipped = state.flipped,
        boardTheme = state.settings.boardTheme,
        pieceSet = state.settings.pieceSet,
        colorBlind = state.settings.colorBlind,
        showCoordinates = state.settings.showCoordinates,
        showLegalMoves = state.settings.showLegalMoves,
        autoPromote = state.settings.autoPromote,
        confirmMove = state.settings.confirmMove,
        animationSpeed = state.settings.animationSpeed,
        enabled = !state.thinking && state.gameOver == null &&
            (state.mode != GameMode.BOT || state.snapshot.turn == state.playerColor),
        hintMove = state.hintMove,
        onMove = onMove,
        modifier = modifier,
    )
}

@Composable
private fun GameSidePanel(state: GameUiState, viewModel: GameViewModel, onResign: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.thinking || state.hintThinking) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(if (state.hintThinking) "Finding a hint…" else "OpenRouter is thinking…")
        }
        if (state.aiReasoning.isNotBlank()) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Bot notes", style = MaterialTheme.typography.labelLarge)
                Text(state.aiReasoning, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = viewModel::requestHint,
                enabled = state.hintsRemaining > 0 && !state.thinking && !state.hintThinking && state.gameOver == null,
            ) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(3.dp)); Text("Hint (${state.hintsRemaining})") }
            OutlinedButton(onClick = viewModel::undo, enabled = state.history.isNotEmpty() && !state.thinking && state.gameOver == null) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(3.dp)); Text("Undo")
            }
            OutlinedButton(onClick = viewModel::offerDraw, enabled = !state.thinking && state.gameOver == null) { Text("½ Draw") }
            OutlinedButton(onClick = onResign, enabled = state.gameOver == null) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(3.dp)); Text("Resign") }
        }
        if (state.history.isNotEmpty()) {
            Text("Moves", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.history.size) { index ->
                    val move = state.history[index]
                    Text(
                        "${if (index % 2 == 0) "${index / 2 + 1}. " else ""}${move.san}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (index == state.history.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Text(
            "Moves are proposed by OpenRouter and validated locally. If the network fails, bot play uses random legal moves.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerBar(state: GameUiState, color: ChessColor, modifier: Modifier = Modifier) {
    val isPlayer = state.mode == GameMode.BOT && state.playerColor == color
    val name = when {
        state.mode == GameMode.PASS_AND_PLAY -> if (color == ChessColor.WHITE) "White" else "Black"
        isPlayer -> "You"
        else -> "Cheshers AI · ${state.difficulty.displayName}"
    }
    val clock = if (color == ChessColor.WHITE) state.whiteMs else state.blackMs
    Surface(
        modifier = modifier.padding(vertical = 4.dp),
        color = if (state.snapshot.turn == color && state.gameOver == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (color == ChessColor.WHITE) "♔" else "♚", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(8.dp))
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(formatClock(clock), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
    }
}

private fun topColor(flipped: Boolean) = if (flipped) ChessColor.WHITE else ChessColor.BLACK
private fun bottomColor(flipped: Boolean) = if (flipped) ChessColor.BLACK else ChessColor.WHITE

fun formatClock(milliseconds: Long): String {
    if (milliseconds == Long.MAX_VALUE) return "∞"
    val total = milliseconds.coerceAtLeast(0)
    val minutes = total / 60_000
    val seconds = (total % 60_000) / 1_000
    return if (total < 10_000) String.format(Locale.US, "%d.%01d", seconds, (total % 1_000) / 100)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}
