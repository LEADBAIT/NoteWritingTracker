// Place at: NoteWritingTracker/app/src/main/java/com/leadbait/notewritingtracker/NotesStreakWidget.kt

package com.leadbait.notewritingtracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews

/**
 * AppWidgetProvider for the Notes Streak widget.
 *
 * One class handles both the 2×2 and 4×2 layouts. Layout selection is based on the
 * widget's current minimum width reported by AppWidgetManager:
 *   < 200 dp  →  widget_notes_streak_2x2  (compact, vertical)
 *   ≥ 200 dp  →  widget_notes_streak_4x2  (wide, horizontal split)
 *
 * Tap flow:
 *   Widget tap → PendingIntent broadcast → ACTION_LOG_TODAY received in onReceive()
 *   → WidgetDataManager.logToday() → refreshes all widget instances.
 *
 * Because the widget registers ACTION_LOG_TODAY in the manifest, the broadcast is
 * delivered even when the app process is not running (required on Android 8+).
 */
class NotesStreakWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_LOG_TODAY = "com.leadbait.notewritingtracker.LOG_TODAY"

        // Widgets wider than this threshold use the 4×2 horizontal layout.
        private const val WIDE_LAYOUT_THRESHOLD_DP = 200

        private val COLOR_LOGGED = Color.parseColor("#4CAF50")   // green
        private val COLOR_PENDING = Color.parseColor("#9E9E9E")  // grey

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val data = WidgetDataManager(context)
            val isLogged = data.isLoggedToday()
            val streak = data.getCurrentStreak()
            val longest = data.getLongestStreak()

            val options = manager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val isWide = minWidth >= WIDE_LAYOUT_THRESHOLD_DP

            val layoutRes = if (isWide) R.layout.widget_notes_streak_4x2
                            else        R.layout.widget_notes_streak_2x2

            val views = RemoteViews(context.packageName, layoutRes)

            val accentColor = if (isLogged) COLOR_LOGGED else COLOR_PENDING
            val statusText = if (isLogged) "✓ Logged" else "Tap to log"

            // Shared across both layouts:
            views.setTextViewText(R.id.tv_streak_count, streak.toString())
            views.setTextColor(R.id.tv_streak_count, accentColor)

            // Layout-specific views (IDs differ between 2×2 and 4×2):
            if (isWide) {
                views.setTextViewText(R.id.tv_logged_status, statusText)
                views.setTextColor(R.id.tv_logged_status, accentColor)
                views.setTextViewText(R.id.tv_longest_streak, "Best: $longest days")
            } else {
                views.setTextViewText(R.id.tv_status, statusText)
                views.setTextColor(R.id.tv_status, accentColor)
            }

            // Tap the widget root to log today. FLAG_IMMUTABLE is required on API 31+;
            // using it here is safe for minSdk 26.
            val tapIntent = Intent(context, NotesStreakWidget::class.java).apply {
                action = ACTION_LOG_TODAY
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

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

    /** Called whenever the user resizes the widget on the home screen. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_LOG_TODAY) {
            WidgetDataManager(context).logToday()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NotesStreakWidget::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }
    }
}
