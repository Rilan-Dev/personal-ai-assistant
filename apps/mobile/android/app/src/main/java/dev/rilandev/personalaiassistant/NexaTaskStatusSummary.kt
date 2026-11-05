package dev.rilandev.personalaiassistant

import android.app.KeyguardManager
import android.content.Context

/**
 * One read-only task identity/status view for the local assistant, overlay and
 * owner Chat/Tools navigation. Uses the canonical encrypted task index and
 * marks legacy Voice checkpoints as NOT verified. No goal, provider payload,
 * screen text, contact, tool arguments or local keys are ever exposed.
 */
internal object NexaTaskStatusSummary {
    private val supported=setOf(
        "task status","my task status","show my tasks","list my tasks",
        "what tasks are pending","what tasks are running","task progress"
    )
    fun matches(text:String):Boolean{
        if(text.length !in 8..140 || text.any{it.code<32})return false
        val normalized=text.trim().lowercase(java.util.Locale.ROOT)
            .removePrefix("hey nexa ").removePrefix("please ")
            .trimEnd('.','?','!').trim()
        return normalized in supported
    }
    fun localOwnerReport(context:Context):String{
        val locked=try{
            (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.isKeyguardLocked==true
        }catch(_:Exception){true}
        if(locked)return "Unlock your device to check private task status."
        return try{
            val items=NexaUnifiedTaskTimeline.recent(context,5)
            if(items.length()==0)return "No saved native tasks or legacy voice checkpoints."
            val reports=mutableListOf<String>()
            for(index in 0 until items.length()){
                val task=items.optJSONObject(index)?:continue
                val source=task.optString("source")
                val origin=task.optString("origin")
                val status=task.optString("status")
                val steps=task.optInt("stepCount",0)
                val verified=task.optInt("verifiedSteps",0)
                val id=task.optString("taskId").takeLast(8)
                if(status.isBlank() || id.length!=8)continue
                val evidence=if(source=="native") {
                    if(steps>0)" · verified native steps ${verified.coerceAtMost(steps)}/$steps"
                    else " · review only"
                }else " · legacy checkpoint; not action verification"
                reports.add("${origin.replace('-',' ')} task ${id}: $status$evidence")
            }
            if(reports.isEmpty())"No readable task status is available."
            else ("Your latest tasks: "+reports.joinToString(". ")+
                ". Open Nexa Tools for details or to revoke an active native task.").take(850)
        }catch(_:Exception){"Private task status unavailable. No task was changed."}
    }
}
