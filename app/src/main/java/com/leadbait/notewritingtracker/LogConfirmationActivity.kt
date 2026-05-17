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

    private fun openNotesApp() {
        val pkg = "com.standardnotes"

        // Try 1: getLaunchIntentForPackage
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(it); return } catch (_: Exception) {}
        }

        // Try 2: explicit ACTION_MAIN + CATEGORY_LAUNCHER
        try {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                setPackage(pkg)
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        } catch (_: Exception) { }

        // Try 3: queryIntentActivities to find the real launch activity
        val probe = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(pkg)
        }
        val resolved = packageManager.queryIntentActivities(probe, 0)
        if (resolved.isNotEmpty()) {
            val ai = resolved[0].activityInfo
            try {
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    component = android.content.ComponentName(ai.packageName, ai.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return
            } catch (_: Exception) { }
        }

        android.widget.Toast.makeText(
            this, "Standard Notes not found — opening Play Store", android.widget.Toast.LENGTH_LONG
        ).show()

        // Play Store fallback
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { }
        }
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
