package com.learnsyncai.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.learnsyncai.MainActivity

/**
 * Notification locale de fin de génération : l'utilisateur peut quitter l'écran
 * pendant l'analyse d'un gros document et être averti quand c'est prêt.
 */
object GenerationNotifier {

    private const val CHANNEL_ID = "course_generation"

    fun notifyDone(context: Context, courseTitle: String, success: Boolean, detail: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Génération de cours",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "Notifications de fin de génération du contenu pédagogique" }
                )
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                courseTitle.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle(if (success) "Cours prêt" else "Échec de la génération")
                .setContentText("$courseTitle — $detail")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$courseTitle — $detail"))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(courseTitle.hashCode(), notification)
        } catch (_: Exception) {
            // Jamais bloquant : la notification est un bonus.
        }
    }
}
