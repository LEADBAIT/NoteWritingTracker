// Place at: NoteWritingTracker/app/src/main/java/com/leadbait/notewritingtracker/LogConfirmationActivity.kt

package com.leadbait.notewritingtracker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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

        val btnYes = findViewById<Button>(R.id.btn_yes)
        val btnNo  = findViewById<Button>(R.id.btn_no)
        val btnGotIt = findViewById<Button>(R.id.btn_got_it)

        btnYes.visibility = View.VISIBLE
        btnNo.visibility  = View.VISIBLE
        btnGotIt.visibility = View.GONE

        btnYes.setOnClickListener {
            data.logToday()
            refreshAllWidgets()
            finish()
        }
        btnNo.setOnClickListener { finish() }
    }

    private fun showAlreadyLoggedMode() {
        findViewById<TextView>(R.id.tv_dialog_icon).text = "✅"
        findViewById<TextView>(R.id.tv_dialog_title).apply {
            text = "Already done for today!"
            setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        }

        val btnYes = findViewById<Button>(R.id.btn_yes)
        val btnNo  = findViewById<Button>(R.id.btn_no)
        val btnGotIt = findViewById<Button>(R.id.btn_got_it)

        btnYes.visibility  = View.GONE
        btnNo.visibility   = View.GONE
        btnGotIt.visibility = View.VISIBLE

        btnGotIt.setOnClickListener { finish() }
    }

    private fun refreshAllWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        manager.getAppWidgetIds(ComponentName(this, NotesStreakWidget::class.java))
            .forEach { NotesStreakWidget.updateWidget(this, manager, it) }
        manager.getAppWidgetIds(ComponentName(this, NotesStreakWidgetSmall::class.java))
            .forEach { NotesStreakWidgetSmall.updateWidget(this, manager, it) }
    }
}
