package dev.rilandev.personalaiassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only owner task index across the canonical v3 store and legacy voice
 * checkpoints. This is an observability adapter, NOT a second executor.
 *
 * Legacy voice checkpoints retain their own lifecycle until Voice is migrated
 * into the native coordinator. Never treat a checkpoint as a mandate, approval,
 * verified action, or permission to resume an interrupted external action.
 */
internal object NexaUnifiedTaskTimeline {
    private data class Summary(
        val taskId:String,val source:String,val origin:String,
        val status:String,val revision:Int,val updatedAt:Long,
        val stepCount:Int,val verifiedSteps:Int,val detailsRedacted:Boolean=false,
        val parentTaskId:String?=null,val parentOrdinal:Int?=null,
        val linkedUiActions:Int=0,val observedUiActions:Int=0
    ) {
        fun json():JSONObject=JSONObject()
            .put("taskId",taskId)
            .put("source",source)
            .put("origin",origin)
            .put("status",status)
            .put("revision",revision)
            .put("updatedAt",updatedAt)
            .put("stepCount",stepCount)
            .put("verifiedSteps",verifiedSteps)
            .put("detailsRedacted",detailsRedacted)
            .apply {
                if(parentTaskId!=null)put("parentTaskId",parentTaskId)
                if(parentOrdinal!=null)put("parentOrdinal",parentOrdinal)
                if(linkedUiActions>0)put("linkedUiActions",linkedUiActions)
                if(linkedUiActions>0)put("observedUiActions",observedUiActions)
            }
    }
    private val id=Regex("task-[A-Za-z0-9_-]{8,96}")
    private val status=Regex("[a-z_]{1,40}")
    private val origins=setOf("chat","live-voice","assistant-role","overlay")

    fun recent(context:Context,limit:Int):JSONArray {
        // Reconcile native and UI claims before calculating an owner timeline.
        // Neither recovery path ever dispatches or retries an Android action.
        NexaTaskCoordinator.recoverStaleInFlight(context)
        NexaUiTaskAudit.recoverStale(context)
        NexaAgentActionAudit.recoverStale(context)
        val maximum=limit.coerceIn(1,60)
        val helper=NexaConversationDb(context)
        try{
            val db=helper.readableDatabase
            val all=mutableListOf<Summary>()
            db.rawQuery(
                "SELECT t.task_id,t.origin,t.status,t.plan_revision,t.updated_at,t.policy_version," +
                    "(SELECT COUNT(*) FROM agent_task_steps s WHERE s.task_id=t.task_id)," +
                    "(SELECT COUNT(*) FROM agent_task_steps s " +
                        "WHERE s.task_id=t.task_id AND s.status='verified')," +
                    "(SELECT COUNT(*) FROM agent_task_events e WHERE e.task_id=t.task_id " +
                        "AND e.event_kind='owner_redacted_legacy_voice') " +
                    "FROM agent_tasks t ORDER BY t.updated_at DESC,t.rowid DESC LIMIT ?",
                arrayOf(maximum.toString())
            ).use{c->
                while(c.moveToNext()) {
                    val taskId=c.getString(0)
                    val origin=c.getString(1)
                    val current=c.getString(2)
                    if(!id.matches(taskId)||origin !in origins||
                        !status.matches(current))continue
                    val source=when(c.getString(5)){
                        NexaUiTaskAudit.POLICY -> "ui_action_audit"
                        NexaAgentActionAudit.POLICY -> "chat_action_audit"
                        NexaLegacyVoiceTaskMirror.POLICY -> "legacy_voice_checkpoint"
                        else -> "native"
                    }
                    // Neither a Voice cursor nor a UI dispatch is evidence
                    // that an external app completed the requested outcome.
                    val verified=if(source=="native")c.getInt(7) else 0
                    all.add(Summary(taskId,source,origin,current,
                        c.getInt(3),c.getLong(4),c.getInt(6),verified,
                        c.getInt(8)>0))
                }
            }
            db.rawQuery(
                "SELECT v.task_id,v.status,v.step_index,v.updated_at " +
                    "FROM voice_task_checkpoints v WHERE NOT EXISTS (" +
                    "SELECT 1 FROM agent_tasks t WHERE t.task_id=v.task_id) " +
                    "ORDER BY v.updated_at DESC,v.rowid DESC LIMIT ?",
                arrayOf(maximum.toString())
            ).use{c->
                while(c.moveToNext()){
                    val taskId=c.getString(0)
                    val current=c.getString(1)
                    if(!id.matches(taskId)||!status.matches(current))continue
                    // Voice checkpoints don't attest that the external app
                    // actually completed a step. Do not count them as verified.
                    all.add(Summary(taskId,"legacy_voice_checkpoint","live-voice",
                        current,0,c.getLong(3),0,0))
                }
            }
            // Parent/child links were sealed in the SAME SQLite transaction
            // as each voice-origin UI action's permanent claim. Decrypt only
            // opaque IDs and the ordinal for this owner-visible local index.
            // Never expose target package digests or untrusted screen text.
            val links=mutableMapOf<String,Pair<String,Int>>()
            db.rawQuery(
                "SELECT task_id,detail_cipher FROM agent_task_events "+
                    "WHERE event_kind='voice_ui_child_claimed' "+
                    "AND detail_cipher IS NOT NULL ORDER BY id DESC LIMIT ?",
                arrayOf((maximum*6).toString())
            ).use{rows->
                while(rows.moveToNext()){
                    try{
                        val parent=rows.getString(0)
                        val data=JSONObject(NexaConversationDb
                            .unsealNativeTaskArguments(rows.getBlob(1)))
                        val child=data.optString("childTaskId")
                        val ordinal=data.optInt("ordinal",-1)
                        if(id.matches(parent)&&id.matches(child)&&
                            ordinal in 0..7 && !links.containsKey(child))
                            links[child]=parent to ordinal
                    }catch(_:Exception){/* Old/redacted rows have no link. */}
                }
            }
            val childStatuses=mutableMapOf<String,String>()
            if(links.isNotEmpty()){
                db.rawQuery(
                    "SELECT task_id,status FROM agent_tasks "+
                        "WHERE policy_version=? ORDER BY updated_at DESC LIMIT ?",
                    arrayOf(NexaUiTaskAudit.POLICY,(maximum*6).toString())
                ).use{rows->
                    while(rows.moveToNext()){
                        val child=rows.getString(0)
                        if(child in links)childStatuses[child]=rows.getString(1)
                    }
                }
            }
            val totals=mutableMapOf<String,Pair<Int,Int>>()
            for((child,parent) in links){
                val old=totals[parent.first] ?: (0 to 0)
                totals[parent.first]=(old.first+1) to
                    (old.second+if(childStatuses[child]=="observed")1 else 0)
            }
            val result=JSONArray()
            for(item in all.sortedByDescending{it.updatedAt}.take(maximum)){
                val parent=if(item.source=="ui_action_audit")
                    links[item.taskId] else null
                val counts=if(item.source=="legacy_voice_checkpoint")
                    totals[item.taskId] else null
                val summary=item.copy(
                    parentTaskId=parent?.first,parentOrdinal=parent?.second,
                    linkedUiActions=counts?.first ?: 0,
                    observedUiActions=counts?.second ?: 0)
                result.put(summary.json())
            }
            return result
        }finally{helper.close()}
    }
}
