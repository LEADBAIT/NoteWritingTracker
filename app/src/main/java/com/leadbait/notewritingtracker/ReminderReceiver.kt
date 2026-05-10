package com.leadbait.notewritingtracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * Fires at 18:00, 20:00, and 22:00 each day (scheduled by the widget's onUpdate).
 * Checks if today's streak is already logged; if so it exits silently.
 * Otherwise it posts an escalating sound notification to push the user to log.
 *
 * schedule() is safe to call repeatedly — it skips any reminder time already past.
 * cancel() kills all pending alarms and dismisses any visible notifications.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (WidgetDataManager(context).isLoggedToday()) return
        val slot = intent.getIntExtra(EXTRA_SLOT, 0).coerceIn(0, MESSAGES.lastIndex)
        showNotification(context, slot)
    }

    private fun showNotification(context: Context, slot: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val tapIntent = Intent(context, LogConfirmationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LogConfirmationActivity.EXTRA_ALREADY_LOGGED, false)
        }
        val pi = PendingIntent.getActivity(
            context, REQUEST_BASE + slot, tapIntent,
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
        const val CHANNEL_ID    = "streak_reminders"
        const val EXTRA_SLOT    = "reminder_slot"
        private const val REQUEST_BASE  = 200
        private const val NOTIF_ID_BASE = 10

        // Three escalating reminders after 6 PM
        private val REMINDER_HOURS = intArrayOf(18, 20, 22)
        private val MESSAGES = arrayOf(
            "🔥 Don't break your streak — log your writing for today!",
            "⏰ Still haven't logged today. Keep the streak alive!",
            "🚨 Almost midnight — log now or lose your streak!"
        )

        /**
         * Schedules whichever of the three daily reminders still lie in the future.
         * If today is already logged, cancels everything instead.
         * Safe to call on every widget update — past times are silently skipped.
         */
        fun schedule(context: Context) {
            if (WidgetDataManager(context).isLoggedToday()) {
                cancel(context)
                return
            }
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()
            REMINDER_HOURS.forEachIndexed { slot, hour ->
                val triggerMs = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (triggerMs <= now) return@forEachIndexed   // already past, skip
                val pi = pendingIntentFor(context, slot)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                }
            }
        }

        /** Cancels all pending alarms and dismisses any visible reminder notifications. */
        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            REMINDER_HOURS.indices.forEach { slot ->
                am.cancel(pendingIntentFor(context, slot))
                nm.cancel(NOTIF_ID_BASE + slot)
            }
        }

        private fun pendingIntentFor(context: Context, slot: Int): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_SLOT, slot)
            return PendingIntent.getBroadcast(
                context, REQUEST_BASE + slot, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
