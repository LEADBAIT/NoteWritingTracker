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
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

/**
 * AppWidgetProvider for the resizable Notes Streak widget (2×2 / 4×2).
 *
 * Double-tap flow:
 *   1st tap → widget flashes "Tap again!" in amber for 800 ms
 *   2nd tap within 800 ms → LogConfirmationActivity opens (Yes/No dialog)
 *   Confirmed Yes → WidgetDataManager.logToday() → all widgets refresh
 *
 * Shared double-tap state (lastTapTime, uiHandler) lives in this companion so
 * NotesStreakWidgetSmall can reuse it — both widget types trigger the same dialog.
 */
class NotesStreakWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_TAP = "com.leadbait.notewritingtracker.WIDGET_TAP"

        private const val DOUBLE_TAP_WINDOW_MS = 800L
        private const val WIDE_LAYOUT_THRESHOLD_DP = 200

        private val COLOR_LOGGED   = Color.parseColor("#4CAF50")
        private val COLOR_PENDING  = Color.parseColor("#9E9E9E")
        private val COLOR_FEEDBACK = Color.parseColor("#FF9800") // amber on first tap

        // Shared across both widget classes — same process, same static field.
        @Volatile internal var lastTapTime = 0L
        private val uiHandler = Handler(Looper.getMainLooper())

        /** Called by both widget classes on every tap. */
        fun handleTap(context: Context, manager: AppWidgetManager) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastTapTime

            if (lastTapTime > 0L && elapsed <= DOUBLE_TAP_WINDOW_MS) {
                // Second tap within window → open confirmation dialog
                lastTapTime = 0L
                uiHandler.removeCallbacksAndMessages(null)
                refreshAllWidgets(context, manager) // restore normal colours first
                context.startActivity(
                    Intent(context, LogConfirmationActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            } else {
                // First tap → flash "Tap again!" feedback
                lastTapTime = now
                showTapFeedback(context, manager)
                uiHandler.removeCallbacksAndMessages(null)
                uiHandler.postDelayed({
                    lastTapTime = 0L
                    refreshAllWidgets(context, manager)
                }, DOUBLE_TAP_WINDOW_MS + 200L)
            }
        }

        private fun showTapFeedback(context: Context, manager: AppWidgetManager) {
            // Large widgets: update status text to amber "Tap again!"
            val ids = manager.getAppWidgetIds(ComponentName(context, NotesStreakWidget::class.java))
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val isWide = minWidth >= WIDE_LAYOUT_THRESHOLD_DP
                val layoutRes = if (isWide) R.layout.widget_notes_streak_4x2
                               else        R.layout.widget_notes_streak_2x2
                val views = RemoteViews(context.packageName, layoutRes)
                if (isWide) {
                    views.setTextViewText(R.id.tv_logged_status, "Tap again!")
                    views.setTextColor(R.id.tv_logged_status, COLOR_FEEDBACK)
                } else {
                    views.setTextViewText(R.id.tv_status, "Tap again!")
                    views.setTextColor(R.id.tv_status, COLOR_FEEDBACK)
                }
                manager.updateAppWidget(id, views)
            }
            // Small widget: flash streak number amber (no status text view)
            val smallIds = manager.getAppWidgetIds(ComponentName(context, NotesStreakWidgetSmall::class.java))
            for (id in smallIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_notes_streak_small)
                views.setTextColor(R.id.tv_streak_count_small, COLOR_FEEDBACK)
                manager.updateAppWidget(id, views)
            }
        }

        internal fun refreshAllWidgets(context: Context, manager: AppWidgetManager) {
            manager.getAppWidgetIds(ComponentName(context, NotesStreakWidget::class.java))
                .forEach { updateWidget(context, manager, it) }
            manager.getAppWidgetIds(ComponentName(context, NotesStreakWidgetSmall::class.java))
                .forEach { NotesStreakWidgetSmall.updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val data = WidgetDataManager(context)
            val isLogged = data.isLoggedToday()
            val streak   = data.getCurrentStreak()
            val longest  = data.getLongestStreak()

            val options  = manager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val isWide   = minWidth >= WIDE_LAYOUT_THRESHOLD_DP

            val layoutRes = if (isWide) R.layout.widget_notes_streak_4x2
                            else        R.layout.widget_notes_streak_2x2
            val views = RemoteViews(context.packageName, layoutRes)

            val accentColor = if (isLogged) COLOR_LOGGED else COLOR_PENDING
            val statusText  = if (isLogged) "✓ Logged" else "Tap twice to log"

            views.setTextViewText(R.id.tv_streak_count, streak.toString())
            views.setTextColor(R.id.tv_streak_count, accentColor)

            if (isWide) {
                views.setTextViewText(R.id.tv_logged_status, statusText)
                views.setTextColor(R.id.tv_logged_status, accentColor)
                views.setTextViewText(R.id.tv_longest_streak, "Best: $longest days")
            } else {
                views.setTextViewText(R.id.tv_status, statusText)
                views.setTextColor(R.id.tv_status, accentColor)
            }

            val tapIntent = Intent(context, NotesStreakWidget::class.java).apply {
                action = ACTION_WIDGET_TAP
            }
            val pi = PendingIntent.getBroadcast(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager,
        appWidgetId: Int, newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TAP) {
            handleTap(context, AppWidgetManager.getInstance(context))
        }
    }
}
