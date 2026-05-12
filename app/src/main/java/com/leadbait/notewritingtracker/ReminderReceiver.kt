package com.leadbait.notewritingtracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * Two kinds of alarms are scheduled each day:
 *
 *  Transition alarms (06:00, 12:00, 21:00) — silently repaint the widget so the
 *  background color changes the instant the hour boundary is crossed, without
 *  waiting for the 30-minute widget update cycle.
 *
 *  Notification alarms (18:00, 20:00, 22:00) — repaint the widget AND post an
 *  escalating sound notification if today's streak is still unlogged.
 *
 * EXTRA_SLOT distinguishes the two types:
 *   -1  → transition only (no notification)
 *   0–2 → notification slot index
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Always repaint the widget so the colour changes at the exact boundary.
        val mgr = AppWidgetManager.getInstance(context)
        NotesStreakWidget.refreshAllWidgets(context, mgr)

        // Only show a notification for reminder slots (0, 1, 2).
        val slot = intent.getIntExtra(EXTRA_SLOT, SLOT_TRANSITION)
        if (slot >= 0 && !WidgetDataManager(context).isLoggedToday()) {
            showNotification(context, slot)
        }
    }

    private fun showNotification(context: Context, slot: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val tapIntent = Intent(context, LogConfirmationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LogConfirmationActivity.EXTRA_ALREADY_LOGGED, false)
        }
        val pi = PendingIntent.getActivity(
            context, REQUEST_NOTIF_BASE + slot, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Notes Streak")
            .setContentText(MESSAGES[slot])
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_BASE + slot, notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Streak Reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Daily nudges to log your writing before midnight" }
        )
    }

    companion object {
        const val CHANNEL_ID      = "streak_reminders"
        const val EXTRA_SLOT      = "reminder_slot"
        const val SLOT_TRANSITION = -1

        private const val REQUEST_NOTIF_BASE      = 200  // slots 200–202 for notifications
        private const val REQUEST_TRANSITION_BASE = 210  // slots 210–212 for colour transitions

        private const val NOTIF_ID_BASE = 10

        // Colour-boundary alarms — repaint widget only, no notification
        private val TRANSITION_HOURS = intArrayOf(6, 12, 21)

        // Notification alarms — repaint widget + show notification if unlogged
        private val REMINDER_HOURS = intArrayOf(18, 20, 22)

        private val MESSAGES = arrayOf(
            "🔥 Don't break your streak — log your writing for today!",
            "⏰ Still haven't logged today. Keep the streak alive!",
            "🚨 Almost midnight — log now or lose your streak!"
        )

        /**
         * Schedules all future alarms for today (transition + notification).
         * If today is already logged, cancels everything instead.
         * Safe to call repeatedly — past times are silently skipped.
         */
        fun schedule(context: Context) {
            if (WidgetDataManager(context).isLoggedToday()) {
                cancel(context)
                return
            }
            val am  = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()

            // Transition-only alarms
            TRANSITION_HOURS.forEachIndexed { idx, hour ->
                val triggerMs = todayAt(hour)
                if (triggerMs <= now) return@forEachIndexed
                setAlarm(am, triggerMs, transitionPendingIntent(context, idx))
            }

            // Notification alarms
            REMINDER_HOURS.forEachIndexed { slot, hour ->
                val triggerMs = todayAt(hour)
                if (triggerMs <= now) return@forEachIndexed
                setAlarm(am, triggerMs, notifPendingIntent(context, slot))
            }
        }

        /** Cancels all pending alarms and dismisses any visible notifications. */
        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            TRANSITION_HOURS.indices.forEach { idx -> am.cancel(transitionPendingIntent(context, idx)) }
            REMINDER_HOURS.indices.forEach { slot ->
                am.cancel(notifPendingIntent(context, slot))
                nm.cancel(NOTIF_ID_BASE + slot)
            }
        }

        private fun setAlarm(am: AlarmManager, triggerMs: Long, pi: PendingIntent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        }

        private fun todayAt(hour: Int): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun transitionPendingIntent(context: Context, idx: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQUEST_TRANSITION_BASE + idx,
                Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_SLOT, SLOT_TRANSITION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun notifPendingIntent(context: Context, slot: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQUEST_NOTIF_BASE + slot,
                Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_SLOT, slot),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
