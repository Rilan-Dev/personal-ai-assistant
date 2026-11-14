package dev.rilandev.personalaiassistant

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.UUID

/**
 * One in-memory handoff from the floating Chat to the EXISTING React Chat
 * planner. It is not another AI provider, native executor or approval source.
 * A suspended React runtime is reported as unavailable; never replay a
 * potentially executed action after a crash, timeout or lost reply.
 */
internal object NexaOverlayChatRelay {
    private const val MAX_REQUEST=500
    private const val MAX_REPLY=1200
    private const val TTL_MS=60_000L
    private val lock=Any()
    private val handler=Handler(Looper.getMainLooper())
    private data class Request(val token:String,val startedAt:Long)
    private var react:ReactApplicationContext?=null
    private var pending:Request?=null

    fun attach(context:ReactApplicationContext)=synchronized(lock) {
        react=context
    }
    fun detach(context:ReactApplicationContext)=synchronized(lock) {
        if(react===context)react=null
        // A JS restart cannot inherit a previous approval or action.
        pending=null
    }
    fun isPending():Boolean=synchronized(lock) {
        val request=pending ?:return@synchronized false
        SystemClock.elapsedRealtime()-request.startedAt in 0 until TTL_MS
    }

    fun offer(raw:String):Boolean {
        val query=raw.trim()
        if(query.isEmpty()||query.length>MAX_REQUEST ||
            query.any{it=='\u0000'})return false
        val context:ReactApplicationContext
        val token:String
        synchronized(lock) {
            context=react?:return false
            if(isPending())return false
            token=UUID.randomUUID().toString()
            pending=Request(token,SystemClock.elapsedRealtime())
        }
        // This runs only for an owner-typed overlay message. No model
        // receives Android system permissions through this event.
        handler.post {
            val stillCurrent=synchronized(lock){pending?.token==token}
            if(!stillCurrent)return@post
            try {
                val event=Arguments.createMap().apply {
                    putString("id",token)
                    putString("request",query)
                }
                context.getJSModule(
                    DeviceEventManagerModule.RCTDeviceEventEmitter::class.java
                ).emit("NexaOverlayChatRequest",event)
            }catch (_:Exception){
                if(cancel(token))NexaToolOverlayService.showSharedChatReply(
                    "Shared Chat engine is unavailable. No AI task started.")
            }
        }
        handler.postDelayed({
            if(cancel(token))NexaToolOverlayService.showSharedChatReply(
                "AI response timed out. The result is unknown; inspect the task journal before retrying.")
        },TTL_MS)
        return true
    }

    private fun cancel(token:String):Boolean=synchronized(lock) {
        if(pending?.token!=token)return@synchronized false
        pending=null
        true
    }
    /** Only the unique pending token is accepted. No native tool executes. */
    fun complete(token:String,response:String):Boolean {
        if(token.length!=36 || response.length>8_000)return false
        if(!cancel(token))return false
        NexaToolOverlayService.showSharedChatReply(
            response.take(MAX_REPLY).ifBlank{"The shared Chat engine returned no result."})
        return true
    }
}
