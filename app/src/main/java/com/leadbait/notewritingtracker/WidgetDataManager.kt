// Place at: NoteWritingTracker/app/src/main/java/com/leadbait/notewritingtracker/WidgetDataManager.kt

package com.leadbait.notewritingtracker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Manages all streak data via SharedPreferences.
 *
 * Streak algorithm:
 *  - If today is logged, count backwards from today through consecutive logged days.
 *  - If today is not logged, count backwards from yesterday (yesterday must be logged
 *    for the streak to be > 0; a gap beyond yesterday resets it to 0).
 *  - Missing any calendar day breaks the streak.
 *
 * Data stored:
 *  - "logged_dates"   : StringSet of "yyyy-MM-dd" strings
 *  - "longest_streak" : Int, updated every time a new log produces a higher streak
 */
class WidgetDataManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "notes_streak_prefs"
        private const val KEY_LOGGED_DATES = "logged_dates"
        private const val KEY_LONGEST_STREAK = "longest_streak"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Locale.US ensures consistent yyyy-MM-dd parsing regardless of device locale.
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    private fun todayKey(): String = isoFormat.format(Date())

    // Always copy the set — SharedPreferences StringSets must not be modified in place.
    private fun loggedDates(): MutableSet<String> =
        HashSet(prefs.getStringSet(KEY_LOGGED_DATES, emptySet()) ?: emptySet())

    fun isLoggedToday(): Boolean = todayKey() in loggedDates()

    /**
     * Records today as a completed writing session.
     * No-op if today is already logged.
     * Updates the longest streak record if the new streak exceeds it.
     */
    fun logToday() {
        if (isLoggedToday()) return
        val dates = loggedDates().also { it.add(todayKey()) }
        prefs.edit().putStringSet(KEY_LOGGED_DATES, dates).apply()
        val streak = computeStreak(dates)
        if (streak > getLongestStreak()) {
            prefs.edit().putInt(KEY_LONGEST_STREAK, streak).apply()
        }
    }

    fun getCurrentStreak(): Int = computeStreak(loggedDates())

    fun getLongestStreak(): Int = prefs.getInt(KEY_LONGEST_STREAK, 0)

    /** Returns today's date formatted for display, e.g. "Sat, Apr 25". */
    fun getTodayFormatted(): String = displayFormat.format(Date())

    private fun computeStreak(dates: Set<String>): Int {
        if (dates.isEmpty()) return 0
        // Start counting from today if logged, otherwise from yesterday.
        val cal = Calendar.getInstance(Locale.US)
        if (todayKey() !in dates) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var count = 0
        while (isoFormat.format(cal.time) in dates) {
            count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }
}
