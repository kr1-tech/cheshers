package app.cheshers.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cheshers.core.ai.AiSource
import app.cheshers.core.ai.MoveProposalRequest
import app.cheshers.core.ai.MovePurpose
import app.cheshers.core.ai.OpenRouterAiEngine
import app.cheshers.core.chess.*
import app.cheshers.core.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class GameViewModel @Inject constructor(
    private val aiEngine: OpenRouterAiEngine,
    private val games: GameRepository,
    private val progression: ProgressionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var game = ChessGame()
    private var config = ActiveGameConfig("active-bot", GameMode.BOT)
    private var started = false
    private var ending = false
    private var baseWhiteMs = Long.MAX_VALUE
    private var baseBlackMs = Long.MAX_VALUE
    private var turnStartedAt = System.currentTimeMillis()
    private var clockJob: Job? = null
    private var botJob: Job? = null
    private var hintJob: Job? = null
    private val moveMutex = Mutex()
    private val persistMutex = Mutex()

    init {
        settingsRepository.settings.onEach { settings ->
            _state.update { it.copy(settings = settings) }
        }.launchIn(viewModelScope)
        viewModelScope.launch { runCatching { progression.ensureInitialized() } }
    }

    fun startNew(
        mode: GameMode,
        difficulty: Difficulty,
        sideChoice: SideChoice,
        timeControl: TimeControl,
        personality: Personality,
    ) {
        if (started) return
        started = true
        val playerColor = when (sideChoice) {
            SideChoice.WHITE -> ChessColor.WHITE
            SideChoice.BLACK -> ChessColor.BLACK
            SideChoice.RANDOM -> if (Random.Default.nextBoolean()) ChessColor.WHITE else ChessColor.BLACK
        }
        game = ChessGame()
        config = ActiveGameConfig(
            id = "active-${mode.name.lowercase()}",
            mode = mode,
            playerColor = playerColor,
            difficulty = difficulty,
            personality = personality,
            timeControl = timeControl,
            whiteName = if (mode == GameMode.BOT && playerColor == ChessColor.BLACK) "Cheshers AI" else if (mode == GameMode.BOT) "You" else "White",
            blackName = if (mode == GameMode.BOT && playerColor == ChessColor.WHITE) "Cheshers AI" else if (mode == GameMode.BOT) "You" else "Black",
        )
        baseWhiteMs = timeControl.initialMs
        baseBlackMs = timeControl.initialMs
        turnStartedAt = System.currentTimeMillis()
        ending = false
        _state.update {
            it.copy(
                initialized = true,
                snapshot = game.snapshot(),
                history = emptyList(),
                mode = mode,
                playerColor = playerColor,
                difficulty = difficulty,
                personality = personality,
                timeControl = timeControl,
                whiteMs = baseWhiteMs,
                blackMs = baseBlackMs,
                hintsRemaining = config.hintsRemaining,
                flipped = playerColor == ChessColor.BLACK,
                gameOver = null,
                aiReasoning = "",
                hintMove = null,
            )
        }
        startClock()
        persist()
        maybeRequestBotMove()
    }

    fun resume(activeId: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            val restored = games.restoreActive(activeId)
            if (restored == null) {
                _state.update { it.copy(message = "The saved game could not be restored") }
                return@launch
            }
            game = restored.game
            config = restored.config
            baseWhiteMs = restored.whiteMs
            baseBlackMs = restored.blackMs
            turnStartedAt = System.currentTimeMillis()
            ending = false
            _state.update {
                it.copy(
                    initialized = true,
                    snapshot = game.snapshot(),
                    history = game.history,
                    mode = config.mode,
                    playerColor = config.playerColor ?: ChessColor.WHITE,
                    difficulty = config.difficulty ?: Difficulty.INTERMEDIATE,
                    personality = config.personality,
                    timeControl = config.timeControl,
                    whiteMs = baseWhiteMs,
                    blackMs = baseBlackMs,
                    hintsRemaining = config.hintsRemaining,
                    flipped = if (config.mode == GameMode.PASS_AND_PLAY) game.turn == ChessColor.BLACK else config.playerColor == ChessColor.BLACK,
                )
            }
            if (game.ruleStatus().isOver) handleRuleEnd() else {
                startClock()
                maybeRequestBotMove()
            }
        }
    }

    fun playMove(move: ChessMove) {
        if (!started || ending || _state.value.thinking) return
        if (config.mode == GameMode.BOT && game.turn != config.playerColor) return
        viewModelScope.launch {
            moveMutex.withLock { applyMove(move) }
        }
    }

    private suspend fun applyMove(move: ChessMove) {
        if (ending || game.isOver()) return
        val now = System.currentTimeMillis()
        val movingColor = game.turn
        val remaining = remainingFor(movingColor, now)
        if (remaining <= 0) {
            completeFlag(movingColor)
            return
        }
        val record = try {
            game.move(move)
        } catch (_: IllegalChessMoveException) {
            showMessage("That move is not legal in this position")
            return
        }
        if (config.timeControl.isTimed) {
            val withIncrement = remaining + config.timeControl.incrementMs
            if (movingColor == ChessColor.WHITE) baseWhiteMs = withIncrement else baseBlackMs = withIncrement
        }
        turnStartedAt = now
        _state.update {
            it.copy(
                snapshot = game.snapshot(),
                history = game.history,
                whiteMs = baseWhiteMs,
                blackMs = baseBlackMs,
                hintMove = null,
                thinking = false,
                flipped = if (config.mode == GameMode.PASS_AND_PLAY && it.settings.flipPassAndPlay) game.turn == ChessColor.BLACK else it.flipped,
            )
        }
        emitMoveSound(record)
        saveNow()
        if (game.ruleStatus().isOver) handleRuleEnd() else maybeRequestBotMove()
    }

    fun requestHint() {
        val current = _state.value
        if (!started || ending || current.hintThinking || current.hintsRemaining <= 0 || current.snapshot.legalMoves.isEmpty()) return
        if (config.mode == GameMode.BOT && game.turn != config.playerColor) return
        hintJob?.cancel()
        val fen = game.fen
        val legal = game.legalMoves()
        config = config.copy(hintsRemaining = config.hintsRemaining - 1)
        _state.update { it.copy(hintsRemaining = config.hintsRemaining, hintThinking = true, hintMove = null) }
        persist()
        hintJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val result = aiEngine.proposeMove(
                MoveProposalRequest(
                    fen = fen,
                    legalMoves = legal,
                    difficulty = Difficulty.EXPERT,
                    personality = Personality.BALANCED,
                    model = settings.openRouterModel,
                    apiKey = settings.openRouterApiKey,
                    purpose = MovePurpose.HINT,
                )
            )
            if (game.fen != fen || ending) return@launch
            _state.update {
                it.copy(
                    hintThinking = false,
                    hintMove = result.move,
                    message = if (result.move == null) "AI is thinking… please retry" else null,
                )
            }
        }.also { job -> job.invokeOnCompletion { _state.update { it.copy(hintThinking = false) } } }
    }

    fun undo() {
        if (ending || config.mode == GameMode.ONLINE || game.history.isEmpty()) return
        botJob?.cancel()
        hintJob?.cancel()
        viewModelScope.launch {
            moveMutex.withLock {
                game.undo()
                if (config.mode == GameMode.BOT && game.turn != config.playerColor && game.history.isNotEmpty()) game.undo()
                turnStartedAt = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        snapshot = game.snapshot(), history = game.history, thinking = false, hintThinking = false,
                        hintMove = null, aiReasoning = "",
                        flipped = if (config.mode == GameMode.PASS_AND_PLAY && it.settings.flipPassAndPlay) game.turn == ChessColor.BLACK else it.flipped,
                    )
                }
                saveNow()
            }
        }
    }

    fun resign() {
        if (ending || !started) return
        viewModelScope.launch {
            moveMutex.withLock {
                val losingColor = if (config.mode == GameMode.BOT) config.playerColor ?: game.turn else game.turn
                completeWithWinner(losingColor.opposite(), "Resignation")
            }
        }
    }

    fun offerDraw() {
        if (ending || !started) return
        if (config.mode == GameMode.PASS_AND_PLAY) {
            _state.update { it.copy(drawOfferPending = true) }
            return
        }
        if (config.mode != GameMode.BOT || _state.value.thinking) return
        viewModelScope.launch {
            _state.update { it.copy(thinking = true) }
            val settings = settingsRepository.settings.first()
            val decision = aiEngine.respondToDraw(settings.openRouterApiKey, settings.openRouterModel, game.fen, game.legalMoves())
            _state.update { it.copy(thinking = false, message = decision.message) }
            if (decision.accepted) moveMutex.withLock { completeDraw("Draw by agreement") }
        }
    }

    fun respondToDraw(accepted: Boolean) {
        _state.update { it.copy(drawOfferPending = false) }
        if (accepted) viewModelScope.launch { moveMutex.withLock { completeDraw("Draw by agreement") } }
        else showMessage("Draw offer declined")
    }

    fun toggleFlip() = _state.update { it.copy(flipped = !it.flipped) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun persist() {
        if (!started || ending) return
        viewModelScope.launch { saveNow() }
    }

    private suspend fun saveNow() {
        if (!started || ending) return
        persistMutex.withLock {
            if (ending) return@withLock
            runCatching { games.saveActive(config, game, baseWhiteMs, baseBlackMs) }
        }
    }

    private fun maybeRequestBotMove() {
        if (config.mode != GameMode.BOT || ending || game.isOver() || game.turn == config.playerColor || botJob?.isActive == true) return
        val fen = game.fen
        val legal = game.legalMoves()
        botJob = viewModelScope.launch {
            _state.update { it.copy(thinking = true, hintMove = null) }
            val settings = settingsRepository.settings.first()
            val result = aiEngine.proposeMove(
                MoveProposalRequest(
                    fen = fen,
                    legalMoves = legal,
                    difficulty = config.difficulty ?: Difficulty.INTERMEDIATE,
                    personality = config.personality,
                    model = settings.openRouterModel,
                    apiKey = settings.openRouterApiKey,
                )
            )
            if (ending || game.fen != fen) return@launch
            _state.update {
                it.copy(
                    thinking = false,
                    aiReasoning = result.reasoning,
                    message = if (result.isFallback) "AI unavailable — continuing with random legal moves" else null,
                )
            }
            val move = result.move
            if (move != null) moveMutex.withLock { applyMove(move) }
            else showMessage("AI is thinking… please retry")
        }.also { job ->
            job.invokeOnCompletion { cause ->
                if (cause != null) _state.update { it.copy(thinking = false) }
            }
        }
    }

    private fun startClock() {
        clockJob?.cancel()
        if (!config.timeControl.isTimed) return
        clockJob = viewModelScope.launch {
            while (!ending) {
                delay(200)
                val now = System.currentTimeMillis()
                val white = remainingFor(ChessColor.WHITE, now)
                val black = remainingFor(ChessColor.BLACK, now)
                _state.update { it.copy(whiteMs = white, blackMs = black) }
                if ((game.turn == ChessColor.WHITE && white <= 0) || (game.turn == ChessColor.BLACK && black <= 0)) {
                    moveMutex.withLock { if (!ending) completeFlag(game.turn) }
                    break
                }
            }
        }
    }

    private fun remainingFor(color: ChessColor, now: Long): Long {
        val base = if (color == ChessColor.WHITE) baseWhiteMs else baseBlackMs
        if (!config.timeControl.isTimed || base == Long.MAX_VALUE) return Long.MAX_VALUE
        if (game.turn != color || ending) return base
        return (base - (now - turnStartedAt)).coerceAtLeast(0)
    }

    private suspend fun handleRuleEnd() {
        when (game.ruleStatus()) {
            RuleStatus.CHECKMATE -> completeWithWinner(game.turn.opposite(), "Checkmate")
            RuleStatus.STALEMATE -> completeDraw("Stalemate")
            RuleStatus.DRAW_INSUFFICIENT_MATERIAL -> completeDraw("Insufficient material")
            RuleStatus.DRAW_REPETITION -> completeDraw("Threefold repetition")
            RuleStatus.DRAW_FIFTY_MOVE -> completeDraw("Fifty-move rule")
            else -> Unit
        }
    }

    private suspend fun completeFlag(losingColor: ChessColor) = completeWithWinner(losingColor.opposite(), "Time forfeit")

    private suspend fun completeWithWinner(winner: ChessColor, reason: String) {
        val result = if (winner == ChessColor.WHITE) "1-0" else "0-1"
        val title = when (config.mode) {
            GameMode.BOT -> if (winner == config.playerColor) "You won" else "You lost"
            else -> "${if (winner == ChessColor.WHITE) "White" else "Black"} wins"
        }
        completeGame(result, title, reason, winner)
    }

    private suspend fun completeDraw(reason: String) = completeGame("1/2-1/2", "Draw", reason, null)

    private suspend fun completeGame(result: String, title: String, reason: String, winner: ChessColor?) {
        if (ending) return
        ending = true
        hintJob?.cancel()
        _state.update { it.copy(thinking = false, hintThinking = false) }
        val playerWon = winner != null && winner == config.playerColor
        val outcome = when {
            winner == null -> GameOutcome.DRAW
            config.mode == GameMode.BOT && playerWon -> GameOutcome.WIN
            config.mode == GameMode.BOT -> GameOutcome.LOSS
            else -> GameOutcome.DRAW
        }
        val last = game.history.lastOrNull()
        val progress = if (config.mode == GameMode.BOT) runCatching {
            progression.recordGame(
                outcome = outcome,
                online = false,
                category = config.timeControl.category,
                opponentRating = ratingFor(config.difficulty ?: Difficulty.INTERMEDIATE),
                rated = config.timeControl.category != RatingCategory.UNLIMITED,
                checkmate = reason == "Checkmate" && playerWon,
                queenCheckmate = reason == "Checkmate" && playerWon && last?.mate == true && last.piece == PieceType.QUEEN,
            )
        }.getOrNull() else null
        val completed = persistMutex.withLock {
            runCatching { games.completeActive(config, game, result, reason, progress?.ratingDelta ?: 0) }.getOrNull()
        }
        _state.update {
            it.copy(
                whiteMs = baseWhiteMs,
                blackMs = baseBlackMs,
                gameOver = GameOverState(result, title, reason, completed?.id, progress),
            )
        }
        _events.emit(GameEvent.PlaySound(GameSound.END))
    }

    private fun emitMoveSound(record: MoveRecord) {
        val isCastle = record.piece == PieceType.KING && kotlin.math.abs(record.uci[0] - record.uci[2]) == 2
        val sound = when {
            record.check -> GameSound.CHECK
            isCastle -> GameSound.CASTLE
            record.captured != null -> GameSound.CAPTURE
            else -> GameSound.MOVE
        }
        _events.tryEmit(GameEvent.PlaySound(sound))
    }

    private fun showMessage(message: String) = _state.update { it.copy(message = message) }

    private fun ratingFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.BEGINNER -> 500
        Difficulty.EASY -> 800
        Difficulty.INTERMEDIATE -> 1200
        Difficulty.ADVANCED -> 1600
        Difficulty.EXPERT -> 2000
        Difficulty.MASTER -> 2400
    }
}
