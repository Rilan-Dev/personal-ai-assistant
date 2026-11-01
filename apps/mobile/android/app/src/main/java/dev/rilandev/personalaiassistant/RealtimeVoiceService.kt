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
import android.os.SystemClock
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
        const val EXTRA_ASSISTANT_PANEL = "dev.rilandev.personalaiassistant.ASSISTANT_PANEL_TOOLS"
        const val ACTION_STOP = "dev.rilandev.personalaiassistant.VOICE_STOP"
        const val ACTION_MUTE = "dev.rilandev.personalaiassistant.VOICE_MUTE"
        const val ACTION_UNMUTE = "dev.rilandev.personalaiassistant.VOICE_UNMUTE"
        const val ACTION_APPROVE_TOOL = "dev.rilandev.personalaiassistant.TOOL_APPROVE"
        const val ACTION_DENY_TOOL = "dev.rilandev.personalaiassistant.TOOL_DENY"
        @Volatile var pendingApprovalSummary: String? = null
            private set
        @Volatile var lastToolResult = ""
            private set
        @Volatile var microphoneMuted = false
            private set
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
    private val micSendLock = Any()
    private var socket: WebSocket? = null
    private var sarvamTransport: SarvamAudioTransport? = null
    private var selectedProvider = "openai"
    @Volatile private var assistantPanelEnabled = false
    private data class PendingAppTool(val callId: String, val app: String, val command: String)
    private var pendingAppTool: PendingAppTool? = null
    private var lastLocalUtterance = ""
    private var lastLocalUtteranceAt = 0L
    private val seenCallIds = HashSet<String>()
    private var toolCallsInSession = 0
    private var activeRate = RATE
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
    private data class PlaybackChunk(val turn: Long, val pcm: ByteArray)
    private val audioQueue = ArrayBlockingQueue<PlaybackChunk>(32)
    private val playbackTurn = AtomicLong(0L)
    private val playbackLock = Any()
    @Volatile private var responseId = ""
    // Keep sending AEC-filtered mic input while the model speaks; otherwise
    // the server VAD never hears the owner interrupting (broken barge-in).
    @Volatile private var interruptedResponse = false
    private val maxSession = Runnable { stopWithStatus("stopped","15-minute session limit reached") }
    private val idleGate = NexaVoiceIdleGate()
    private val idleCheck = object : Runnable {
        override fun run() {
            if (!active.get() || status != "listening") return
            if (idleGate.inactive(SystemClock.elapsedRealtime())) {
                stopWithStatus("stopped", "Conversation idle · say Hey Nexa to start again")
            } else main.postDelayed(this, 5_000L)
        }
    }
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
        if (intent?.action == ACTION_APPROVE_TOOL || intent?.action == ACTION_DENY_TOOL) {
            val pending = pendingAppTool
            if (active.get() && assistantPanelEnabled && pending != null) {
                pendingAppTool = null
                pendingApprovalSummary = null
                val approved = intent.action == ACTION_APPROVE_TOOL
                val outcome = if (approved)
                    NexaOverlayCommands.handle(this, pending.command)
                    else "Denied by owner. No app was opened."
                lastToolResult = outcome.take(320)
                message = lastToolResult
                if (selectedProvider == "openai") {
                    sendRealtimeToolOutput(pending.callId, lastToolResult)
                    socket?.send(JSONObject().put("type", "response.create").toString())
                }
                // Sarvam's managed CALL transport has no documented on-phone
                // function_call_output event. The outcome is shown locally;
                // never falsely tell Sarvam the tool ran through its harness.
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_MUTE || intent?.action == ACTION_UNMUTE) {
            if (active.get()) {
                synchronized(micSendLock) {
                    microphoneMuted = intent.action == ACTION_MUTE
                }
                message = if(microphoneMuted)
                    "Microphone muted: only silence sent to provider"
                    else "Microphone streaming restored"
                try {
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_ID,makeNotification(message))
                }catch (_:Exception){}
            }
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
        val credentials=try {
            AgentJournalModule.openVoiceCredentials(applicationContext)
        } catch (_: Exception) { null }
        if (credentials == null) {
            stopWithStatus("error","Configure a supported voice provider and Voice Agent IDs")
            return START_NOT_STICKY
        }
        microphoneMuted=false
        assistantPanelEnabled = intent.getBooleanExtra(EXTRA_ASSISTANT_PANEL, false)
        pendingAppTool = null
        pendingApprovalSummary = null
        lastToolResult = ""
        lastLocalUtterance = ""
        lastLocalUtteranceAt = 0L
        micReleaseRetries = 0
        seenCallIds.clear()
        toolCallsInSession = 0
        idleGate.reset(0L)
        selectedProvider=credentials.provider
        activeRate=if(selectedProvider=="sarvam")16000 else RATE
        // Stop the separate offline wake AudioRecord before attempting a
        // second microphone capture. The wake service remains owner-armed,
        // but may not record concurrently with the live session.
        NexaWakeWordService.pauseForLiveVoice()
        active.set(true)
        status="connecting"
        message="Connecting to "+(if(selectedProvider=="sarvam")"Sarvam Voice Agents" else "OpenAI")
        main.postDelayed(maxSession,15*60*1000L)
        main.postDelayed(handshakeTimeout,25_000L)
        try {
        if(selectedProvider=="sarvam") {
            val transport=SarvamAudioTransport(object:SarvamAudioTransport.Callbacks{
                override fun connected() {
                    main.post {if(active.get() && status=="connecting")startAudio()}
                }
                override fun audio(bytes:ByteArray) {
                    if(!audioQueue.offer(PlaybackChunk(playbackTurn.get(),bytes)))
                        main.post {if(active.get())stopWithStatus("error","Voice playback buffer overloaded")}
                }
                override fun interrupted() {
                    idleGate.reset(SystemClock.elapsedRealtime())
                    interruptSpeakerPlayback()
                }
                override fun transcript(role: String, content: String) {
                    // Official Sarvam transcript callback: role=user, content.
                    // Never allow bot text or arbitrary transcript to run tools.
                    main.post { if(active.get() && status=="listening" &&
                        selectedProvider=="sarvam" && role=="user")
                        handleSarvamOwnerRequest(content)
                    }
                }
                override fun failed(reason:String){
                    main.post {if(active.get())stopWithStatus("error",reason)}
                }
                override fun ended(){
                    main.post {if(active.get())stopWithStatus("stopped","Sarvam ended voice session")}
                }
            })
            sarvamTransport=transport
            transport.connect(credentials)
        } else connect(credentials.apiKey)
        } catch (failure: Exception) {
            // Network credential/provider configuration errors must not kill
            // the app process. No URL, key, or microphone samples in Logcat.
            stopWithStatus("error", "Could not initialize live voice provider (" +
                failure.javaClass.simpleName + ")")
        }
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
        val muteAction = Intent(this,RealtimeVoiceService::class.java).apply {
            action = if(microphoneMuted) ACTION_UNMUTE else ACTION_MUTE
        }
        val mutePending = PendingIntent.getService(this,
            if(microphoneMuted) 16 else 15,muteAction,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_assistant_tile)
            .setContentTitle("AI voice is active")
            .setContentText(statusText)
            .setContentIntent(openPending)
            .addAction(Notification.Action.Builder(
                R.drawable.ic_assistant_tile,"Stop voice",stopPending).build())
            .addAction(Notification.Action.Builder(
                R.drawable.ic_assistant_tile,
                if(microphoneMuted) "Unmute mic" else "Mute mic",
                mutePending).build())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
    /**
     * Invalidate queued and already-dequeued chunks on barge-in. AudioTrack
     * writes and flushes share a lock, so old audio cannot be written after
     * the interruption flush. This never stops mic capture/VAD.
     */
    private fun interruptSpeakerPlayback() {
        playbackTurn.incrementAndGet()
        audioQueue.clear()
        main.post {
            synchronized(playbackLock) {
                try {
                    player?.pause()
                    player?.flush()
                    if (active.get()) player?.play()
                } catch (_: Exception) {}
            }
        }
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
                    .put("instructions","You are Nexa, a natural conversational voice assistant. Reply in English, Tamil or Hindi as appropriate. Keep answers brief and grounded, remember prior turns, handle user interruptions, and state tool outcomes precisely. You have three restricted Android function tools: read device status, read battery, and request opening Brave/GitHub/WhatsApp. An app-open request ALWAYS requires a visible human approval. Never claim an app opened unless the result confirms it; intent dispatch does not prove the app screen. You cannot send WhatsApp messages, change arbitrary permissions, control the screen, or access other private data in this mode. Explain limitations rather than inventing actions. Never ask for secrets.")
                    .put("tools", if (assistantPanelEnabled)
                        NexaLiveToolPolicy.definitions() else org.json.JSONArray())
                    .put("tool_choice", if (assistantPanelEnabled) "auto" else "none")
                    .put("max_output_tokens",512)
                webSocket.send(JSONObject().put("type","session.update").put("session",session).toString())
            }
            override fun onMessage(webSocket: WebSocket,text: String) {
                if (!active.get() || text.length>200000) return
                val json = try { JSONObject(text) } catch (_: Exception) { return }
                when (json.optString("type")) {
                    "session.updated" -> main.post { if (active.get() && status=="connecting") startAudio() }
                    "response.done" -> main.post {
                        if (active.get() && selectedProvider == "openai" &&
                            assistantPanelEnabled) handleRealtimeToolCalls(json)
                    }
                    "response.created" -> {
                        // A cancelled response can still yield late network deltas.
                        // Only the newest server response may reach the speaker.
                        responseId = json.optJSONObject("response")?.optString("id").orEmpty()
                        interruptedResponse = false
                    }
                    "input_audio_buffer.speech_started" -> {
                        idleGate.reset(SystemClock.elapsedRealtime())
                        // The owner interrupted playback. Clear stale output
                        // immediately; never play responses after a VAD cancel.
                        interruptedResponse = true
                        interruptSpeakerPlayback()
                    }
                    "response.output_audio.delta" -> {
                        if (!interruptedResponse && responseId.isNotEmpty() &&
                            json.optString("response_id") == responseId) {
                            val delta = json.optString("delta")
                            if (delta.length <= 160000) {
                                try {
                                    if (!audioQueue.offer(PlaybackChunk(playbackTurn.get(),Base64.decode(delta,Base64.DEFAULT)))) {
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
                    "error" -> main.post {
                        if (active.get()) stopWithStatus("error","Live provider rejected the session or audio")
                    }
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

    /**
     * Transcribed owner utterances are untrusted input. The Sarvam managed
     * model remains responsible for live listening/reasoning/speaking; Nexa
     * independently proposes only explicit allowlisted app launches, with a
     * visible confirmation before the native Intent. No automatic dispatch.
     *
     * Sarvam-managed webhooks are not client-device tools. This local bridge
     * does NOT feed results into Sarvam's remote LLM conversation.
     */
    private fun handleSarvamOwnerRequest(transcript: String) {
        if (!assistantPanelEnabled || pendingAppTool != null) return
        val intent = NexaVoiceIntentGate.appRequest(transcript) ?: return
        val now = SystemClock.elapsedRealtime()
        if (intent.command == lastLocalUtterance &&
            now - lastLocalUtteranceAt < 12_000L) return
        if (++toolCallsInSession > 12) {
            lastToolResult = "Voice device-action limit reached"
            message = lastToolResult
            return
        }
        lastLocalUtterance = intent.command
        lastLocalUtteranceAt = now
        pendingAppTool = PendingAppTool("local-sarvam", intent.app, intent.command)
        pendingApprovalSummary = "Open ${intent.app}? Confirm or deny below."
        message = "Nexa local device approval required to open ${intent.app}"
        try {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID, makeNotification("App approval pending · open Nexa Voice"))
        } catch (_: Exception) {}
    }

    private fun sendRealtimeToolOutput(callId: String, result: String) {
        val output = JSONObject().put("type", "conversation.item.create")
            .put("item", JSONObject().put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", JSONObject().put("result", result.take(500)).toString()))
        socket?.send(output.toString())
    }

    private fun handleRealtimeToolCalls(event: JSONObject) {
        val output = event.optJSONObject("response")?.optJSONArray("output") ?: return
        var handled = false
        for (i in 0 until minOf(output.length(), 12)) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "function_call") continue
            val id = item.optString("call_id")
            if (!id.matches(Regex("[a-zA-Z0-9_-]{1,128}")) ||
                !seenCallIds.add(id)) continue
            handled = true
            toolCallsInSession++
            if (toolCallsInSession > 12) {
                sendRealtimeToolOutput(id, "Rejected: voice tool-call budget exhausted")
                continue
            }
            if (pendingAppTool != null) {
                sendRealtimeToolOutput(id, "Rejected: another action awaits owner's approval")
                continue
            }
            val name = item.optString("name")
            val args = item.optString("arguments")
            when (val decision = NexaLiveToolPolicy.decide(this, name, args)) {
                is NexaLiveToolPolicy.Decision.Immediate -> {
                    lastToolResult = decision.result.take(320)
                    message = "Tool $name: $lastToolResult"
                    sendRealtimeToolOutput(id, decision.result)
                }
                is NexaLiveToolPolicy.Decision.NeedsApproval -> {
                    pendingAppTool = PendingAppTool(id, decision.app, decision.command)
                    pendingApprovalSummary = "Open ${decision.app}? Confirm or deny below."
                    message = "Approval required to open ${decision.app}"
                }
            }
        }
        // The next model turn is requested only after all pending owner
        // approvals have been resolved. Realtime does not auto-continue tools.
        if (handled && pendingAppTool == null)
            socket?.send(JSONObject().put("type", "response.create").toString())
    }

    private var micReleaseRetries = 0

    private fun startAudio() {
        if (!active.get() || status!="connecting") return
        // The offline wake capture can still be closing on some OEMs. Never
        // initialize a second AudioRecord until the first one is released.
        if (!NexaWakeWordService.liveMicReleased()) {
            if (++micReleaseRetries > 20) {
                stopWithStatus("error","Wake microphone handoff timed out; use Stop and retry")
            } else main.postDelayed({ startAudio() },100L)
            return
        }
        micReleaseRetries = 0
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
            val inputSize = maxOf(AudioRecord.getMinBufferSize(activeRate,
                AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),4096)
            val outputSize = maxOf(AudioTrack.getMinBufferSize(activeRate,
                AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT),4096)
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                activeRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,inputSize*2)
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
            val playback = AudioTrack(AudioManager.STREAM_MUSIC,activeRate,
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
            idleGate.reset(SystemClock.elapsedRealtime())
            main.removeCallbacks(idleCheck)
            main.postDelayed(idleCheck, 5_000L)
            try {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID,makeNotification("Microphone active · Tap Stop to end"))
            } catch (_: Exception) {}
            playbackThread = Thread({
                while (active.get()) {
                    try {
                        val chunk = audioQueue.poll(250,TimeUnit.MILLISECONDS) ?: continue
                        synchronized(playbackLock) {
                            if (active.get() && chunk.turn == playbackTurn.get()) {
                                player?.write(chunk.pcm,0,chunk.pcm.size)
                            }
                        }
                    } catch (_: InterruptedException) { break }
                    catch (_: Exception) { break }
                }
            },"paa-voice-playback").also { it.start() }
            captureThread = Thread({
                val data = ByteArray(activeRate*2/25) // 40ms PCM16 mono
                while (active.get()) {
                    val got = try { recorder?.read(data,0,data.size) ?: break }
                        catch (_: Exception) { break }
                    if (got>0 && active.get()) {
                        // This local amplitude check never uploads audio by
                        // itself; the provider's consent rules still apply.
                        idleGate.observePcm(data, got, SystemClock.elapsedRealtime())
                        // Serialize state changes with the transmit boundary:
                        // after a mute action is applied, no new raw PCM
                        // frames can enter the provider's outgoing queue.
                        val sent=try { synchronized(micSendLock) {
                            if(!active.get()) true else {
                                val bytes=if(microphoneMuted)
                                    ByteArray(got) else data.copyOf(got)
                                if(selectedProvider=="sarvam") {
                                    sarvamTransport?.sendAudio(bytes) ?: false
                                } else {
                                    val encoded=Base64.encodeToString(bytes,Base64.NO_WRAP)
                                    val ws=socket
                                    ws!=null && ws.queueSize()<=256*1024 && ws.send(JSONObject()
                                        .put("type","input_audio_buffer.append")
                                        .put("audio",encoded).toString())
                                }
                            }
                        } } catch (_: Exception) { false }
                        if(!sent){
                            main.post {if(active.get())stopWithStatus("error","Voice network too slow")}
                            break
                        }
                    }
                }
            },"paa-voice-microphone").also { it.start() }
        } catch (failure: Exception) {
            stopWithStatus("error","Could not start microphone or speaker (" +
                failure.javaClass.simpleName + "); check Android privacy permissions")
        }
    }

    private fun stopWithStatus(state: String,reason: String) {
        // Commit the terminal state at the same boundary as microphone sends.
        // No raw frame can be queued after Stop is applied.
        synchronized(micSendLock) {
            active.set(false)
            microphoneMuted=false
        }
        status=state
        message=reason
        main.removeCallbacks(maxSession)
        main.removeCallbacks(idleCheck)
        main.removeCallbacks(handshakeTimeout)
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        captureThread?.interrupt()
        captureThread = null
        playbackThread?.interrupt()
        playbackThread = null
        playbackTurn.incrementAndGet()
        audioQueue.clear()
        try { echoCanceler?.release() } catch (_: Exception) {}
        echoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        try { audioManager?.abandonAudioFocus(audioFocusListener) } catch (_: Exception) {}
        audioManager = null
        synchronized(playbackLock) {
            try { player?.pause(); player?.flush(); player?.release() } catch (_: Exception) {}
        }
        player = null
        // Resume detection only after both live capture and playback release;
        // an already-visible assistant panel still keeps the wake mic paused.
        NexaWakeWordService.resumeAfterLiveVoice()
        try { sarvamTransport?.close() } catch (_: Exception) {}
        sarvamTransport=null
        pendingAppTool = null
        pendingApprovalSummary = null
        assistantPanelEnabled = false
        lastToolResult = ""
        seenCallIds.clear()
        toolCallsInSession = 0
        try { socket?.close(1000,"owner-stop") } catch (_: Exception) {}
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
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
                // The visible React Native Voice tab exposes Approve/Deny.
                putExtra(RealtimeVoiceService.EXTRA_ASSISTANT_PANEL, true)
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
    @ReactMethod fun setVoiceMicrophoneMuted(muted: Boolean,p: Promise) {
        if (RealtimeVoiceService.status!="listening" &&
            RealtimeVoiceService.status!="connecting") {
            p.reject("VOICE_NOT_ACTIVE","Start the live voice session before muting")
            return
        }
        if(context.currentActivity?.hasWindowFocus()!=true) {
            p.reject("NOT_FOREGROUND","Change microphone state from the visible Voice screen")
            return
        }
        try {
            context.startService(Intent(context,RealtimeVoiceService::class.java).apply {
                action=if(muted)RealtimeVoiceService.ACTION_MUTE
                    else RealtimeVoiceService.ACTION_UNMUTE
            })
            p.resolve(true)
        }catch (_:Exception) {
            p.reject("MIC_MUTE_FAILED","Android rejected microphone mute request")
        }
    }
    @ReactMethod fun decideVoiceAppAction(approve: Boolean,p: Promise) {
        // No background or remote invocation may approve a proposed app
        // launch. This action is initiated by one visible owner tap only.
        if(context.currentActivity?.hasWindowFocus()!=true) {
            p.reject("NOT_FOREGROUND","Open Nexa Voice to review the pending action")
            return
        }
        if(RealtimeVoiceService.pendingApprovalSummary == null ||
            RealtimeVoiceService.status!="listening") {
            p.reject("NO_PENDING_APP","No live voice app action is awaiting review")
            return
        }
        try {
            context.startService(Intent(context,RealtimeVoiceService::class.java).apply {
                action=if(approve)RealtimeVoiceService.ACTION_APPROVE_TOOL
                    else RealtimeVoiceService.ACTION_DENY_TOOL
            })
            p.resolve(true)
        } catch (_: Exception) {
            p.reject("VOICE_APPROVAL_FAILED","Android rejected the app approval request")
        }
    }
    @ReactMethod fun getVoiceSessionStatus(p: Promise) {
        val data=Arguments.createMap()
        data.putString("state",RealtimeVoiceService.status)
        data.putString("message",RealtimeVoiceService.message)
        data.putBoolean("microphoneActive",RealtimeVoiceService.status=="listening")
        data.putBoolean("microphoneMuted",RealtimeVoiceService.microphoneMuted)
        data.putString("pendingAppApproval",RealtimeVoiceService.pendingApprovalSummary)
        data.putString("lastToolResult",RealtimeVoiceService.lastToolResult)
        p.resolve(data)
    }
}
