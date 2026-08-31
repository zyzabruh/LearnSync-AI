package com.learnsyncai.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.learnsyncai.MainActivity
import com.learnsyncai.R
import com.learnsyncai.data.database.LearnSyncDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Widget d'accueil : nombre de cartes dues aujourd'hui + raccourci "Réviser".
 */
class DueCardsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val dueCount = try {
            runBlocking(Dispatchers.IO) {
                val db = LearnSyncDatabase.getDatabase(context)
                db.flashcardDao().getDueFlashcards(System.currentTimeMillis()).first().size
            }
        } catch (_: Exception) {
            0
        }

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, dueCount)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, dueCount: Int) {
            val views = RemoteViews(context.packageName, R.layout.due_cards_widget)
            views.setTextViewText(
                R.id.widget_due_count,
                when {
                    dueCount <= 0 -> "Rien à réviser !"
                    dueCount == 1 -> "1 carte à réviser"
                    else -> "$dueCount cartes à réviser"
                }
            )

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "review")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_review_button, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, DueCardsWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val dueCount = try {
                    runBlocking(Dispatchers.IO) {
                        val db = LearnSyncDatabase.getDatabase(context)
                        db.flashcardDao().getDueFlashcards(System.currentTimeMillis()).first().size
                    }
                } catch (_: Exception) {
                    0
                }
                for (id in ids) {
                    updateAppWidget(context, manager, id, dueCount)
                }
            } catch (_: Exception) {}
        }
    }
}
