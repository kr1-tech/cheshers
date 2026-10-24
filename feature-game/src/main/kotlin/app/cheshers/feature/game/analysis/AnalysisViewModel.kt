package app.cheshers.feature.game.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cheshers.core.ai.GameAnalysis
import app.cheshers.core.ai.OpenRouterAiEngine
import app.cheshers.core.ai.PositionAdvice
import app.cheshers.core.chess.*
import app.cheshers.core.data.repository.CheshersSettings
import app.cheshers.core.data.repository.GameRepository
import app.cheshers.core.data.repository.ProgressionRepository
import app.cheshers.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalysisUiState(
    val initialized: Boolean = false,
    val snapshot: PositionSnapshot = ChessGame().snapshot(),
    val history: List<MoveRecord> = emptyList(),
    val currentPly: Int = 0,
    val readOnly: Boolean = false,
    val gameId: String? = null,
    val advice: PositionAdvice? = null,
    val analysis: GameAnalysis? = null,
    val evaluating: Boolean = false,
    val reviewing: Boolean = false,
    val message: String? = null,
    val settings: CheshersSettings = CheshersSettings(),
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val ai: OpenRouterAiEngine,
    private val games: GameRepository,
    private val progression: ProgressionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalysisUiState())
    val state = _state.asStateFlow()
    private var game = ChessGame()
    private var initialFen = START_FEN
    private var allMoves: List<MoveRecord> = emptyList()
    private var whiteName = "White"
    private var blackName = "Black"
    private var result = "*"
    private var started = false
    private var evaluationJob: Job? = null
    private var reviewJob: Job? = null

    init {
        settingsRepository.settings.onEach { settings -> _state.update { it.copy(settings = settings) } }.launchIn(viewModelScope)
    }

    fun start(gameId: String?) {
        if (started) return
        started = true
        if (gameId == null) {
            game = ChessGame()
            _state.update { it.copy(initialized = true, snapshot = game.snapshot()) }
            scheduleEvaluation()
            return
        }
        viewModelScope.launch {
            val entity = games.gameById(gameId)
            val restored = entity?.let { games.restore(it) }
            if (entity == null || restored == null) {
                _state.update { it.copy(message = "Game could not be loaded") }
                return@launch
            }
            game = restored.game
            initialFen = entity.initialFen
            allMoves = game.history
            whiteName = entity.whiteName
            blackName = entity.blackName
            result = entity.result
            _state.update {
                it.copy(
                    initialized = true, snapshot = game.snapshot(), history = allMoves, currentPly = allMoves.size,
                    readOnly = true, gameId = gameId, analysis = games.decodeAnalysis(entity),
                )
            }
            if (_state.value.analysis == null && allMoves.isNotEmpty()) generateReview() else scheduleEvaluation()
        }
    }

    fun playMove(move: ChessMove) {
        if (_state.value.readOnly || game.isOver()) return
        runCatching { game.move(move) }.getOrElse {
            _state.update { it.copy(message = "Illegal move") }
            return
        }
        allMoves = game.history
        _state.update { it.copy(snapshot = game.snapshot(), history = allMoves, currentPly = allMoves.size, analysis = null) }
        scheduleEvaluation()
    }

    fun undo() {
        if (_state.value.readOnly) seek(_state.value.currentPly - 1) else {
            game.undo() ?: return
            allMoves = game.history
            _state.update { it.copy(snapshot = game.snapshot(), history = allMoves, currentPly = allMoves.size, analysis = null) }
            scheduleEvaluation()
        }
    }

    fun seek(ply: Int) {
        val target = ply.coerceIn(0, allMoves.size)
        val rebuilt = ChessGame(initialFen)
        allMoves.take(target).forEach { rebuilt.move(it.uci) }
        game = rebuilt
        _state.update { it.copy(snapshot = game.snapshot(), currentPly = target, advice = null) }
        scheduleEvaluation()
    }

    fun reset() {
        if (_state.value.readOnly) return
        game = ChessGame()
        initialFen = START_FEN
        allMoves = emptyList()
        _state.update { it.copy(snapshot = game.snapshot(), history = emptyList(), currentPly = 0, analysis = null, advice = null) }
        scheduleEvaluation()
    }

    fun generateReview() {
        if (allMoves.isEmpty() || _state.value.reviewing) {
            if (allMoves.isEmpty()) _state.update { it.copy(message = "Play some moves before requesting a review") }
            return
        }
        reviewJob?.cancel()
        reviewJob = viewModelScope.launch {
            _state.update { it.copy(reviewing = true) }
            val settings = settingsRepository.settings.first()
            val pgn = exportPgn()
            val analysisResult = ai.analyzeGame(settings.openRouterApiKey, settings.openRouterModel, pgn, allMoves.size)
            analysisResult.onSuccess { analysis ->
                _state.update { it.copy(analysis = analysis, reviewing = false) }
                _state.value.gameId?.let { id -> runCatching { games.saveAnalysis(id, analysis) } }
                runCatching { progression.recordAccuracy(analysis.accuracy) }
            }.onFailure {
                _state.update { it.copy(reviewing = false, message = "OpenRouter review is unavailable. Check your key and connection, then retry.") }
            }
        }.also { job -> job.invokeOnCompletion { _state.update { it.copy(reviewing = false) } } }
    }

    private fun scheduleEvaluation() {
        evaluationJob?.cancel()
        evaluationJob = viewModelScope.launch {
            delay(650)
            if (game.legalMoves().isEmpty()) return@launch
            _state.update { it.copy(evaluating = true) }
            val settings = settingsRepository.settings.first()
            val result = ai.evaluatePosition(settings.openRouterApiKey, settings.openRouterModel, game.fen, game.legalMoves())
            result.onSuccess { advice -> _state.update { it.copy(advice = advice, evaluating = false) } }
                .onFailure { _state.update { it.copy(advice = null, evaluating = false) } }
        }.also { job -> job.invokeOnCompletion { _state.update { it.copy(evaluating = false) } } }
    }

    fun exportPgn(): String {
        val export = ChessGame(initialFen)
        allMoves.forEach { export.move(it.uci) }
        return export.toPgn(whiteName, blackName, result, event = "Cheshers analysis")
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
