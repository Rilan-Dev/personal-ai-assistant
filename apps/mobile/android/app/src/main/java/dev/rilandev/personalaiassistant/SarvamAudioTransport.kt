package dev.rilandev.personalaiassistant

import android.net.Uri
import android.util.Base64
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Sarvam Samvaad managed Voice Agent audio transport.
 *
 * Wire protocol reviewed against sarvam-conv-ai-sdk 0.0.42 MIT source:
 * SocketManager + VoiceAgent; signed URL and client.media.audio_chunk.
 * Only PCM16 audio is surfaced. No transcript or tool events are surfaced.
 */
internal class SarvamAudioTransport(private val callbacks: Callbacks) {
    interface Callbacks {
        fun connected()
        fun audio(bytes: ByteArray)
        fun interrupted()
        fun failed(message: String)
        fun ended()
    }

    private val http=OkHttpClient.Builder()
        .connectTimeout(12,TimeUnit.SECONDS)
        .pingInterval(15,TimeUnit.SECONDS)
        .build()
    @Volatile private var running=true
    @Volatile private var socket:WebSocket?=null
    private var signedCall:Call?=null

    fun connect(cfg:AgentJournalModule.VoiceCredentials) {
        val uri=Uri.parse("https://apps.sarvam.ai/api/app-runtime/")
            .buildUpon()
            .appendEncodedPath("orgs/${cfg.orgId}/workspaces/${cfg.workspaceId}/apps/${cfg.appId}/url")
            .appendQueryParameter("interaction_type","call").build().toString()
        val request=Request.Builder().url(uri)
            .header("X-API-Key",cfg.apiKey).get().build()
        signedCall=http.newCall(request)
        signedCall?.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {
                if(running)callbacks.failed("Sarvam session authorization unavailable")
            }
            override fun onResponse(call:Call,response:Response) {
                response.use {
                    if(!running)return
                    if(!it.isSuccessful){
                        callbacks.failed(when(it.code){
                            401,403->"Sarvam API key rejected"
                            404->"Sarvam Voice Agent IDs or committed version not found"
                            else->"Sarvam session authorization failed"
                        })
                        return
                    }
                    val data=try {
                        val body=it.body?.string().orEmpty()
                        if(body.length>8192)null else JSONObject(body)
                    }catch(_:Exception){null}
                    val signed=data?.optString("url").orEmpty()
                    val endpoint=try{Uri.parse(signed)}catch(_:Exception){null}
                    val host=endpoint?.host.orEmpty().lowercase()
                    // Protect owner credentials and prevent SSRF via untrusted
                    // signed-URL response. Never log a signed websocket URL.
                    if(endpoint==null || endpoint.scheme!="wss" ||
                        !(host=="sarvam.ai" || host.endsWith(".sarvam.ai")) ||
                        (endpoint.port!=-1 && endpoint.port!=443) ||
                        endpoint.encodedAuthority?.contains("@")==true) {
                        callbacks.failed("Sarvam returned an untrusted WebSocket URL")
                        return
                    }
                    val url=endpoint.buildUpon()
                        .appendQueryParameter("user_identifier",cfg.userId)
                        .appendQueryParameter("user_identifier_type","custom")
                        .appendQueryParameter("input_sample_rate","16000")
                        .appendQueryParameter("output_sample_rate","16000")
                        .appendQueryParameter("interaction_type","call").build().toString()
                    if(!running)return
                    socket=http.newWebSocket(Request.Builder().url(url).build(),
                        object:WebSocketListener(){
                            override fun onOpen(ws:WebSocket,res:Response){
                                if(!running)return
                                ws.send(JSONObject()
                                    .put("type","client.action.interaction_start")
                                    .put("origin","client")
                                    .put("timestamp",System.currentTimeMillis()/1000.0)
                                    .toString())
                            }
                            override fun onMessage(ws:WebSocket,text:String){
                                if(!running || text.length>180000)return
                                val event=try{JSONObject(text)}catch(_:Exception){return}
                                when(event.optString("type")){
                                    "server.action.interaction_connected"->callbacks.connected()
                                    "server.media.audio_chunk"->{
                                        if(event.optString("status")=="pending"){
                                            val audio=event.optString("audio_base64")
                                            if(audio.length in 1..160000) {
                                                try {
                                                    val bytes=Base64.decode(audio,Base64.DEFAULT)
                                                    if(bytes.size<=120000)callbacks.audio(bytes)
                                                }catch(_:Exception){}
                                            }
                                        }
                                    }
                                    "server.event.user_interrupt"->callbacks.interrupted()
                                    "server.system.ping"->ws.send(JSONObject()
                                        .put("type","client.system.pong")
                                        .put("origin","client")
                                        .put("timestamp",System.currentTimeMillis()/1000.0)
                                        .put("event_id",event.optString("event_id")).toString())
                                    "server.action.interaction_end"->callbacks.ended()
                                    // Transcripts, text responses, media metadata
                                    // and tool suggestions are intentionally ignored.
                                }
                            }
                            override fun onFailure(ws:WebSocket,t:Throwable,res:Response?){
                                if(running)callbacks.failed("Sarvam audio connection failed")
                            }
                            override fun onClosed(ws:WebSocket,code:Int,reason:String){
                                if(running)callbacks.ended()
                            }
                        })
                }
            }
        })
    }

    fun sendAudio(pcm:ByteArray):Boolean{
        if(!running || pcm.isEmpty() || pcm.size>4096)return false
        val ws=socket?:return false
        if(ws.queueSize()>256*1024)return false
        return ws.send(JSONObject()
            .put("type","client.media.audio_chunk")
            .put("origin","client")
            .put("timestamp",System.currentTimeMillis()/1000.0)
            .put("audio_base64",Base64.encodeToString(pcm,Base64.NO_WRAP))
            .put("format","audio/wav")
            .put("sample_rate",16000).toString())
    }

    fun close(){
        running=false
        signedCall?.cancel()
        signedCall=null
        try{socket?.close(1000,"owner-stop")}catch(_:Exception){}
        socket=null
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}
