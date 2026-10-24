package app.cheshers.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cheshers.core.ai.OpenRouterModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private val Context.preferencesStore by preferencesDataStore(name = "cheshers_settings")

enum class BoardTheme { LIGHT, DARK, WOOD, GREEN, BLUE }
enum class PieceSet { CLASSIC, NEO, ALPHA }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class CheshersSettings(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val boardTheme: BoardTheme = BoardTheme.GREEN,
    val pieceSet: PieceSet = PieceSet.CLASSIC,
    val animationSpeed: Float = 1f,
    val autoPromote: Boolean = false,
    val showCoordinates: Boolean = true,
    val confirmMove: Boolean = false,
    val colorBlind: Boolean = false,
    val showLegalMoves: Boolean = true,
    val flipPassAndPlay: Boolean = true,
    val notifications: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingComplete: Boolean = false,
    val openRouterModel: String = OpenRouterModel.DEFAULT_ID,
    val openRouterApiKey: String = "",
    val backendUrl: String = "",
    val secureStorageAvailable: Boolean = true,
)

data class OnlineSession(val token: String, val userId: String, val username: String, val guest: Boolean)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("defaultOpenRouterApi") private val defaultOpenRouterApi: String,
    @Named("defaultBackendUrl") private val defaultBackendUrl: String,
) {
    private val secureStore = SecureStore(context)
    private data class SecureValues(val apiKey: String, val session: OnlineSession?)

    private fun readSecureValues(): SecureValues = SecureValues(
        apiKey = secureStore.getString(KEY_API) ?: defaultOpenRouterApi,
        session = secureStore.getString(KEY_TOKEN)?.takeIf { it.isNotBlank() }?.let { token ->
            OnlineSession(
                token,
                secureStore.getString(KEY_USER_ID).orEmpty(),
                secureStore.getString(KEY_USERNAME).orEmpty(),
                secureStore.getString(KEY_GUEST) == "true",
            )
        },
    )

    private val secureValues = MutableStateFlow(readSecureValues())
    private val defaults = CheshersSettings(openRouterApiKey = defaultOpenRouterApi, backendUrl = defaultBackendUrl)

    val settings: Flow<CheshersSettings> = combine(context.preferencesStore.data, secureValues) { prefs, secure ->
        CheshersSettings(
            sound = prefs[SOUND] ?: true,
            haptics = prefs[HAPTICS] ?: true,
            boardTheme = prefs[BOARD_THEME]?.enumOrNull<BoardTheme>() ?: BoardTheme.GREEN,
            pieceSet = prefs[PIECE_SET]?.enumOrNull<PieceSet>() ?: PieceSet.CLASSIC,
            animationSpeed = (prefs[ANIMATION_SPEED] ?: 1f).coerceIn(0.5f, 2f),
            autoPromote = prefs[AUTO_PROMOTE] ?: false,
            showCoordinates = prefs[SHOW_COORDINATES] ?: true,
            confirmMove = prefs[CONFIRM_MOVE] ?: false,
            colorBlind = prefs[COLOR_BLIND] ?: false,
            showLegalMoves = prefs[SHOW_LEGAL] ?: true,
            flipPassAndPlay = prefs[FLIP_LOCAL] ?: true,
            notifications = prefs[NOTIFICATIONS] ?: true,
            themeMode = prefs[THEME_MODE]?.enumOrNull<ThemeMode>() ?: ThemeMode.SYSTEM,
            onboardingComplete = prefs[ONBOARDED] ?: false,
            openRouterModel = prefs[OPENROUTER_MODEL]?.takeIf { it.isNotBlank() } ?: OpenRouterModel.DEFAULT_ID,
            openRouterApiKey = secure.apiKey,
            backendUrl = prefs[BACKEND_URL] ?: defaults.backendUrl,
            secureStorageAvailable = secureStore.available,
        )
    }

    // Session updates are mirrored explicitly; no polling or token logging.
    private val sessionFlow = MutableStateFlow(secureValues.value.session)
    val session: StateFlow<OnlineSession?> = sessionFlow.asStateFlow()

    suspend fun save(value: CheshersSettings) {
        context.preferencesStore.edit { prefs ->
            prefs[SOUND] = value.sound
            prefs[HAPTICS] = value.haptics
            prefs[BOARD_THEME] = value.boardTheme.name
            prefs[PIECE_SET] = value.pieceSet.name
            prefs[ANIMATION_SPEED] = value.animationSpeed.coerceIn(0.5f, 2f)
            prefs[AUTO_PROMOTE] = value.autoPromote
            prefs[SHOW_COORDINATES] = value.showCoordinates
            prefs[CONFIRM_MOVE] = value.confirmMove
            prefs[COLOR_BLIND] = value.colorBlind
            prefs[SHOW_LEGAL] = value.showLegalMoves
            prefs[FLIP_LOCAL] = value.flipPassAndPlay
            prefs[NOTIFICATIONS] = value.notifications
            prefs[THEME_MODE] = value.themeMode.name
            prefs[ONBOARDED] = value.onboardingComplete
            prefs[OPENROUTER_MODEL] = value.openRouterModel.trim()
            prefs[BACKEND_URL] = value.backendUrl.trim().trimEnd('/')
        }
        secureStore.putString(KEY_API, value.openRouterApiKey.trim())
        secureValues.value = readSecureValues()
    }

    fun saveOnlineSession(session: OnlineSession) {
        secureStore.putString(KEY_TOKEN, session.token)
        secureStore.putString(KEY_USER_ID, session.userId)
        secureStore.putString(KEY_USERNAME, session.username)
        secureStore.putString(KEY_GUEST, session.guest.toString())
        secureValues.value = readSecureValues()
        sessionFlow.value = session
    }

    fun clearOnlineSession() {
        secureStore.remove(KEY_TOKEN)
        secureStore.remove(KEY_USER_ID)
        secureStore.remove(KEY_USERNAME)
        secureStore.remove(KEY_GUEST)
        secureValues.value = readSecureValues()
        sessionFlow.value = null
    }

    private companion object {
        val SOUND = booleanPreferencesKey("sound")
        val HAPTICS = booleanPreferencesKey("haptics")
        val BOARD_THEME = stringPreferencesKey("board_theme")
        val PIECE_SET = stringPreferencesKey("piece_set")
        val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
        val AUTO_PROMOTE = booleanPreferencesKey("auto_promote")
        val SHOW_COORDINATES = booleanPreferencesKey("show_coordinates")
        val CONFIRM_MOVE = booleanPreferencesKey("confirm_move")
        val COLOR_BLIND = booleanPreferencesKey("color_blind")
        val SHOW_LEGAL = booleanPreferencesKey("show_legal")
        val FLIP_LOCAL = booleanPreferencesKey("flip_local")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val OPENROUTER_MODEL = stringPreferencesKey("openrouter_model")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        const val KEY_API = "openrouter_api"
        const val KEY_TOKEN = "online_token"
        const val KEY_USER_ID = "online_user_id"
        const val KEY_USERNAME = "online_username"
        const val KEY_GUEST = "online_guest"
    }
}

private inline fun <reified T : Enum<T>> String.enumOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()

private class SecureStore(context: Context) {
    private val memory = mutableMapOf<String, String>()
    private val preferences: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "cheshers_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    val available: Boolean get() = preferences != null
    fun getString(key: String): String? = preferences?.getString(key, null) ?: memory[key]
    fun putString(key: String, value: String) {
        if (preferences != null) preferences.edit().putString(key, value).apply() else memory[key] = value
    }
    fun remove(key: String) {
        if (preferences != null) preferences.edit().remove(key).apply() else memory.remove(key)
    }
}
