package com.example.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.R
import com.example.data.database.LearnSyncDatabase
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReviewNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = LearnSyncDatabase.getDatabase(applicationContext)
        val prefs = db.userPreferencesDao().getPreferences().firstOrNull()

        // Check if notifications are enabled by user
        if (prefs != null && !prefs.notificationsEnabled) {
            return Result.success()
        }

        // Check Android 13+ runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }
        }

        // Check if there are due flashcards
        val dueCards = db.flashcardDao().getDueFlashcards(System.currentTimeMillis()).firstOrNull() ?: emptyList()
        if (dueCards.isEmpty()) {
            return Result.success() // Nothing to notify
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "learn_sync_reviews"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rappels de révision",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rappels quotidiens pour vos cartes dues dans LearnSync AI"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val count = dueCards.size
        val text = if (count == 1) "1 carte vous attend pour maintenir votre mémorisation !" else "$count cartes vous attendent aujourd'hui. Maintenez votre streak !"

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("LearnSync AI — C'est l'heure de réviser !")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "LearnSyncDailyReviewReminder"

        fun scheduleDailyReminder(context: Context, reminderTimeString: String = "08:00") {
            try {
                val parts = reminderTimeString.split(":")
                val hourOfDay = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

                val currentDate = Calendar.getInstance()
                val dueDate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }

                if (dueDate.before(currentDate)) {
                    dueDate.add(Calendar.HOUR_OF_DAY, 24)
                }

                val timeDiff = (dueDate.timeInMillis - currentDate.timeInMillis).coerceAtLeast(0L)

                val dailyWorkRequest = PeriodicWorkRequestBuilder<ReviewNotificationWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(false)
                            .build()
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    dailyWorkRequest
                )
            } catch (_: Throwable) {
                // Ignore background scheduling exceptions if WorkManager is not initialized
            }
        }

        fun cancelDailyReminder(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (_: Throwable) {}
        }

    }
}
