package dev.rilandev.personalaiassistant

import android.content.Context
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

/**
 * Explicit owner-tapped, no-microphone API-only readiness check.
 * Probes both websocket handshakes, then one synthetic LLM completion.
 * It never opens a Samvaad workspace URL, prints keys or records real audio.
 */
internal object SarvamModelApiReadiness {
    fun check(ctx:Context, callback:(Boolean,String,String)->Unit) {
        val key=try { AgentJournalModule.openSarvamApiKey(ctx) } catch (_:Exception) { null }
        if(key.isNullOrBlank()) {
            callback(false,"MISSING_KEY","Save a Sarvam Model API subscription key under AI Provider first.")
            return
        }
        val http=OkHttpClient.Builder().connectTimeout(9,TimeUnit.SECONDS)
            .readTimeout(14,TimeUnit.SECONDS).pingInterval(15,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
        val terminal=AtomicBoolean(false)
        val sockets=ConcurrentLinkedQueue<WebSocket>()
        val deadline=Executors.newSingleThreadScheduledExecutor { action ->
            Thread(action,"nexa-sarvam-readiness-timeout").apply { isDaemon=true }
        }
        fun finish(ok:Boolean,code:String,message:String) {
            if(!terminal.compareAndSet(false,true))return
            try {
                callback(ok,code,message)
            } finally {
                deadline.shutdownNow()
                sockets.forEach { try { it.cancel() } catch (_:Exception) {} }
                http.dispatcher.cancelAll()
                http.dispatcher.executorService.shutdown()
                http.connectionPool.evictAll()
            }
        }
        // Neither a WebSocket read timeout nor a later provider callback
        // reliably bounds the complete STT -> TTS -> chat -> tool-schema chain.
        // A single hard deadline closes every in-flight probe and settles the
        // owner-tapped readiness result once, without touching the microphone.
        deadline.schedule({
            finish(false,"READINESS_TIMEOUT",
                "Sarvam API readiness did not finish within 45 seconds. No audio or device action was sent.")
        },45,TimeUnit.SECONDS)
        // The earlier key check sent no tools, so it could succeed while
        // live voice consistently failed with HTTP 400 on the tool schema.
        // Probe the SAME declarations with a harmless synthetic prompt.
        fun probeToolSchema() {
            if(terminal.get())return
            val request=JSONObject().put("model",SarvamApiVoiceTransport.CHAT_MODEL)
                .put("max_tokens",20).put("messages",JSONArray()
                    .put(JSONObject().put("role","user")
                        .put("content","Reply OK only. Do not call a function.")))
                .put("tools",NexaSarvamActionTools.definitions())
                .put("tool_choice","auto")
            http.newCall(Request.Builder().url("https://api.sarvam.ai/v1/chat/completions")
                .header("api-subscription-key",key)
                .post(request.toString().toRequestBody("application/json".toMediaType()))
                .build()).enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) {
                    finish(false,"MODEL_TOOL_NETWORK",
                        "Sarvam conversation tool-schema probe could not connect.")
                }
                override fun onResponse(call:Call,response:Response) {
                    response.use {
                        if(it.isSuccessful) {
                            val reply=NexaSarvamResponseBounds.textOrNull(it,
                                NexaSarvamResponseBounds.MAX_CHAT_BYTES)
                            if(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(reply))
                                finish(true,"AUTHORIZED",
                                    "Saaras and Bulbul WebSocket handshakes and two synthetic text-only model completions were accepted. Live audio and phone tools were not tested.")
                            else finish(false,"MODEL_TOOL_RESPONSE",
                                "Sarvam returned HTTP success but no valid non-executing tool-schema probe completion. No phone tool was executed.")
                        }else {
                            val safe=NexaSarvamChatError.classify(it.code,
                                NexaSarvamResponseBounds.textOrNull(it,
                                    NexaSarvamResponseBounds.MAX_ERROR_BYTES).orEmpty())
                            finish(false,"MODEL_TOOL_"+safe.code.uppercase(java.util.Locale.ROOT),
                                "Sarvam tool-schema readiness failed: "+safe.explanation+
                                    " No device tool was executed.")
                        }
                    }
                }
            })
        }
        fun probeChat() {
            if(terminal.get())return
            val body=JSONObject().put("model",SarvamApiVoiceTransport.CHAT_MODEL)
                .put("max_tokens",12).put("messages",JSONArray()
                    .put(JSONObject().put("role","user").put("content","Reply OK only.")))
            http.newCall(Request.Builder().url("https://api.sarvam.ai/v1/chat/completions")
                .header("api-subscription-key",key)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()).enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) {
                    finish(false,"MODEL_NETWORK","Sarvam-105B Conversations could not be reached.")
                }
                override fun onResponse(call:Call,response:Response) {
                    response.use {
                        if(terminal.get())return
                        if(it.isSuccessful) {
                            val reply=NexaSarvamResponseBounds.textOrNull(it,
                                NexaSarvamResponseBounds.MAX_CHAT_BYTES)
                            if(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(reply))
                                probeToolSchema()
                            else finish(false,"MODEL_RESPONSE",
                                "Sarvam returned HTTP success without a valid synthetic assistant completion. Voice model not verified.")
                        }else finish(false,"MODEL_AUTH",
                            "Sarvam Conversations returned HTTP ${it.code}; verify the Model API key, quota or model access.")
                    }
                }
            })
        }
        fun probeTts() {
            if(terminal.get())return
            val ttsSocket=http.newWebSocket(Request.Builder().url(SarvamApiVoiceTransport.ttsUrl())
                .header("api-subscription-key",key).build(),object:WebSocketListener() {
                override fun onOpen(ws:WebSocket,response:Response) {
                    ws.close(1000,"authorization-probe")
                    if(!terminal.get())probeChat()
                }
                override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {
                    finish(false,"TTS_AUTH","Bulbul streaming TTS authorization failed; verify Model API key and access.")
                }
            })
            sockets.add(ttsSocket)
            if(terminal.get())ttsSocket.cancel()
        }
        val sttSocket=http.newWebSocket(Request.Builder().url(SarvamApiVoiceTransport.sttUrl())
            .header("api-subscription-key",key).build(),object:WebSocketListener(){
            override fun onOpen(ws:WebSocket,response:Response) {
                ws.close(1000,"authorization-probe")
                if(!terminal.get())probeTts()
            }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {
                finish(false,"STT_AUTH",
                    "Saaras v3 Realtime WebSocket authorization failed; check Model API access.")
            }
        })
        sockets.add(sttSocket)
        if(terminal.get())sttSocket.cancel()
    }
}
