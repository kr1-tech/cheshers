package app.cheshers.feature.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cheshers.core.chess.*
import app.cheshers.core.data.repository.CheshersSettings
import app.cheshers.core.data.repository.OnlineSession
import app.cheshers.core.data.repository.SettingsRepository
import app.cheshers.feature.online.protocol.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnlineUiState(
    val session: OnlineSession? = null,
    val connection: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val game: OnlineGameState? = null,
    val snapshot: PositionSnapshot? = null,
    val playerColor: ChessColor? = null,
    val queue: QueueWaiting? = null,
    val challenge: ChallengeCreated? = null,
    val invite: ChallengeInvite? = null,
    val settings: CheshersSettings = CheshersSettings(),
    val mutedUsers: Set<String> = emptySet(),
    val whiteMs: Long = Long.MAX_VALUE,
    val blackMs: Long = Long.MAX_VALUE,
    val flipped: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val localCompletedGameId: String? = null,
)

@HiltViewModel
class OnlineViewModel @Inject constructor(
    private val repository: OnlineRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnlineUiState())
    val state = _state.asStateFlow()
    private var clockSyncedAt = System.currentTimeMillis()
    private var clockJob: Job? = null
    private var lastClockKey = ""

    init {
        settingsRepository.session.onEach { session -> _state.update { it.copy(session = session) } }.launchIn(viewModelScope)
        settingsRepository.settings.onEach { settings -> _state.update { it.copy(settings = settings) } }.launchIn(viewModelScope)
        repository.connection.onEach { value -> _state.update { it.copy(connection = value) } }.launchIn(viewModelScope)
        repository.queue.onEach { value -> _state.update { it.copy(queue = value) } }.launchIn(viewModelScope)
        repository.challenge.onEach { value -> _state.update { it.copy(challenge = value) } }.launchIn(viewModelScope)
        repository.localCompletedGameId.onEach { value -> _state.update { it.copy(localCompletedGameId = value) } }.launchIn(viewModelScope)
        repository.mutedUsers.onEach { value -> _state.update { it.copy(mutedUsers = value) } }.launchIn(viewModelScope)
        repository.messages.onEach { message -> _state.update { it.copy(message = message) } }.launchIn(viewModelScope)
        repository.invites.onEach { invite -> _state.update { it.copy(invite = invite) } }.launchIn(viewModelScope)
        repository.game.onEach(::receiveGame).launchIn(viewModelScope)
    }

    fun start(gameId: String? = null) {
        repository.connect()
        if (gameId != null) repository.resume(gameId)
    }

    private fun receiveGame(game: OnlineGameState?) {
        val previous = _state.value.game
        val session = _state.value.session
        val playerColor = if (game == null) null else when (session?.userId) {
            game.white.id -> ChessColor.WHITE
            game.black.id -> ChessColor.BLACK
            else -> null
        }
        val snapshot = game?.let { remote ->
            runCatching { ChessGame(remote.fen).snapshot().copy(lastMove = remote.lastMove?.let(ChessMove::parse)) }.getOrNull()
        }
        if (game != null) {
            val clockKey = "${game.id}:${game.fen}:${game.serverAt}"
            if (clockKey != lastClockKey) {
                lastClockKey = clockKey
                clockSyncedAt = System.currentTimeMillis()
            }
        }
        _state.update {
            it.copy(
                game = game,
                snapshot = snapshot,
                playerColor = playerColor,
                whiteMs = game?.whiteMs?.toClock() ?: Long.MAX_VALUE,
                blackMs = game?.blackMs?.toClock() ?: Long.MAX_VALUE,
                flipped = if (previous?.id != game?.id) playerColor == ChessColor.BLACK else it.flipped,
            )
        }
        if (game != null && game.result == "*") startClock() else clockJob?.cancel()
    }

    private fun startClock() {
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            while (true) {
                delay(200)
                val game = _state.value.game ?: break
                if (game.result != "*") break
                _state.update { it.copy(whiteMs = remaining(ChessColor.WHITE), blackMs = remaining(ChessColor.BLACK)) }
            }
        }
    }

    private fun remaining(color: ChessColor): Long {
        val game = _state.value.game ?: return Long.MAX_VALUE
        val base = (if (color == ChessColor.WHITE) game.whiteMs else game.blackMs).toClock()
        if (base == Long.MAX_VALUE || game.turn != color.name || game.result != "*") return base
        return (base - (System.currentTimeMillis() - clockSyncedAt)).coerceAtLeast(0)
    }

    fun playMove(move: ChessMove) {
        val current = _state.value
        if (current.connection != ConnectionStatus.CONNECTED || current.game?.result != "*" || current.snapshot?.turn != current.playerColor) return
        repository.move(move.uci)
    }
    fun toggleFlip() = _state.update { it.copy(flipped = !it.flipped) }
    fun joinQueue(timeControl: String) = repository.joinQueue(timeControl)
    fun leaveQueue() = repository.leaveQueue()
    fun createChallenge(timeControl: String, username: String) = repository.createChallenge(timeControl, username)
    fun joinChallenge(code: String) = repository.joinChallenge(code)
    fun acceptInvite() { _state.value.invite?.let { repository.joinChallenge(it.code) }; _state.update { it.copy(invite = null) } }
    fun dismissInvite() = _state.update { it.copy(invite = null) }
    fun resign() = repository.resign()
    fun offerDraw() = repository.offerDraw()
    fun respondDraw(accepted: Boolean) = repository.respondDraw(accepted)
    fun sendChat(text: String) = repository.sendChat(text)
    fun report(messageId: String) = repository.report(messageId)
    fun toggleMute(userId: String) = repository.toggleMute(userId)
    fun clearGame() = repository.clearGame()

    fun guest() = authenticate { repository.signInGuest() }
    fun login(identity: String, password: String) = authenticate { repository.login(identity, password) }
    fun register(username: String, email: String, password: String) = authenticate { repository.register(username, email, password) }
    private fun authenticate(call: suspend () -> Result<OnlineUser>) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            call().onSuccess { repository.connect() }.onFailure { error -> _state.update { it.copy(message = error.message) } }
            _state.update { it.copy(busy = false) }
        }
    }
    fun signOut() = repository.signOut()
    fun clearMessage() = _state.update { it.copy(message = null) }
}

private fun Long.toClock(): Long = if (this < 0) Long.MAX_VALUE else this
