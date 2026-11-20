package dev.rilandev.personalaiassistant

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
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
    private const val RESPONSE_TTL_MS=60_000L
    private const val REVIEW_TTL_MS=120_000L
        // Keep the owner-approved planner/tool round trip bounded while allowing
        // slower external-app launches and post-action observations to finish.
        private const val EXECUTION_TTL_MS=180_000L
    private const val CHAT_TIMEOUT_EVENT="NexaOverlayChatTimeout"
    private enum class Phase { PLANNING, REVIEW, EXECUTING }
    private val lock=Any()
    private val handler=Handler(Looper.getMainLooper())
    private data class Request(
        val token:String,var phase:Phase,var deadlineAt:Long,
        var timeoutGeneration:Int=0,
        var approvalSummary:String?=null,var decisionSubmitted:Boolean=false
    )
    private var react:ReactApplicationContext?=null
    private var pending:Request?=null

    internal fun isSafeScreenActionReviewSummary(summary:String):Boolean {
        if(summary.length !in 1..1800)return false
        if(Regex("^screen\\.scroll direction=(up|down|left|right)$").matches(summary))
            return true
        val blocked=Regex(
            "\\b(send|submit|pay|purchase|buy|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|passcode|pin|otp|logout|sign out|payment|bank|security|verify)\\b",
            RegexOption.IGNORE_CASE
        )
        val tap=Regex("^screen\\.tap_label label=([0-9]|[A-Za-z][A-Za-z0-9 _.-]{1,44})$")
            .matchEntire(summary)
        if(tap!=null)return !blocked.containsMatchIn(tap.groupValues[1])

        val action=try{JSONObject(summary)}catch(_:Exception){return false}
        if(!hasKeys(action,"toolId","input"))return false
        val tool=action.opt("toolId") as? String?:return false
        val input=action.optJSONObject("input")?:return false
        fun exactKeys(vararg keys:String):Boolean=hasKeys(input,*keys)
        fun safeApp(value:Any?):Boolean {
            val app=value as? String?:return false
            return app.length in 2..56&&app.trim()==app&&
                Regex("^[a-z0-9][a-z0-9 ._+\\-]*$",RegexOption.IGNORE_CASE).matches(app)&&
                !Regex("\\s{2}|\\b(and|then|send|text|message|call|dial|delete|install|uninstall)\\b",
                    RegexOption.IGNORE_CASE).containsMatchIn(app)&&
                app.lowercase(java.util.Locale.ROOT) !in setOf("app","an app","the app")
        }
        fun safeRecipient(value:Any?):Boolean {
            val name=value as? String?:return false
            return Regex("^[\\p{L}][\\p{L}\\p{N} '-]{1,49}$").matches(name)&&
                !Regex("\\b(and|then|send|submit|pay|buy|call|delete|grant|allow|confirm|password|otp)\\b",
                    RegexOption.IGNORE_CASE).containsMatchIn(name)
        }
        fun exactText(value:Any?):Boolean {
            val text=value as? String?:return false
            return text.length in 1..500&&text.isNotBlank()&&
                text.none{it.code in 0..8||it.code in 11..31}
        }
        fun safeLabel(value:Any?):Boolean {
            val label=value as? String?:return false
            val singleDigit=label.length==1&&label[0] in '0'..'9'
            return (singleDigit || label.length in 2..45&&label.trim()==label&&
                Regex("^[A-Za-z][A-Za-z0-9 _.-]{1,44}$").matches(label))&&
                !blocked.containsMatchIn(label)
        }
        fun safeObservedTap():Boolean {
            if(!exactKeys("snapshotId","index","label"))return false
            val snapshotId=input.opt("snapshotId") as? String?:return false
            val index=input.opt("index") as? Number?:return false
            val value=index.toDouble()
            return Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
                RegexOption.IGNORE_CASE).matches(snapshotId)&&
                value.isFinite()&&value%1.0==0.0&&value in 0.0..39.0&&
                safeLabel(input.opt("label"))
        }
        fun safeField(value:Any?):Boolean {
            val label=value as? String?:return false
            return label.length>=2&&safeLabel(label)&&!Regex(
                "\\b(recipient|account|email address|phone number|amount|address|checkout|password|security|payment)\\b",
                RegexOption.IGNORE_CASE).containsMatchIn(label)
        }
        fun appMessage(value:Any?):Boolean=value in setOf("whatsapp","imo","botim")

        return when(tool){
            "device.open_app"->exactKeys("app")&&safeApp(input.opt("app"))
            "device.visible_controls","device.open_notification_shade"->exactKeys()
            "screen.scroll"->exactKeys("direction")&&
                input.opt("direction") in setOf("up","down","left","right")
            "screen.tap_label"->exactKeys("label")&&safeLabel(input.opt("label"))
            "screen.tap_observed"->safeObservedTap()
            "screen.open_whatsapp_chat"->exactKeys("recipientHint")&&
                safeRecipient(input.opt("recipientHint"))
            "screen.fill_text"->exactKeys("targetLabel","exactText")&&
                safeField(input.opt("targetLabel"))&&exactText(input.opt("exactText"))
            "screen.compose_whatsapp","screen.send_whatsapp"->
                exactKeys("recipientHint","exactText")&&
                    safeRecipient(input.opt("recipientHint"))&&exactText(input.opt("exactText"))
            "screen.compose_message","screen.send_message"->
                exactKeys("appId","recipientHint","exactText")&&
                    appMessage(input.opt("appId"))&&safeRecipient(input.opt("recipientHint"))&&
                    exactText(input.opt("exactText"))
            else->false
        }
    }

    private fun hasKeys(value:JSONObject,vararg keys:String):Boolean=
        value.length()==keys.size&&keys.all(value::has)

    fun attach(context:ReactApplicationContext)=synchronized(lock) {
        react=context
    }
    fun detach(context:ReactApplicationContext) {
        val expired=synchronized(lock) {
            if(react===context)react=null
            // A JS restart cannot inherit a previous approval or action.
            pending?.token.also { pending=null }
        }
        expired?.let { NexaToolOverlayService.showSharedChatReply(
            it,"Shared Chat engine stopped. No device action was executed.") }
    }
    fun isPending():Boolean=synchronized(lock) {
        val request=pending ?:return@synchronized false
        SystemClock.elapsedRealtime()<request.deadlineAt
    }

    fun offer(raw:String):Boolean {
        val query=raw.trim()
        if(query.isEmpty()||query.length>MAX_REQUEST ||
            query.any{it=='\u0000'})return false
        val context:ReactApplicationContext
        val token:String
        val generation:Int
        synchronized(lock) {
            context=react?:return false
            if(isPending())return false
            token=UUID.randomUUID().toString()
            generation=1
            pending=Request(
                token,Phase.PLANNING,
                SystemClock.elapsedRealtime()+RESPONSE_TTL_MS,
                timeoutGeneration=generation
            )
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
                if(cancel(token))NexaToolOverlayService.showSharedChatReply(token,
                    "Shared Chat engine is unavailable. No AI task started.")
            }
        }
        scheduleTimeout(token,generation,RESPONSE_TTL_MS)
        return true
    }

    private fun scheduleTimeout(token:String,generation:Int,delayMs:Long) {
        handler.postDelayed({
            val expired=synchronized(lock) {
                val request=pending ?:return@synchronized false
                if(request.token!=token || request.timeoutGeneration!=generation ||
                    SystemClock.elapsedRealtime()<request.deadlineAt)
                    return@synchronized false
                pending=null
                true
            }
            if(expired){
                val context=synchronized(lock){react}
                if(context!=null)try{
                    val event=Arguments.createMap().apply{putString("id",token)}
                    context.getJSModule(
                        DeviceEventManagerModule.RCTDeviceEventEmitter::class.java
                    ).emit(CHAT_TIMEOUT_EVENT,event)
                }catch (_:Exception){}
                NexaToolOverlayService.showSharedChatReply(token,
                    "AI response timed out. The result is unknown; inspect the task journal before retrying.")
            }
        },delayMs)
    }

    private fun cancel(token:String):Boolean=synchronized(lock) {
        if(pending?.token!=token)return@synchronized false
        pending=null
        true
    }
    /** Stages one constrained screen tap/scroll review in the floating panel. */
    fun requestApproval(token:String,summary:String):Boolean {
        if(!Regex("^[0-9a-f-]{36}$").matches(token) ||
            !isSafeScreenActionReviewSummary(summary))
            return false
        val generation=synchronized(lock) {
            val request=pending ?:return@synchronized null
            val now=SystemClock.elapsedRealtime()
            if(request.token!=token || request.phase==Phase.REVIEW ||
                now>=request.deadlineAt)
                return@synchronized null
            request.approvalSummary=summary
            request.decisionSubmitted=false
            request.phase=Phase.REVIEW
            request.deadlineAt=now+REVIEW_TTL_MS
            request.timeoutGeneration+=1
            request.timeoutGeneration
        }
        if(generation==null)return false
        scheduleTimeout(token,generation,REVIEW_TTL_MS)
        return NexaToolOverlayService.showSharedChatApproval(token,summary)
    }

    /** One owner button press is relayed to the shared JS task; native code
     * never interprets this event as permission to run a gesture itself. */
    fun decideApproval(token:String,approve:Boolean):Boolean {
        val context:ReactApplicationContext
        val generation:Int
        synchronized(lock) {
            val request=pending ?:return false
            val now=SystemClock.elapsedRealtime()
            if(request.token!=token || request.phase!=Phase.REVIEW || request.approvalSummary.isNullOrBlank() ||
                request.decisionSubmitted ||
                now>=request.deadlineAt)
                return false
            context=react?:return false
            request.decisionSubmitted=true
            request.phase=Phase.EXECUTING
            request.deadlineAt=now+EXECUTION_TTL_MS
            request.timeoutGeneration+=1
            generation=request.timeoutGeneration
        }
        scheduleTimeout(token,generation,EXECUTION_TTL_MS)
        handler.post {
            val current=synchronized(lock) {
                pending?.let { it.token==token && it.decisionSubmitted }==true
            }
            if(!current)return@post
            try {
                val event=Arguments.createMap().apply {
                    putString("id",token)
                    putBoolean("approve",approve)
                }
                context.getJSModule(
                    DeviceEventManagerModule.RCTDeviceEventEmitter::class.java
                ).emit("NexaOverlayChatApproval",event)
            }catch (_:Exception) {
                if(cancel(token))NexaToolOverlayService.showSharedChatReply(token,
                    "Owner review relay failed. No device action was executed.")
            }
        }
        return true
    }
    /** Only the unique pending token is accepted. No native tool executes. */
    fun complete(token:String,response:String):Boolean {
        if(token.length!=36 || response.length>8_000)return false
        if(!cancel(token))return false
        NexaToolOverlayService.showSharedChatReply(token,
            response.take(MAX_REPLY).ifBlank{"The shared Chat engine returned no result."})
        return true
    }
}
