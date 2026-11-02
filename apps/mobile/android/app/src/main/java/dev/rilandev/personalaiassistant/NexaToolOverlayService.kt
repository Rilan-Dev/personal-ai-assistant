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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
    private var inspectedScreen: OverlayScreenSnapshot? = null
    private val guidedNavigation = NexaGuidedNavigation()

    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int = NexaOverlayTheme.palette(this).card,
                   border: Boolean = false): GradientDrawable =
        if (border) NexaOverlayTheme.card(this, NexaOverlayTheme.palette(this), 20)
        else NexaOverlayTheme.inset(this, NexaOverlayTheme.palette(this), 18)
            .apply { setColor(color) }
    private fun text(value: String, size: Float = 13f, muted: Boolean = false): TextView =
        NexaOverlayTheme.label(this, value, NexaOverlayTheme.palette(this), size, muted)
    private fun button(label: String, action: () -> Unit): Button =
        NexaOverlayTheme.action(this, label, NexaOverlayTheme.palette(this), action)
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

    private fun startGuidedNavigation(raw: String) {
        inspectedScreen = null
        NexaScreenActions.reset()
        NexaScreenScroll.cancel()
        val result = guidedNavigation.start(raw, android.os.SystemClock.elapsedRealtime())
        note = when(result) {
            is NexaGuidedNavigation.StartResult.Started -> result.description
            is NexaGuidedNavigation.StartResult.Rejected -> result.reason
        }
        section = "tools"
        render()
    }
    private fun stopGuidedNavigation() {
        guidedNavigation.cancel()
        inspectedScreen = null
        NexaScreenActions.reset()
        note = "Guided navigation cancelled; no additional actions requested."
        render()
    }
    private fun sendLocal(raw: String) {
        val query = raw.trim().take(150)
        if (query.isEmpty()) return
        // Strict local intent grammar: inspection is read-only; scrolling only
        // PREPARES a pending confirmation and never fires automatically.
        when (query.lowercase(java.util.Locale.ROOT).trim().trimEnd('?','.','!')) {
            "inspect screen", "inspect screen icons", "find screen icons",
            "what icons are on screen", "what icons are on my screen" -> {
                section = "tools"
                inspectVisibleIcons()
                return
            }
            "find search in menu", "open search from menu", "find search from menu",
            "open settings from menu", "find settings in menu",
            "find search", "open search", "open menu", "find menu",
            "find settings", "open settings" -> {
                startGuidedNavigation(query)
                return
            }
            "scroll up", "scroll down" -> {
                section = "tools"
                proposeScroll(if (query.lowercase().contains("down")) "down" else "up")
                return
            }
        }
        val result = NexaOverlayCommands.handle(this,query).take(550)
        history.addLast("YOU · $query")
        history.addLast("NEXA LOCAL · $result")
        while (history.size > 10) history.removeFirst()
        note = "No cloud prompt or background AI action was executed"
        if ((query.lowercase().contains("find") && result.startsWith("Launcher search started")) ||
            result.startsWith("WhatsApp review opened"))
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
            NexaWakeWordService.persistEnabled(this, false)
            stopService(Intent(this, NexaWakeWordService::class.java))
            note = "Hey Nexa disabled; saved preference cleared"
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
                    NexaWakeWordService.persistEnabled(this, true)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(start)
                    else startService(start)
                    note = "Wake start requested · check Android microphone indicator and Stop notification"
                } catch (_: Exception) {
                    NexaWakeWordService.persistEnabled(this, false)
                    note = "Android blocked background microphone startup. Open Nexa Setup to start visibly."
                }
            }
        }
        render()
    }

    private fun talkToNexa() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            note = "Microphone is off. Open Nexa Setup to grant permission."
            render()
            return
        }
        if (RealtimeVoiceService.status == "listening" ||
            RealtimeVoiceService.status == "connecting") {
            note = "Stop the existing live voice call first, then tap Talk."
            render()
            return
        }
        // A wake detector and a one-shot assistant session cannot own the
        // microphone simultaneously. Stop the detector and give it time to
        // release AudioRecord before requesting the system assistant.
        if (NexaWakeWordService.listening) {
            NexaWakeWordService.pauseForAssistant()
        }
        collapse()
        Handler(Looper.getMainLooper()).postDelayed({
            if (!NexaAssistantService.showFromOwnerTap()) {
                note = "Select Nexa in Android Default assistant settings before Talk."
                expanded = true
                section = "voice"
                render()
            }
        }, 450L)
    }


    private fun showActionJournalSummary() {
        val counts=NexaUiActionJournal.summary(this)
        note=if(counts==null) "Encrypted action journal is unavailable."
            else if(counts.isEmpty()) "No owner-confirmed UI actions recorded."
            else "Encrypted UI actions · " + counts.toSortedMap().entries
                .joinToString(" · ") { it.key.replace('_',' ') + ": " + it.value }.take(280)
        render()
    }

    private fun inspectVisibleIcons() {
        val service = ScreenAssistService.active
        if (service == null) {
            inspectedScreen = null
            note = "Enable Nexa Screen Assist in Android Accessibility settings before inspecting."
        } else {
            val result = NexaScreenActions.inspect(service)
            inspectedScreen = result.first
            note = result.second
        }
        render()
    }

    private fun selectVisibleIcon(id: String) {
        val snap = inspectedScreen
        val selected=snap?.icons?.singleOrNull { it.id==id }
        val expected=guidedNavigation.expected(android.os.SystemClock.elapsedRealtime())
        if (expected != null && selected?.semantic != expected) {
            note="Guide is waiting for the " + expected + " control. Cancel guide to choose another."
            render()
            return
        }
        val item = if (snap != null) NexaScreenActions.select(snap.token, id) else null
        note = if (item == null) "Icon selection expired. Inspect again."
            else "Confirm one tap on ${item.label} in ${snap?.packageName}? This action cannot be undone."
        render()
    }

    private fun confirmVisibleIcon() {
        val snap = inspectedScreen
        val service = ScreenAssistService.active
        val selected = NexaScreenActions.selected()
        val expected=guidedNavigation.expected(android.os.SystemClock.elapsedRealtime())
        if (expected != null && selected?.semantic != expected) {
            note = "Guide target changed. Re-inspect the " + expected + " icon."
            inspectedScreen = null
            NexaScreenActions.reset()
            render()
            return
        }
        val outcome = if (snap == null || service == null) {
            "No current screen target. Inspect again."
        } else {
            NexaScreenActions.confirm(service, snap.token)
        }
        note=outcome
        if (selected != null && outcome.startsWith("Android accepted") &&
            guidedNavigation.afterConfirmedClick(selected.semantic,android.os.SystemClock.elapsedRealtime())) {
            val next=guidedNavigation.expected(android.os.SystemClock.elapsedRealtime())
            note += if (next != null) " Next guide step: " + next +
                ". Inspect the updated screen and approve again."
                else " All guide taps were requested. Inspect the app to verify the result."
        }
        inspectedScreen = null
        render()
    }

    private fun cancelVisibleIcon() {
        inspectedScreen = null
        NexaScreenActions.reset()
        note = "Screen action cancelled. No click requested."
        render()
    }


    private fun proposeScroll(direction: String) {
        inspectedScreen = null
        NexaScreenActions.reset()
        note = ScreenAssistService.active?.let { NexaScreenScroll.prepare(it,direction) }
            ?: "Enable Screen Assist before requesting a scroll."
        render()
    }
    private fun confirmScroll() {
        note = ScreenAssistService.active?.let { NexaScreenScroll.confirm(it) }
            ?: "Screen Assist unavailable; no scroll performed."
        render()
    }
    private fun cancelScroll() {
        NexaScreenScroll.cancel()
        note = "Scroll cancelled."
        render()
    }
    private fun showSection(name: String) {
        if (name != "tools") {
            inspectedScreen = null
            NexaScreenActions.reset()
            NexaScreenScroll.cancel()
            guidedNavigation.cancel()
        }
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
            ImageView(this).apply {
                setImageResource(R.drawable.ic_nexa_assistant)
                setColorFilter(NexaOverlayTheme.palette(this@NexaToolOverlayService).accent)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                contentDescription = "Nexa assistant. Tap to expand, drag to move."
                scaleType = ImageView.ScaleType.FIT_CENTER
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
                background=NexaOverlayTheme.card(this@NexaToolOverlayService,
                    NexaOverlayTheme.palette(this@NexaToolOverlayService))
                elevation=dp(12).toFloat()
                addView(text("━━    ✦ NEXA  ·  LIVE ASSISTANT",12f,true))
                addView(text("How can I help?",22f))
                addView(NexaOverlayTheme.waveform(this@NexaToolOverlayService,
                    NexaOverlayTheme.palette(this@NexaToolOverlayService)),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(37)))
                addView(text("Choose a tool or ask a local device question",12f,true))
                addView(row(
                    button("Chat") { showSection("chat") },
                    button("Tools") { showSection("tools") },
                    button("Voice") { showSection("voice") }
                ))
                when(section) {
                    "chat" -> {
                        addView(text("Ask Nexa to check, find or open an app.",12f,true))
                        val transcript = ScrollView(this@NexaToolOverlayService).apply {
                            background = NexaOverlayTheme.inset(this@NexaToolOverlayService,
                                NexaOverlayTheme.palette(this@NexaToolOverlayService))
                            addView(text(
                                if(history.isEmpty()) "Try: open Brave, device status, check GitHub."
                                else history.takeLast(4).joinToString("\n\n"),12f))
                        }
                        addView(transcript, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,dp(96)))
                        val input = EditText(this@NexaToolOverlayService).apply {
                            hint = "Ask a local tool…"
                            setHintTextColor(NexaOverlayTheme.palette(this@NexaToolOverlayService).secondary)
                            setTextColor(NexaOverlayTheme.palette(this@NexaToolOverlayService).title)
                            textSize=13f
                            isSingleLine=true
                            imeOptions=EditorInfo.IME_ACTION_SEND
                            background=NexaOverlayTheme.inset(this@NexaToolOverlayService,
                                NexaOverlayTheme.palette(this@NexaToolOverlayService))
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
                            button("Open Brave") { sendLocal("open brave") },
                            button("Open WhatsApp") { sendLocal("open whatsapp") }
                        ))
                        addView(button("Open GitHub") { sendLocal("open github") })
                        addView(row(
                            button("Launcher status") { sendLocal("launcher status") },
                            button("Full Tools") { openTab("tools") }
                        ))
                        addView(row(
                            button("App permissions") { sendLocal("open app settings") },
                            button("Battery settings") { sendLocal("background settings") }
                        ))
                        addView(text("Owner-guided navigation: one approved tap at each step.",11f,true))
                        addView(row(
                            button("Menu → Search") { startGuidedNavigation("find search in menu") },
                            button("Menu → Settings") { startGuidedNavigation("open settings from menu") }
                        ))
                        val expectedGuide=guidedNavigation.expected(android.os.SystemClock.elapsedRealtime())
                        if (expectedGuide != null) {
                            addView(text("Guided step: find " + expectedGuide +
                                " · inspect, choose and confirm. No auto-taps.",12f))
                            addView(button("Cancel navigation guide") { stopGuidedNavigation() })
                        }
                        addView(button("Action journal summary") { showActionJournalSummary() })
                        addView(text("The action log stores encrypted approval fingerprints and result categories, not screen contents.",11f,true))
                        addView(text("Safe owner-confirmed scrolling of the currently visible app.",11f,true))
                        if (NexaScreenScroll.isPending()) {
                            addView(row(
                                button("Confirm scroll") { confirmScroll() },
                                button("Cancel scroll") { cancelScroll() }
                            ))
                        } else {
                            addView(row(
                                button("Scroll up") { proposeScroll("up") },
                                button("Scroll down") { proposeScroll("down") }
                            ))
                        }
                        addView(text("Inspect icons on the app behind this floating panel.",11f,true))
                        addView(button("Inspect visible icons") { inspectVisibleIcons() })
                        val snapshot = inspectedScreen
                        if (snapshot != null && android.os.SystemClock.elapsedRealtime() < snapshot.expiresAt) {
                            addView(text("Screen: " + snapshot.packageName + " · expires in " +
                                ((snapshot.expiresAt - android.os.SystemClock.elapsedRealtime()) / 1000) + "s",11f,true))
                            val chosen = NexaScreenActions.selected()
                            if (chosen != null) {
                                addView(text("Approve one tap: " + chosen.label,13f))
                                addView(row(
                                    button("Confirm tap") { confirmVisibleIcon() },
                                    button("Cancel") { cancelVisibleIcon() }
                                ))
                            } else {
                                snapshot.icons.take(12).forEach { item ->
                                    addView(row(
                                        button(item.label.take(18)) {
                                            if (NexaScreenActionPolicy.permits(item) &&
                                                (expectedGuide==null || item.semantic==expectedGuide))
                                                selectVisibleIcon(item.id)
                                            else {
                                                note = "Read-only control. Calls, messages and destructive actions are blocked."
                                                render()
                                            }
                                        },
                                        button(if (NexaScreenActionPolicy.permits(item) &&
                                            (expectedGuide==null || item.semantic==expectedGuide))
                                            "Select" else "Read only") {
                                            if (NexaScreenActionPolicy.permits(item) &&
                                                (expectedGuide==null || item.semantic==expectedGuide))
                                                selectVisibleIcon(item.id)
                                            else {
                                                note = "This icon cannot be acted on by the screen assistant."
                                                render()
                                            }
                                        }
                                    ))
                                }
                            }
                            addView(button("Discard inspected screen") { cancelVisibleIcon() })
                        }
                        addView(text("Only navigation controls can be tapped, once, after confirmation. No AI or automatic gestures.",11f,true))
                        addView(text("Messages require recipient review in full Nexa.",11f,true))
                    }
                    else -> {
                        addView(text("Microphone access and model status",12f,true))
                        addView(text(NexaOverlayCommands.status(this@NexaToolOverlayService),12f))
                        addView(button("Talk to Nexa · one device command") { talkToNexa() })
                        addView(row(
                            button("Arm Hey Nexa") { startWake() },
                            button("Stop wake") { stopWake() }
                        ))
                        addView(row(
                            button("Stop live voice") { stopVoice() },
                            button("Full Voice") { openTab("voice") }
                        ))
                        addView(text("Talk opens Android's compact Nexa assistant (when selected), listens for one on-device command, and never sends messages without review.",11f,true))
                        addView(text("Microphone indicator and Stop notification are Android privacy protections.",11f,true))
                    }
                }
                addView(text("Actions need approval  ·  Screen access requires permission",11f,true))
                addView(text(note,11f,true))
                addView(button("Theme · " + NexaOverlayTheme.mode(this@NexaToolOverlayService) +
                    " (tap to change)") {
                    val newMode=NexaOverlayTheme.cycleMode(this@NexaToolOverlayService)
                    note="Appearance updated: " + newMode + ". Auto follows Android and wallpaper colors."
                    render()
                })
                addView(row(
                    button("Collapse") { collapse() },
                    button("Hide Nexa") { stopSelf() }
                ))
            }
        }
        val fittedLayout: View = if (expanded) {
            ScrollView(this).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = true
                addView(layout)
            }
        } else layout
        val p = WindowManager.LayoutParams(
            if (expanded) minOf(dp(360), resources.displayMetrics.widthPixels - dp(24)) else dp(52),
            if (expanded) minOf(dp(490), resources.displayMetrics.heightPixels - dp(110)) else dp(52),
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
            wm.addView(fittedLayout,p)
            view=fittedLayout
            visible=true
        } catch (_: Exception) {
            view=null
            visible=false
            stopSelf()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (view != null) render() // Follow system day/night and display changes.
    }

    override fun onDestroy() {
        view?.let {try {wm.removeView(it)}catch (_: Exception) {}}
        view=null
        visible=false
        history.clear()
        inspectedScreen = null
        NexaScreenActions.reset()
        NexaScreenScroll.cancel()
        guidedNavigation.cancel()
        super.onDestroy()
    }
}
