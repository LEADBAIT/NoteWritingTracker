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
 * Delegates all double-tap detection to NotesStreakWidget.handleTap() so both
 * widget sizes share the same tap state and open the same confirmation dialog.
 */
class NotesStreakWidgetSmall : AppWidgetProvider() {

    companion object {
        private val COLOR_LOGGED  = Color.parseColor("#4CAF50")
        private val COLOR_PENDING = Color.parseColor("#9E9E9E")

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val data = WidgetDataManager(context)
            val isLogged = data.isLoggedToday()
            val streak   = data.getCurrentStreak()

            val views = RemoteViews(context.packageName, R.layout.widget_notes_streak_small)
            views.setTextViewText(R.id.tv_streak_count_small, streak.toString())
            views.setTextColor(R.id.tv_streak_count_small, if (isLogged) COLOR_LOGGED else COLOR_PENDING)

            val tapIntent = Intent(context, NotesStreakWidgetSmall::class.java).apply {
                action = NotesStreakWidget.ACTION_WIDGET_TAP
            }
            val pi = PendingIntent.getBroadcast(
                context, 1, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root_small, pi)
            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == NotesStreakWidget.ACTION_WIDGET_TAP) {
            NotesStreakWidget.handleTap(context, AppWidgetManager.getInstance(context))
        }
    }
}
