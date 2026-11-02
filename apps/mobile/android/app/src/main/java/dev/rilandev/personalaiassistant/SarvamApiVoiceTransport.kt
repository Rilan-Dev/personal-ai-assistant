package dev.rilandev.personalaiassistant

import android.content.Context
import android.util.Base64
import android.net.Uri
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * BYOK, model-API-only, voice conversation. No Samvaad workspace, Agent ID,
 * remote agent dashboard or AI Comms server. One long-running Saaras realtime
 * STT WS + a Bulbul streaming TTS WS, with per-turn Sarvam-105B Conversations
 * HTTPS inference. Speech is continuous to the user; no transcript UI or
 * persistent transcript, journal or server-side Nexa records are created here.
 *
 * This is a composed duplex experience, NOT an invented single STS API.
 */
internal class SarvamApiVoiceTransport(
    private val context: Context,
    private val apiKey: String,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun connected()
        fun audio(pcm16: ByteArray)
        fun interrupted()
        /** Return true only if an explicit owner request needs native review. */
        fun ownerRequest(text: String): Boolean
        fun failed(reason: String)
        fun ended()
    }

    internal companion object {
        const val STT_MODEL = "saaras:v3-realtime"
        const val CHAT_MODEL = "sarvam-105b-conversations"
        const val TTS_MODEL = "bulbul:v4-flash"
        const val RATE = 16000
        fun sttUrl(): String = Uri.parse("https://api.sarvam.ai/speech-to-text-realtime/ws")
            .buildUpon()
            .appendQueryParameter("language_code","auto")
            .appendQueryParameter("model",STT_MODEL)
            .appendQueryParameter("stream_type","fast")
            .appendQueryParameter("encoding","linear16")
            .appendQueryParameter("sample_rate","16000")
            .appendQueryParameter("endpointing","vad")
            .appendQueryParameter("threshold","0.3")
            .appendQueryParameter("silence_duration_ms","650")
            .appendQueryParameter("min_speech_duration_ms","250")
            .build().toString()
        fun ttsUrl(): String =
            "wss://api.sarvam.ai/text-to-speech/ws?model=bulbul:v4-flash&send_completion_event=true"
        fun ttsConfig(): JSONObject = JSONObject().put("type","config").put("data",
            JSONObject().put("speaker","simran_en_customer")
                .put("language_code","en-IN")
                .put("output_audio_codec","linear16")
                .put("speech_sample_rate",RATE)
                .put("pace",1.0)
                .put("min_buffer_size",30)
                .put("max_chunk_length",150))
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(12,TimeUnit.SECONDS)
        .readTimeout(25,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .pingInterval(20,TimeUnit.SECONDS).build()
    private val lock = Any()
    @Volatile private var running = true
    @Volatile private var sttReady = false
    @Volatile private var ttsReady = false
    @Volatile private var announced = false
    private var stt: WebSocket? = null
    private var tts: WebSocket? = null
    private var modelCall: Call? = null
    private val generation = AtomicInteger(0)
    private val ttsGeneration = AtomicInteger(0)
    private val turns = ArrayDeque<Pair<String,String>>()
    private var pendingSpeech: String? = null
    private var recentUtterance = ""
    private var recentUtteranceAt = 0L
    private val heartbeat = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task,"nexa-sarvam-heartbeat").apply { isDaemon=true }
    }

    fun connect() {
        val request = Request.Builder().url(sttUrl())
            .header("api-subscription-key",apiKey).build()
        stt = http.newWebSocket(request,object: WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {
                sttReady=true
                maybeReady()
            }
            override fun onMessage(ws:WebSocket,text:String) {
                if (!running || text.length>100000) return
                val event = try { JSONObject(text) } catch (_: Exception) { return }
                when(event.optString("event",event.optString("type"))) {
                    "vad.speech_start" -> interruptReply()
                    "transcript.final" -> {
                        val phrase=event.optString("text").trim()
                        if(phrase.length in 1..800) onFinal(phrase)
                    }
                    "error" -> if(event.optBoolean("is_fatal",false))
                        callbacks.failed("Saaras realtime STT rejected the session or audio")
                    "session.end" -> if(running) callbacks.ended()
                }
            }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {
                if(running) callbacks.failed("Saaras realtime WebSocket connection failed")
            }
            override fun onClosed(ws:WebSocket,code:Int,reason:String) {
                if(running) callbacks.ended()
            }
        })
        openTts()
        heartbeat.scheduleAtFixedRate({
            if(running) {
                try {
                    stt?.send(JSONObject().put("event","ping").toString())
                    tts?.send(JSONObject().put("type","ping").put("data",JSONObject()).toString())
                } catch (_: Exception) {}
            }
        },20,20,TimeUnit.SECONDS)
    }

    private fun maybeReady() {
        // STT and TTS complete their handshakes on different OkHttp threads.
        // Exactly one callback may start Android AudioRecord/AudioTrack.
        val notify=synchronized(lock) {
            if(running && sttReady && ttsReady && !announced) {
                announced=true
                true
            } else false
        }
        if(notify) callbacks.connected()
    }

    private fun openTts() {
        if(!running) return
        ttsReady=false
        val marker=ttsGeneration.incrementAndGet()
        tts=http.newWebSocket(Request.Builder().url(ttsUrl())
            .header("api-subscription-key",apiKey).build(),object:WebSocketListener(){
            override fun onOpen(ws:WebSocket,response:Response) {
                if(!running || marker!=ttsGeneration.get()) { ws.close(1000,"superseded");return }
                if(!ws.send(ttsConfig().toString())) {
                    callbacks.failed("Bulbul TTS configuration could not be sent")
                    return
                }
                ttsReady=true
                maybeReady()
                synchronized(lock) {
                    val text=pendingSpeech
                    if(text!=null){pendingSpeech=null;sendSpeech(ws,text)}
                }
            }
            override fun onMessage(ws:WebSocket,text:String) {
                if(!running || marker!=ttsGeneration.get() || text.length>240000)return
                val event=try{JSONObject(text)}catch(_:Exception){return}
                if(event.optString("type")=="audio") {
                    val payload=event.optJSONObject("data") ?: return
                    val base64=payload.optString("audio")
                    if(base64.length in 1..220000) try {
                        val bytes=Base64.decode(base64,Base64.DEFAULT)
                        if(bytes.isNotEmpty() && bytes.size<=165000)callbacks.audio(bytes)
                    }catch(_:Exception){}
                } else if(event.optString("type")=="error") {
                    callbacks.failed("Bulbul streaming TTS rejected the request")
                }
            }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {
                if(running && marker==ttsGeneration.get())
                    callbacks.failed("Bulbul streaming TTS WebSocket disconnected")
            }
            override fun onClosed(ws:WebSocket,code:Int,reason:String) {
                if(running && marker==ttsGeneration.get() && code!=1000)
                    callbacks.failed("Bulbul streaming TTS closed unexpectedly")
            }
        })
    }

    private fun sendSpeech(ws:WebSocket,text:String) {
        if(!running || text.isBlank())return
        val sent=ws.send(JSONObject().put("type","text")
            .put("data",JSONObject().put("text",text.take(1100))).toString())
        if(sent) ws.send(JSONObject().put("type","flush").put("data",JSONObject()).toString())
        else callbacks.failed("Voice reply could not be streamed")
    }

    private fun speak(text:String) {
        if(!running)return
        synchronized(lock) {
            val current=tts
            if(ttsReady && current!=null)sendSpeech(current,text)
            else pendingSpeech=text
        }
    }

    private fun interruptReply() {
        if(!running)return
        generation.incrementAndGet()
        synchronized(lock){ modelCall?.cancel(); modelCall=null; pendingSpeech=null }
        // Bulbul has no in-band cancel. Drop old audio immediately and open
        // a clean TTS websocket for the next spoken reply.
        ttsReady=false
        ttsGeneration.incrementAndGet()
        try { tts?.close(1000,"barge-in") }catch(_:Exception){}
        tts=null
        callbacks.interrupted()
        openTts()
    }

    private fun onFinal(text:String) {
        val now=android.os.SystemClock.elapsedRealtime()
        synchronized(lock) {
            if(text==recentUtterance && now-recentUtteranceAt<1500L)return
            recentUtterance=text
            recentUtteranceAt=now
        }
        // Native owner-approval is an independent consent boundary. No
        // model instruction or transcript may silently execute a phone tool.
        if(callbacks.ownerRequest(text)) {
            synchronized(lock) {
                turns.addLast("user" to text.take(800))
                while(turns.size>12)turns.removeFirst()
            }
            return
        }
        askModel(text)
    }

    private fun askModel(text:String) {
        if(!running)return
        val turn=generation.incrementAndGet()
        val body=JSONObject().put("model",CHAT_MODEL).put("max_tokens",230)
            .put("messages",JSONArray().put(JSONObject().put("role","system")
                .put("content","You are Nexa, a concise phone voice assistant. Use provided read-only battery, storage and device-status functions when needed; report their actual results, not guessed values. App launch only follows a separate local owner approval gate. Never claim an app opened or a message was sent. Do not request contacts, private files, secrets or screen content.")))
            .put("tools",NexaSarvamReadTools.definitions())
            .put("tool_choice","auto")
        synchronized(lock) {
            turns.addLast("user" to text.take(800))
            while(turns.size>12)turns.removeFirst()
            val messages=body.getJSONArray("messages")
            turns.forEach { (role,content) ->
                messages.put(JSONObject().put("role",role).put("content",content))
            }
        }
        completeModelTurn(turn,body,0)
    }

    /**
     * A real function-call→native execution→tool result→model reply loop.
     * One read-only tool per owner utterance; no autonomous UI action.
     * A model-proposed name and arguments are untrusted and validated against
     * NexaSarvamReadTools. The owner-approved app route is kept separate.
     */
    private fun completeModelTurn(turn:Int, body:JSONObject, round:Int) {
        if(!running || turn!=generation.get() || round>1)return
        val request=Request.Builder().url("https://api.sarvam.ai/v1/chat/completions")
            .header("api-subscription-key",apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val call=http.newCall(request)
        synchronized(lock) { modelCall?.cancel(); modelCall=call }
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {
                if(running && turn==generation.get() && !call.isCanceled())
                    callbacks.failed("Sarvam conversation model could not be reached")
            }
            override fun onResponse(call:Call,response:Response) {
                if(!running || turn!=generation.get()){response.close();return}
                try { response.use {
                    if(!it.isSuccessful) {
                        callbacks.failed(if(it.code==401 || it.code==403)
                            "Sarvam Model API rejected the subscription key"
                            else "Sarvam conversation model returned HTTP ${it.code}")
                        return
                    }
                    val raw=it.body?.string().orEmpty()
                    if(raw.length !in 2..140000) {
                        callbacks.failed("Sarvam conversation response was invalid");return
                    }
                    val message=JSONObject(raw).optJSONArray("choices")
                        ?.optJSONObject(0)?.optJSONObject("message")
                        ?: run {
                            callbacks.failed("Sarvam returned no conversation message");return
                        }
                    val toolCalls=message.optJSONArray("tool_calls")
                    if(toolCalls!=null && toolCalls.length()>0) {
                        if(round>=1 || toolCalls.length()!=1) {
                            speak("I cannot perform additional actions in this voice turn.")
                            return
                        }
                        val tool=toolCalls.optJSONObject(0)
                        val id=tool?.optString("id").orEmpty()
                        val function=tool?.optJSONObject("function")
                        val name=function?.optString("name").orEmpty()
                        val args=function?.optString("arguments").orEmpty()
                        if(!id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                            speak("That tool request was not valid.")
                            return
                        }
                        // Never execute a tool outside the native read-only
                        // registry. In particular, there is no remote model
                        // gesture, app-launch, send, call or approval function.
                        val result=NexaSarvamReadTools.execute(context,name,args)
                        val next=JSONObject(body.toString())
                        val history=next.getJSONArray("messages")
                        history.put(JSONObject().put("role","assistant")
                            .put("content",JSONObject.NULL)
                            .put("tool_calls",JSONArray().put(tool)))
                        history.put(JSONObject().put("role","tool")
                            .put("tool_call_id",id)
                            .put("content",result))
                        next.remove("tools")
                        next.remove("tool_choice")
                        completeModelTurn(turn,next,round+1)
                        return
                    }
                    val answer=message.optString("content").trim()
                    if(answer.isBlank() || answer.length>3000) {
                        callbacks.failed("Sarvam did not return a spoken response");return
                    }
                    synchronized(lock) {
                        if(!running || turn!=generation.get())return
                        turns.addLast("assistant" to answer.take(1000))
                        while(turns.size>12)turns.removeFirst()
                        speak(answer)
                    }
                }} catch (_:Exception) {
                    if(running && turn==generation.get())
                        callbacks.failed("Sarvam conversation response could not be processed")
                }
            }
        })
    }

    /** A verified Android owner-approval result, never provider-invented. */
    fun toolResult(result:String) {
        if(!running) return
        val safe=result.take(300)
        synchronized(lock){
            turns.addLast("assistant" to safe)
            while(turns.size>12)turns.removeFirst()
        }
        speak(safe)
    }

    fun sendAudio(pcm:ByteArray):Boolean {
        if(!running || !sttReady || pcm.isEmpty() || pcm.size>4096)return false
        val ws=stt?:return false
        if(ws.queueSize()>256*1024)return false
        return try {
            ws.send(JSONObject().put("event","audio_input")
                .put("audio",Base64.encodeToString(pcm,Base64.NO_WRAP)).toString())
        }catch(_:Exception){false}
    }

    fun close(){
        running=false
        generation.incrementAndGet()
        ttsGeneration.incrementAndGet()
        synchronized(lock){modelCall?.cancel();modelCall=null;turns.clear();pendingSpeech=null}
        heartbeat.shutdownNow()
        try { stt?.close(1000,"owner-stop") }catch(_:Exception){}
        try { tts?.close(1000,"owner-stop") }catch(_:Exception){}
        stt=null;tts=null
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}
