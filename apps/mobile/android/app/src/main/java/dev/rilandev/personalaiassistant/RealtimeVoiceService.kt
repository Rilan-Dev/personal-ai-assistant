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
 * The owner-started provider stream exposes permission-gated Android tools.
 * Model tool requests never bypass Android Accessibility or visible one-time approval.
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
        const val ACTION_APPROVE_ALWAYS_TOOL = "dev.rilandev.personalaiassistant.TOOL_APPROVE_ALWAYS"
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
        @Volatile var currentProviderId = ""
            private set
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
    private var sarvamApiTransport: SarvamApiVoiceTransport? = null
    private var selectedProvider = "openai"
    @Volatile private var assistantPanelEnabled = false
    private data class PendingAppTool(
        val callId: String, val app: String, val command: String,
        val installedApp: NexaPhoneVoiceTools.AppChoice? = null,
        val createdAtMs: Long = SystemClock.elapsedRealtime()
    )
    private var pendingAppTool: PendingAppTool? = null
    private var currentCompound: NexaVoiceTaskCursor? = null
    // A model function name is not owner authority: this short-lived, one-use
    // identity is minted only from an exact user microphone transcript.
    private var ownerFactSpeech: Pair<String,Long>? = null
    private var taskRequestEpoch = 0L
    private var activeToolOperation = false
    private var voiceSessionId = "voice-" + java.util.UUID.randomUUID().toString()
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
    private val audioQueue = ArrayBlockingQueue<PlaybackChunk>(96)
    private val playbackTurn = AtomicLong(0L)
    private val playbackLock = Any()
    @Volatile private var responseId = ""
    // Keep sending AEC-filtered mic input while the model speaks; otherwise
    // the server VAD never hears the owner interrupting (broken barge-in).
    @Volatile private var interruptedResponse = false
    private val maxSession = Runnable { stopWithStatus("stopped","60-minute session limit reached") }
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
            stopWithStatus("error","Live voice provider did not accept session within 25 seconds")
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWithStatus("stopped","Stopped by owner")
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_APPROVE_TOOL || intent?.action == ACTION_APPROVE_ALWAYS_TOOL || intent?.action == ACTION_DENY_TOOL) {
            val pending = pendingAppTool
            if (active.get() && assistantPanelEnabled && pending != null && !activeToolOperation) {
                pendingAppTool = null
                pendingApprovalSummary = null
                val approved = intent.action != ACTION_DENY_TOOL
                if(approved && intent.action==ACTION_APPROVE_ALWAYS_TOOL) {
                    if(NexaVoicePermissionPolicy.alwaysAllow(this,pending.command) &&
                        NexaVoicePermissionPolicy.mode(this)==NexaVoicePermissionPolicy.ASK_EVERY_TIME)
                        NexaVoicePermissionPolicy.setMode(this,NexaVoicePermissionPolicy.TRUST_SAFE)
                }
                // A visible approval tap authorizes ONE bounded action, not a
                // stale request from an earlier conversation.
                val age = SystemClock.elapsedRealtime() - pending.createdAtMs
                val operationSession=voiceSessionId
                activeToolOperation = true
                // Accessibility traversal, crypto journaling, and OEM binder clicks must
                // never execute on the foreground service/main looper (ANR risk).
                Thread({
                    val task=currentCompound
                    val checkpointOk=task==null || NexaConversationDb.saveTask(
                        this,task.taskId,operationSession,"executing",task.stepIndex,
                        task.originalRequest,task.steps,"Owner reviewed step")
                    val outcome = try { when {
                    !checkpointOk -> "Task checkpoint could not be saved. No action executed."
                    !active.get() || operationSession != voiceSessionId -> "Session stopped; no action performed."
                    !approved -> "Denied by owner. No device operation performed."
                    age !in 0..60_000L -> "Approval expired. Request it again."
                    pending.command.startsWith("chrome_search:") ->
                        NexaBrowserTaskActions.openChromeSearchApproved(
                            this,pending.command.removePrefix("chrome_search:"))
                    pending.command == "open_notification_shade" ->
                        NexaBrowserTaskActions.notificationsApproved(this)
                    pending.command == "go_back" ->
                        NexaBrowserTaskActions.goBackApproved(this)
                    pending.command == "list_installed_apps" ->
                        NexaPhoneVoiceTools.installedAppsApproved(this)
                    pending.command.startsWith("open_whatsapp_chat:") ->
                        ScreenAssistService.active?.let { service ->
                            NexaWhatsAppChatActions.openApproved(service,
                                pending.command.removePrefix("open_whatsapp_chat:"))
                        } ?: "Screen Assist is disabled. No chat opened."
                    pending.command.startsWith("tap_visible_label:") ->
                        ScreenAssistService.active?.let { service ->
                            NexaScreenLabelTap.approved(service,pending.command.removePrefix("tap_visible_label:"))
                        } ?: "Screen Assist is disabled. No tap executed."
                    pending.command == "read_visible_screen_text" ->
                        NexaScreenVoiceOverview.readVisibleTextApproved()
                    pending.command.startsWith("scroll_screen:") ->
                        NexaScreenVoiceOverview.scrollApproved(pending.command.removePrefix("scroll_screen:"))
                    pending.command.startsWith("tap_safe_icon:") ->
                        NexaScreenVoiceOverview.tapSafeApproved(pending.command.removePrefix("tap_safe_icon:"))
                    pending.command == "observe_screen_icons" -> NexaScreenVoiceOverview.readApproved()
                    pending.command.startsWith("locate_icon:") ->
                        NexaScreenVoiceOverview.findApproved(pending.command.removePrefix("locate_icon:"))
                    pending.installedApp != null ->
                        NexaPhoneVoiceTools.launchApproved(this,pending.installedApp)
                    else -> NexaOverlayCommands.handle(this,pending.command)
                } } catch (failure:Exception) {
                    "Android refused the approved screen action ("+
                        failure.javaClass.simpleName+"). No automatic retry."
                }
                main.post {
                  activeToolOperation=false
                  if(!active.get() || operationSession != voiceSessionId)return@post
                  lastToolResult=outcome.take(320)
                message = lastToolResult
                if (selectedProvider == "sarvam-api") {
                    if(pending.callId.startsWith("sarvam-model-")) {
                        sarvamApiTransport?.completeNativeTool(
                            pending.callId.removePrefix("sarvam-model-"),lastToolResult)
                    }else sarvamApiTransport?.toolResult(lastToolResult)
                }
                if (selectedProvider == "openai") {
                    sendRealtimeToolOutput(pending.callId, lastToolResult)
                    socket?.send(JSONObject().put("type", "response.create").toString())
                }
                advanceCompoundAfterVerifiedAction(pending,outcome,approved)
                }
                },"nexa-reviewed-action").start()
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
            stopWithStatus("error","Save a supported Model API key and enable voice streaming consent in Nexa Settings")
            return START_NOT_STICKY
        }
        microphoneMuted=false
        assistantPanelEnabled = intent.getBooleanExtra(EXTRA_ASSISTANT_PANEL, false)
        pendingAppTool = null
        currentCompound=null
        ownerFactSpeech=null
        taskRequestEpoch++
        activeToolOperation=false
        pendingApprovalSummary = null
        lastToolResult = ""
        voiceSessionId = "voice-" + java.util.UUID.randomUUID().toString()
        lastLocalUtterance = ""
        lastLocalUtteranceAt = 0L
        micReleaseRetries = 0
        seenCallIds.clear()
        toolCallsInSession = 0
        idleGate.reset(0L)
        selectedProvider=credentials.provider
        currentProviderId=selectedProvider
        activeRate=if(selectedProvider=="sarvam-api" || selectedProvider=="sarvam")16000 else RATE
        // Stop the separate offline wake AudioRecord before attempting a
        // second microphone capture. The wake service remains owner-armed,
        // but may not record concurrently with the live session.
        NexaWakeWordService.pauseForLiveVoice()
        active.set(true)
        status="connecting"
        message="Connecting to "+(if(selectedProvider=="sarvam-api")"Sarvam API realtime voice" else if(selectedProvider=="sarvam")"Sarvam Voice Agents" else "OpenAI")
        main.postDelayed(maxSession,60*60*1000L)
        main.postDelayed(handshakeTimeout,25_000L)
        try {
        if(selectedProvider=="sarvam-api") {
            val transport=SarvamApiVoiceTransport(applicationContext,credentials.apiKey,
                object:SarvamApiVoiceTransport.Callbacks {
                    override fun connected() {
                        main.post { if(active.get() && status=="connecting")startAudio() }
                    }
                    override fun audio(pcm16:ByteArray) {
                        if(!active.get())return
                        idleGate.reset(SystemClock.elapsedRealtime())
                        val chunk=PlaybackChunk(playbackTurn.get(),pcm16)
                        if(!audioQueue.offer(chunk)) {
                            audioQueue.poll()
                            audioQueue.offer(chunk)
                        }
                    }
                    override fun interrupted() {
                        idleGate.reset(SystemClock.elapsedRealtime())
                        interruptSpeakerPlayback()
                        main.post {
                            if(pendingAppTool?.callId?.startsWith("sarvam-model-")==true){
                                pendingAppTool=null
                                pendingApprovalSummary=null
                                message="Old model request cancelled by new speech."
                            }
                        }
                    }
                    override fun ownerRequest(text:String):Boolean {
                        if(!assistantPanelEnabled)return false
                        val task=NexaOwnerTaskReview.parse(text)
                        if(task!=null){
                            val session=voiceSessionId
                            main.post {
                                if(active.get() && selectedProvider=="sarvam-api" &&
                                    session==voiceSessionId) {
                                    message=if(NexaOwnerTaskReview.offerInForeground(
                                            this@RealtimeVoiceService,task,"live-voice"))
                                        "Task ready for owner review in Nexa. Nothing executed."
                                    else "Task review is unavailable or already pending. Nothing executed."
                                }
                            }
                            return true
                        }
                        val recognizable=try {
                            NexaVoiceTaskPlan.parse(text)!=null ||
                            NexaWhatsAppChatIntent.followupRecipient(text)!=null ||
                            NexaGeneralMessageVoice.parse(text)!=null ||
                            NexaVoiceTextIntent.parse(text)!=null ||
                            NexaPhoneVoiceTools.wantsVisibleText(text) ||
                            NexaPhoneVoiceTools.wantsInstalledApps(text) ||
                            NexaPhoneVoiceTools.wantsScreenHelp(text) ||
                            NexaPhoneVoiceTools.tapVisibleLabelIntent(text)!=null ||
                            NexaPhoneVoiceTools.scrollDirection(text)!=null ||
                            NexaPhoneVoiceTools.clickSafeIcon(text)!=null ||
                            NexaPhoneVoiceTools.wantsScreenOverview(text) ||
                            NexaPhoneVoiceTools.locateIconIntent(text)!=null ||
                            NexaVoiceIntentGate.appRequest(text)!=null ||
                            NexaPhoneVoiceTools.deviceFacts(this@RealtimeVoiceService,text)!=null ||
                            NexaPhoneVoiceTools.findLaunchableApp(this@RealtimeVoiceService,text)!=null
                        }catch (_:Exception){false}
                        if(!recognizable)return false
                        main.post {
                            if(active.get() && selectedProvider=="sarvam-api")
                                handleSarvamOwnerRequest(text)
                        }
                        return true
                    }
                    override fun requestNativeTool(name:String,args:String,
                                                    callId:String,utterance:String):Boolean {
                        if(!assistantPanelEnabled || selectedProvider!="sarvam-api" ||
                            !callId.matches(Regex("[A-Za-z0-9_-]{1,128}")))return false
                        main.post {
                            if(!active.get() || selectedProvider!="sarvam-api")return@post
                            if(pendingAppTool!=null || activeToolOperation) {
                                sarvamApiTransport?.completeNativeTool(callId,
                                    "Rejected: finish or deny the previous action first.")
                                return@post
                            }
                            if(++toolCallsInSession>40) {
                                sarvamApiTransport?.completeNativeTool(callId,
                                    "Rejected: voice device action budget exhausted.")
                                return@post
                            }
                            // Sarvam model tool calls do not carry a verified owner
                            // speech identity. Exact owner-spoken reads are routed
                            // through handleSarvamOwnerRequest and the coordinator.
                            if(name=="get_battery_level" || name=="get_storage_usage") {
                                sarvamApiTransport?.completeNativeTool(callId,
                                    "Use an explicit owner request for a native device reading.")
                                return@post
                            }
                            when(val decision=NexaLiveToolPolicy.decide(
                                this@RealtimeVoiceService,name,args)){
                                is NexaLiveToolPolicy.Decision.Immediate -> {
                                    sarvamApiTransport?.completeNativeTool(
                                        callId,decision.result)
                                }
                                is NexaLiveToolPolicy.Decision.NeedsApproval -> {
                                    // This is a model-proposed action, NOT an
                                    // implicit "always approve" permission.
                                    val pending=PendingAppTool("sarvam-model-$callId",
                                        decision.app,decision.command,decision.installedApp)
                                    pendingAppTool=pending
                                    pendingApprovalSummary=when {
                                        decision.command=="read_visible_screen_text" ->
                                            "The assistant requests visible screen labels. Share once with Sarvam? Allow once or Deny."
                                        decision.command=="list_installed_apps" ->
                                            "The assistant requests launcher app names. Share once with Sarvam? Allow once or Deny."
                                        else -> "Model suggested: "+decision.app.take(65)+
                                            ". Allow one reviewed Android action or Deny."
                                    }
                                    message="Review suggested tool: "+name.take(55)
                                    offerOwnerTool(pending)
                                }
                            }
                        }
                        return true
                    }
                    override fun failed(reason:String) {
                        main.post { if(active.get())stopWithStatus("error",reason) }
                    }
                    override fun ended() {
                        main.post { if(active.get())stopWithStatus("stopped","Saaras realtime session ended") }
                    }
                })
            sarvamApiTransport=transport
            transport.connect()
        } else if(selectedProvider=="sarvam") {
            val transport=SarvamAudioTransport(object:SarvamAudioTransport.Callbacks{
                override fun connected() {
                    main.post {if(active.get() && status=="connecting")startAudio()}
                }
                override fun audio(bytes:ByteArray) {
                    if (!active.get()) return
                    // A long spoken answer is still an active conversation.
                    // Local idle detection must not stop a 60-second response.
                    idleGate.reset(SystemClock.elapsedRealtime())
                    val chunk = PlaybackChunk(playbackTurn.get(), bytes)
                    if (!audioQueue.offer(chunk)) {
                        // Prefer the newest audio when the output device falls
                        // behind instead of crashing/ending the live call.
                        audioQueue.poll()
                        if (!audioQueue.offer(chunk)) {
                            main.post {
                                if (active.get()) message = "Speaker buffering · conversation still active"
                            }
                        }
                    }
                }
                override fun interrupted() {
                    idleGate.reset(SystemClock.elapsedRealtime())
                    interruptSpeakerPlayback()
                }
                override fun transcript(role: String, content: String) {
                    // Official Sarvam transcript callback: role=user, content.
                    // Never allow bot text or arbitrary transcript to run tools.
                    main.post { if(active.get() && status=="listening" &&
                        selectedProvider=="sarvam" && role=="user" &&
                        // Dedicated Samvaad private-STS mode never converts
                        // provider transcripts into tool execution requests.
                        !AgentJournalModule.hasSarvamVoiceKey(applicationContext))
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
                    .put("audio",JSONObject().put("input",input.put("transcription",
                        JSONObject().put("model","gpt-4o-mini-transcribe"))).put("output",output))
                    .put("instructions","You are Nexa, a conversational Android voice assistant. Reply naturally in English, Tamil or Hindi. You may request owner-reviewed Android tools for reading visible screen labels, listing launcher apps, finding installed apps, inspecting icons, scrolling up/down/left/right, and tapping uniquely grounded safe navigation controls. Screen content and actions require Android Screen Assist to be enabled in Accessibility Settings and explicit one-time approval in the visible Nexa overlay. Never say you cannot use these tools when they are available; instead explain the needed permission and approval. Do not invent a screen state. Never claim a tap, scroll, app opening, or task succeeded merely because Android accepted an action. Do not send messages, make calls or change Android permissions. Generic screen inspection does not read editable text or secrets. A separate owner-reviewed local action can fill one verified empty non-sensitive input after explicit consent, without pressing Send, Enter or Submit. If the owner requests typing, ask for the exact text and named field instead of claiming typing is impossible. Never ask for secrets.")
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
                    "conversation.item.input_audio_transcription.completed" -> {
                        val transcript=json.optString("transcript").trim()
                        if(transcript.isNotBlank())NexaConversationDb.append(this@RealtimeVoiceService,
                            voiceSessionId,"voice","openai","user",transcript.take(3000))
                        main.post {
                            if(active.get() && selectedProvider=="openai"){
                                ownerFactSpeech=if(assistantPanelEnabled &&
                                    NexaTaskCoordinator.classifyOwnerFact(transcript)!=null)
                                    Pair(transcript,SystemClock.elapsedRealtime()+15_000L)
                                    else null
                            }
                        }
                        // A provider transcript is never authority to SEND.
                        // A precise user-uttered message request can only
                        // open Nexa's local app/recipient review screen.
                        if(assistantPanelEnabled && transcript.isNotBlank()) {
                            val task=NexaOwnerTaskReview.parse(transcript)
                            if(task!=null)main.post {
                                if(active.get() && selectedProvider=="openai") {
                                    message=if(NexaOwnerTaskReview.offerInForeground(
                                            this@RealtimeVoiceService,task,"live-voice"))
                                        "Task ready for owner review in Nexa. Nothing executed."
                                    else "Task review is unavailable or already pending. Nothing executed."
                                }
                            }
                            val field=NexaVoiceTextIntent.parse(transcript)
                            if(field!=null)main.post {
                                if(active.get() && selectedProvider=="openai"){
                                    NexaVoiceTextIntent.queue(field)
                                    try {
                                        startActivity(Intent(this@RealtimeVoiceService,MainActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                    }catch(_:Exception){NexaVoiceTextIntent.clear()}
                                }
                            }
                            val request=NexaGeneralMessageVoice.parse(transcript)
                            if(request!=null) main.post {
                                if(active.get() && selectedProvider=="openai") {
                                    NexaGeneralMessageVoice.queue(request)
                                    try {
                                        startActivity(Intent(this@RealtimeVoiceService,MainActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                        message="Message review opened. Choose app, contact and exact text. Nothing sent."
                                    } catch (_:Exception) {
                                        message="Message review must be opened manually. No message sent."
                                    }
                                }
                            }
                        }
                    }
                    "response.output_audio_transcript.done","response.output_text.done" -> {
                        val transcript=json.optString("transcript",
                            json.optString("text")).trim()
                        if(transcript.isNotBlank())NexaConversationDb.append(this@RealtimeVoiceService,
                            voiceSessionId,"voice","openai","assistant",transcript.take(3000))
                    }
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
    /** Pending approvals expire without leaving a call or action blocked.
     * Only the exact current instance may expire itself; Stop and replacement
     * invalidate it. An expiry is a rejection, never an automatic action.
     */
    private fun expirePendingTool(tool:PendingAppTool) {
        main.postDelayed({
            if(!active.get() || pendingAppTool !== tool)return@postDelayed
            pendingAppTool=null
            pendingApprovalSummary=null
            lastToolResult="Approval expired after 60 seconds. Nothing was opened."
            message=lastToolResult
            if(selectedProvider=="sarvam-api") {
                if(tool.callId.startsWith("sarvam-model-")) {
                    sarvamApiTransport?.completeNativeTool(
                        tool.callId.removePrefix("sarvam-model-"),lastToolResult)
                }else sarvamApiTransport?.toolResult(lastToolResult)
            } else if(selectedProvider=="openai") {
                sendRealtimeToolOutput(tool.callId,lastToolResult)
                socket?.send(JSONObject().put("type","response.create").toString())
            }
        },60_000L)
    }

    /** Persist a task before any dependent step. On failure, stop safely. */
    private fun checkpointAndContinue(cursor:NexaVoiceTaskCursor,status:String,
                                      evidence:String,next:()->Unit) {
        val session=voiceSessionId
        Thread({
            val saved=NexaConversationDb.saveTask(this,cursor.taskId,session,status,
                cursor.stepIndex,cursor.originalRequest,cursor.steps,evidence.take(350))
            main.post {
                if(currentCompound!==cursor || session!=voiceSessionId || !active.get())
                    return@post
                if(saved)next()
                else {
                    cursor.cancel();currentCompound=null
                    sarvamApiTransport?.toolResult(
                        "Secure task checkpoint unavailable. Stopped before next action.")
                }
            }
        },"nexa-task-checkpoint").start()
    }

    /** Never falsely claim a failed or uncertain device dispatch completed a plan. */
    private fun closeCompound(cursor:NexaVoiceTaskCursor,status:String,evidence:String) {
        if(currentCompound===cursor)currentCompound=null
        val index=cursor.stepIndex
        cursor.cancel()
        val session=voiceSessionId
        Thread({
            NexaConversationDb.saveTask(this,cursor.taskId,session,status,index,
                cursor.originalRequest,cursor.steps,evidence.take(350))
        },"nexa-task-terminal").start()
    }

    /** Slow OEM launches may expose the old window briefly. Retry only
     * READ-ONLY foreground observation, never the app launch or tap. */
    private fun verifyCompoundAppForeground(cursor:NexaVoiceTaskCursor,
                                            expected:String,session:String,attempt:Int=0) {
        val delays=longArrayOf(800L,1400L,2200L)
        if(attempt !in delays.indices)return
        main.postDelayed({
            if(!active.get() || currentCompound!==cursor || voiceSessionId!=session)
                return@postDelayed
            Thread({
                val root=try{ScreenAssistService.active?.externalWindowRoot()}
                    catch(_:Exception){null}
                val pkg=try{root?.packageName?.toString()}
                    catch(_:Exception){null}
                    finally{try{root?.recycle()}catch(_:Exception){}}
                main.post {
                    if(!active.get() || currentCompound!==cursor ||
                        voiceSessionId!=session)return@post
                    if(pkg!=expected) {
                        if(attempt<delays.lastIndex)
                            verifyCompoundAppForeground(cursor,expected,session,attempt+1)
                        else {
                            closeCompound(cursor,"uncertain",
                                "Foreground package not verified after bounded observations")
                            sarvamApiTransport?.toolResult(
                                "Android could not verify the requested app on screen. No further action.")
                        }
                        return@post
                    }
                    val next=cursor.advance(true)
                    checkpointAndContinue(cursor,
                        if(cursor.finished)"completed" else "verified",
                        "Foreground package verified: "+expected) {
                        if(next==null)currentCompound=null
                        else handleSarvamOwnerRequest(next,true)
                    }
                }
            },"nexa-task-app-verification").start()
        },delays[attempt])
    }

    private fun advanceCompoundAfterVerifiedAction(tool:PendingAppTool,outcome:String,
                                                   approved:Boolean) {
        val cursor=currentCompound ?: return
        if(!approved || outcome.startsWith("Rejected") ||
            outcome.contains("could not",true) || outcome.contains("refused",true) ||
            outcome.contains("No visible",true) || outcome.contains("not saved",true) ||
            outcome.contains("No action",true)) {
            closeCompound(cursor,"failed",outcome)
            return
        }
        if(tool.command.startsWith("open_whatsapp_chat:")) {
            // ACTION_CLICK alone never completes a chat-navigation task.
            // Verify the actual new WhatsApp conversation header + composer
            // after Android updates the foreground Accessibility window.
            if(!outcome.startsWith("Android accepted a uniquely grounded WhatsApp chat-row tap") &&
                !outcome.startsWith("Matching chat header and composer are already visible")) {
                closeCompound(cursor,"unverified",outcome)
                return
            }
            val name=tool.command.removePrefix("open_whatsapp_chat:")
            val session=voiceSessionId
            main.postDelayed({
                if(!active.get() || currentCompound!==cursor || voiceSessionId!=session)
                    return@postDelayed
                Thread({
                    val verified=NexaWhatsAppChatActions.verifiedOpen(
                        ScreenAssistService.active,name)
                    main.post {
                        if(!active.get() || currentCompound!==cursor ||
                            voiceSessionId!=session)return@post
                        if(!verified) {
                            closeCompound(cursor,"unverified",
                                "WhatsApp conversation header not independently verified")
                            sarvamApiTransport?.toolResult(
                                "Chat-row tap was not confirmed in WhatsApp. No further action.")
                            return@post
                        }
                        val next=cursor.advance(true)
                        checkpointAndContinue(cursor,
                            if(cursor.finished)"completed" else "verified",
                            "Matching WhatsApp chat header and composer verified") {
                            if(next==null){
                                currentCompound=null
                                sarvamApiTransport?.toolResult(
                                    "WhatsApp conversation header and message input are verified. Ready to review a draft. Nothing typed or sent.")
                            }else handleSarvamOwnerRequest(next,true)
                        }
                    }
                },"nexa-chat-verification").start()
            },1200L)
            return
        }
        val isAppOpen=tool.command.startsWith("open ") || tool.command=="open" ||
            tool.command.startsWith("chrome_search:")
        if(isAppOpen) {
            if(!outcome.contains("launch requested",true)) {
                closeCompound(cursor,"uncertain",outcome)
                return
            }
            val expected=tool.installedApp?.packageName ?: when {
                tool.command.startsWith("chrome_search:")->"com.android.chrome"
                tool.command.contains("whatsapp",true)->"com.whatsapp"
                tool.command.contains("github",true)->"com.github.android"
                tool.command.contains("brave",true)->"com.brave.browser"
                else -> ""
            }
            if(expected.isBlank()) {
                closeCompound(cursor,"uncertain","Unknown launched package")
                return
            }
            verifyCompoundAppForeground(cursor,expected,voiceSessionId)
            return
        }
        val verified=outcome.startsWith("Visible screen labels") ||
            outcome.startsWith("Visible controls") ||
            outcome.startsWith("Installed launcher apps")
        if(!verified) {
            closeCompound(cursor,"uncertain",outcome)
            return
        }
        val next=cursor.advance(true)
        checkpointAndContinue(cursor,
            if(cursor.finished)"completed" else "verified",outcome.take(350)) {
            if(next==null) currentCompound=null
            else handleSarvamOwnerRequest(next,true)
        }
    }

    private fun offerOwnerTool(tool:PendingAppTool) {
        expirePendingTool(tool)
        // An app discovered by fuzzy launcher label matching may be wrong.
        // Always show its exact target for review, even in trusted navigation.
        if(tool.command=="open" && tool.installedApp!=null)return
        if(selectedProvider=="sarvam-api" && NexaVoicePermissionPolicy.canExecute(this,tool.command)) {
            // Current user request plus persistent low-risk grant, never model-only
            // arbitrary permission bypass. High-risk classes cannot be whitelisted.
            main.post { if(active.get() && pendingAppTool===tool)
                onStartCommand(Intent(this,RealtimeVoiceService::class.java).apply{
                    action=ACTION_APPROVE_TOOL
                },0,0)
            }
        }
    }

    private fun handleSarvamOwnerRequest(rawTranscript: String, fromPlan:Boolean=false) {
        if (!assistantPanelEnabled || pendingAppTool != null || activeToolOperation)return
        if(!fromPlan) {
            taskRequestEpoch++
            currentCompound?.let { closeCompound(it,"cancelled","New owner request") }
        }
        if(!fromPlan && NexaTaskCoordinator.classifyOwnerReadOnlyPlan(rawTranscript)!=null) {
            val session=voiceSessionId
            val epoch=taskRequestEpoch
            Thread({
                val result=NexaTaskCoordinator.executeOwnerReadOnlyPlan(
                    this@RealtimeVoiceService,rawTranscript,"live-voice")
                main.post {
                    if(active.get() && selectedProvider=="sarvam-api" &&
                        session==voiceSessionId && epoch==taskRequestEpoch) {
                        lastToolResult=result.take(320)
                        message=lastToolResult
                        sarvamApiTransport?.toolResult(result)
                    }
                }
            },"nexa-sarvam-read-plan").start()
            return
        }
        val plan=if(fromPlan)null else NexaVoiceTaskPlan.parse(rawTranscript)
        if(plan!=null){
            val cursor=NexaVoiceTaskCursor(plan)
            val session=voiceSessionId
            val epoch=taskRequestEpoch
            Thread({
                val saved=NexaConversationDb.saveTask(this,cursor.taskId,session,
                    "planned",0,cursor.originalRequest,cursor.steps)
                main.post {
                    if(!active.get() || !assistantPanelEnabled || session!=voiceSessionId ||
                        taskRequestEpoch!=epoch || pendingAppTool!=null) return@post
                    if(!saved) {
                        sarvamApiTransport?.toolResult(
                            "Secure task journal unavailable. No device action started.")
                        return@post
                    }
                    currentCompound=cursor
                    handleSarvamOwnerRequest(cursor.steps.first(),true)
                }
            },"nexa-task-start").start()
            return
        }
        val transcript=rawTranscript
        val now = SystemClock.elapsedRealtime()
        if (transcript == lastLocalUtterance &&
            now - lastLocalUtteranceAt < 12_000L) return
        if (++toolCallsInSession > 40) {
            lastToolResult = "Voice device-action limit reached"
            message = lastToolResult
            return
        }
        lastLocalUtterance = transcript
        lastLocalUtteranceAt = now
        val textIntent=NexaVoiceTextIntent.parse(transcript)
        if(textIntent!=null){
            NexaVoiceTextIntent.queue(textIntent)
            val opened=try{
                startActivity(Intent(this,MainActivity::class.java).apply{
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                true
            }catch(_:Exception){false}
            lastToolResult=if(opened)
                "Nexa text-field review opened. Find the correct empty field and approve typing. No submit."
                else "Nexa could not open text-field review. Nothing typed or submitted."
            message=lastToolResult
            sarvamApiTransport?.toolResult(lastToolResult)
            return
        }
        val whatsappChat=NexaWhatsAppChatIntent.followupRecipient(transcript)
        if(whatsappChat!=null) {
            if(!ScreenAssistState.connected || ScreenAssistService.active==null) {
                lastToolResult="Enable Nexa Screen Assist before opening a WhatsApp chat. No chat opened."
                message=lastToolResult
                sarvamApiTransport?.toolResult(lastToolResult)
                return
            }
            pendingAppTool=PendingAppTool("local-sarvam",
                "WhatsApp chat "+whatsappChat,"open_whatsapp_chat:"+whatsappChat)
            pendingAppTool?.let{offerOwnerTool(it)}
            pendingApprovalSummary="Open the one uniquely verified WhatsApp chat matching the requested contact? Approve once or Deny. No text will be read or sent."
            message="Contact navigation is ready for one owner review."
            return
        }
        val messageIntent=NexaGeneralMessageVoice.parse(transcript)
        if(messageIntent!=null) {
            // Speech supplies a local message draft request, not authorization
            // to choose a channel, tap a recipient or send any external text.
            NexaGeneralMessageVoice.queue(messageIntent)
            val opened=try {
                startActivity(Intent(this,MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                true
            }catch(_:Exception){false}
            lastToolResult=if(opened)
                "Nexa message review opened. Select the app and confirm the recipient. Nothing sent."
                else "Message held for local review. Open Nexa to choose an app; nothing sent."
            message=lastToolResult
            sarvamApiTransport?.toolResult(lastToolResult)
            return
        }
        val chromeQuery=NexaPhoneVoiceTools.chromeSearchQuery(transcript)
        if(chromeQuery!=null) {
            pendingAppTool=PendingAppTool("local-sarvam","Chrome search for $chromeQuery",
                "chrome_search:$chromeQuery")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Open Chrome Google search results for '$chromeQuery'? Approve once or Deny. Article selection is a separate request."
            message="Public Chrome search awaits owner approval."
            return
        }
        if(NexaPhoneVoiceTools.wantsNotificationShade(transcript)) {
            pendingAppTool=PendingAppTool("local-sarvam","notification shade",
                "open_notification_shade")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Open Android's notification shade? No notification contents will be read. Approve or Deny."
            message="Notification shade opening awaits owner approval."
            return
        }
        if(NexaPhoneVoiceTools.wantsGoBack(transcript)) {
            pendingAppTool=PendingAppTool("local-sarvam","back navigation","go_back")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Navigate Back once? Approve or Deny."
            message="Back navigation awaits owner approval."
            return
        }
        val screenOperation = NexaPhoneVoiceTools.wantsVisibleText(transcript) ||
            NexaPhoneVoiceTools.tapVisibleLabelIntent(transcript)!=null ||
            NexaPhoneVoiceTools.scrollDirection(transcript)!=null ||
            NexaPhoneVoiceTools.clickSafeIcon(transcript)!=null ||
            NexaPhoneVoiceTools.wantsScreenOverview(transcript) ||
            NexaPhoneVoiceTools.locateIconIntent(transcript)!=null
        if(screenOperation && (!ScreenAssistState.connected || ScreenAssistService.active==null)) {
            lastToolResult="Screen Assist is disabled. In Nexa, open Tools and enable Screen Assist in Android Accessibility Settings, then return to your app. No screen action was performed."
            message=lastToolResult
            sarvamApiTransport?.toolResult(lastToolResult)
            return
        }
        if(NexaPhoneVoiceTools.wantsInstalledApps(transcript)) {
            pendingAppTool=PendingAppTool("local-sarvam","installed launcher apps","list_installed_apps")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Share a limited list of installed launcher-app names with your voice provider? Approve once or Deny."
            message="App-list reading awaits your approval."
            return
        }
        if(NexaPhoneVoiceTools.wantsVisibleText(transcript)) {
            pendingAppTool=PendingAppTool("local-sarvam","screen text","read_visible_screen_text")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Read visible app labels and share the result with your voice provider? Approve once or Deny."
            message="Screen reading awaits your approval. Text fields and passwords are excluded."
            return
        }
        val scroll=NexaPhoneVoiceTools.scrollDirection(transcript)
        if(scroll!=null) {
            pendingAppTool=PendingAppTool("local-sarvam","scroll $scroll","scroll_screen:$scroll")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Scroll $scroll once in the visible app? Approve once or Deny."
            message="One accessibility scroll request awaits approval."
            return
        }
        val icon=NexaPhoneVoiceTools.clickSafeIcon(transcript)
        if(icon!=null) {
            pendingAppTool=PendingAppTool("local-sarvam","$icon control","tap_safe_icon:$icon")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Tap the visible $icon navigation icon once? Approve once or Deny."
            message="A fresh accessibility target will be checked after approval."
            return
        }
        val label=NexaPhoneVoiceTools.tapVisibleLabelIntent(transcript)
        if(label!=null) {
            pendingAppTool=PendingAppTool("local-sarvam",label,"tap_visible_label:$label")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Tap the uniquely visible control named $label once? Approve or Deny. No raw coordinates are trusted."
            message="A screen-grounded labeled tap awaits approval."
            return
        }
        if(NexaPhoneVoiceTools.wantsScreenHelp(transcript)) {
            val help=if(ScreenAssistState.connected && ScreenAssistService.active!=null)
                "Screen Assist is enabled. Say read my screen, scroll left, scroll right, scroll up, scroll down, tap menu, or open a named app. Review each proposed action in the Nexa overlay."
                else "Enable Nexa Screen Assist through Tools, then Android Accessibility Settings. After enabling, say read my screen or scroll down and approve the action in the Nexa overlay."
            lastToolResult=help
            message=help
            sarvamApiTransport?.toolResult(help)
            return
        }
        // A literal owner-spoken battery/storage request is handled by the
        // same durable Kotlin coordinator as Chat. Do not take a second read.
        if(!fromPlan && NexaTaskCoordinator.classifyOwnerFact(transcript)!=null) {
            val session=voiceSessionId
            Thread({
                val result=NexaTaskCoordinator.executeOwnerDeviceRead(
                    this@RealtimeVoiceService,transcript,"live-voice")
                main.post {
                    if(active.get() && selectedProvider=="sarvam-api" &&
                        voiceSessionId==session) {
                        lastToolResult=result.take(320)
                        message=lastToolResult
                        sarvamApiTransport?.toolResult(result)
                    }
                }
            },"nexa-sarvam-task-read").start()
            return
        }
        val fact=NexaPhoneVoiceTools.deviceFacts(this,transcript)
        if(fact!=null) {
            lastToolResult=fact.take(320)
            message=lastToolResult
            sarvamApiTransport?.toolResult(fact)
            return
        }
        if(NexaPhoneVoiceTools.wantsScreenOverview(transcript)) {
            pendingAppTool=PendingAppTool("local-sarvam","visible screen controls","observe_screen_icons")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Read labeled icons on the visible screen once? Approve or Deny below."
            message="Approve screen icon inspection. No screenshot or tap will occur."
            try {
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,makeNotification("Screen inspection approval pending"))
            } catch (_: Exception) {}
            return
        }
        val requestedIcon=NexaPhoneVoiceTools.locateIconIntent(transcript)
        if(requestedIcon!=null) {
            pendingAppTool=PendingAppTool("local-sarvam", "$requestedIcon icon",
                "locate_icon:$requestedIcon")
            pendingAppTool?.let { offerOwnerTool(it) }
            pendingApprovalSummary="Locate the $requestedIcon icon on screen once? Approve or Deny."
            message="Awaiting owner approval for read-only icon lookup. No tap or screenshot."
            try {
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,makeNotification("Icon lookup approval pending"))
            } catch (_: Exception) {}
            return
        }
        val known=NexaVoiceIntentGate.appRequest(transcript)
        val discovered=if(known==null)
            NexaPhoneVoiceTools.findLaunchableApp(this,transcript) else null
        if(known!=null) {
            pendingAppTool=PendingAppTool("local-sarvam",known.app,known.command)
        } else when(discovered) {
            is NexaPhoneVoiceTools.Decision.Immediate -> {
                lastToolResult=discovered.result.take(320)
                message=lastToolResult
                sarvamApiTransport?.toolResult(lastToolResult)
                return
            }
            is NexaPhoneVoiceTools.Decision.NeedsApproval -> {
                pendingAppTool=PendingAppTool("local-sarvam",discovered.app.label,
                    "open",discovered.app)
            }
            else -> return
        }
        val pending=pendingAppTool ?: return
        expirePendingTool(pending)
        val requested=pending.app
        pendingApprovalSummary = "Open $requested? Tap Approve once or Deny here."
        message = "Approval needed to open $requested · no app has been opened yet"
        try {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID, makeNotification("App approval pending · tap Nexa overlay"))
        } catch (_: Exception) {}
    }

    private fun sendRealtimeToolOutput(callId: String, result: String) {
        val output = JSONObject().put("type", "conversation.item.create")
            .put("item", JSONObject().put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", JSONObject().put("result", result.take(500)).toString()))
        NexaConversationDb.append(this,voiceSessionId,"tool","openai","tool",result.take(500))
        socket?.send(output.toString())
    }

    private fun handleRealtimeToolCalls(event: JSONObject) {
        val output = event.optJSONObject("response")?.optJSONArray("output") ?: return
        var handled = false
        var deferred = false
        for (i in 0 until minOf(output.length(), 12)) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "function_call") continue
            val id = item.optString("call_id")
            if (!id.matches(Regex("[a-zA-Z0-9_-]{1,128}")) ||
                !seenCallIds.add(id)) continue
            handled = true
            toolCallsInSession++
            if (toolCallsInSession > 40) {
                sendRealtimeToolOutput(id, "Rejected: voice tool-call budget exhausted")
                continue
            }
            if (pendingAppTool != null) {
                sendRealtimeToolOutput(id, "Rejected: another action awaits owner's approval")
                continue
            }
            val name = item.optString("name")
            val args = item.optString("arguments")
            if(name=="get_battery_level" || name=="get_storage_usage") {
                val expected=ownerFactSpeech
                ownerFactSpeech=null // one model call consumes this owner utterance
                val matched=expected?.first?.takeIf {
                    SystemClock.elapsedRealtime()<expected.second &&
                    NexaTaskCoordinator.classifyOwnerFact(it)==name
                }
                if(!assistantPanelEnabled || matched==null || args.trim()!="{}") {
                    sendRealtimeToolOutput(id,
                        "Rejected: a fresh exact owner request is required for device facts.")
                    continue
                }
                deferred=true
                val session=voiceSessionId
                Thread({
                    val response=NexaTaskCoordinator.executeOwnerDeviceRead(
                        this@RealtimeVoiceService,matched,"live-voice")
                    main.post {
                        if(active.get() && selectedProvider=="openai" &&
                            session==voiceSessionId) {
                            lastToolResult=response.take(320)
                            message=lastToolResult
                            sendRealtimeToolOutput(id,response)
                            socket?.send(JSONObject().put("type","response.create").toString())
                        }
                    }
                },"nexa-openai-task-read").start()
                continue
            }
            when (val decision = NexaLiveToolPolicy.decide(this, name, args)) {
                is NexaLiveToolPolicy.Decision.Immediate -> {
                    lastToolResult = decision.result.take(320)
                    message = "Tool $name: $lastToolResult"
                    sendRealtimeToolOutput(id, decision.result)
                }
                is NexaLiveToolPolicy.Decision.NeedsApproval -> {
                    pendingAppTool = PendingAppTool(id, decision.app, decision.command,decision.installedApp)
                    pendingAppTool?.let { offerOwnerTool(it) }
                    pendingApprovalSummary = when {
                        decision.command=="list_installed_apps" ->
                            "Share a limited launcher app list with voice provider? Approve once or Deny."
                        decision.command=="read_visible_screen_text" ->
                            "Read visible non-editable screen labels to voice provider? Approve once or Deny."
                        decision.command.startsWith("scroll_screen:") ->
                            "Scroll ${decision.command.removePrefix("scroll_screen:")} once on the visible screen? Approve or Deny."
                        decision.command.startsWith("tap_safe_icon:") || decision.command.startsWith("tap_visible_label:") ->
                            "Tap the visible ${decision.app} control once? Approve or Deny."
                        decision.command=="observe_screen_icons" ->
                            "Allow one read-only inspection of visible icon labels? Approve or Deny."
                        decision.command.startsWith("locate_icon:") ->
                            "Allow one read-only lookup of ${decision.app}? Approve or Deny."
                        else -> "Open ${decision.app}? Confirm or deny below."
                    }
                    message = "Owner approval required: "+pendingApprovalSummary
                }
            }
        }
        // The next model turn is requested only after all pending owner
        // approvals have been resolved. Realtime does not auto-continue tools.
        if (handled && pendingAppTool == null && !deferred)
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
                                if(selectedProvider=="sarvam-api") {
                                    sarvamApiTransport?.sendAudio(bytes) ?: false
                                } else if(selectedProvider=="sarvam") {
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
        // Capture sanitized lifecycle diagnostics for owner export.
        // No audio, access token or raw screen text is included.
        if(state=="error" || reason.contains("interrupted",true)) {
            NexaConversationDb.append(this,voiceSessionId,"tool",
                selectedProvider.take(45),"tool","Voice session $state: "+reason.take(220))
        }
        currentProviderId=""
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
        try { sarvamApiTransport?.close() } catch (_: Exception) {}
        sarvamApiTransport=null
        try { sarvamTransport?.close() } catch (_: Exception) {}
        sarvamTransport=null
        pendingAppTool = null
        currentCompound?.let { closeCompound(it,"cancelled","Voice session ended") }
        currentCompound=null
        ownerFactSpeech=null
        taskRequestEpoch++
        activeToolOperation=false
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

    @ReactMethod fun checkSarvamVoiceAuthorization(p: Promise) {
        // Read-only, explicit foreground owner test. The key stays in native
        // encrypted storage; React Native sees only safe readiness codes.
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Open Nexa Voice to test Sarvam credentials")
            return
        }
        SarvamModelApiReadiness.check(context.applicationContext) { ready,code,explanation ->
            val data = Arguments.createMap()
            data.putBoolean("ready", ready)
            data.putString("code", code)
            data.putString("message", explanation)
            p.resolve(data)
        }
    }

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
    @ReactMethod fun alwaysAllowPendingVoiceAction(p:Promise) {
        if(context.currentActivity?.hasWindowFocus()!=true){
            p.reject("NOT_FOREGROUND","Open Nexa to review pending action");return
        }
        if(RealtimeVoiceService.pendingApprovalSummary==null ||
            RealtimeVoiceService.status!="listening"){
            p.reject("NO_PENDING_ACTION","No action is awaiting review");return
        }
        context.startService(Intent(context,RealtimeVoiceService::class.java).apply{
            action=RealtimeVoiceService.ACTION_APPROVE_ALWAYS_TOOL
        })
        p.resolve(true)
    }
    @ReactMethod fun getVoicePermissionMode(p:Promise) {
        p.resolve(NexaVoicePermissionPolicy.mode(context))
    }
    @ReactMethod fun setVoicePermissionMode(mode:String,p:Promise) {
        if(context.currentActivity?.hasWindowFocus()!=true){
            p.reject("NOT_FOREGROUND","Open Nexa to change voice permissions");return
        }
        if(NexaVoicePermissionPolicy.setMode(context,mode))p.resolve(true)
        else p.reject("INVALID_MODE","Unknown voice permission mode")
    }
    @ReactMethod fun clearVoicePermissions(p:Promise) {
        if(context.currentActivity?.hasWindowFocus()!=true){
            p.reject("NOT_FOREGROUND","Open Nexa to revoke voice permissions");return
        }
        p.resolve(NexaVoicePermissionPolicy.clearGrants(context))
    }
    @ReactMethod fun getVoiceSessionStatus(p: Promise) {
        val data=Arguments.createMap()
        data.putString("state",RealtimeVoiceService.status)
        data.putString("provider",RealtimeVoiceService.currentProviderId.takeIf { it.isNotBlank() })
        data.putString("message",RealtimeVoiceService.message)
        data.putBoolean("microphoneActive",RealtimeVoiceService.status=="listening")
        data.putBoolean("microphoneMuted",RealtimeVoiceService.microphoneMuted)
        data.putString("pendingAppApproval",RealtimeVoiceService.pendingApprovalSummary)
        data.putString("lastToolResult",RealtimeVoiceService.lastToolResult)
        p.resolve(data)
    }
}
