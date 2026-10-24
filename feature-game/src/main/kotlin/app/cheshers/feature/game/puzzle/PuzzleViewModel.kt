package app.cheshers.feature.game.puzzle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cheshers.core.ai.MoveProposalRequest
import app.cheshers.core.ai.MovePurpose
import app.cheshers.core.ai.OpenRouterAiEngine
import app.cheshers.core.chess.*
import app.cheshers.core.data.repository.CheshersSettings
import app.cheshers.core.data.repository.ProgressUpdate
import app.cheshers.core.data.repository.ProgressionRepository
import app.cheshers.core.data.repository.SettingsRepository
import app.cheshers.core.data.local.entity.ProfileEntity
import app.cheshers.feature.game.GameEvent
import app.cheshers.feature.game.GameSound
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PuzzleStatus { PLAYING, SOLVED }

data class PuzzleUiState(
    val initialized: Boolean = false,
    val puzzle: Puzzle? = null,
    val snapshot: PositionSnapshot = ChessGame().snapshot(),
    val playerColor: ChessColor = ChessColor.WHITE,
    val status: PuzzleStatus = PuzzleStatus.PLAYING,
    val daily: Boolean = false,
    val attempts: Int = 0,
    val hintsUsed: Int = 0,
    val hintMove: ChessMove? = null,
    val thinking: Boolean = false,
    val hintThinking: Boolean = false,
    val message: String? = null,
    val progress: ProgressUpdate? = null,
    val profile: ProfileEntity? = null,
    val settings: CheshersSettings = CheshersSettings(),
)

@HiltViewModel
class PuzzleViewModel @Inject constructor(
    private val ai: OpenRouterAiEngine,
    private val settingsRepository: SettingsRepository,
    private val progression: ProgressionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PuzzleUiState())
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()
    private var game = ChessGame()
    private var solutionIndex = 0
    private var hintJob: Job? = null
    private var replyJob: Job? = null

    init {
        settingsRepository.settings.onEach { settings -> _state.update { it.copy(settings = settings) } }.launchIn(viewModelScope)
        progression.observeProfile().onEach { profile -> _state.update { it.copy(profile = profile) } }.launchIn(viewModelScope)
        viewModelScope.launch { runCatching { progression.ensureInitialized() } }
    }

    fun start(puzzleId: String?, daily: Boolean) {
        val puzzle = if (daily) PuzzleCatalog.daily(System.currentTimeMillis()) else puzzleId?.let(PuzzleCatalog::byId) ?: PuzzleCatalog.puzzles.first()
        if (_state.value.initialized && _state.value.puzzle?.id == puzzle.id && _state.value.daily == daily) return
        load(puzzle, daily)
    }

    private fun load(puzzle: Puzzle, daily: Boolean, keepAttempts: Boolean = false) {
        hintJob?.cancel(); replyJob?.cancel()
        game = ChessGame(puzzle.fen)
        solutionIndex = 0
        _state.update {
            it.copy(
                initialized = true, puzzle = puzzle, snapshot = game.snapshot(), playerColor = game.turn,
                status = PuzzleStatus.PLAYING, daily = daily, attempts = if (keepAttempts) it.attempts else 0,
                hintsUsed = 0, hintMove = null, thinking = false, hintThinking = false, progress = null, message = null,
            )
        }
    }

    fun playMove(move: ChessMove) {
        val puzzle = _state.value.puzzle ?: return
        if (_state.value.status != PuzzleStatus.PLAYING || _state.value.thinking || game.turn != _state.value.playerColor) return
        val expected = puzzle.solution.getOrNull(solutionIndex) ?: return
        if (!move.uci.equals(expected, true)) {
            _state.update { it.copy(attempts = it.attempts + 1, hintMove = null, message = "Not the strongest continuation — try again") }
            return
        }
        val record = runCatching { game.move(expected) }.getOrNull() ?: return
        solutionIndex++
        _events.tryEmit(GameEvent.PlaySound(if (record.check) GameSound.CHECK else if (record.captured != null) GameSound.CAPTURE else GameSound.MOVE))
        _state.update { it.copy(snapshot = game.snapshot(), hintMove = null) }
        if (solutionIndex >= puzzle.solution.size) solved() else reply(puzzle)
    }

    private fun reply(puzzle: Puzzle) {
        _state.update { it.copy(thinking = true) }
        replyJob = viewModelScope.launch {
            delay(400)
            val uci = puzzle.solution.getOrNull(solutionIndex) ?: return@launch
            val record = runCatching { game.move(uci) }.getOrNull() ?: return@launch
            solutionIndex++
            _events.tryEmit(GameEvent.PlaySound(if (record.check) GameSound.CHECK else GameSound.MOVE))
            _state.update { it.copy(snapshot = game.snapshot(), thinking = false) }
            if (solutionIndex >= puzzle.solution.size) solved()
        }.also { job -> job.invokeOnCompletion { _state.update { it.copy(thinking = false) } } }
    }

    private fun solved() {
        val puzzle = _state.value.puzzle ?: return
        _state.update { it.copy(status = PuzzleStatus.SOLVED, thinking = false) }
        _events.tryEmit(GameEvent.PlaySound(GameSound.END))
        viewModelScope.launch {
            val dailyTag = if (_state.value.daily) "-${Math.floorDiv(System.currentTimeMillis(), 86_400_000L)}" else ""
            val progress = runCatching {
                progression.recordPuzzle(puzzle.id + dailyTag, _state.value.attempts + 1, _state.value.hintsUsed, _state.value.daily)
            }.getOrNull()
            _state.update { it.copy(progress = progress) }
        }
    }

    fun requestHint() {
        val puzzle = _state.value.puzzle ?: return
        if (_state.value.hintThinking || _state.value.hintsUsed >= 3 || _state.value.status == PuzzleStatus.SOLVED) return
        val fen = game.fen
        _state.update { it.copy(hintsUsed = it.hintsUsed + 1, hintThinking = true, hintMove = null) }
        hintJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val result = ai.proposeMove(MoveProposalRequest(
                fen, game.legalMoves(), Difficulty.EXPERT, model = settings.openRouterModel,
                apiKey = settings.openRouterApiKey, purpose = MovePurpose.HINT,
                puzzleObjective = "${puzzle.theme}: ${puzzle.objective}",
            ))
            if (game.fen != fen) return@launch
            _state.update { it.copy(hintThinking = false, hintMove = result.move, message = if (result.move == null) "AI is thinking… please retry" else null) }
        }.also { job -> job.invokeOnCompletion { _state.update { it.copy(hintThinking = false) } } }
    }

    fun reset() = _state.value.puzzle?.let { load(it, _state.value.daily, keepAttempts = true) } ?: Unit
    fun next() = _state.value.puzzle?.let { load(PuzzleCatalog.next(it.id), false) } ?: Unit
    fun clearMessage() = _state.update { it.copy(message = null) }
}
