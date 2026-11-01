package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-armed on-device wake phrase listener. Does not upload, log or store
 * microphone data; no boot receiver or automatic restart. Releases AudioRecord
 * before transferring control to Android's selected assistant.
 */
class NexaWakeWordService : Service() {
    companion object {
        const val START = "dev.rilandev.personalaiassistant.WAKE_START"
        const val STOP = "dev.rilandev.personalaiassistant.WAKE_STOP"
        private const val CHANNEL = "nexa_wake_v1"
        private const val ID = 4921
        @Volatile var listening = false
            private set
        @Volatile var state = "off"
            private set
        @Volatile var message = "Wake-word detection is off"
            private set
    }
    private val running = AtomicBoolean(false)
    private var audio: AudioRecord? = null
    private var thread: Thread? = null
    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String, detected: Boolean = false): Notification {
        val target = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val open = PendingIntent.getActivity(this, 0, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, NexaWakeWordService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_assistant_tile)
            .setContentTitle(if (detected) "Hey Nexa detected" else "Nexa · Offline wake listening")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(!detected)
            .setOnlyAlertOnce(!detected)
            .setPriority(if (detected) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .apply {
                if (!detected) addAction(R.drawable.ic_assistant_tile, "Stop wake listening", stop)
            }.build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action != START || !running.compareAndSet(false, true))
            return START_NOT_STICKY
        if (!NexaWakeWordModule.modelReady(this) ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state = "error"
            message = "Wake model or microphone grant is missing"
            running.set(false)
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                "Nexa on-device wake listening", NotificationManager.IMPORTANCE_LOW))
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, notification("Local listening · Stop available"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(ID, notification("Local listening · Stop available"))
            }
        } catch (_: Exception) {
            state = "error"
            message = "Android blocked the microphone foreground service"
            running.set(false)
            stopSelf()
            return START_NOT_STICKY
        }
        state = "loading"
        message = "Loading local speech model"
        thread = Thread { detect(manager) }.apply { name = "NexaOfflineWake"; start() }
        return START_NOT_STICKY
    }
    private fun detect(manager: NotificationManager) {
        val frame = ShortArray(4000)
        val gate = NexaWakePhrase()
        var heard = false
        try {
            Model(NexaWakeWordModule.modelDir(this).absolutePath).use { model ->
                // Grammar keeps decoder local and tightly scoped to trigger sounds.
                val grammar = "[\"hey nexus\", \"hey next a\", \"[unk]\"]"
                Recognizer(model, 16000.0f, grammar).use { recognizer ->
                    if (!running.get()) return
                    val min = AudioRecord.getMinBufferSize(16000,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    if (min <= 0) error("Microphone buffer unavailable")
                    val source = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(min * 2, frame.size * 2))
                    audio = source
                    if (source.state != AudioRecord.STATE_INITIALIZED) error("Microphone unavailable")
                    source.startRecording()
                    state = "listening"
                    message = "Offline Hey Nexa detector active"
                    listening = true
                    while (running.get()) {
                        if (RealtimeVoiceService.status == "connecting" ||
                            RealtimeVoiceService.status == "listening") {
                            state = "paused"
                            message = "Another voice session owns the microphone"
                            break
                        }
                        val n = source.read(frame, 0, frame.size)
                        if (n <= 0) { if (!running.get()) break; error("Mic capture interrupted") }
                        val utteranceFinished = recognizer.acceptWaveForm(frame, n)
                        val raw = if (utteranceFinished) recognizer.result else recognizer.partialResult
                        val json = JSONObject(raw)
                        val phrase = if (utteranceFinished) json.optString("text")
                            else json.optString("partial")
                        if (gate.observe(phrase, SystemClock.elapsedRealtime())) {
                            heard = true
                            state = "detected"
                            message = "Hey Nexa recognized locally"
                            running.set(false)
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                state = "error"
                message = "Local wake detection failed: " + (e.message ?: "audio error").take(90)
            }
        } finally {
            releaseAudio()
            listening = false
            running.set(false)
            if (heard) {
                // Display Android's selected assistant, if available. If not
                // selected, user can tap the detection notification to continue.
                // Always retain a user-clickable fallback, including when an
                // OEM rejects the selected-assistant session asynchronously.
                manager.notify(ID + 1, notification(
                    "Tap if the assistant did not open automatically", true))
                NexaAssistantService.showFromWakeWord()
            } else if (state == "listening" || state == "loading") {
                state = "off"
                message = "Wake listener stopped"
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    private fun releaseAudio() {
        val old = audio
        audio = null
        try { old?.stop() } catch (_: Exception) {}
        try { old?.release() } catch (_: Exception) {}
    }
    override fun onDestroy() {
        running.set(false)
        releaseAudio()
        listening = false
        if (state == "listening" || state == "loading") {
            state = "off"; message = "Wake listener stopped"
        }
        thread?.interrupt()
        super.onDestroy()
    }
}
