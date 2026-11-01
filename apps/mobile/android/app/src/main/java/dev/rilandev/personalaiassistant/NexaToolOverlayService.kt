package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * User-controlled small Android overlay with local Chat/Tools/Voice panels.
 * Does not display RN's full app, read the current screen, call a model,
 * grant permissions, send messages, or execute arbitrary agent instructions.
 * Full Nexa navigation is explicitly separate from the compact panels.
 */
class NexaToolOverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "dev.rilandev.personalaiassistant.OVERLAY_SHOW"
        const val ACTION_HIDE = "dev.rilandev.personalaiassistant.OVERLAY_HIDE"
        @Volatile var visible = false
            private set
    }
    private lateinit var wm: WindowManager
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var section = "chat"
    private var note = "Local tools only. Full AI chat is available through Open full Chat."
    private val history = ArrayDeque<String>()

    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int = Color.rgb(19,26,48), border: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(16).toFloat()
            if (border) setStroke(dp(1), Color.rgb(91,102,158))
        }
    private fun text(value: String, size: Float = 13f, muted: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(if (muted) Color.rgb(185,198,225) else Color.WHITE)
            setPadding(dp(3),dp(4),dp(3),dp(4))
        }
    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            minHeight = dp(40)
            setPadding(dp(7),0,dp(7),0)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(59,67,130))
            setOnClickListener { action() }
        }
    private fun row(vararg buttons: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (b in buttons) addView(b, LinearLayout.LayoutParams(0,dp(44),1f))
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action != ACTION_SHOW || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) {
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            render()
        }
        return START_NOT_STICKY
    }

    private fun openTab(tab: String) {
        getSharedPreferences("nexa_overlay", MODE_PRIVATE)
            .edit().putString("pending_tab",tab).apply()
        try {
            startActivity(Intent(this,MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            collapse()
        } catch (_: Exception) {
            note = "Android blocked opening the full Nexa application"
            render()
        }
    }

    private fun collapse() {
        expanded = false
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view?.windowToken,0)
        render()
    }

    private fun sendLocal(raw: String) {
        val query = raw.trim().take(150)
        if (query.isEmpty()) return
        val result = NexaOverlayCommands.handle(this,query).take(550)
        history.addLast("YOU · $query")
        history.addLast("NEXA LOCAL · $result")
        while (history.size > 10) history.removeFirst()
        note = "No cloud prompt or background AI action was executed"
        if (query.lowercase().contains("find") && result.startsWith("Launcher search started"))
            collapse()
        else render()
    }

    private fun stopVoice() {
        try {
            startService(Intent(this,RealtimeVoiceService::class.java).apply {
                action = RealtimeVoiceService.ACTION_STOP
            })
            note = "Stop live voice requested · check Android notification"
        } catch (_: Exception) {
            stopService(Intent(this,RealtimeVoiceService::class.java))
            note = "Android stop requested"
        }
        render()
    }

    private fun stopWake() {
        try {
            stopService(Intent(this,NexaWakeWordService::class.java))
            note = "Offline Hey Nexa listener stopped by user"
        } catch (_: Exception) { note = "Android rejected stop · use notification Stop" }
        render()
    }

    private fun startWake() {
        when {
            !NexaWakeWordModule.modelReady(this) ->
                note = "Download the verified offline model from Nexa Setup first"
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                note = "Microphone is off · open full Nexa Setup and grant access"
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                note = "Notifications are off · grant access in Android Settings"
            RealtimeVoiceService.status == "listening" ||
                RealtimeVoiceService.status == "connecting" ->
                note = "Stop the current live voice session before arming Hey Nexa"
            else -> {
                try {
                    val start = Intent(this,NexaWakeWordService::class.java).apply {
                        action = NexaWakeWordService.START
                    }
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(start)
                    else startService(start)
                    note = "Wake start requested · check Android microphone indicator and Stop notification"
                } catch (_: Exception) {
                    note = "Android blocked background microphone startup. Open Nexa Setup to start visibly."
                }
            }
        }
        render()
    }

    private fun showSection(name: String) {
        section = name
        render()
    }

    private fun render() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val savedX = params?.x ?: dp(12)
        val savedY = params?.y ?: dp(160)
        val old = view
        if (old != null) {
            try { wm.removeView(old) } catch (_: Exception) {}
            view = null
            visible = false
        }
        val layout: View = if (!expanded) {
            TextView(this).apply {
                text = "✦"
                contentDescription = "Nexa tools. Tap to expand, drag to move."
                setTextColor(Color.WHITE)
                textSize = 23f
                gravity = Gravity.CENTER
                background = bg(border = true)
                setOnClickListener { expanded = true; render() }
                var startX = 0f
                var startY = 0f
                var originX = 0
                var originY = 0
                var moved = false
                setOnTouchListener { v, event ->
                    when(event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX=event.rawX;startY=event.rawY
                            originX=params?.x ?: 0;originY=params?.y ?: 0
                            moved=false;true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if(abs(event.rawX-startX)>dp(8)||abs(event.rawY-startY)>dp(8))
                                moved=true
                            if(moved) params?.let { p ->
                                p.x=(originX-(event.rawX-startX)).toInt().coerceAtLeast(0)
                                p.y=(originY+(event.rawY-startY)).toInt().coerceAtLeast(0)
                                try {wm.updateViewLayout(v,p)}catch (_: Exception){}
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {if(!moved) v.performClick();true}
                        else -> false
                    }
                }
            }
        } else {
            LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(dp(9),dp(9),dp(9),dp(9))
                background=bg(border=true)
                addView(text("✦  NEXA · Quick assistant",16f))
                addView(text("Floating local workspace · owner controlled",11f,true))
                addView(row(
                    button("Chat") { showSection("chat") },
                    button("Tools") { showSection("tools") },
                    button("Voice") { showSection("voice") }
                ))
                when(section) {
                    "chat" -> {
                        addView(text("Test device tools here without opening Nexa.",12f,true))
                        val transcript = ScrollView(this@NexaToolOverlayService).apply {
                            background = bg(Color.rgb(26,35,58))
                            addView(text(
                                if(history.isEmpty()) "Try: check device status, battery, check github, open whatsapp, find my github app."
                                else history.joinToString("\n\n"),12f))
                        }
                        addView(transcript, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,dp(168)))
                        val input = EditText(this@NexaToolOverlayService).apply {
                            hint = "Ask a local tool…"
                            setHintTextColor(Color.rgb(150,166,195))
                            setTextColor(Color.WHITE)
                            textSize=13f
                            isSingleLine=true
                            imeOptions=EditorInfo.IME_ACTION_SEND
                            background=bg(Color.rgb(32,41,65),true)
                            setPadding(dp(9),dp(9),dp(9),dp(9))
                        }
                        addView(input, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,dp(46)))
                        input.setOnEditorActionListener { _,_,_ ->
                            sendLocal(input.text.toString());true
                        }
                        addView(row(
                            button("Send") { sendLocal(input.text.toString()) },
                            button("Open full Chat") { openTab("chat") }
                        ))
                    }
                    "tools" -> {
                        addView(text("Direct device tools (not unrestricted control)",12f,true))
                        addView(row(
                            button("Battery") { sendLocal("battery") },
                            button("Permissions") { sendLocal("device status") }
                        ))
                        addView(row(
                            button("Check GitHub") { sendLocal("check github") },
                            button("Find GitHub") { sendLocal("find my github app") }
                        ))
                        addView(row(
                            button("Open GitHub") { sendLocal("open github") },
                            button("Open WhatsApp") { sendLocal("open whatsapp") }
                        ))
                        addView(row(
                            button("Launcher status") { sendLocal("launcher status") },
                            button("Full Tools") { openTab("tools") }
                        ))
                        addView(text("WhatsApp sending requires recipient review and confirmation in full Nexa.",11f,true))
                    }
                    else -> {
                        addView(text("Microphone access and model status",12f,true))
                        addView(text(NexaOverlayCommands.status(this@NexaToolOverlayService),12f))
                        addView(row(
                            button("Arm Hey Nexa") { startWake() },
                            button("Stop wake") { stopWake() }
                        ))
                        addView(row(
                            button("Stop live voice") { stopVoice() },
                            button("Full Voice") { openTab("voice") }
                        ))
                        addView(text("Android may block background microphone starts. Use full Voice/Setup to grant permission and start a session.",11f,true))
                    }
                }
                addView(text(note,11f,true))
                addView(row(
                    button("Collapse") { collapse() },
                    button("Hide Nexa") { stopSelf() }
                ))
            }
        }
        val p = WindowManager.LayoutParams(
            if (expanded) dp(320) else dp(52),
            if (expanded) WindowManager.LayoutParams.WRAP_CONTENT else dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                (if (expanded && section == "chat") 0
                    else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.RIGHT
            x=savedX
            y=savedY
            softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        params=p
        try {
            wm.addView(layout,p)
            view=layout
            visible=true
        } catch (_: Exception) {
            view=null
            visible=false
            stopSelf()
        }
    }

    override fun onDestroy() {
        view?.let {try {wm.removeView(it)}catch (_: Exception) {}}
        view=null
        visible=false
        history.clear()
        super.onDestroy()
    }
}
