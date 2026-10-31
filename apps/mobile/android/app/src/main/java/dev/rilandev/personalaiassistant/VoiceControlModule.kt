package dev.rilandev.personalaiassistant

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.Locale
import java.util.UUID

/**
 * Explicit push-to-talk only. On-device STT, no cloud fallback, no recording
 * files, no always-on audio service, and no automatic command execution.
 * SpeechRecognizer and TTS engines must live on the main Android thread.
 */
class VoiceControlModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context), LifecycleEventListener {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recognition: Promise? = null
    private var recognitionTimeout: Runnable? = null
    private var tts: TextToSpeech? = null
    private var utterance: Promise? = null
    private var pendingText: String? = null
    private var pendingLanguage: String? = null

    init { context.addLifecycleEventListener(this) }
    override fun getName(): String = "PAAVoiceControl"

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    @ReactMethod fun getCapabilities(promise: Promise) {
        main.post {
            try {
                val value = Arguments.createMap()
                value.putBoolean("onDeviceRecognition", onDeviceAvailable())
                value.putBoolean("micGranted",
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                value.putBoolean("offlineOnly", true)
                value.putBoolean("wakeWord", false)
                value.putInt("androidApi", Build.VERSION.SDK_INT)
                promise.resolve(value)
            } catch (_: Exception) {
                promise.reject("VOICE_UNAVAILABLE", "Speech capabilities unavailable")
            }
        }
    }

    private fun releaseRecognizer() {
        recognitionTimeout?.let { main.removeCallbacks(it) }
        recognitionTimeout = null
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }
    private fun rejectRecognition(code: String, message: String) {
        val current = recognition
        recognition = null
        releaseRecognizer()
        current?.reject(code, message)
    }
    private fun finishRecognition(transcript: String) {
        val current = recognition
        recognition = null
        releaseRecognizer()
        if (transcript.isBlank()) current?.reject("EMPTY_TRANSCRIPT", "No speech recognized")
        else current?.resolve(transcript.take(2000))
    }
    @ReactMethod fun listenOnce(languageTag: String, promise: Promise) {
        main.post {
            if (recognition != null) {
                promise.reject("VOICE_BUSY", "Speech recognition is already active")
                return@post
            }
            if (currentActivity?.hasWindowFocus() != true) {
                promise.reject("NOT_FOREGROUND", "Open this app to use the microphone")
                return@post
            }
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                promise.reject("MIC_PERMISSION_REQUIRED", "Microphone permission not granted")
                return@post
            }
            if (!onDeviceAvailable()) {
                promise.reject("OFFLINE_STT_UNAVAILABLE", "No on-device recognizer is installed on this Android device")
                return@post
            }
            try {
                val tag = languageTag.ifBlank { Locale.getDefault().toLanguageTag() }
                require(tag.length <= 35 && Locale.forLanguageTag(tag).language.isNotEmpty())
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                recognizer = engine
                recognition = promise
                engine.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        main.post { if (recognition === promise) rejectRecognition("STT_ERROR", "Speech recognition failed (code $error)") }
                    }
                    override fun onResults(results: Bundle?) {
                        val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        main.post { if (recognition === promise) finishRecognition(value) }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                recognitionTimeout = Runnable {
                    if (recognition === promise) rejectRecognition("STT_TIMEOUT", "Speech recognition timed out")
                }.also { main.postDelayed(it, 25000L) }
                engine.startListening(intent)
            } catch (_: Exception) {
                if (recognition === promise) {
                    rejectRecognition("STT_START_FAILED", "Could not start on-device recognition")
                } else {
                    releaseRecognizer()
                    promise.reject("STT_START_FAILED", "Could not start on-device recognition")
                }
            }
        }
    }
    @ReactMethod fun cancelListening(promise: Promise) {
        main.post {
            if (recognition != null) rejectRecognition("STT_CANCELLED", "Speech input cancelled")
            else releaseRecognizer()
            promise.resolve(true)
        }
    }

    private fun resolveUtterance() {
        val current = utterance
        utterance = null
        pendingText = null
        pendingLanguage = null
        current?.resolve(true)
    }
    private fun rejectUtterance(code: String, description: String) {
        val current = utterance
        utterance = null
        pendingText = null
        pendingLanguage = null
        current?.reject(code, description)
    }
    private fun speakWhenInitialized(engine: TextToSpeech) {
        val text = pendingText ?: return
        val tag = pendingLanguage ?: return
        try {
            val language = Locale.forLanguageTag(tag)
            // Restrict to voices explicitly marked as not requiring network access.
            val voices = engine.voices.orEmpty()
            val selected = voices.firstOrNull {
                !it.isNetworkConnectionRequired && it.locale == language
            } ?: voices.firstOrNull {
                !it.isNetworkConnectionRequired &&
                    it.locale.language == language.language &&
                    it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            if (selected == null || selected.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) {
                rejectUtterance("OFFLINE_TTS_UNAVAILABLE", "No installed offline voice for selected language")
                return
            }
            if (engine.setVoice(selected) != TextToSpeech.SUCCESS) {
                rejectUtterance("OFFLINE_TTS_UNAVAILABLE", "Could not select an offline voice")
                return
            }
            val utteranceId = UUID.randomUUID().toString()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) main.post { resolveUtterance() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) main.post { rejectUtterance("TTS_FAILED", "Speech playback failed") }
                }
                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) main.post { rejectUtterance("TTS_FAILED", "Speech playback failed") }
                }
            })
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
                rejectUtterance("TTS_FAILED", "Unable to play speech")
            }
        } catch (_: Exception) {
            rejectUtterance("TTS_FAILED", "Unable to prepare offline speech")
        }
    }

    @ReactMethod fun speakOffline(text: String, languageTag: String, promise: Promise) {
        main.post {
            if (utterance != null) {
                promise.reject("TTS_BUSY", "Speech playback already active")
                return@post
            }
            if (currentActivity?.hasWindowFocus() != true) {
                promise.reject("NOT_FOREGROUND", "Speech playback is available in the open app only")
                return@post
            }
            val language = languageTag.ifBlank { Locale.getDefault().toLanguageTag() }
            if (text.isBlank() || text.length > 1000 ||
                language.length > 35 || Locale.forLanguageTag(language).language.isEmpty()) {
                promise.reject("INVALID_TTS_INPUT", "Invalid speech text or language")
                return@post
            }
            utterance = promise
            pendingText = text
            pendingLanguage = language
            try {
                if (tts == null) {
                    // Android invokes this callback after engine initialization.
                    tts = TextToSpeech(context) { status ->
                        main.post {
                            if (status != TextToSpeech.SUCCESS) {
                                rejectUtterance("OFFLINE_TTS_UNAVAILABLE", "Speech engine unavailable")
                                tts?.shutdown()
                                tts = null
                            } else {
                                tts?.let { speakWhenInitialized(it) }
                            }
                        }
                    }
                } else {
                    speakWhenInitialized(tts!!)
                }
            } catch (_: Exception) {
                rejectUtterance("OFFLINE_TTS_UNAVAILABLE", "Speech engine could not be initialized")
            }
        }
    }

    @ReactMethod fun stopSpeaking(promise: Promise) {
        main.post {
            try { tts?.stop() } catch (_: Exception) {}
            rejectUtterance("TTS_STOPPED", "Speech stopped")
            promise.resolve(true)
        }
    }

    override fun onHostResume() {}
    override fun onHostPause() {
        main.post {
            if (recognition != null) rejectRecognition("STT_INTERRUPTED", "App left foreground; microphone stopped")
            if (utterance != null) {
                try { tts?.stop() } catch (_: Exception) {}
                rejectUtterance("TTS_STOPPED", "Playback stopped when app left foreground")
            }
        }
    }
    override fun onHostDestroy() {
        main.post {
            if (recognition != null) rejectRecognition("STT_INTERRUPTED", "App closed")
            else releaseRecognizer()
            if (utterance != null) rejectUtterance("TTS_STOPPED", "App closed")
            try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
            tts = null
        }
    }
}
