package app.cheshers.feature.online.protocol

data class OnlineUser(
    val id: String = "",
    val username: String = "",
    val guest: Boolean = true,
    val country: String = "",
    val ratings: Map<String, Int> = emptyMap(),
    val gamesPlayed: Int = 0,
)
data class AuthResponse(val token: String = "", val user: OnlineUser = OnlineUser())
data class RegisterRequest(val username: String, val email: String, val password: String)
data class LoginRequest(val username: String, val password: String)
data class OnlineMove(val uci: String = "", val san: String = "")
data class ChatMessage(val id: String = "", val userId: String = "", val username: String = "", val text: String = "", val at: Long = 0)
data class RatingChange(val delta: Int = 0, val rating: Int = 1200, val category: String = "blitz")
data class OnlineGameState(
    val id: String = "",
    val fen: String = "",
    val turn: String = "WHITE",
    val white: OnlineUser = OnlineUser(),
    val black: OnlineUser = OnlineUser(),
    val timeControl: String = "5+0",
    val whiteMs: Long = -1,
    val blackMs: Long = -1,
    val serverAt: Long = 0,
    val moves: List<OnlineMove> = emptyList(),
    val lastMove: String? = null,
    val result: String = "*",
    val reason: String = "",
    val winner: String? = null,
    val drawOfferBy: String? = null,
    val chat: List<ChatMessage> = emptyList(),
    val ratingChanges: Map<String, RatingChange> = emptyMap(),
)
data class QueueWaiting(val position: Int = 0, val timeControl: String = "5+0")
data class ChallengeCreated(val code: String = "", val timeControl: String = "5+0")
data class ChallengeInvite(val code: String = "", val from: OnlineUser = OnlineUser(), val timeControl: String = "5+0")

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }
