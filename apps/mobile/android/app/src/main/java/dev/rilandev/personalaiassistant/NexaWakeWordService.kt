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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owner-armed offline microphone foreground service. Retains one foreground
 * service across successive wake turns; releases its microphone while the
 * assistant session owns it, then re-arms after onHide.
 *
 * Android's green mic privacy indicator and ongoing FGS notification are
 * intentional OS disclosures and MUST NOT be hidden.
 */
class NexaWakeWordService : Service() {
    companion object {
        const val START = "dev.rilandev.personalaiassistant.WAKE_START"
        const val STOP = "dev.rilandev.personalaiassistant.WAKE_STOP"
        private const val CHANNEL = "nexa_wake_v1"
        private const val CHANNEL_TRIGGER = "nexa_wake_detected_v1"
        private const val ID = 4921
        @Volatile private var active: NexaWakeWordService? = null
        @Volatile var listening = false
            private set
        @Volatile var state = "off"
            private set
        @Volatile var message = "Wake-word detection is off"
            private set

        /** Called by the Android system-selected assistant session. */
        fun assistantShown() {
            active?.apply {
                handoffPending = true
                assistantSessionVisible = true
                handoffStartedAt = 0L
            }
        }
        fun assistantClosed() {
            active?.apply {
                assistantSessionVisible = false
                handoffPending = false
                handoffStartedAt = 0L
            }
        }

        /** Pause an existing armed FGS without destroying it. */
        fun pauseForAssistant() {
            active?.apply {
                assistantSessionVisible = false
                handoffStartedAt = SystemClock.elapsedRealtime()
                handoffPending = true
            }
        }

        // A legacy wake_enabled flag alone must not restart continuous microphone
        // capture after an APK upgrade. Version 2 requires a fresh user opt-in.
        const val MODE_GESTURE = "gesture"
        const val MODE_CONTINUOUS = "continuous"
        private const val MODE_KEY = "activation_mode_v2"
        private fun prefs(ctx: Context) =
            ctx.getSharedPreferences("nexa_wake_preferences", Context.MODE_PRIVATE)
        fun mode(ctx: Context): String =
            if (prefs(ctx).getString(MODE_KEY, MODE_GESTURE) == MODE_CONTINUOUS &&
                prefs(ctx).getBoolean("wake_enabled", false)) MODE_CONTINUOUS
            else MODE_GESTURE
        fun enabled(ctx: Context): Boolean = mode(ctx) == MODE_CONTINUOUS
        fun persistEnabled(ctx: Context, enabled: Boolean) {
            prefs(ctx).edit()
                .putString(MODE_KEY, if (enabled) MODE_CONTINUOUS else MODE_GESTURE)
                .putBoolean("wake_enabled", enabled)
                .apply()
        }
    }

    private val running = AtomicBoolean(false)
    @Volatile private var handoffPending = false
    @Volatile private var assistantSessionVisible = false
    @Volatile private var handoffStartedAt = 0L
    @Volatile private var audio: AudioRecord? = null
    private var worker: Thread? = null
    private val ui = Handler(Looper.getMainLooper())
    private var fallbackOverlay: NexaWakeFallbackOverlay? = null
    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFallbackConversation(manager: NotificationManager) {
        ui.post {
            if (!running.get() || !enabled(this)) return@post
            if (fallbackOverlay != null) return@post
            val fallback = NexaWakeFallbackOverlay(this) {
                fallbackOverlay = null
                assistantClosed()
            }
            if (fallback.show()) {
                fallbackOverlay = fallback
                state = "paused"
                message = "Compact local conversation is active"
                manager.cancel(ID + 1)
            } else {
                handoffPending = false
                handoffStartedAt = 0L
                state = "listening"
                message = "Wake heard but Android did not show assistant or allow an overlay"
                manager.notify(ID + 1, notification(
                    "Tap to continue · grant Nexa overlay and default assistant access", true))
            }
        }
    }

    private fun notification(text: String, detected: Boolean = false): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, NexaWakeWordService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, if (detected) CHANNEL_TRIGGER else CHANNEL)
            .setSmallIcon(R.drawable.ic_nexa_assistant)
            .setContentTitle(if (detected) "Nexa heard the wake phrase" else "Nexa · Microphone listening for Hey Nexa")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(!detected)
            .setAutoCancel(detected)
            .setOnlyAlertOnce(!detected)
            .setPriority(if (detected) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .apply { if (!detected) addAction(R.drawable.ic_nexa_assistant, "Turn off wake listening", stop) }
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            persistEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        // Null intents occur only for OS-managed START_STICKY restarts. Never
        // auto-start from a BOOT_COMPLETED receiver.
        if ((intent?.action != START && intent != null) || !enabled(this))
            return START_NOT_STICKY
        if (!running.compareAndSet(false, true)) return START_STICKY
        if (!NexaWakeWordModule.modelReady(this) ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state = "error"
            message = "Offline model or microphone permission missing"
            running.set(false)
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                "Nexa offline wake microphone", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(CHANNEL_TRIGGER,
                "Nexa wake phrase recognized", NotificationManager.IMPORTANCE_HIGH))
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, notification("Audio stays on this phone · Tap Stop to disable"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else startForeground(ID,
                notification("Audio stays on this phone · Tap Stop to disable"))
        } catch (_: Exception) {
            state = "error"
            message = "Android blocked the microphone foreground service"
            running.set(false)
            stopSelf()
            return START_NOT_STICKY
        }
        // Successful foreground start supersedes any reboot reminder.
        manager.cancel(4925)
        active = this
        state = "loading"
        message = "Loading the on-device wake model"
        worker = Thread { monitor(manager) }.apply { name = "NexaOfflineWake"; start() }
        // An existing owner-started FGS may recover after OS process death if
        // Android permits it; this does not grant permission at boot.
        return START_STICKY
    }

    private fun monitor(manager: NotificationManager) {
        try {
            Model(NexaWakeWordModule.modelDir(this).absolutePath).use { model ->
                while (running.get()) {
                    // Some OEMs neither display the requested assistant session
                    // nor report a failure. Do not remain paused indefinitely.
                    // A genuinely visible session stays paused until onHide.
                    if (handoffPending && !assistantSessionVisible &&
                        handoffStartedAt > 0L &&
                        SystemClock.elapsedRealtime() - handoffStartedAt >= 12000L) {
                        handoffStartedAt = 0L
                        message = "Android assistant session did not open · trying floating voice"
                        showFallbackConversation(manager)
                    }
                    // During a voice command, Android's recognizer needs sole
                    // ownership of the microphone. Keep the existing FGS.
                    if (handoffPending || RealtimeVoiceService.status == "listening" ||
                        RealtimeVoiceService.status == "connecting") {
                        listening = false
                        state = "paused"
                        message = "Assistant or live voice currently owns the microphone"
                        SystemClock.sleep(300)
                        continue
                    }
                    val heard = detectOnce(model)
                    if (!running.get()) break
                    if (heard) {
                        assistantSessionVisible = false
                        handoffStartedAt = SystemClock.elapsedRealtime()
                        handoffPending = true
                        state = "paused"
                        message = "Wake recognized · microphone released for assistant"
                        val shown = NexaAssistantService.showFromWakeWord()
                        if (!shown) {
                            handoffStartedAt = 0L
                            showFallbackConversation(manager)
                            SystemClock.sleep(900)
                        }
                        // If shown, onHide/onShowSessionFailed releases pause.
                        // A failed OEM callback leaves audio paused, not
                        // secretly recording over an active assistant session.
                    } else if (state == "error") {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                state = "error"
                message = "Wake model failure: " + (e.message ?: "unknown").take(100)
            }
        } finally {
            releaseAudio()
            listening = false
            running.set(false)
            if (active === this) active = null
            if (state != "error") {
                state = "off"
                message = "Wake microphone stopped"
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun detectOnce(model: Model): Boolean {
        val gate = NexaWakePhrase()
        val grammar = "[\"hey nexa\", \"hey nexus\", \"hey next a\", \"[unk]\"]"
        val frame = ShortArray(4000)
        try {
            Recognizer(model, 16000.0f, grammar).use { recognizer ->
                val min = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (min <= 0) error("Microphone audio buffer unavailable")
                val source = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min * 2, frame.size * 2))
                audio = source
                if (source.state != AudioRecord.STATE_INITIALIZED) error("Mic unavailable")
                source.startRecording()
                listening = true
                state = "listening"
                message = "Hey Nexa listening locally · Android microphone indicator is expected"
                while (running.get() && !handoffPending) {
                    if (RealtimeVoiceService.status == "listening" ||
                        RealtimeVoiceService.status == "connecting") break
                    val n = source.read(frame, 0, frame.size)
                    if (n <= 0) {
                        if (!running.get() || handoffPending) break
                        error("Microphone capture interrupted")
                    }
                    val utteranceFinished = recognizer.acceptWaveForm(frame, n)
                    val json = JSONObject(if (utteranceFinished) recognizer.result else recognizer.partialResult)
                    val phrase = if (utteranceFinished) json.optString("text")
                        else json.optString("partial")
                    if (gate.observe(phrase, SystemClock.elapsedRealtime(), utteranceFinished))
                        return true
                }
            }
        } catch (e: Exception) {
            if (running.get() && !handoffPending) {
                state = "error"
                message = "Wake capture error: " + (e.message ?: "unknown").take(100)
            }
        } finally {
            releaseAudio()
            listening = false
        }
        return false
    }

    private fun releaseAudio() {
        val current = audio
        audio = null
        try { current?.stop() } catch (_: Exception) {}
        try { current?.release() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        running.set(false)
        ui.post {
            fallbackOverlay?.finish("Wake listener disabled")
            fallbackOverlay = null
        }
        handoffPending = false
        assistantSessionVisible = false
        handoffStartedAt = 0L
        releaseAudio()
        listening = false
        worker?.interrupt()
        if (active === this) active = null
        if (state != "error") { state = "off"; message = "Wake microphone stopped" }
        super.onDestroy()
    }
}
