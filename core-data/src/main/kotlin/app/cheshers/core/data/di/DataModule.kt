package app.cheshers.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import app.cheshers.core.ai.AiCache
import app.cheshers.core.ai.OpenRouterAiEngine
import app.cheshers.core.ai.OpenRouterService
import app.cheshers.core.ai.RetrofitOpenRouterClient
import app.cheshers.core.ai.TextAiClient
import app.cheshers.core.data.local.CheshersDatabase
import app.cheshers.core.data.repository.RoomAiCache
import com.google.gson.Gson
import kotlinx.serialization.json.Json
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Provides @Singleton fun gson(): Gson = Gson()

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): CheshersDatabase = Room.databaseBuilder(
        context, CheshersDatabase::class.java, "cheshers.db",
    ).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()

    @Provides @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides @Singleton
    fun openRouterService(client: OkHttpClient): OpenRouterService = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/v1/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenRouterService::class.java)

    @Provides @Singleton
    fun textAiClient(service: OpenRouterService): TextAiClient = RetrofitOpenRouterClient(service)

    @Provides @Singleton
    fun aiCache(database: CheshersDatabase): AiCache = RoomAiCache(database.aiCache())

    @Provides @Singleton
    fun aiEngine(client: TextAiClient, cache: AiCache): OpenRouterAiEngine = OpenRouterAiEngine(client, cache)
}
