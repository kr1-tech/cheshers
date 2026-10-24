package app.cheshers.feature.quests

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import app.cheshers.core.data.repository.ProgressionRepository
import app.cheshers.core.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class QuestResetWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val progression: ProgressionRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        progression.ensureQuests()
        if (settings.settings.first().notifications && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val notification = NotificationCompat.Builder(context, "quests")
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("Cheshers quests refreshed")
                .setContentText("New daily challenges are ready.")
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(2001, notification)
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }

    companion object {
        fun schedule(context: Context) {
            val now = System.currentTimeMillis()
            val nextUtcMidnight = (Math.floorDiv(now, 86_400_000L) + 1) * 86_400_000L + 300_000L
            val request = PeriodicWorkRequestBuilder<QuestResetWorker>(1, TimeUnit.DAYS)
                .setInitialDelay((nextUtcMidnight - now).coerceAtLeast(60_000L), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("quest-reset", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
