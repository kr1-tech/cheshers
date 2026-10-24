package app.cheshers.feature.online

import app.cheshers.core.chess.*
import app.cheshers.core.data.repository.*
import app.cheshers.feature.online.protocol.*
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

interface OnlineAuthApi {
    @POST("auth/guest") suspend fun guest(): AuthResponse
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse
}

@Singleton
class OnlineRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val games: GameRepository,
    private val progression: ProgressionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private val persistMutex = Mutex()
    private val completed = mutableSetOf<String>()

    val connection = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val game = MutableStateFlow<OnlineGameState?>(null)
    val queue = MutableStateFlow<QueueWaiting?>(null)
    val challenge = MutableStateFlow<ChallengeCreated?>(null)
    val localCompletedGameId = MutableStateFlow<String?>(null)
    val mutedUsers = MutableStateFlow<Set<String>>(emptySet())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    private val _invites = MutableSharedFlow<ChallengeInvite>(extraBufferCapacity = 4)
    val invites = _invites.asSharedFlow()

    suspend fun signInGuest(): Result<OnlineUser> = authenticate { guest() }
    suspend fun register(username: String, email: String, password: String): Result<OnlineUser> = authenticate { register(RegisterRequest(username.trim(), email.trim(), password)) }
    suspend fun login(identity: String, password: String): Result<OnlineUser> = authenticate { login(LoginRequest(identity.trim(), password)) }

    private suspend fun authenticate(call: suspend OnlineAuthApi.() -> AuthResponse): Result<OnlineUser> = try {
        val response = api().call()
        if (response.token.isBlank() || response.user.id.isBlank()) throw IllegalStateException("Invalid authentication response")
        settings.saveOnlineSession(OnlineSession(response.token, response.user.id, response.user.username, response.user.guest))
        Result.success(response.user)
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        Result.failure(IllegalStateException("Authentication failed. Check the backend and your credentials."))
    }

    private suspend fun api(): OnlineAuthApi {
        val base = settings.settings.first().backendUrl.trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://10.0.2.2") || base.startsWith("http://localhost")) { "Use an HTTPS backend URL" }
        return Retrofit.Builder().baseUrl("$base/").client(httpClient).addConverterFactory(GsonConverterFactory.create()).build().create(OnlineAuthApi::class.java)
    }

    fun connect() {
        if (socket?.connected() == true || connection.value == ConnectionStatus.CONNECTING) return
        val session = settings.session.value
        if (session == null) { _messages.tryEmit("Sign in or continue as a guest first"); return }
        scope.launch {
            val url = runCatching { settings.settings.first().backendUrl.trimEnd('/') }.getOrNull().orEmpty()
            if (!(url.startsWith("https://") || url.startsWith("http://10.0.2.2") || url.startsWith("http://localhost"))) {
                connection.value = ConnectionStatus.ERROR
                _messages.tryEmit("Configure a valid HTTPS backend URL in Settings")
                return@launch
            }
            runCatching {
                connection.value = ConnectionStatus.CONNECTING
                val options = IO.Options().apply {
                    auth = mapOf("token" to session.token)
                    transports = arrayOf(WebSocket.NAME)
                    callFactory = httpClient
                    webSocketFactory = httpClient
                    reconnection = true
                    reconnectionAttempts = Int.MAX_VALUE
                    reconnectionDelay = 1_000
                    reconnectionDelayMax = 10_000
                    timeout = 20_000
                }
                val created = IO.socket(url, options)
                socket = created
                registerSocketEvents(created)
                created.connect()
            }.onFailure {
                connection.value = ConnectionStatus.ERROR
                _messages.tryEmit("Could not connect to the Cheshers backend")
            }
        }
    }

    private fun registerSocketEvents(socket: Socket) {
        socket.on(Socket.EVENT_CONNECT) {
            connection.value = ConnectionStatus.CONNECTED
            game.value?.let { current -> resume(current.id) }
        }
        socket.on(Socket.EVENT_DISCONNECT) { if (connection.value != ConnectionStatus.ERROR) connection.value = ConnectionStatus.RECONNECTING }
        socket.on(Socket.EVENT_CONNECT_ERROR) { connection.value = ConnectionStatus.ERROR }
        socket.on("game:available") { args -> (args.firstOrNull() as? JSONObject)?.optString("gameId")?.let(::resume) }
        socket.on("game:started") { args ->
            queue.value = null; challenge.value = null; localCompletedGameId.value = null
            parse(args, OnlineGameState::class.java)?.let(::receiveGame)
        }
        socket.on("game:state") { args -> parse(args, OnlineGameState::class.java)?.let(::receiveGame) }
        socket.on("game:over") { args -> parse(args, OnlineGameState::class.java)?.let(::receiveGame) }
        socket.on("game:chat") { args ->
            parse(args, ChatMessage::class.java)?.let { message ->
                game.update { current ->
                    if (current == null || current.chat.any { chat -> chat.id == message.id }) current
                    else current.copy(chat = current.chat + message)
                }
            }
        }
        socket.on("queue:waiting") { args -> queue.value = parse(args, QueueWaiting::class.java) }
        socket.on("queue:left") { queue.value = null }
        socket.on("challenge:created") { args -> challenge.value = parse(args, ChallengeCreated::class.java) }
        socket.on("challenge:invite") { args -> parse(args, ChallengeInvite::class.java)?.let(_invites::tryEmit) }
        socket.on("error:message") { args -> _messages.tryEmit((args.firstOrNull() as? JSONObject)?.optString("message") ?: "Online action failed") }
        socket.on("report:received") { _messages.tryEmit("Report received. Thank you.") }
    }

    private fun receiveGame(state: OnlineGameState) {
        if (state.id.isBlank() || state.fen.isBlank()) return
        game.value = state
        scope.launch { persistGame(state) }
    }

    fun joinQueue(timeControl: String) = emit("queue:join", JSONObject().put("timeControl", timeControl))
    fun leaveQueue() { emit("queue:leave"); queue.value = null }
    fun createChallenge(timeControl: String, username: String) = emit("queue:leave").also {
        emit("challenge:create", JSONObject().put("timeControl", timeControl).put("username", username.trim()))
    }
    fun joinChallenge(code: String) = emit("challenge:join", JSONObject().put("code", code.trim().uppercase()))
    fun resume(gameId: String) = emit("game:resume", JSONObject().put("gameId", gameId))
    fun move(uci: String) = game.value?.let { emit("game:move", JSONObject().put("gameId", it.id).put("uci", uci)) }
    fun resign() = game.value?.let { emit("game:resign", JSONObject().put("gameId", it.id)) }
    fun offerDraw() = game.value?.let { emit("game:draw:offer", JSONObject().put("gameId", it.id)) }
    fun respondDraw(accepted: Boolean) = game.value?.let { emit("game:draw:respond", JSONObject().put("gameId", it.id).put("accepted", accepted)) }
    fun sendChat(text: String) {
        val clean = ProfanityFilter.clean(text)
        if (clean.isNotBlank()) game.value?.let { emit("game:chat", JSONObject().put("gameId", it.id).put("text", clean)) }
    }
    fun report(messageId: String) = game.value?.let { emit("game:report", JSONObject().put("gameId", it.id).put("messageId", messageId).put("reason", "In-game chat")) }
    fun toggleMute(userId: String) = mutedUsers.update { if (userId in it) it - userId else it + userId }
    fun clearGame() { game.value = null; localCompletedGameId.value = null; challenge.value = null }

    private fun emit(event: String, payload: Any? = null) {
        val active = socket
        if (active == null || !active.connected()) {
            _messages.tryEmit("Reconnect to the backend and try again")
            connect()
            return
        }
        if (payload == null) active.emit(event) else active.emit(event, payload)
    }

    private suspend fun persistGame(state: OnlineGameState) = persistMutex.withLock {
        val session = settings.session.value ?: return@withLock
        val playerColor = when (session.userId) {
            state.white.id -> ChessColor.WHITE
            state.black.id -> ChessColor.BLACK
            else -> null
        }
        val chess = ChessGame()
        runCatching { state.moves.forEach { chess.move(it.uci) } }.getOrElse { return@withLock }
        val config = ActiveGameConfig(
            id = "online-${state.id}", mode = GameMode.ONLINE, playerColor = playerColor,
            timeControl = TimeControl.fromId(state.timeControl), whiteName = state.white.username,
            blackName = state.black.username, hintsRemaining = 0,
        )
        val whiteMs = if (state.whiteMs < 0) Long.MAX_VALUE else state.whiteMs
        val blackMs = if (state.blackMs < 0) Long.MAX_VALUE else state.blackMs
        if (state.result == "*") {
            runCatching { games.saveActive(config, chess, whiteMs, blackMs) }
            return@withLock
        }
        if (!completed.add(state.id)) return@withLock
        runCatching { games.saveActive(config, chess, whiteMs, blackMs) }
        val winnerColor = when (state.winner) { "w" -> ChessColor.WHITE; "b" -> ChessColor.BLACK; else -> null }
        val outcome = when {
            winnerColor == null -> GameOutcome.DRAW
            playerColor != null && winnerColor == playerColor -> GameOutcome.WIN
            else -> GameOutcome.LOSS
        }
        if (playerColor != null) {
            val last = chess.history.lastOrNull()
            runCatching {
                progression.recordGame(
                    outcome, true, TimeControl.fromId(state.timeControl).category, rated = false,
                    checkmate = state.reason == "Checkmate" && outcome == GameOutcome.WIN,
                    queenCheckmate = state.reason == "Checkmate" && outcome == GameOutcome.WIN && last?.piece == PieceType.QUEEN && last.mate,
                )
            }
            state.ratingChanges[session.userId]?.let { change ->
                val category = runCatching { RatingCategory.valueOf(change.category.uppercase()) }.getOrNull()
                if (category != null) runCatching { progression.setOnlineRating(category, change.rating) }
            }
        }
        val local = runCatching {
            games.completeActive(config, chess, state.result, state.reason, state.ratingChanges[session.userId]?.delta ?: 0)
        }.getOrNull()
        if (local != null) localCompletedGameId.value = local.id
    }

    fun signOut() {
        socket?.disconnect(); socket?.off(); socket = null
        connection.value = ConnectionStatus.DISCONNECTED
        game.value = null; queue.value = null; challenge.value = null
        settings.clearOnlineSession()
    }

    private fun <T> parse(args: Array<Any>, type: Class<T>): T? =
        args.firstOrNull()?.let { runCatching { gson.fromJson(it.toString(), type) }.getOrNull() }
}

object ProfanityFilter {
    private val blocked = listOf("fuck", "shit", "bitch", "cunt", "nigger", "faggot")
    fun clean(input: String): String {
        var text = input.replace(Regex("[\\u0000-\\u001f\\u007f]"), " ").replace(Regex("\\s+"), " ").trim().take(200)
        blocked.forEach { word -> text = text.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), "*".repeat(word.length)) }
        return text
    }
}
