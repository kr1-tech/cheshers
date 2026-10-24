package app.cheshers.feature.game.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cheshers.feature.game.board.ChessBoard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    gameId: String? = null,
    onBack: () -> Unit,
    onSharePgn: (String) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var flipped by remember { mutableStateOf(false) }
    LaunchedEffect(gameId) { viewModel.start(gameId) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.readOnly) "Game review" else "Analysis board") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { flipped = !flipped }) { Text("Flip") }
                    IconButton(onClick = { onSharePgn(viewModel.exportPgn()) }) { Icon(Icons.Default.Share, "Export PGN") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChessBoard(
                snapshot = state.snapshot,
                flipped = flipped,
                boardTheme = state.settings.boardTheme,
                pieceSet = state.settings.pieceSet,
                colorBlind = state.settings.colorBlind,
                showCoordinates = state.settings.showCoordinates,
                showLegalMoves = state.settings.showLegalMoves,
                autoPromote = state.settings.autoPromote,
                confirmMove = false,
                animationSpeed = state.settings.animationSpeed,
                enabled = state.initialized && !state.readOnly,
                hintMove = state.advice?.bestMove,
                onMove = viewModel::playMove,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            EvaluationCard(state)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { viewModel.seek(state.currentPly - 1) }, enabled = state.currentPly > 0) { Text("←") }
                OutlinedButton(onClick = { viewModel.seek(state.currentPly + 1) }, enabled = state.currentPly < state.history.size) { Text("→") }
                if (!state.readOnly) {
                    OutlinedButton(onClick = viewModel::undo, enabled = state.history.isNotEmpty()) { Text("Undo") }
                    OutlinedButton(onClick = viewModel::reset) { Text("Reset") }
                }
            }
            if (state.history.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                item { FilterChip(selected = state.currentPly == 0, onClick = { viewModel.seek(0) }, label = { Text("Start") }) }
                items(state.history.size) { index ->
                    FilterChip(
                        selected = state.currentPly == index + 1,
                        onClick = { viewModel.seek(index + 1) },
                        label = { Text("${if (index % 2 == 0) "${index / 2 + 1}." else ""} ${state.history[index].san}") },
                    )
                }
            }
            Button(onClick = viewModel::generateReview, enabled = state.history.isNotEmpty() && !state.reviewing, modifier = Modifier.fillMaxWidth()) {
                if (state.reviewing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (state.analysis == null) "Generate OpenRouter review" else "Regenerate review")
            }
            state.analysis?.let { analysis ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Coach review · ${"%.1f".format(analysis.accuracy)}% accuracy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (analysis.summary.isNotBlank()) Text(analysis.summary)
                        if (analysis.coach.isNotBlank()) Text(analysis.coach, color = MaterialTheme.colorScheme.primary)
                        val annotations = analysis.annotations.filter { it.ply == state.currentPly }
                        annotations.forEach { annotation ->
                            HorizontalDivider()
                            Text("${annotation.severity} · ply ${annotation.ply}", fontWeight = FontWeight.Bold)
                            Text(annotation.comment)
                            annotation.bestMove?.let { Text("Suggested: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (analysis.annotations.isNotEmpty() && annotations.isEmpty()) {
                            Text("${analysis.annotations.size} annotations. Select an annotated move to read it.", style = MaterialTheme.typography.bodySmall)
                            val blunders = analysis.annotations.count { it.severity == app.cheshers.core.ai.AnnotationSeverity.BLUNDER }
                            val mistakes = analysis.annotations.count { it.severity == app.cheshers.core.ai.AnnotationSeverity.MISTAKE }
                            Text("$blunders blunders · $mistakes mistakes")
                        }
                    }
                }
            }
            Text(
                "OpenRouter is a language-model provider, not a chess engine. Evaluations and reviews can be wrong; chesslib remains authoritative for every move.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EvaluationCard(state: AnalysisUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OpenRouter evaluation", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (state.evaluating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(state.advice?.let { "%+.2f".format(it.evaluation) } ?: "Unavailable")
            }
            val whiteFraction = ((state.advice?.evaluation ?: 0.0) + 1.0).toFloat() / 2f
            Row(Modifier.fillMaxWidth().height(12.dp)) {
                Box(Modifier.weight(whiteFraction.coerceAtLeast(0.001f)).fillMaxHeight().background(Color(0xFFF5F5F5)))
                Box(Modifier.weight((1f - whiteFraction).coerceAtLeast(0.001f)).fillMaxHeight().background(Color(0xFF202124)))
            }
            state.advice?.let {
                it.bestMove?.let { best -> Text("Suggested move: ${best.from} → ${best.to}", fontWeight = FontWeight.Medium) }
                if (it.explanation.isNotBlank()) Text(it.explanation, style = MaterialTheme.typography.bodySmall)
            } ?: Text("Add an OpenRouter key and connect to enable position advice.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
