package dev.rilandev.personalaiassistant

import android.content.Context
import java.io.IOException
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
        fun finish(ok:Boolean,code:String,message:String) {
            if(terminal.compareAndSet(false,true)) {
                callback(ok,code,message)
                http.dispatcher.executorService.shutdown()
                http.connectionPool.evictAll()
            }
        }
        fun probeTts() {
            http.newWebSocket(Request.Builder().url(SarvamApiVoiceTransport.ttsUrl())
                .header("api-subscription-key",key).build(),object:WebSocketListener() {
                override fun onOpen(ws:WebSocket,response:Response) {
                    ws.close(1000,"authorization-probe")
                    probeChat()
                }
                override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {
                    finish(false,"TTS_AUTH","Bulbul streaming TTS authorization failed; verify Model API key and access.")
                }
            })
        }
        fun probeChat() {
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
                        if(it.isSuccessful)finish(true,"AUTHORIZED",
                            "Saaras v3 Realtime and Bulbul WebSockets accepted the key, and Sarvam Conversations answered a synthetic test. No microphone audio was sent.")
                        else finish(false,"MODEL_AUTH",
                            "Sarvam Conversations returned HTTP ${it.code}; verify the Model API key, quota or model access.")
                    }
                }
            })
        }
        http.newWebSocket(Request.Builder().url(SarvamApiVoiceTransport.sttUrl())
            .header("api-subscription-key",key).build(),object:WebSocketListener(){
            override fun onOpen(ws:WebSocket,response:Response) {
                ws.close(1000,"authorization-probe")
                probeTts()
            }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {
                finish(false,"STT_AUTH",
                    "Saaras v3 Realtime WebSocket authorization failed; check Model API access.")
            }
        })
    }
}
