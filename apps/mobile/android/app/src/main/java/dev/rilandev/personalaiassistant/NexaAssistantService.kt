package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.facebook.react.common.LifecycleState
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/** System-selected entry point. Never opens microphone on its own. */
class NexaAssistantService : VoiceInteractionService() {
    companion object {
        @Volatile private var current: NexaAssistantService? = null
        fun showFromWakeWord(): Boolean = requestSession("nexa_hotword")

        // Only the owner's direct tap on the small floating overlay invokes this.
        fun showFromOwnerTap(): Boolean = requestSession("nexa_overlay_tap")

        private fun requestSession(trigger: String): Boolean {
            val service = current ?: return false
            return try {
                service.handler.post {
                    try {
                        service.showSession(Bundle().apply { putBoolean(trigger, true) }, 0)
                    } catch (_: Exception) {}
                }
                true // Dispatch requested; OEM display success not yet observed
            } catch (_: Exception) { false }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    override fun onReady() {
        super.onReady()
        current = this
        // Android explicitly exempts the currently active system-selected
        // VoiceInteractionService from some background microphone-FGS limits.
        // This is a best-effort restore for an ALREADY opted-in assistant,
        // never a new permission grant or a silent recorder. OEM may reject it.
        if (NexaWakeWordService.enabled(this) && NexaWakeWordModule.modelReady(this) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            try {
                val start = Intent(this, NexaWakeWordService::class.java)
                    .setAction(NexaWakeWordService.START)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(start)
                else startService(start)
            } catch (_: Exception) {
                // Android/OEM is authoritative; boot recovery notification
                // and the visible-app resume path remain available.
            }
        }
    }
    override fun onShowSessionFailed(args: Bundle) {
        NexaWakeWordService.assistantClosed()
        super.onShowSessionFailed(args)
    }
    override fun onShutdown() {
        if (current === this) current = null
        super.onShutdown()
    }
}

/** A compact system assistant session, not an app-wide overlay. */
class NexaVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = NexaVoiceSession(this)
}

internal class NexaVoiceSession(private val app: Context) : VoiceInteractionSession(app) {
    private val handler = Handler(Looper.getMainLooper())
    private var label: TextView? = null
    private var approvalRow: LinearLayout? = null
    private var commandRecognizer: SpeechRecognizer? = null
    private var commandBusy = false
    private var commandFeedback: String? = null
    private var autoDismissAfterCommand = false
    // SpeechRecognizer is one-turn by design. Keep the *assistant session*
    // open and restart a new recognition turn only after the spoken result.
    private var localConversationActive = false
    private var localTurnCount = 0
    private var localTurnGeneration = 0
    private var silenceDeadlineMs = 0L
    private var outputSpeech: TextToSpeech? = null
    private var outputSpeechReady = false
    private var outputUtterance = ""
    private var liveEverActive = false
    private val silenceWindowMs = 18_000L
    private val silenceTimer = Runnable {
        if (localConversationActive) {
            localConversationActive = false
            label?.text = "No more requests · returning to Hey Nexa"
            handler.postDelayed({ hide() }, 350L)
        }
    }
    private var liveVoiceLaunchedFromSession = false
    private val autoHide = Runnable {
        if (autoDismissAfterCommand && !localConversationActive && !commandBusy) hide()
    }
    private var feedbackUntil = 0L
    private val refresh = object : Runnable {
        override fun run() {
            approvalRow?.visibility =
                if (RealtimeVoiceService.pendingApprovalSummary != null) View.VISIBLE else View.GONE
            label?.text = if (RealtimeVoiceService.pendingApprovalSummary != null)
                RealtimeVoiceService.pendingApprovalSummary else if (commandBusy) "Listening · tell me what to do…" else if
                (commandFeedback != null && SystemClock.elapsedRealtime() < feedbackUntil)
                commandFeedback else if (localConversationActive)
                "Nexa ready for your next command" else when (RealtimeVoiceService.status) {
                "listening" -> if (RealtimeVoiceService.microphoneMuted)
                    "Nexa voice active · microphone muted" else "Nexa voice active · microphone in use"
                "connecting" -> "Connecting to the selected voice provider…"
                "error" -> RealtimeVoiceService.message
                else -> "Nexa ready"
            }
            if (liveVoiceLaunchedFromSession) {
                if (RealtimeVoiceService.status == "listening") liveEverActive = true
                if (liveEverActive && (RealtimeVoiceService.status == "stopped" ||
                    RealtimeVoiceService.status == "error")) {
                    handler.post { hide() }
                }
            }
            if (label != null) handler.postDelayed(this, 750L)
        }
    }
    private fun button(text: String, action: () -> Unit): Button = Button(app).apply {
        this.text = text
        textSize = 13f
        isAllCaps = false
        minHeight = (48 * app.resources.displayMetrics.density).toInt()
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(66, 73, 147))
        setOnClickListener { action() }
    }
    private fun row(vararg children: Button): LinearLayout =
        LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            children.forEach { child ->
                addView(child, LinearLayout.LayoutParams(0,
                    (50 * app.resources.displayMetrics.density).toInt(), 1f))
            }
        }
    override fun onCreateContentView(): View {
        val d = app.resources.displayMetrics.density
        val pad = (14 * d).toInt()
        val panel = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 24, 45))
                cornerRadius = 22 * d
                setStroke((1*d).toInt(), Color.rgb(85, 92, 151))
            }
            addView(TextView(app).apply {
                text = "✦  Nexa AI  ·  Voice"
                textSize = 17f
                setTextColor(Color.WHITE)
            })
        }
        label = TextView(app).also {
            it.setTextColor(Color.WHITE)
            it.textSize = 14f
            it.setPadding(0, pad, 0, pad)
            it.maxLines = 3
            panel.addView(it)
        }
        panel.addView(row(
            button("🎙 Talk") { if (localConversationActive) listenOneDeviceCommand()
                else if (RealtimeVoiceService.status == "stopped") beginLocalConversation() },
            button("Stop / Dismiss") { stopConversation() }
        ))
        panel.addView(row(
            button("Live AI") { startOwnerVoice() },
            button("Open Nexa") {
                try {
                    app.startActivity(Intent(app, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    hide()
                } catch (_: Exception) { label?.text = "Unable to open Nexa" }
            }
        ))
        approvalRow = row(
            button("Approve app") {
                try {
                    app.startService(Intent(app, RealtimeVoiceService::class.java).apply {
                        action = RealtimeVoiceService.ACTION_APPROVE_TOOL
                    })
                } catch (_: Exception) { feedback("Android blocked action approval") }
            },
            button("Deny") {
                try {
                    app.startService(Intent(app, RealtimeVoiceService::class.java).apply {
                        action = RealtimeVoiceService.ACTION_DENY_TOOL
                    })
                } catch (_: Exception) { feedback("Could not reject pending action") }
            }
        )
        approvalRow?.visibility = View.GONE
        approvalRow?.let { panel.addView(it) }
        return panel
    }

    private fun configureVoicePlate() {
        try {
            val window = getWindow().window ?: return
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT)
        } catch (_: Exception) {
            // Some OEMs reject changing system-owned assistant window decor.
        }
    }

    private fun feedback(message: String) {
        commandFeedback = message.take(360)
        feedbackUntil = SystemClock.elapsedRealtime() + 6500L
        label?.text = commandFeedback
        if (autoDismissAfterCommand && !localConversationActive) {
            handler.removeCallbacks(autoHide)
            handler.postDelayed(autoHide, 6500L)
        }
    }

    private fun initSpeechOutput() {
        if (outputSpeech != null) return
        outputSpeech = TextToSpeech(app) { status ->
            handler.post {
                outputSpeechReady = status == TextToSpeech.SUCCESS
                if (outputSpeechReady) {
                    try { outputSpeech?.language = Locale.getDefault() } catch (_: Exception) {}
                }
            }
        }
        outputSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) { speechFinished(utteranceId) }
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String) { speechFinished(utteranceId) }
        })
    }

    private fun speechFinished(id: String) {
        handler.post {
            if (localConversationActive && outputUtterance == id) {
                outputUtterance = ""
                handler.postDelayed({ listenOneDeviceCommand() }, 400L)
            }
        }
    }

    private fun replyAndListen(message: String) {
        feedback(message)
        handler.removeCallbacks(silenceTimer)
        if (!localConversationActive) return
        if (localTurnCount >= 16) {
            endLocalConversation("Conversation limit reached · say Hey Nexa to start again")
            return
        }
        // Speak only a bounded tool result. TTS cannot drive or approve tools.
        val speech = outputSpeech
        if (outputSpeechReady && speech != null) {
            val id = "nexa-local-" + (++localTurnGeneration)
            outputUtterance = id
            val result = try {
                speech.speak(message.take(240), TextToSpeech.QUEUE_FLUSH, null, id)
            } catch (_: Exception) { TextToSpeech.ERROR }
            if (result == TextToSpeech.SUCCESS) {
                handler.postDelayed({
                    if (localConversationActive && outputUtterance == id)
                        speechFinished(id)
                }, 8_000L)
                return
            }
            outputUtterance = ""
        }
        // A missing/offline TTS engine must not deadlock recognition.
        handler.postDelayed({ if (localConversationActive) listenOneDeviceCommand() }, 850L)
    }

    private fun cancelDeviceCommand() {
        val recognizer = commandRecognizer
        commandRecognizer = null
        commandBusy = false
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
    }

    private fun endLocalConversation(message: String) {
        if (!localConversationActive && !commandBusy) return
        localConversationActive = false
        handler.removeCallbacks(silenceTimer)
        cancelDeviceCommand()
        outputSpeech?.stop()
        outputUtterance = ""
        feedback(message)
        handler.postDelayed({ hide() }, 650L)
    }

    private fun stopConversation() {
        localConversationActive = false
        handler.removeCallbacks(silenceTimer)
        cancelDeviceCommand()
        outputSpeech?.stop()
        outputUtterance = ""
        if (liveVoiceLaunchedFromSession) {
            try {
                app.startService(Intent(app, RealtimeVoiceService::class.java).apply {
                    action = RealtimeVoiceService.ACTION_STOP
                })
            } catch (_: Exception) {}
        }
        hide()
    }

    private fun beginLocalConversation() {
        if (localConversationActive || commandBusy) return
        if (RealtimeVoiceService.status == "listening" ||
            RealtimeVoiceService.status == "connecting") {
            feedback("Live voice is already active. Stop it before starting local commands.")
            return
        }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            feedback("Microphone permission needed · open Nexa Setup")
            return
        }
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(app)) {
            feedback("On-device speech recognition is not installed. Configure live AI in Nexa.")
            return
        }
        localConversationActive = true
        autoDismissAfterCommand = false
        localTurnCount = 0
        commandFeedback = null
        initSpeechOutput()
        listenOneDeviceCommand()
    }

    private fun listenOneDeviceCommand() {
        if (!localConversationActive) {
            beginLocalConversation()
            return
        }
        if (commandBusy || outputUtterance.isNotEmpty()) return
        if (RealtimeVoiceService.status == "listening" ||
            RealtimeVoiceService.status == "connecting") {
            endLocalConversation("Stopped local commands · live voice has the microphone")
            return
        }
        // Each recognition invocation is one Android speech turn, but the
        // Nexa voice session and permitted tool loop remain open.
        val recognizer: SpeechRecognizer
        try { recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(app) }
        catch (_: Exception) {
            endLocalConversation("Android on-device speech recognizer unavailable")
            return
        }
        commandRecognizer = recognizer
        commandBusy = true
        commandFeedback = null
        silenceDeadlineMs = SystemClock.elapsedRealtime() + silenceWindowMs
        handler.removeCallbacks(silenceTimer)
        handler.postDelayed(silenceTimer, silenceWindowMs)
        label?.text = "Listening · tell Nexa your next request"
        try {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    handler.post { if (commandRecognizer === recognizer)
                        label?.text = "Listening · speak naturally" }
                }
                override fun onBeginningOfSpeech() {
                    handler.post { if (commandRecognizer === recognizer) {
                        handler.removeCallbacks(silenceTimer)
                        label?.text = "Hearing your request…"
                    } }
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    handler.post {
                        if (commandRecognizer !== recognizer) return@post
                        cancelDeviceCommand()
                        if (!localConversationActive) return@post
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            // Silence means close and rearm the offline wake
                            // detector; do not run an endless mic loop.
                            endLocalConversation("No further request · Hey Nexa is ready")
                        } else {
                            endLocalConversation("Speech recognition stopped · try Hey Nexa again")
                        }
                    }
                }
                override fun onResults(results: Bundle?) {
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    handler.post {
                        if (commandRecognizer !== recognizer) return@post
                        cancelDeviceCommand()
                        handler.removeCallbacks(silenceTimer)
                        if (!localConversationActive) return@post
                        if (phrase.isBlank()) {
                            endLocalConversation("No request heard · Hey Nexa is ready")
                        } else applyDeviceCommand(phrase)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
            handler.postDelayed({
                if (commandRecognizer === recognizer) {
                    cancelDeviceCommand()
                    endLocalConversation("No further request · Hey Nexa is ready")
                }
            }, 20000L)
        } catch (_: Exception) {
            cancelDeviceCommand()
            endLocalConversation("Android rejected speech recognition · retry Hey Nexa")
        }
    }

    private fun applyDeviceCommand(raw: String) {
        val command = raw.lowercase(Locale.ROOT).trim()
            .removePrefix("hey nexa ").trim().trimEnd('.', '!', '?')
        if (command in setOf("stop", "stop listening", "that's all", "goodbye",
                "thank you goodbye", "cancel", "end conversation")) {
            endLocalConversation("Conversation ended · Hey Nexa is ready")
            return
        }
        localTurnCount++
        // Native spoken and overlay typed requests share the same restricted
        // tool gateway. General LLM-generated actions never bypass approval.
        val result = NexaOverlayCommands.handle(app, command)
        if (result.startsWith("Launcher search started") ||
            result.contains("launch requested") ||
            result.startsWith("Android settings opened") ||
            result.startsWith("WhatsApp review opened")) {
            localConversationActive = false
            feedback(result)
            handler.postDelayed({ hide() }, 900L)
        } else replyAndListen(result)
    }

    private fun startOwnerVoice() {
        if (RealtimeVoiceService.status == "listening" || RealtimeVoiceService.status == "connecting") {
            label?.text = "Voice already active. Use Stop to end it."
            return
        }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            label?.text = "Open Nexa and grant microphone permission first."
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            label?.text = "Open Nexa and grant notification permission first."
            return
        }
        if (try { AgentJournalModule.openVoiceCredentials(app) } catch (_: Exception) { null } == null) {
            label?.text = "Configure a live voice provider in Nexa Settings."
            return
        }
        try {
            app.startForegroundService(Intent(app, RealtimeVoiceService::class.java).apply {
                action = RealtimeVoiceService.ACTION_START
            })
            liveVoiceLaunchedFromSession = true
            label?.text = "Connecting to live AI · microphone access visible · Stop to end"
        } catch (_: Exception) { label?.text = "Android blocked startup. Open Nexa to retry." }
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Coordinate microphone ownership even for a system-assistant gesture,
        // not only for a recognized wake phrase or floating-overlay tap.
        NexaWakeWordService.assistantShown()
        configureVoicePlate()
        localConversationActive = false
        liveEverActive = false
        outputUtterance = ""
        approvalRow?.visibility = View.GONE
        handler.removeCallbacks(silenceTimer)
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(autoHide)
        handler.post(refresh)
        // Only a deliberate, visible Setup opt-in enables provider audio from a
        // system assistant invocation. Legacy installs default to local recognition.
        // Neither the role service nor this preference opens an idle microphone.
        cancelDeviceCommand()
        val saved = app.getSharedPreferences("nexa_setup", Context.MODE_PRIVATE)
            .getBoolean("auto_voice_invocation_v1", false)
        val hasCredentials = try {
            AgentJournalModule.openVoiceCredentials(app) != null
        } catch (_: Exception) { false }
        val automaticLiveVoice = saved && hasCredentials
        autoDismissAfterCommand = false
        // Preserve which explicit activation path brought up the session for
        // accessibility, while the on-device recognizer behavior is identical.
        val activationSource = when {
            args?.getBoolean("nexa_hotword") == true -> "Hey Nexa"
            args?.getBoolean("nexa_overlay_tap") == true -> "Nexa overlay"
            else -> "Android assistant gesture"
        }
        label?.contentDescription = if (automaticLiveVoice)
            "$activationSource: live AI conversation with configured provider" else
            "$activationSource: continuous on-device command conversation"
        commandFeedback = null
        feedbackUntil = 0L
        handler.postDelayed({
            if (!commandBusy && label != null) {
                if (automaticLiveVoice) startOwnerVoice()
                else beginLocalConversation()
            }
        }, 420L)
    }
    override fun onHide() {
        autoDismissAfterCommand = false
        localConversationActive = false
        handler.removeCallbacks(silenceTimer)
        handler.removeCallbacks(autoHide)
        outputSpeech?.stop()
        outputSpeech?.shutdown()
        outputSpeech = null
        outputSpeechReady = false
        outputUtterance = ""
        // A session-invoked conversation must not keep streaming after its
        // assistant window is dismissed. App-started sessions are unaffected.
        if (liveVoiceLaunchedFromSession) {
            liveVoiceLaunchedFromSession = false
            try {
                app.startService(Intent(app, RealtimeVoiceService::class.java).apply {
                    action = RealtimeVoiceService.ACTION_STOP
                })
            } catch (_: Exception) {}
        }
        NexaWakeWordService.assistantClosed()
        cancelDeviceCommand()
        handler.removeCallbacks(refresh)
        super.onHide()
    }
    override fun onDestroy() {
        localConversationActive = false
        handler.removeCallbacks(silenceTimer)
        outputSpeech?.stop()
        outputSpeech?.shutdown()
        outputSpeech = null
        outputUtterance = ""
        if (liveVoiceLaunchedFromSession) {
            liveVoiceLaunchedFromSession = false
            try {
                app.startService(Intent(app, RealtimeVoiceService::class.java).apply {
                    action = RealtimeVoiceService.ACTION_STOP
                })
            } catch (_: Exception) {}
        }
        autoDismissAfterCommand = false
        handler.removeCallbacks(autoHide)
        NexaWakeWordService.assistantClosed()
        cancelDeviceCommand()
        handler.removeCallbacks(refresh)
        label = null
        approvalRow = null
        super.onDestroy()
    }
}

class NexaAssistantRoleModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName(): String = "PAANexaAssistantRole"
    private fun roleManager(): RoleManager? = if (Build.VERSION.SDK_INT >= 29)
        context.getSystemService(RoleManager::class.java) else null
    @ReactMethod fun getAssistantRoleStatus(p: Promise) {
        val manager = roleManager()
        val result = Arguments.createMap()
        result.putBoolean("available", manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true)
        result.putBoolean("selected", manager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true)
        result.putBoolean("wakeWordReady", NexaWakeWordModule.modelReady(context))
        p.resolve(result)
    }
    private fun canPresent(): Boolean {
        val activity = context.currentActivity
        return activity != null && !activity.isFinishing &&
            context.lifecycleState == LifecycleState.RESUMED
    }
    private fun openDefaultAssistantSettings(p: Promise) {
        val activity = context.currentActivity
        if (!canPresent() || activity == null) {
            p.reject("NOT_FOREGROUND", "Open Nexa to change the Android default assistant")
            return
        }
        // OEMs may omit the role dialog; attempt public Settings actions.
        val intents = listOf(Settings.ACTION_VOICE_INPUT_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_SETTINGS)
        for (action in intents) {
            try {
                activity.startActivity(Intent(action))
                p.resolve("settings_opened")  // Never claims the role was granted.
                return
            } catch (_: Exception) { }
        }
        p.reject("ASSISTANT_SETTINGS_UNAVAILABLE",
            "Android does not expose default assistant settings")
    }
    @ReactMethod fun openAssistantSettings(p: Promise) = openDefaultAssistantSettings(p)
    @ReactMethod fun requestAssistantRole(p: Promise) {
        val activity = context.currentActivity
        if (!canPresent() || activity == null) {
            p.reject("NOT_FOREGROUND", "Open Nexa to select it as your assistant")
            return
        }
        val manager = roleManager()
        if (manager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true) {
            p.resolve("already_selected")
            return
        }
        if (manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) != true) {
            openDefaultAssistantSettings(p)
            return
        }
        try {
            activity.startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            p.resolve("selection_opened")
        } catch (_: Exception) {
            openDefaultAssistantSettings(p)
        }
    }

}
