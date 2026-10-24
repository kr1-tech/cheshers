package app.cheshers.feature.online

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheshers.core.chess.ChessColor
import app.cheshers.feature.game.board.ChessBoard
import app.cheshers.feature.game.formatClock
import app.cheshers.feature.online.protocol.ChatMessage
import app.cheshers.feature.online.protocol.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineGameScreen(
    gameId: String,
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    viewModel: OnlineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var resignConfirmation by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    LaunchedEffect(gameId) { viewModel.start(gameId) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    val game = state.game
    val snapshot = state.snapshot

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Online · ${game?.timeControl ?: "Connecting"}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = viewModel::toggleFlip) { Text("Flip") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (game == null || snapshot == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(); Text("Connecting and restoring game…", Modifier.padding(16.dp)); TextButton(onClick = { viewModel.start(gameId) }) { Text("Retry") }
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.connection != ConnectionStatus.CONNECTED) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(10.dp)) { Text("${state.connection.name.lowercase()}…", Modifier.weight(1f)); TextButton(onClick = { viewModel.start(gameId) }) { Text("Reconnect") } }
            }
            OnlinePlayerBar(if (state.flipped) game.white else game.black, if (state.flipped) state.whiteMs else state.blackMs, state.session?.userId, game.turn == if (state.flipped) "WHITE" else "BLACK")
            ChessBoard(
                snapshot = snapshot,
                flipped = state.flipped,
                boardTheme = state.settings.boardTheme,
                pieceSet = state.settings.pieceSet,
                colorBlind = state.settings.colorBlind,
                showCoordinates = state.settings.showCoordinates,
                showLegalMoves = state.settings.showLegalMoves,
                autoPromote = state.settings.autoPromote,
                confirmMove = state.settings.confirmMove,
                animationSpeed = state.settings.animationSpeed,
                enabled = state.connection == ConnectionStatus.CONNECTED && game.result == "*" && snapshot.turn == state.playerColor,
                hintMove = null,
                onMove = viewModel::playMove,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            OnlinePlayerBar(if (state.flipped) game.black else game.white, if (state.flipped) state.blackMs else state.whiteMs, state.session?.userId, game.turn == if (state.flipped) "BLACK" else "WHITE")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(game.moves.size) { index -> Text("${if (index % 2 == 0) "${index / 2 + 1}." else ""} ${game.moves[index].san}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = viewModel::offerDraw, enabled = game.result == "*") { Text("½ Draw") }
                OutlinedButton(onClick = { resignConfirmation = true }, enabled = game.result == "*") { Icon(Icons.Default.Close, null); Text("Resign") }
            }
            ChatPanel(
                game.chat, state.session?.userId, state.mutedUsers,
                chatInput, { chatInput = it }, { viewModel.sendChat(chatInput); chatInput = "" }, viewModel::toggleMute, viewModel::report,
            )
        }
    }
    if (resignConfirmation) AlertDialog(
        onDismissRequest = { resignConfirmation = false }, title = { Text("Resign online game?") },
        confirmButton = { TextButton(onClick = { resignConfirmation = false; viewModel.resign() }) { Text("Resign") } },
        dismissButton = { TextButton(onClick = { resignConfirmation = false }) { Text("Keep playing") } },
    )
    val incomingDraw = game?.drawOfferBy?.takeIf { it != state.session?.userId }
    if (incomingDraw != null && game.result == "*") AlertDialog(
        onDismissRequest = { viewModel.respondDraw(false) }, title = { Text("Draw offer") }, text = { Text("Your opponent offers a draw.") },
        confirmButton = { TextButton(onClick = { viewModel.respondDraw(true) }) { Text("Accept") } },
        dismissButton = { TextButton(onClick = { viewModel.respondDraw(false) }) { Text("Decline") } },
    )
    if (game != null && game.result != "*") AlertDialog(
        onDismissRequest = {},
        title = { Text("Game over · ${game.result}") },
        text = {
            Column {
                Text(game.reason)
                state.session?.userId?.let { userId -> game.ratingChanges[userId]?.let { change -> Text("Rating ${if (change.delta >= 0) "+" else ""}${change.delta} · new ${change.rating}") } }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.clearGame(); onBack() }) { Text("Online lobby") } },
        dismissButton = { state.localCompletedGameId?.let { id -> TextButton(onClick = { onReview(id) }) { Text("Review") } } },
    )
}

@Composable
private fun OnlinePlayerBar(player: app.cheshers.feature.online.protocol.OnlineUser, clock: Long, sessionId: String?, active: Boolean) {
    Surface(color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (player.id == sessionId) "${player.username} (You)" else player.username, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(formatClock(clock), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChatPanel(
    messages: List<ChatMessage>, sessionId: String?, muted: Set<String>, input: String, onInput: (String) -> Unit,
    onSend: () -> Unit, onMute: (String) -> Unit, onReport: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("In-game chat", fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxWidth().height(170.dp)) {
                items(messages, key = { it.id }) { message ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(message.username, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(if (message.userId in muted) "Message hidden" else message.text)
                        }
                        if (message.userId != sessionId) {
                            TextButton(onClick = { onMute(message.userId) }) { Text(if (message.userId in muted) "Unmute" else "Mute") }
                            TextButton(onClick = { onReport(message.id) }) { Text("Report") }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, onInput, modifier = Modifier.weight(1f), placeholder = { Text("Message") }, singleLine = true)
                IconButton(onClick = onSend, enabled = input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
            Text("Chat is filtered and rate limited. Mute is local; reports are sent to the server.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
