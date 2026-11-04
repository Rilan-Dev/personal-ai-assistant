package dev.rilandev.personalaiassistant

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * DEBUG ONLY: a synthetic external-app-shaped screen with benign controls.
 * Not exported, never included in a release build and never accesses contacts,
 * apps, provider credentials, or real messaging services.
 */
class NexaInputQaActivity : Activity() {
    lateinit var searchField: EditText
        private set
    lateinit var notesField: EditText
        private set
    lateinit var secretField: EditText
        private set

    companion object {
        @Volatile var searchClicks: Int = 0
        @Volatile var sendClicks: Int = 0
        @Volatile var unknownClicks: Int = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchClicks = 0
        sendClicks = 0
        unknownClicks = 0
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
        }
        val title = TextView(this).apply { text = "Nexa synthetic input and icon fixture"; textSize = 22f }
        container.addView(title)
        val searchIcon = ImageButton(this).apply {
            contentDescription = "Search"
            setImageResource(android.R.drawable.ic_menu_search)
            setOnClickListener { searchClicks++ }
        }
        container.addView(searchIcon, LinearLayout.LayoutParams(90, 90))
        val unknownIcon = ImageButton(this).apply {
            contentDescription = "Unknown widget"
            setImageResource(android.R.drawable.ic_menu_help)
            setOnClickListener { unknownClicks++ }
        }
        container.addView(unknownIcon, LinearLayout.LayoutParams(90, 90))
        searchField = EditText(this).apply {
            hint = "Search"
            contentDescription = "Search"
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(searchField)
        notesField = EditText(this).apply {
            hint = "Notes"
            contentDescription = "Notes"
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(notesField)
        secretField = EditText(this).apply {
            hint = "Password"
            contentDescription = "Password"
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(secretField)
        val send = Button(this).apply {
            text = "Send"
            contentDescription = "Send"
            setOnClickListener { sendClicks++ }
        }
        container.addView(send)
        setContentView(ScrollView(this).apply { addView(container) })
    }
}
