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
class NexaAssistantService : VoiceInteractionService()

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
            text = "Audio starts only when you tap Start. Android shows a mic indicator and a Stop notification."
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
        // Exact fixed grammar, no LLM output and no arbitrary app/tool dispatch.
        val command = raw.lowercase(Locale.ROOT).trim()
            .removePrefix("hey nexa ").trim().trimEnd("."[0], "!"[0], "?"[0])
        when (command) {
            "open github", "open github app", "open my github app" -> {
                launchKnownApp("com.github.android")
            }
            "open whatsapp", "open my whatsapp" -> {
                launchKnownApp("com.whatsapp")
            }
            "find github", "find my github app", "find github app" -> {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val pkg = app.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName.orEmpty()
                if (app.packageManager.getLaunchIntentForPackage("com.github.android") == null) {
                    feedback("GitHub is not installed or not visible.")
                } else if (pkg.isBlank() || pkg == "android" || pkg == app.packageName ||
                    !LauncherNavigationState.begin(pkg)) {
                    feedback("Enable Nexa Launcher Navigation in Android Accessibility Settings.")
                } else try {
                    app.startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    hide()
                } catch (_: Exception) {
                    LauncherNavigationState.stop("launch_failed")
                    feedback("Android could not open the home launcher.")
                }
            }
            else -> feedback("Supported commands: Open GitHub, Open WhatsApp, Find my GitHub app. Messages require review in Nexa Tools.")
        }
    }
    private fun launchKnownApp(packageId: String) {
        val target = app.packageManager.getLaunchIntentForPackage(packageId)
        if (target == null) {
            feedback("The requested app is not installed or cannot be opened.")
            return
        }
        try {
            app.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            hide() // Intent dispatch is not proof that the app opened.
        } catch (_: Exception) { feedback("Android rejected the application launch request.") }
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
        handler.post(refresh)
    }
    override fun onHide() {
        cancelDeviceCommand()
        handler.removeCallbacks(refresh)
        super.onHide()
    }
    override fun onDestroy() {
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
        result.putBoolean("wakeWordReady", false)
        p.resolve(result)
    }
    @ReactMethod fun requestAssistantRole(p: Promise) {
        val activity = context.currentActivity
        if (activity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Open Nexa to select it as your assistant")
            return
        }
        val manager = roleManager()
        if (manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) != true) {
            p.resolve("unavailable")
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            p.resolve("already_selected")
            return
        }
        try {
            activity.startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            p.resolve("selection_opened") // Not evidence of a granted role
        } catch (_: Exception) { p.reject("ROLE_REQUEST_FAILED", "Android could not show assistant selection") }
    }
}
