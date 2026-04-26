package com.leadbait.notewritingtracker

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "Add the Notes Streak widget to your home screen to get started."
            setPadding(64, 64, 64, 64)
            textSize = 16f
        }
        setContentView(tv)
    }
}
