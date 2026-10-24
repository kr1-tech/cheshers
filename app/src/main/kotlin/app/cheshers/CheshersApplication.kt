package app.cheshers

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.cheshers.feature.quests.QuestResetWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CheshersApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("quests", getString(R.string.notification_channel_quests), NotificationManager.IMPORTANCE_DEFAULT))
            manager.createNotificationChannel(NotificationChannel("games", getString(R.string.notification_channel_games), NotificationManager.IMPORTANCE_DEFAULT))
        }
        QuestResetWorker.schedule(this)
    }
}
