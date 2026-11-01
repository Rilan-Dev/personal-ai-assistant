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
    private var recognizer: SpeechRecognizer? = null
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var outputId = ""
    private var turn = 0
    private var active = false
    private val maxTurns = 16
    private val silence = Runnable { finish("No more requests · Hey Nexa is ready") }
    private val maximum = Runnable { finish("Conversation ended · Hey Nexa is ready") }

    fun show(): Boolean {
        if (active) return true
        if (!Settings.canDrawOverlays(service) ||
            service.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(service)) return false
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = service.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        fun action(text: String, run: () -> Unit) = Button(service).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(66, 73, 147))
            setOnClickListener { run() }
        }
        val shell = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(12), px(14), px(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 24, 45))
                cornerRadius = 22f * d
                setStroke(px(1), Color.rgb(94, 107, 160))
            }
            addView(TextView(service).apply {
                text = "✦ Nexa · Voice"
                textSize = 17f
                setTextColor(Color.WHITE)
            })
        }
        status = TextView(service).apply {
            text = "Listening · tell Nexa what to do"
            textSize = 14f
            setTextColor(Color.WHITE)
            minHeight = px(53)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 3
        }.also { shell.addView(it) }
        shell.addView(LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(action("Stop") { finish("Stopped · Hey Nexa ready") },
                LinearLayout.LayoutParams(0, px(50), 1f))
            addView(action("Open Nexa") {
                try {
                    service.startActivity(Intent(service, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                } catch (_: Exception) { }
                finish("Nexa opened")
            }, LinearLayout.LayoutParams(0, px(50), 1f))
        })
        val args = WindowManager.LayoutParams(
            minOf(px(330), service.resources.displayMetrics.widthPixels - px(20)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = px(75)
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
        initSpeech()
        handler.postDelayed({ listen() }, 450L)
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
        status?.text = result.take(300)
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
        status?.text = "Listening · ask your next device command"
        handler.removeCallbacks(silence)
        handler.postDelayed(silence, 18_000L)
        try {
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { handler.post {
                    if (recognizer === r) {
                        handler.removeCallbacks(silence)
                        status?.text = "Hearing your request…"
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
                            status?.text = answer.take(220)
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
        releaseRecognizer()
        outputId = ""
        try { speech?.stop(); speech?.shutdown() } catch (_: Exception) {}
        speech = null
        speechReady = false
        window?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        window = null
        windowManager = null
        status = null
        closed()
    }
}
