// Place at: NoteWritingTracker/app/src/main/java/com/leadbait/notewritingtracker/LogConfirmationActivity.kt

package com.leadbait.notewritingtracker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/**
 * Translucent confirmation dialog that opens on a widget double-tap.
 * "Yes" logs today's session and refreshes all widget instances.
 * "No" (or tapping outside the card) just dismisses.
 */
class LogConfirmationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_confirmation)

        val data = WidgetDataManager(this)

        // Show today's date as subtitle so the user is fully aware of what they're logging.
        findViewById<TextView>(R.id.tv_dialog_date).text = data.getTodayFormatted()

        // Tapping the dim scrim outside the card dismisses without logging.
        findViewById<android.view.View>(R.id.dialog_scrim).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_yes).setOnClickListener {
            data.logToday()
            refreshAllWidgets()
            finish()
        }

        findViewById<Button>(R.id.btn_no).setOnClickListener {
            finish()
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
