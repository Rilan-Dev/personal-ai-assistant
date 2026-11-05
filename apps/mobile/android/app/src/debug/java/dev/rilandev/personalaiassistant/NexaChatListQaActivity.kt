package dev.rilandev.personalaiassistant

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only synthetic WhatsApp-like list, never a real messaging account. */
class NexaChatListQaActivity:Activity() {
    lateinit var messageInput:EditText
        private set
    companion object {
        @Volatile var duplicateContact=false
        @Volatile var chatRowClicks=0
        @Volatile var sendClicks=0
        @Volatile var opened=false
    }
    private fun base()=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL
        setPadding(22,24,22,24)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        chatRowClicks=0;sendClicks=0;opened=false
        showChatList()
    }
    private fun showChatList() {
        val area=base()
        area.addView(TextView(this).apply {
            text="WhatsApp - Synthetic QA only";textSize=18f
            setContentDescription("WhatsApp - Synthetic QA only")
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        })
        area.addView(TextView(this).apply { text="Ask Meta AI or Search" })
        fun addRow(name:String) {
            val row=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                isClickable=true
                isFocusable=true
                contentDescription=name
                setOnClickListener {
                    chatRowClicks++
                    opened=true
                    showConversation()
                }
            }
            row.addView(TextView(this).apply {
                text=name
                textSize=19f
                contentDescription=name
                setPadding(0,25,0,25)
            })
            area.addView(row)
        }
        addRow("Nova Forever 🖤")
        if(duplicateContact)addRow("Nova Forever 🖤")
        area.addView(TextView(this).apply {text="Other contact"})
        setContentView(area)
    }
    private fun showConversation() {
        val area=base()
        area.addView(TextView(this).apply{
            text="Nova Forever 🖤";contentDescription="Nova Forever 🖤"
            textSize=19f
        })
        messageInput=EditText(this).apply{
            hint="Type a message"
            contentDescription="Type a message"
            inputType=InputType.TYPE_CLASS_TEXT
        }
        area.addView(messageInput)
        area.addView(Button(this).apply{
            text="Send"
            setOnClickListener{sendClicks++}
        })
        setContentView(area)
    }
}
