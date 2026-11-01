package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Fallback when the OEM does not bind the selected VoiceInteractionService.
 *
 * Runs INSIDE the user-armed wake-word microphone FGS. It neither starts a
 * background Activity nor starts a second microphone FGS. An explicitly
 * granted TYPE_APPLICATION_OVERLAY shows a compact voice plate and Android's
 * on-device SpeechRecognizer receives consecutive bounded speech turns.
 *
 * No overlay permission -> no window, no recording, and only the truthful
 * notification fallback remains available. This is not a DSP hotword bypass.
 */
internal class NexaWakeFallbackOverlay(
    private val service: Service,
    private val closed: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var window: View? = null
    private var status: TextView? = null
    private var miniStatus: TextView? = null
    private var detailCard: View? = null
    private var expanded = true

    private fun showDetail(visible: Boolean) {
        expanded = visible
        detailCard?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showStatus(value: String) {
        status?.text = value
        miniStatus?.text = value.take(52)
    }
    private var recognizer: SpeechRecognizer? = null
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var outputId = ""
    private var approvalRow: LinearLayout? = null
    private var providerManaged = false
    private var providerEverListening = false
    private var providerStartedAt = 0L
    private val liveMonitor = object : Runnable {
        override fun run() {
            if (!active || !providerManaged) return
            val current = RealtimeVoiceService.status
            if (current == "listening") providerEverListening = true
            val approval = RealtimeVoiceService.pendingApprovalSummary
            approvalRow?.visibility = if (approval != null) View.VISIBLE else View.GONE
            val statusMessage = when {
                approval != null -> approval
                current == "connecting" -> "Connecting to your live AI provider…"
                current == "listening" ->
                    if (RealtimeVoiceService.lastToolResult.isNotBlank())
                        "Tool result · " + RealtimeVoiceService.lastToolResult.take(140)
                    else "Live Nexa is listening · speak naturally"
                else -> RealtimeVoiceService.message.take(180)
            }
            showStatus(statusMessage)
            if (current == "error" || (current == "stopped" &&
                (providerEverListening ||
                    SystemClock.elapsedRealtime() - providerStartedAt > 4500L))) {
                // "Hey Nexa" is solely a wake trigger; never swap the
                // ongoing Sarvam conversation for unrelated command STT.
                showStatus(if (current=="error") RealtimeVoiceService.message
                    else "Live conversation ended · Hey Nexa ready")
                handler.postDelayed({
                    if (active) finish("Live conversation ended · Hey Nexa ready")
                }, 2_200L)
                return
            }
            handler.postDelayed(this, 800L)
        }
    }
    private var turn = 0
    private var active = false
    private val maxTurns = 16
    private val silence = Runnable { finish("No more requests · Hey Nexa is ready") }
    private val maximum = Runnable { finish("Conversation ended · Hey Nexa is ready") }

    fun show(): Boolean {
        if (active) return true
        val providerReady = service.getSharedPreferences("nexa_setup", Context.MODE_PRIVATE)
            .getBoolean("auto_voice_invocation_v1", false) &&
            (try { AgentJournalModule.openVoiceCredentials(service) != null }
                catch (_: Exception) { false })
        // A configured, owner-enabled live voice overlay does not require an
        // Android on-device speech engine; only the offline fallback does.
        if (!Settings.canDrawOverlays(service) ||
            service.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED || !providerReady) return false
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val palette = NexaOverlayTheme.palette(service)
        fun px(v: Int) = NexaOverlayTheme.dp(service, v)
        fun action(text: String, run: () -> Unit) =
            NexaOverlayTheme.action(service, text, palette, run)
        fun quickCommand(command: String) {
            val outcome = try { NexaOverlayCommands.handle(service, command) }
                catch (_: Exception) { "Could not complete this local action" }
            showStatus(outcome.take(220))
        }
        val shell = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(2), px(2), px(2), px(2))
        }
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(13), px(16), px(14))
            background = NexaOverlayTheme.card(service, palette)
            elevation = px(14).toFloat()
            addView(NexaOverlayTheme.label(service, "━━  ✦  NEXA  ·  LIVE ASSISTANT",
                palette, 12f, true))
            addView(NexaOverlayTheme.label(service, "How can I help?", palette, 23f))
        }
        detailCard = panel
        status = NexaOverlayTheme.label(service,
            "Listening · tell Nexa what to do", palette, 14f).apply {
            minHeight = px(65)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 4
            background = NexaOverlayTheme.inset(service, palette)
            setPadding(px(12), px(9), px(12), px(9))
        }.also { panel.addView(it) }
        panel.addView(LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(action("Battery") { quickCommand("battery") },
                LinearLayout.LayoutParams(0, px(48), 1f))
            addView(action("Find GitHub") { quickCommand("find my github app") },
                LinearLayout.LayoutParams(0, px(48), 1f))
            addView(action("Tools") {
                try {
                    service.startService(Intent(service, NexaToolOverlayService::class.java)
                        .setAction(NexaToolOverlayService.ACTION_SHOW))
                    showDetail(false)
                } catch (_: Exception) { showStatus("Android could not open Nexa tools") }
            }, LinearLayout.LayoutParams(0, px(48), 1f))
        })
        panel.addView(NexaOverlayTheme.label(service,
            "Screen access requires consent  ·  Actions need approval",
            palette, 11f, true))
        approvalRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            addView(action("Approve app") {
                try {
                    service.startService(Intent(service, RealtimeVoiceService::class.java)
                        .setAction(RealtimeVoiceService.ACTION_APPROVE_TOOL))
                } catch (_: Exception) { showStatus("Could not approve app action") }
            }, LinearLayout.LayoutParams(0, px(48), 1f))
            addView(action("Deny") {
                try {
                    service.startService(Intent(service, RealtimeVoiceService::class.java)
                        .setAction(RealtimeVoiceService.ACTION_DENY_TOOL))
                } catch (_: Exception) { showStatus("Could not deny app action") }
            }, LinearLayout.LayoutParams(0, px(48), 1f))
        }
        approvalRow?.let { panel.addView(it) }
        shell.addView(panel)
        val dock = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(8), px(8), px(8), px(8))
            background = NexaOverlayTheme.card(service, palette, 30)
            elevation = px(12).toFloat()
            addView(action("＋") { showDetail(!expanded) },
                LinearLayout.LayoutParams(px(51), px(50)))
            addView(NexaOverlayTheme.waveform(service, palette),
                LinearLayout.LayoutParams(0, px(50), 1f))
            addView(action("Stop") { finish("Stopped · Hey Nexa ready") },
                LinearLayout.LayoutParams(px(72), px(50)))
        }
        miniStatus = NexaOverlayTheme.label(service, "Listening", palette, 11f, true)
        shell.addView(dock, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, px(67)).apply {
            topMargin = px(8)
        })
        shell.addView(miniStatus)
        val args = WindowManager.LayoutParams(
            minOf(px(360), service.resources.displayMetrics.widthPixels - px(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = px(24)
            }
        try { wm.addView(shell, args) }
        catch (_: Exception) {
            status = null
            return false
        }
        window = shell
        windowManager = wm
        active = true
        turn = 0
        NexaWakeWordService.assistantShown()
        handler.postDelayed(maximum, 2 * 60 * 1000L)
        handler.postDelayed({
            if (!active) return@postDelayed
            val optedIn = service.getSharedPreferences("nexa_setup", Context.MODE_PRIVATE)
                .getBoolean("auto_voice_invocation_v1", false)
            val configured = try { AgentJournalModule.openVoiceCredentials(service) != null }
                catch (_: Exception) { false }
            if (optedIn && configured && Build.VERSION.SDK_INT >= 26) {
                try {
                    service.startForegroundService(Intent(service,
                        RealtimeVoiceService::class.java).apply {
                        action = RealtimeVoiceService.ACTION_START
                        putExtra(RealtimeVoiceService.EXTRA_ASSISTANT_PANEL, true)
                    })
                    providerManaged = true
                    providerStartedAt = SystemClock.elapsedRealtime()
                    showStatus("Connecting to live AI · tap Stop at any time")
                    handler.postDelayed(liveMonitor, 800L)
                } catch (_: Exception) {
                    showStatus("Android blocked live microphone · open Nexa Voice to retry")
                    handler.postDelayed({ if(active) finish("Voice could not start") }, 2400L)
                }
            } else {
                showStatus("Configure a live voice provider and enable invocation in Nexa Setup")
                handler.postDelayed({ if(active) finish("Voice not configured") }, 2400L)
            }
        }, 550L)
        return true
    }

    private fun initSpeech() {
        if (speech != null) return
        speech = TextToSpeech(service) { result ->
            handler.post {
                speechReady = result == TextToSpeech.SUCCESS
                if (speechReady) {
                    try { speech?.language = Locale.getDefault() } catch (_: Exception) {}
                }
            }
        }
        speech?.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) { speechDone(utteranceId) }
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String) { speechDone(utteranceId) }
        })
    }

    private fun speechDone(id: String) {
        handler.post {
            if (active && id == outputId) {
                outputId = ""
                handler.postDelayed({ listen() }, 350L)
            }
        }
    }

    private fun speak(result: String) {
        if (!active) return
        handler.removeCallbacks(silence)
        showStatus(result.take(300))
        if (turn >= maxTurns) {
            handler.postDelayed({ finish("Conversation limit reached · Hey Nexa ready") }, 3500L)
            return
        }
        if (speechReady && speech != null) {
            outputId = "nexa-fallback-$turn"
            val spoken = try {
                speech?.speak(result.take(200), TextToSpeech.QUEUE_FLUSH, null, outputId)
            } catch (_: Exception) { TextToSpeech.ERROR }
            if (spoken == TextToSpeech.SUCCESS) {
                val id = outputId
                handler.postDelayed({ if (active && outputId == id) speechDone(id) }, 8_000L)
                return
            }
            outputId = ""
        }
        handler.postDelayed({ listen() }, 1200L)
    }

    private fun listen() {
        if (!active || recognizer != null || outputId.isNotEmpty()) return
        val r = try { SpeechRecognizer.createOnDeviceSpeechRecognizer(service) }
        catch (_: Exception) {
            finish("On-device speech unavailable · Hey Nexa ready")
            return
        }
        recognizer = r
        showStatus("Listening · ask your next device command")
        handler.removeCallbacks(silence)
        handler.postDelayed(silence, 18_000L)
        try {
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { handler.post {
                    if (recognizer === r) {
                        handler.removeCallbacks(silence)
                        showStatus("Hearing your request…")
                    }
                } }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { handler.post {
                    if (recognizer === r) {
                        releaseRecognizer()
                        finish(if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                            "No request · Hey Nexa ready" else
                            "Speech engine unavailable · Hey Nexa ready")
                    }
                } }
                override fun onResults(results: Bundle?) {
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    handler.post {
                        if (recognizer !== r) return@post
                        releaseRecognizer()
                        handler.removeCallbacks(silence)
                        if (phrase.isBlank() || phrase.lowercase().trim() in setOf(
                                "stop", "goodbye", "stop listening", "end conversation")) {
                            finish("Done · Hey Nexa ready")
                            return@post
                        }
                        turn++
                        val answer = try { NexaOverlayCommands.handle(service, phrase) }
                        catch (_: Exception) { "Unable to execute this command." }
                        if (answer.startsWith("Launcher search started") ||
                            answer.contains("launch requested") ||
                            answer.startsWith("Android settings opened") ||
                            answer.startsWith("WhatsApp review opened")) {
                            showStatus(answer.take(220))
                            handler.postDelayed({ finish("Action dispatched · return to Hey Nexa") }, 2000L)
                        } else speak(answer)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) {
            releaseRecognizer()
            finish("Speech recognition unavailable · Hey Nexa ready")
        }
    }

    private fun releaseRecognizer() {
        val r = recognizer
        recognizer = null
        try { r?.cancel(); r?.destroy() } catch (_: Exception) {}
    }

    fun finish(message: String) {
        if (!active) return
        active = false
        handler.removeCallbacks(silence)
        handler.removeCallbacks(maximum)
        handler.removeCallbacks(liveMonitor)
        if (providerManaged && RealtimeVoiceService.status in setOf("connecting", "listening")) {
            try {
                service.startService(Intent(service, RealtimeVoiceService::class.java)
                    .setAction(RealtimeVoiceService.ACTION_STOP))
            } catch (_: Exception) {}
        }
        providerManaged = false
        providerEverListening = false
        approvalRow = null
        releaseRecognizer()
        outputId = ""
        try { speech?.stop(); speech?.shutdown() } catch (_: Exception) {}
        speech = null
        speechReady = false
        window?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        window = null
        windowManager = null
        status = null
        miniStatus = null
        detailCard = null
        closed()
    }
}
