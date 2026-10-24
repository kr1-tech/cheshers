package app.cheshers.feature.profile

import app.cheshers.core.chess.RatingCategory
import app.cheshers.core.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

data class LeaderboardEntry(
    val username: String = "",
    val rating: Int = 1200,
    val gamesPlayed: Int = 0,
    val country: String = "",
)
data class LeaderboardResponse(val entries: List<LeaderboardEntry> = emptyList())

interface LeaderboardApi {
    @GET("leaderboard")
    suspend fun leaderboard(
        @Header("Authorization") authorization: String?,
        @Query("scope") scope: String,
        @Query("category") category: String,
    ): LeaderboardResponse
}

@Singleton
class LeaderboardRepository @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) {
    suspend fun load(scope: String, category: RatingCategory): Result<List<LeaderboardEntry>> = try {
        val config = settings.settings.first()
        val base = config.backendUrl.trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://10.0.2.2") || base.startsWith("http://localhost")) { "Use an HTTPS backend URL" }
        val api = Retrofit.Builder().baseUrl("$base/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(LeaderboardApi::class.java)
        val token = settings.session.value?.token
        Result.success(api.leaderboard(token?.let { "Bearer $it" }, scope, category.name.lowercase()).entries)
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        Result.failure(IllegalStateException("Leaderboard is unavailable"))
    }
}
