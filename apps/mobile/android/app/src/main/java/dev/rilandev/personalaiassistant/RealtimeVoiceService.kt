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
import android.media.audiofx.AcousticEchoCanceler
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.NoiseSuppressor
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Owner-started continuous audio session with a visible system notification.
 * This is an experimental direct OpenAI Realtime transport. It never exposes
 * device tools or starts itself in the background, from boot, or from a model.
 * All audio goes directly to the owner's chosen provider over encrypted WSS.
 */
class RealtimeVoiceService : Service() {
    companion object {
        const val ACTION_START = "dev.rilandev.personalaiassistant.VOICE_START"
        const val ACTION_STOP = "dev.rilandev.personalaiassistant.VOICE_STOP"
        private const val CHANNEL = "paa_voice_session_v1"
        private const val NOTIFICATION_ID = 4417
        private const val RATE = 24000
        private const val MODEL = "gpt-realtime-2.1-mini"
        @Volatile var status = "stopped"
            private set
        @Volatile var message = "No live conversation"
            private set
    }
    private val main = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private var socket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var audioManager: AudioManager? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            main.post {
                if (active.get()) stopWithStatus("stopped", "Android audio focus interrupted")
            }
        }
    }
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private val audioQueue = ArrayBlockingQueue<ByteArray>(32)
    // Keep sending AEC-filtered mic input while the model speaks; otherwise
    // the server VAD never hears the owner interrupting (broken barge-in).
    @Volatile private var interruptedResponse = false
    private val maxSession = Runnable { stopWithStatus("stopped","15-minute session limit reached") }
    private val handshakeTimeout = Runnable {
        if (active.get() && status=="connecting")
            stopWithStatus("error","Live voice provider did not accept session within 15 seconds")
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWithStatus("stopped","Stopped by owner")
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || active.get()) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "error"
            message = "Microphone permission not granted"
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            createNotificationChannel()
            val notification = makeNotification("Connecting to your voice provider • Tap Stop to end")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID,notification)
            }
        } catch (_: Exception) {
            status = "error"
            message = "Android denied the microphone foreground service"
            stopSelf()
            return START_NOT_STICKY
        }
        val key = try { AgentJournalModule.openAiVoiceKey(applicationContext) } catch (_: Exception) { null }
        if (key.isNullOrBlank()) {
            stopWithStatus("error","Configure your OpenAI API key in Settings first")
            return START_NOT_STICKY
        }
        active.set(true)
        status = "connecting"
        message = "Connecting to live voice provider"
        main.postDelayed(maxSession, 15*60*1000L)
        main.postDelayed(handshakeTimeout, 15_000L)
        connect(key)
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL,"AI live voice session",
            NotificationManager.IMPORTANCE_LOW)
        channel.description = "User-started live microphone and speech conversation"
        manager.createNotificationChannel(channel)
    }
    private fun makeNotification(statusText: String): Notification {
        val open = Intent(this,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val stop = Intent(this,RealtimeVoiceService::class.java).apply { action=ACTION_STOP }
        val openPending = PendingIntent.getActivity(this,13,open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPending = PendingIntent.getService(this,14,stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_assistant_tile)
            .setContentTitle("AI voice is active")
            .setContentText(statusText)
            .setContentIntent(openPending)
            .addAction(Notification.Action.Builder(
                R.drawable.ic_assistant_tile,"Stop voice",stopPending).build())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
    private fun connect(key: String) {
        val http = OkHttpClient.Builder()
            .connectTimeout(12,TimeUnit.SECONDS)
            .pingInterval(15,TimeUnit.SECONDS)
            .build()
        client = http
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=$MODEL")
            .header("Authorization","Bearer $key")
            .build()
        socket = http.newWebSocket(request,object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket,response: Response) {
                if (!active.get()) return
                val input = JSONObject()
                    .put("format",JSONObject().put("type","audio/pcm").put("rate",RATE))
                    .put("turn_detection",JSONObject()
                        .put("type","server_vad")
                        .put("silence_duration_ms",650)
                        .put("create_response",true)
                        .put("interrupt_response",true))
                val output = JSONObject()
                    .put("format",JSONObject().put("type","audio/pcm").put("rate",RATE))
                    .put("voice","marin")
                val session = JSONObject()
                    .put("type","realtime")
                    .put("audio",JSONObject().put("input",input).put("output",output))
                    .put("instructions","You are a concise conversational voice companion. Do NOT use tools or claim device actions. To control the phone, ask the user to use approved Chat tools. Never request private keys.")
                    .put("max_output_tokens",512)
                webSocket.send(JSONObject().put("type","session.update").put("session",session).toString())
            }
            override fun onMessage(webSocket: WebSocket,text: String) {
                if (!active.get() || text.length>200000) return
                val json = try { JSONObject(text) } catch (_: Exception) { return }
                when (json.optString("type")) {
                    "session.updated" -> main.post { if (active.get() && status=="connecting") startAudio() }
                    "response.created" -> interruptedResponse = false
                    "input_audio_buffer.speech_started" -> {
                        // The owner interrupted playback. Clear stale output
                        // immediately; never play responses after a VAD cancel.
                        interruptedResponse = true
                        audioQueue.clear()
                        main.post {
                            try {
                                player?.pause()
                                player?.flush()
                                if (active.get()) player?.play()
                            } catch (_: Exception) {}
                        }
                    }
                    "response.output_audio.delta" -> {
                        if (!interruptedResponse) {
                            val delta = json.optString("delta")
                            if (delta.length <= 160000) {
                                try {
                                    if (!audioQueue.offer(Base64.decode(delta,Base64.DEFAULT))) {
                                        main.post {
                                            if (active.get()) stopWithStatus(
                                                "error", "Audio playback buffer overloaded"
                                            )
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    "error" -> main.post { stopWithStatus("error","Live provider rejected the session or audio") }
                }
            }
            override fun onFailure(webSocket: WebSocket,t: Throwable,response: Response?) {
                main.post { if (active.get()) stopWithStatus("error","Live voice connection failed") }
            }
            override fun onClosed(webSocket: WebSocket,code: Int,reason: String) {
                main.post { if (active.get()) stopWithStatus("stopped","Provider closed voice session") }
            }
        })
    }

    private fun startAudio() {
        if (!active.get() || status!="connecting") return
        main.removeCallbacks(handshakeTimeout)
        try {
            val manager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager = manager
            @Suppress("DEPRECATION")
            val focus = manager.requestAudioFocus(
                audioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                throw IllegalStateException("Audio focus unavailable")
            }
            val inputSize = maxOf(AudioRecord.getMinBufferSize(RATE,
                AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),4096)
            val outputSize = maxOf(AudioTrack.getMinBufferSize(RATE,
                AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT),4096)
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,inputSize*2)
            if (record.state!=AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("Audio recorder unavailable")
            }
            recorder = record
            // Android DSP cancellation suppresses the assistant's own speaker
            // sound returning through the microphone on supported devices.
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)
                try { echoCanceler?.enabled = true } catch (_: Exception) {}
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)
                try { noiseSuppressor?.enabled = true } catch (_: Exception) {}
            }
            val playback = AudioTrack(AudioManager.STREAM_MUSIC,RATE,
                AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,
                outputSize*2,AudioTrack.MODE_STREAM)
            if (playback.state!=AudioTrack.STATE_INITIALIZED) {
                playback.release()
                throw IllegalStateException("Audio output unavailable")
            }
            player = playback
            playback.play()
            record.startRecording()
            if (record.recordingState!=AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Microphone unavailable")
            }
            status = "listening"
            message = "Live speech-to-speech running · Stop in notification"
            try {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID,makeNotification("Microphone active · Tap Stop to end"))
            } catch (_: Exception) {}
            playbackThread = Thread({
                while (active.get()) {
                    try {
                        val bytes = audioQueue.poll(250,TimeUnit.MILLISECONDS) ?: continue
                        player?.write(bytes,0,bytes.size)
                    } catch (_: InterruptedException) { break }
                    catch (_: Exception) { break }
                }
            },"paa-voice-playback").also { it.start() }
            captureThread = Thread({
                val data = ByteArray(1920) // 40ms of 24kHz, mono PCM16
                while (active.get()) {
                    val got = try { recorder?.read(data,0,data.size) ?: break }
                        catch (_: Exception) { break }
                    if (got>0) {
                        val encoded = Base64.encodeToString(data.copyOf(got),Base64.NO_WRAP)
                        val payload = JSONObject()
                            .put("type","input_audio_buffer.append")
                            .put("audio",encoded).toString()
                        val ws = socket ?: break
                        if (ws.queueSize()>256*1024 || !ws.send(payload)) {
                            main.post { stopWithStatus("error","Voice network too slow") }
                            break
                        }
                    }
                }
            },"paa-voice-microphone").also { it.start() }
        } catch (_: Exception) {
            stopWithStatus("error","Could not start the microphone or speaker")
        }
    }

    private fun stopWithStatus(state: String,reason: String) {
        active.set(false)
        status=state
        message=reason
        main.removeCallbacks(maxSession)
        main.removeCallbacks(handshakeTimeout)
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        captureThread?.interrupt()
        captureThread = null
        playbackThread?.interrupt()
        playbackThread = null
        audioQueue.clear()
        try { echoCanceler?.release() } catch (_: Exception) {}
        echoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        try { audioManager?.abandonAudioFocus(audioFocusListener) } catch (_: Exception) {}
        audioManager = null
        try { player?.pause(); player?.flush(); player?.release() } catch (_: Exception) {}
        player = null
        try { socket?.close(1000,"owner-stop") } catch (_: Exception) {}
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (active.get()) stopWithStatus("stopped","Android ended voice session")
        super.onDestroy()
    }
}

class RealtimeVoiceModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName(): String = "PAARealtimeVoice"

    @ReactMethod fun startVoiceSession(p: Promise) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            p.reject("UNSUPPORTED_ANDROID","Live voice requires Android 8 or newer")
            return
        }
        if (context.currentActivity?.hasWindowFocus()!=true) {
            p.reject("NOT_FOREGROUND","Start a live session from the visible app")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            p.reject("MIC_PERMISSION_REQUIRED","Approve microphone permission first")
            return
        }
        if (RealtimeVoiceService.status=="connecting" || RealtimeVoiceService.status=="listening") {
            p.reject("VOICE_ALREADY_RUNNING","Stop the current session first")
            return
        }
        try {
            val intent=Intent(context,RealtimeVoiceService::class.java).apply {
                action=RealtimeVoiceService.ACTION_START
            }
            context.startForegroundService(intent)
            p.resolve(true) // Service startup requested, not confirmation of connection.
        } catch (_: Exception) {
            p.reject("VOICE_START_FAILED","Android rejected foreground microphone service")
        }
    }
    @ReactMethod fun stopVoiceSession(p: Promise) {
        try {
            context.startService(Intent(context,RealtimeVoiceService::class.java).apply {
                action=RealtimeVoiceService.ACTION_STOP
            })
            p.resolve(true)
        } catch (_: Exception) {
            context.stopService(Intent(context,RealtimeVoiceService::class.java))
            p.resolve(true)
        }
    }
    @ReactMethod fun getVoiceSessionStatus(p: Promise) {
        val data=Arguments.createMap()
        data.putString("state",RealtimeVoiceService.status)
        data.putString("message",RealtimeVoiceService.message)
        data.putBoolean("microphoneActive",RealtimeVoiceService.status=="listening")
        p.resolve(data)
    }
}
