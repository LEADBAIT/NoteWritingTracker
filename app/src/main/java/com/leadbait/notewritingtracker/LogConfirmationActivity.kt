// Place at: NoteWritingTracker/app/src/main/java/com/leadbait/notewritingtracker/LogConfirmationActivity.kt

package com.leadbait.notewritingtracker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

/**
 * Translucent confirmation dialog opened on widget double-tap.
 *
 * Two modes depending on whether today is already logged:
 *
 *  Not yet logged → "Did you write today?" with Yes / No buttons
 *  Already logged → "Already done for today ✓" with a single Got it button
 *
 * Pass EXTRA_ALREADY_LOGGED = true in the intent to trigger the second mode.
 */
class LogConfirmationActivity : Activity() {

    companion object {
        const val EXTRA_ALREADY_LOGGED = "already_logged"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_confirmation)

        requestNotificationPermissionIfNeeded()

        val alreadyLogged = intent.getBooleanExtra(EXTRA_ALREADY_LOGGED, false)
        val data = WidgetDataManager(this)

        findViewById<TextView>(R.id.tv_dialog_date).text = data.getTodayFormatted()
        findViewById<View>(R.id.dialog_scrim).setOnClickListener { finish() }

        if (alreadyLogged) {
            showAlreadyLoggedMode()
        } else {
            showConfirmMode(data)
        }
    }

    private fun showConfirmMode(data: WidgetDataManager) {
        findViewById<TextView>(R.id.tv_dialog_icon).text = "🔥"
        findViewById<TextView>(R.id.tv_dialog_title).apply {
            text = "Did you write today?"
            setTextColor(android.graphics.Color.WHITE)
        }

        findViewById<View>(R.id.btn_row_confirm).visibility = View.VISIBLE
        findViewById<Button>(R.id.btn_got_it).visibility = View.GONE

        findViewById<Button>(R.id.btn_yes).setOnClickListener {
            data.logToday()
            ReminderReceiver.cancel(this)
            refreshAllWidgets()
            finish()
        }
        findViewById<Button>(R.id.btn_no).setOnClickListener {
            openNotesApp()
            finish()
        }
    }

    private fun showAlreadyLoggedMode() {
        findViewById<TextView>(R.id.tv_dialog_icon).text = "✅"
        findViewById<TextView>(R.id.tv_dialog_title).apply {
            text = "Already done for today!"
            setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        }

        findViewById<View>(R.id.btn_row_confirm).visibility = View.GONE
        findViewById<Button>(R.id.btn_got_it).visibility = View.VISIBLE

        findViewById<Button>(R.id.btn_got_it).setOnClickListener { finish() }
    }

    /**
     * Opens the device's notes app. Strategy:
     *  1. Standard CREATE_NOTE intent — handled by Samsung Notes, Google Keep, etc.
     *  2. Fallback: launch known manufacturer/popular note apps by package name.
     * Silently does nothing if no notes app is found (just closes the dialog).
     */
    private fun openNotesApp() {
        // Standard intent — the preferred approach
        if (tryStart(Intent("android.intent.action.CREATE_NOTE")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return

        // Manufacturer and popular fallbacks, tried in order
        val candidates = listOf(
            "com.samsung.android.app.notes",  // Samsung Notes (Android 9+)
            "com.samsung.android.note",        // Samsung Notes (older)
            "com.google.android.keep",          // Google Keep
            "com.miui.notes",                   // Xiaomi / MIUI Notes
            "com.huawei.notepad",               // Huawei Notes
            "com.oneplus.note",                 // OnePlus Notes
            "com.oppo.notes",                   // OPPO Notes
            "com.colornote.notepad"             // ColorNote (popular 3rd-party)
        )
        for (pkg in candidates) {
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            if (tryStart(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        }
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent); true
    } catch (_: Exception) {
        false
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
    }

    private fun refreshAllWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        manager.getAppWidgetIds(ComponentName(this, NotesStreakWidget::class.java))
            .forEach { NotesStreakWidget.updateWidget(this, manager, it) }
        manager.getAppWidgetIds(ComponentName(this, NotesStreakWidgetSmall::class.java))
            .forEach { NotesStreakWidgetSmall.updateWidget(this, manager, it) }
    }
}
