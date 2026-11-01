package dev.rilandev.personalaiassistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Compatibility endpoint required for Android's assistant service registration.
 * This is NOT a speech recognizer and does not access audio. Nexa continues to
 * use the separately owner-triggered on-device speech and live voice pipelines.
 *
 * Fail explicitly rather than advertising fake transcripts or silently trying
 * a cloud speech service. Do not choose Nexa as the system-wide recognizer.
 */
class NexaRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onStopListening(listener: Callback) {}
    override fun onCancel(listener: Callback) {}
}
