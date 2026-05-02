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
import java.util.Calendar

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
        private val COLOR_FEEDBACK = Color.parseColor("#FF9800") // amber on first tap

        // Urgency colors for pending state — progress through the day as a visual rush cue.
        // Dark background means "black" would be invisible, so the final stage uses alarm red.
        private val COLOR_PENDING_BLUE   = Color.parseColor("#64B5F6") // 00:00–05:59
        private val COLOR_PENDING_GREEN  = Color.parseColor("#81C784") // 06:00–11:59
        private val COLOR_PENDING_ORANGE = Color.parseColor("#FFB74D") // 12:00–17:59
        private val COLOR_PENDING_PINK   = Color.parseColor("#F48FB1") // 18:00–20:59
        private val COLOR_PENDING_RED    = Color.parseColor("#FF1744") // 21:00–23:59

        /** Returns the urgency color for an unlogged streak based on current hour. */
        fun getPendingColor(): Int {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when {
                hour < 6  -> COLOR_PENDING_BLUE
                hour < 12 -> COLOR_PENDING_GREEN
                hour < 18 -> COLOR_PENDING_ORANGE
                hour < 21 -> COLOR_PENDING_PINK
                else      -> COLOR_PENDING_RED
            }
        }

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
                val alreadyLogged = WidgetDataManager(context).isLoggedToday()
                context.startActivity(
                    Intent(context, LogConfirmationActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(LogConfirmationActivity.EXTRA_ALREADY_LOGGED, alreadyLogged)
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
            val data    = WidgetDataManager(context)
            val streak  = data.getCurrentStreak()
            val longest = data.getLongestStreak()
            val isLogged = data.isLoggedToday()
            val accentColor = if (isLogged) COLOR_LOGGED else getPendingColor()

            val ids = manager.getAppWidgetIds(ComponentName(context, NotesStreakWidget::class.java))
            for (id in ids) {
                val options  = manager.getAppWidgetOptions(id)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val isWide   = minWidth >= WIDE_LAYOUT_THRESHOLD_DP
                val layoutRes = if (isWide) R.layout.widget_notes_streak_4x2
                               else        R.layout.widget_notes_streak_2x2
                val views = RemoteViews(context.packageName, layoutRes)

                // Always keep the streak number correct — this was the source of the 0-flash bug.
                views.setTextViewText(R.id.tv_streak_count, streak.toString())
                views.setTextColor(R.id.tv_streak_count, accentColor)

                if (isWide) {
                    views.setTextViewText(R.id.tv_logged_status, "Tap again!")
                    views.setTextColor(R.id.tv_logged_status, COLOR_FEEDBACK)
                    views.setTextViewText(R.id.tv_longest_streak, "Best: $longest days")
                } else {
                    views.setTextViewText(R.id.tv_status, "Tap again!")
                    views.setTextColor(R.id.tv_status, COLOR_FEEDBACK)
                }

                val tapIntent = Intent(context, NotesStreakWidget::class.java).apply {
                    action = ACTION_WIDGET_TAP
                }
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getBroadcast(context, 0, tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                manager.updateAppWidget(id, views)
            }

            val smallIds = manager.getAppWidgetIds(ComponentName(context, NotesStreakWidgetSmall::class.java))
            for (id in smallIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_notes_streak_small)
                views.setTextViewText(R.id.tv_streak_count_small, streak.toString())
                views.setTextColor(R.id.tv_streak_count_small, COLOR_FEEDBACK)
                val tapIntent = Intent(context, NotesStreakWidgetSmall::class.java).apply {
                    action = ACTION_WIDGET_TAP
                }
                views.setOnClickPendingIntent(
                    R.id.widget_root_small,
                    PendingIntent.getBroadcast(context, 1, tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
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

            val accentColor = if (isLogged) COLOR_LOGGED else getPendingColor()
            val statusText  = if (isLogged) "✓ Logged" else "Tap twice to log"
            val flameAlpha  = if (isLogged) 1.0f else 0.30f
            val bgRes       = if (isLogged) R.drawable.widget_background
                              else          R.drawable.widget_background_pending

            // Switch background between warm (logged) and cold (pending)
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            // Dim the flame when unlogged so it looks visually "unlit"
            views.setFloat(R.id.tv_flame, "setAlpha", flameAlpha)

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
