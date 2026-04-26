// Place at: NoteWritingTracker/app/src/main/java/com/leadbait/notewritingtracker/NotesStreakWidgetSmall.kt

package com.leadbait.notewritingtracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

/**
 * AppWidgetProvider for the compact 2×1 Notes Streak widget.
 *
 * Displays flame + streak number + "day streak" label in a single horizontal row.
 * Color (green / grey) is the only logged-state indicator — no date, no status text.
 * Tapping logs today exactly like the larger widget.
 */
class NotesStreakWidgetSmall : AppWidgetProvider() {

    companion object {
        private val COLOR_LOGGED = Color.parseColor("#4CAF50")
        private val COLOR_PENDING = Color.parseColor("#9E9E9E")

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val data = WidgetDataManager(context)
            val isLogged = data.isLoggedToday()
            val streak = data.getCurrentStreak()

            val views = RemoteViews(context.packageName, R.layout.widget_notes_streak_small)

            val accentColor = if (isLogged) COLOR_LOGGED else COLOR_PENDING
            views.setTextViewText(R.id.tv_streak_count_small, streak.toString())
            views.setTextColor(R.id.tv_streak_count_small, accentColor)

            val tapIntent = Intent(context, NotesStreakWidgetSmall::class.java).apply {
                action = NotesStreakWidget.ACTION_LOG_TODAY
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1, // distinct request code from the large widget
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root_small, pendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == NotesStreakWidget.ACTION_LOG_TODAY) {
            WidgetDataManager(context).logToday()
            val manager = AppWidgetManager.getInstance(context)
            // Refresh this widget class's instances
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NotesStreakWidgetSmall::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
            // Also refresh the large widget instances so both stay in sync
            val largeIds = manager.getAppWidgetIds(
                ComponentName(context, NotesStreakWidget::class.java)
            )
            largeIds.forEach { NotesStreakWidget.updateWidget(context, manager, it) }
        }
    }
}
