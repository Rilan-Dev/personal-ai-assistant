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
        val stepCount:Int,val verifiedSteps:Int,val detailsRedacted:Boolean=false
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
    }
    private val id=Regex("task-[A-Za-z0-9_-]{8,96}")
    private val status=Regex("[a-z_]{1,40}")
    private val origins=setOf("chat","live-voice","assistant-role","overlay")

    fun recent(context:Context,limit:Int):JSONArray {
        // Reconcile native and UI claims before calculating an owner timeline.
        // Neither recovery path ever dispatches or retries an Android action.
        NexaTaskCoordinator.recoverStaleInFlight(context)
        NexaUiTaskAudit.recoverStale(context)
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
                    "FROM agent_tasks t ORDER BY t.updated_at DESC LIMIT ?",
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
                    "ORDER BY v.updated_at DESC LIMIT ?",
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
            val result=JSONArray()
            for(item in all.sortedByDescending{it.updatedAt}.take(maximum))
                result.put(item.json())
            return result
        }finally{helper.close()}
    }
}
