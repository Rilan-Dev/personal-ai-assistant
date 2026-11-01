package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
    private var commandRecognizer: SpeechRecognizer? = null
    private var commandBusy = false
    private var commandFeedback: String? = null
    private var autoDismissAfterCommand = false
    private val autoHide = Runnable {
        if (autoDismissAfterCommand && !commandBusy) hide()
    }
    private var feedbackUntil = 0L
    private val refresh = object : Runnable {
        override fun run() {
            label?.text = if (commandBusy) "Listening for one device command…" else if
                (commandFeedback != null && android.os.SystemClock.elapsedRealtime() < feedbackUntil)
                commandFeedback else when (RealtimeVoiceService.status) {
                "listening" -> if (RealtimeVoiceService.microphoneMuted)
                    "Nexa voice active · microphone muted" else "Nexa voice active · microphone in use"
                "connecting" -> "Connecting to the selected voice provider…"
                "error" -> RealtimeVoiceService.message
                else -> "Nexa ready · tap Start to talk"
            }
            if (label != null) handler.postDelayed(this, 750L)
        }
    }
    private fun button(text: String, action: () -> Unit): Button = Button(app).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }
    override fun onCreateContentView(): View {
        val density = app.resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val panel = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.rgb(21, 26, 46))
                cornerRadius = 24 * density
            }
        }
        panel.addView(TextView(app).apply {
            text = "✦  Nexa AI"
            textSize = 20f
            setTextColor(Color.WHITE)
        })
        label = TextView(app).also {
            it.setTextColor(Color.WHITE)
            it.textSize = 15f
            it.setPadding(0, pad / 2, 0, pad / 2)
            panel.addView(it)
        }
        panel.addView(TextView(app).apply {
            text = "Tap Speak, or Talk in the Nexa overlay, for one on-device command. Live provider audio starts separately with Start live voice."
            setTextColor(Color.LTGRAY)
        })
        panel.addView(button("Speak a device command") { listenOneDeviceCommand() })
        panel.addView(button("Cancel spoken command") { cancelDeviceCommand() })
        panel.addView(button("Start live voice") { startOwnerVoice() })
        panel.addView(button("Stop live voice") {
            if (RealtimeVoiceService.status == "connecting" || RealtimeVoiceService.status == "listening") {
                try {
                    app.startService(Intent(app, RealtimeVoiceService::class.java).apply {
                        action = RealtimeVoiceService.ACTION_STOP
                    })
                } catch (_: Exception) { label?.text = "Stop failed · use the notification Stop control" }
            }
        })
        panel.addView(button("Open Nexa") {
            try {
                app.startActivity(Intent(app, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                hide()
            } catch (_: Exception) { label?.text = "Unable to open Nexa" }
        })
        panel.addView(button("Dismiss") { hide() })
        return panel
    }
    private fun feedback(message: String) {
        commandFeedback = message
        feedbackUntil = android.os.SystemClock.elapsedRealtime() + 6500L
        label?.text = message
        if (autoDismissAfterCommand) {
            handler.removeCallbacks(autoHide)
            handler.postDelayed(autoHide, 6500L)
        }
    }
    private fun cancelDeviceCommand() {
        val recognizer = commandRecognizer
        commandRecognizer = null
        commandBusy = false
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
    }
    private fun listenOneDeviceCommand() {
        if (commandBusy || RealtimeVoiceService.status == "listening" ||
            RealtimeVoiceService.status == "connecting") {
            feedback("Stop live voice before requesting a separate device command.")
            return
        }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            feedback("Open Nexa to grant microphone permission first.")
            return
        }
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(app)) {
            feedback("No on-device speech recognizer is installed on this phone.")
            return
        }
        try {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
            commandRecognizer = recognizer
            commandBusy = true
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    handler.post {
                        if (commandRecognizer === recognizer) {
                            cancelDeviceCommand()
                            feedback("On-device voice command was not recognized.")
                        }
                    }
                }
                override fun onResults(results: Bundle?) {
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    handler.post {
                        if (commandRecognizer === recognizer) {
                            cancelDeviceCommand()
                            applyDeviceCommand(phrase)
                        }
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
                    feedback("Voice command timed out.")
                }
            }, 20000L)
        } catch (_: Exception) {
            cancelDeviceCommand()
            feedback("Android could not start the on-device recognizer.")
        }
    }
    private fun applyDeviceCommand(raw: String) {
        // One deterministic command gate for typed and spoken tool requests.
        // No AI-generated coordinates or unreviewed external message sends.
        val command = raw.lowercase(Locale.ROOT).trim()
            .removePrefix("hey nexa ").trim()
            .trimEnd('.', '!', '?')
        val result = NexaOverlayCommands.handle(app, command)
        feedback(result)
        if (result.startsWith("Launcher search started") ||
            result.contains("launch requested") ||
            result.startsWith("Android settings opened")) {
            // An Intent request is NOT evidence of a completed action.
            hide()
        }
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
            label?.text = "Start requested; awaiting provider connection."
        } catch (_: Exception) { label?.text = "Android blocked startup. Open Nexa to retry." }
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(autoHide)
        handler.post(refresh)
        autoDismissAfterCommand = args?.getBoolean("nexa_hotword") == true ||
            args?.getBoolean("nexa_overlay_tap") == true
        if (autoDismissAfterCommand) {
            // Wake detector has released AudioRecord before showing this UI.
            // Only a bounded, on-device, one-shot recognizer is started.
            handler.postDelayed({ if (!commandBusy) listenOneDeviceCommand() }, 420L)
        }
    }
    override fun onHide() {
        autoDismissAfterCommand = false
        handler.removeCallbacks(autoHide)
        NexaWakeWordService.assistantClosed()
        cancelDeviceCommand()
        handler.removeCallbacks(refresh)
        super.onHide()
    }
    override fun onDestroy() {
        autoDismissAfterCommand = false
        handler.removeCallbacks(autoHide)
        NexaWakeWordService.assistantClosed()
        cancelDeviceCommand()
        handler.removeCallbacks(refresh)
        label = null
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
