package dev.rilandev.personalaiassistant

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only synthetic WhatsApp-like list, never a real messaging account. */
class NexaChatListQaActivity:Activity() {
    lateinit var messageInput:EditText
        private set
    private var duplicateContact=false
    private var overlappingContacts=false
    private var chatRowClicks=0
    private var sendClicks=0
    private var opened=false
    private var qaRunId=""
    private fun base()=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL
        setPadding(22,24,22,24)
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        duplicateContact=intent.getBooleanExtra("duplicate",false)
        overlappingContacts=intent.getBooleanExtra("qa_overlap",false)
        qaRunId=intent.getStringExtra("qa_run").orEmpty()
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
        area.addView(status())
        area.addView(TextView(this).apply { text="Ask Meta AI or Search" })
        fun makeRow(name:String)=LinearLayout(this).apply{
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
        }
        if(duplicateContact && overlappingContacts) {
            // Two DISTINCT native clickable rows overlap by more than half
            // their height. Geometry alone must never collapse these contacts.
            val layer=FrameLayout(this)
            val first=makeRow("Nova Forever 🖤")
            val second=makeRow("Nova Forever 🖤")
            layer.addView(first,FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,140))
            layer.addView(second,FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,140).apply { topMargin=55 })
            area.addView(layer,LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,195))
        } else {
            area.addView(makeRow("Nova Forever 🖤"))
            if(duplicateContact)area.addView(makeRow("Nova Forever 🖤"))
        }
        area.addView(TextView(this).apply {text="Other contact"})
        setContentView(area)
    }
    private fun showConversation() {
        val area=base()
// Keep the synthetic-window identity present after list -> chat
        // navigation so accessibility-root selection never mistakes an old
        // or unrelated app window for the conversation being exercised.
        area.addView(TextView(this).apply {
            text="WhatsApp - Synthetic QA only"
            contentDescription="WhatsApp - Synthetic QA only"
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        })
        area.addView(status())
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
            setOnClickListener{sendClicks++;showConversation()}
        })
        setContentView(area)
    }
    private fun status()=TextView(this).apply {
        text="QA run: $qaRunId · Duplicate contacts: $duplicateContact · Chat row clicks: $chatRowClicks · Send clicks: $sendClicks · Opened: $opened"
        contentDescription=text.toString()
    }
}
