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
    /** Only the owner may confirm an explicitly typed, short-lived action. */
    private var proposedDeviceAction: String? = null
    private var proposedDeviceActionExpiresAt: Long = 0L
    private val history = ArrayDeque<String>()
    private var inspectedScreen: OverlayScreenSnapshot? = null
    private val guidedNavigation = NexaGuidedNavigation()
    private val evidenceHandler=Handler(Looper.getMainLooper())
    private var activeEvidenceToken: String? = null
    private fun inspectGuidedOutcome(token: String, attempt: Int = 0) {
        evidenceHandler.postDelayed({
            if(activeEvidenceToken != token) return@postDelayed
            val observation=NexaUiActionJournal.outcomeFor(this,token)
            val status=guidedNavigation.onActionEvidence(token,observation,
                android.os.SystemClock.elapsedRealtime())
            when(status) {
                "waiting" -> {
                    if(attempt<3) inspectGuidedOutcome(token,attempt+1)
                    else {
                        guidedNavigation.cancel()
                        activeEvidenceToken=null
                        note="The screen change could not be verified. Guide stopped; start a new inspection."
                        render()
                    }
                }
                "advanced" -> {
                    activeEvidenceToken=null
                    val next=guidedNavigation.expected(android.os.SystemClock.elapsedRealtime())
                    note="Android reported a screen change, not task success. "+
                        "Next: inspect the screen for "+next+" and approve it separately."
                    render()
                }
                "completed" -> {
                    activeEvidenceToken=null
                    note="All guide clicks were observed by Android. "+
                        "Verify the destination yourself; task completion is not confirmed."
                    render()
                }
                else -> {
                    activeEvidenceToken=null
                    note="No matching Android screen-change evidence. Guide cancelled; no retry."
                    render()
                }
            }
        },1_250L)
    }

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
        if (NexaMessageDraftState.peek()!=null || NexaTextInputState.peek()!=null) section="tools"
        if (intent?.action != ACTION_SHOW || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) {
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        render()
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
        activeEvidenceToken=null
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
        activeEvidenceToken=null
        inspectedScreen = null
        NexaScreenActions.reset()
        note = "Guided navigation cancelled; no additional actions requested."
        render()
    }
    private fun proposeOwnerDeviceAction(action:String,description:String) {
        // Avoid offering a dead approval button if Android did not actually
        // connect the user-enabled screen-assistance service.
        if((action=="open_notification_shade" || action=="go_back" ||
            action.startsWith("tap_visible_label:")) &&
            (ScreenAssistService.active==null || !ScreenAssistState.connected)) {
            proposedDeviceAction=null
            proposedDeviceActionExpiresAt=0L
            section="tools"
            note="Nexa Screen Assist is OFF. Enable it in Android Accessibility settings. No system action was attempted."
            render()
            return
        }
        proposedDeviceAction=action
        // The previous 15-second window expired during real-world screen
        // reader use and remote Android test latency. Still single use and
        // bounded, consistent with the native voice-approval timeout.
        proposedDeviceActionExpiresAt=android.os.SystemClock.elapsedRealtime()+60_000L
        section="tools"
        note="Owner review: $description. No operation has executed. Confirm within 60 seconds."
        render()
    }

    private fun cancelOwnerDeviceAction() {
        proposedDeviceAction=null
        proposedDeviceActionExpiresAt=0L
        note="Proposed action cancelled. No operation occurred."
        render()
    }

    /**
     * A notification panel is a system-owned surface. Even a non-focusable
     * TYPE_APPLICATION_OVERLAY bubble can obstruct it on some builds.
     * Temporarily remove ALL Nexa overlay windows while the owner is using
     * the notification shade; the user can re-show the bubble from Nexa.
     * This must run on the Android main thread after an explicit approval.
     */
    private fun detachOverlayForNotificationShade():Boolean {
        // No GLOBAL_ACTION_NOTIFICATIONS request may run while an expanded
        // Nexa overlay is still attached or its removal failed.
        val existing=view ?: return false
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(existing.windowToken,0)
        val detached=try {
            wm.removeViewImmediate(existing)
            true
        } catch (_:Exception) {
            try { wm.removeView(existing); true }catch (_:Exception){false}
        }
        if(!detached)return false
        expanded=false
        view=null
        visible=false
        return true
    }

    private fun confirmOwnerDeviceAction() {
        val action=proposedDeviceAction
        val expiry=proposedDeviceActionExpiresAt
        proposedDeviceAction=null
        proposedDeviceActionExpiresAt=0L
        if(action==null || android.os.SystemClock.elapsedRealtime()>=expiry) {
            note="Approval expired. No operation performed."
            render()
            return
        }

        // Expanded TYPE_APPLICATION_OVERLAY windows may retain input focus and
        // can obscure or dismiss Android's notification shade and Back target.
        // Release that focus before requesting any system navigation.
        val isNotificationShade=action=="open_notification_shade"
        val isSystemNavigation=isNotificationShade || action=="go_back"
        note="Action in progress; completion is not yet verified."
        if(isNotificationShade) {
            if(!detachOverlayForNotificationShade()) {
                note="Nexa could not release its floating window. The notification shade was not requested."
                render()
                return
            }
        } else if(isSystemNavigation) collapse()
        else render()

        val dispatch={
            // Accessibility work and crypto journaling stay off the UI thread.
            Thread({
                val outcome=try {
                    when {
                        action=="open_notification_shade" ->
                            NexaBrowserTaskActions.notificationsApproved(this)
                        action=="go_back" ->
                            NexaBrowserTaskActions.goBackApproved(this)
                        action.startsWith("chrome_search:") ->
                            NexaBrowserTaskActions.openChromeSearchApproved(
                                this,action.removePrefix("chrome_search:"))
                        action.startsWith("tap_visible_label:") ->
                            ScreenAssistService.active?.let {
                                NexaScreenLabelTap.approved(it,action.removePrefix("tap_visible_label:"))
                            } ?: "Screen Assist is disabled. No tap performed."
                        else -> "Invalid device action. No operation performed."
                    }
                } catch (_:Exception) { "Android action was refused or became uncertain." }
                Handler(Looper.getMainLooper()).post {
                    // The owner may have hidden/destroyed Nexa while Android
                    // performed the action; never recreate an accepted shade.
                    note=outcome.take(360)
                    if(isNotificationShade) {
                        if(outcome.startsWith("Android accepted")) {
                            // No overlay window remains over Android SystemUI.
                            // This is dispatch evidence, not shade verification.
                            return@post
                        }
                        // Refusals/uncertain results must be visible to owner.
                        // Never silently lose the failure after detaching.
                        if(!visible && view==null) {
                            expanded=true
                            section="tools"
                            render()
                        }
                        return@post
                    }
                    if(view==null || !visible)return@post
                    if(isSystemNavigation && outcome.startsWith("Android accepted")) {
                        // Keep the unobtrusive bubble. Rendering the expanded
                        // panel here can dismiss the system surface we opened.
                        // Accepted is not a verified task-complete claim.
                        return@post
                    }
                    if(action.startsWith("chrome_search:") &&
                        outcome.contains("launch requested")) {
                        collapse()
                    } else {
                        if(isSystemNavigation) {
                            expanded=true
                            section="tools"
                        }
                        // Refused/unknown actions must still have visible feedback.
                        render()
                    }
                }
            },"nexa-reviewed-device-objective").start()
        }
        if(isSystemNavigation) {
            // Give WindowManager time to release the expanded overlay and IME
            // before Android processes GLOBAL_ACTION_NOTIFICATIONS / BACK.
            Handler(Looper.getMainLooper()).postDelayed({dispatch()},240L)
        } else dispatch()
    }

    private fun sendLocal(raw: String) {
        val query = raw.trim().take(150)
        if (query.isEmpty()) return
        // A newer owner request invalidates an older unconfirmed proposal.
        proposedDeviceAction=null
        proposedDeviceActionExpiresAt=0L
        val search=NexaPhoneVoiceTools.chromeSearchQuery(query)
        if(search!=null) {
            proposeOwnerDeviceAction("chrome_search:$search",
                "Open Chrome Google search results for '$search'")
            return
        }
        if(NexaPhoneVoiceTools.wantsNotificationShade(query)) {
            proposeOwnerDeviceAction("open_notification_shade",
                "Open Android notification shade without reading contents")
            return
        }
        if(NexaPhoneVoiceTools.wantsGoBack(query)) {
            proposeOwnerDeviceAction("go_back","Navigate Back once")
            return
        }
        val requestedLabel=NexaPhoneVoiceTools.tapVisibleLabelIntent(query)
        if(requestedLabel!=null) {
            proposeOwnerDeviceAction("tap_visible_label:$requestedLabel",
                "Tap one uniquely grounded visible control named '$requestedLabel'")
            return
        }
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
            "scroll up", "scroll down", "scroll left", "scroll right" -> {
                section = "tools"
                proposeScroll(query.lowercase().removePrefix("scroll "))
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
        val last=NexaUiActionJournal.latestOutcome(this)
        note=if(counts==null) "Encrypted action journal is unavailable."
            else if(counts.isEmpty()) "No owner-confirmed UI actions recorded."
            else {
                val latest=if(last==null) "Last action unavailable"
                    else "Latest: "+last.first.replace('_',' ')+" — "+
                        last.second.replace('_',' ')+" (not proof of task success)"
                (latest+" · Events: "+counts.toSortedMap().entries
                    .joinToString(", ") { it.key.replace('_',' ') + ": " + it.value }).take(350)
            }
        render()
    }

    private fun inspectVisibleIcons() {
        val service = ScreenAssistService.active
        if (service == null) {
            inspectedScreen = null
            note = "Enable Nexa Screen Assist in Android Accessibility settings before inspecting."
        } else {
            val (snapshot, description) = NexaScreenActions.inspect(service)
            val now=android.os.SystemClock.elapsedRealtime()
            val expected=guidedNavigation.expected(now)
            if (expected != null && snapshot != null &&
                !guidedNavigation.bindPackage(snapshot.packageName,now)) {
                inspectedScreen = null
                NexaScreenActions.reset()
                note = "Guided navigation is bound to its original application. " +
                    "Return to that app or cancel this guide."
            } else {
                inspectedScreen = snapshot
                note = description
            }
        }
        render()
    }

    private fun selectVisibleIcon(id: String) {
        val snap = inspectedScreen
        val selected=snap?.icons?.singleOrNull { it.id==id }
        val expected=guidedNavigation.expected(android.os.SystemClock.elapsedRealtime())
        if (expected != null && (selected == null || !guidedNavigation.canSelect(selected.semantic,android.os.SystemClock.elapsedRealtime()))) {
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
        if (expected != null && (selected == null || !guidedNavigation.canSelect(selected.semantic,android.os.SystemClock.elapsedRealtime()))) {
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
        if (selected != null && snap != null && outcome.startsWith("Android accepted") &&
            guidedNavigation.afterAcceptedDispatch(selected.semantic,snap.token,
                android.os.SystemClock.elapsedRealtime())) {
            activeEvidenceToken=snap.token
            note += " Awaiting same-action Android screen evidence. "+
                "No next step is available until verified."
            inspectGuidedOutcome(snap.token)
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
    private fun inspectNamedTextBox() {
        val service=ScreenAssistService.active
        note=if(service==null)"Screen Assist disconnected; nothing typed."
            else NexaTextInputActions.prepare(service)
        section="tools"
        render()
    }
    private fun confirmNamedTextBox() {
        val service=ScreenAssistService.active
        note=if(service==null){
            NexaTextInputActions.cancel()
            NexaTextInputState.clear()
            "Screen Assist disconnected. Text entry cancelled."
        }else NexaTextInputActions.confirm(service)
        expanded=false
        render()
    }
    private fun cancelNamedTextBox() {
        NexaTextInputActions.cancel()
        NexaTextInputState.clear()
        note="Text entry cancelled without sending or submitting."
        render()
    }

    private fun inspectMessageInput() {
        val service=ScreenAssistService.active
        note=if(service==null) "Screen Assist disconnected. No message was typed."
            else NexaMessageInputActions.prepare(service)
        section="tools"
        render()
    }
    private fun confirmMessageInput() {
        val service=ScreenAssistService.active
        note=if(service==null) {
            NexaMessageInputActions.cancel()
            NexaMessageDraftState.clear()
            "Screen Assist disconnected. Message insertion cancelled."
        } else NexaMessageInputActions.confirm(service)
        expanded=false
        render()
    }
    private fun cancelMessageInput() {
        NexaMessageInputActions.cancel()
        NexaMessageDraftState.clear()
        note="Reviewed message entry cancelled. No message sent."
        render()
    }

    private fun showSection(name: String) {
        if (name != "tools") {
            inspectedScreen = null
            NexaScreenActions.reset()
            NexaScreenScroll.cancel()
            guidedNavigation.cancel()
            activeEvidenceToken=null
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
            val removed=try {
                wm.removeViewImmediate(old)
                true
            } catch (_:Exception) {
                try { wm.removeView(old); true }catch (_:Exception){false}
            }
            if(!removed) {
                // Fail closed; do not build multiple overlapping overlay
                // windows when the old root could not be detached.
                visible=false
                stopSelf()
                return
            }
            view=null
            visible=false
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
                        NexaTextInputState.peek()?.let { draft ->
                            addView(text("REVIEW TEXT FIELD · "+draft.targetLabel,13f))
                            addView(text("Exact proposed text: "+draft.exactText.take(500),12f))
                            addView(text("Open the intended external app and one empty field. Nexa will only enter this text after one more confirmation; never submit or send.",11f,true))
                            if(NexaTextInputActions.ready()) addView(row(
                                button("Confirm type text") { confirmNamedTextBox() },
                                button("Cancel typing") { cancelNamedTextBox() }
                            )) else addView(row(
                                button("Find input box") { inspectNamedTextBox() },
                                button("Cancel typing") { cancelNamedTextBox() }
                            ))
                        }
                        NexaMessageDraftState.peek()?.let { draft ->
                            addView(text("MESSAGE DRAFT · ${draft.appId} · ${draft.recipientHint}",13f))
                            addView(text("Open the correct conversation. Nexa checks its visible title and finds one empty message box. Your message will be inserted only after another tap; it will NEVER be sent by Nexa.",11f,true))
                            if (NexaMessageInputActions.ready()) {
                                addView(row(
                                    button("Confirm fill draft") { confirmMessageInput() },
                                    button("Cancel draft") { cancelMessageInput() }
                                ))
                            } else {
                                addView(row(
                                    button("Find message input") { inspectMessageInput() },
                                    button("Cancel draft") { cancelMessageInput() }
                                ))
                            }
                        }
                        addView(text("Direct device tools (not unrestricted control)",12f,true))
                        val requestedAction=proposedDeviceAction
                        if(requestedAction!=null &&
                            android.os.SystemClock.elapsedRealtime()<proposedDeviceActionExpiresAt) {
                            addView(text("Owner approval needed: "+
                                requestedAction.substringBefore(':').replace('_',' '),13f,true))
                            addView(row(
                                button("Confirm requested action") { confirmOwnerDeviceAction() },
                                button("Cancel requested action") { cancelOwnerDeviceAction() }
                            ))
                        }
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
                        val now=android.os.SystemClock.elapsedRealtime()
                        val expectedGuide=guidedNavigation.expected(now)
                        val awaitingGuideEvidence=guidedNavigation.isAwaitingEvidence(now)
                        if (expectedGuide != null) {
                            if (awaitingGuideEvidence) {
                                addView(text("Android accepted a click request. Waiting for matching screen-change evidence; no next step or retry is permitted.",12f))
                            } else {
                                addView(text("Guided step: find " + expectedGuide +
                                    " · inspect, choose and confirm. No auto-taps.",12f))
                            }
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
                            addView(row(
                                button("Scroll left") { proposeScroll("left") },
                                button("Scroll right") { proposeScroll("right") }
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
                                                (expectedGuide==null || guidedNavigation.canSelect(item.semantic,android.os.SystemClock.elapsedRealtime())))
                                                selectVisibleIcon(item.id)
                                            else {
                                                note = "Read-only control. Calls, messages and destructive actions are blocked."
                                                render()
                                            }
                                        },
                                        button(if (NexaScreenActionPolicy.permits(item) &&
                                            (expectedGuide==null || guidedNavigation.canSelect(item.semantic,android.os.SystemClock.elapsedRealtime())))
                                            "Select" else "Read only") {
                                            if (NexaScreenActionPolicy.permits(item) &&
                                                (expectedGuide==null || guidedNavigation.canSelect(item.semantic,android.os.SystemClock.elapsedRealtime())))
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
        proposedDeviceAction=null
        proposedDeviceActionExpiresAt=0L
        inspectedScreen = null
        NexaScreenActions.reset()
        NexaScreenScroll.cancel()
        guidedNavigation.cancel()
        activeEvidenceToken=null
        super.onDestroy()
    }
}
