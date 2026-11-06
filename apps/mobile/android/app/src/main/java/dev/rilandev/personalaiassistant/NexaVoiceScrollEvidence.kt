package dev.rilandev.personalaiassistant

import android.content.Context
import org.json.JSONObject

/**
 * Evidence-only continuation gate for an already owner-approved Voice scroll.
 *
 * A raw Android gesture acceptance, arbitrary screen change, model response,
 * or different task's UI event can NEVER satisfy this read. It requires the
 * exact consumed parent step, its single linked UI child, and a native
 * Accessibility scroll event recorded after that child claim. This NEVER
 * re-dispatches the scroll and does not attest any business outcome.
 */
internal object NexaVoiceScrollEvidence {
    private val id=Regex("task-[A-Za-z0-9_-]{8,96}")
    private val stepId=Regex("step-[A-Za-z0-9_-]{8,96}")

    fun observed(context:Context,taskId:String,sessionId:String,
                 ordinal:Int,direction:String):Boolean {
        if(!id.matches(taskId)||sessionId.length !in 1..100 ||
            ordinal !in 0..7 || direction !in setOf("up","down","left","right"))
            return false
        return try {
            val helper=NexaConversationDb(context)
            try{
                val db=helper.readableDatabase
                val now=System.currentTimeMillis()
                val parentStep=db.rawQuery(
                    "SELECT s.step_id FROM voice_task_checkpoints v "+
                        "JOIN agent_tasks t ON t.task_id=v.task_id "+
                        "JOIN agent_task_steps s ON s.task_id=t.task_id "+
                            "AND s.ordinal=v.step_index "+
                        "JOIN agent_step_approvals a ON a.step_id=s.step_id "+
                        "WHERE v.task_id=? AND v.session_id=? "+
                        "AND v.status='executing' AND v.step_index=? "+
                        "AND t.session_id=? AND t.origin='live-voice' "+
                        "AND t.policy_version=? AND t.status='executing' "+
                        "AND s.status='checkpoint_pending' "+
                        "AND a.state='consumed' AND a.expires_at>? LIMIT 2",
                    arrayOf(taskId,sessionId,ordinal.toString(),sessionId,
                        NexaLegacyVoiceTaskMirror.POLICY,now.toString())
                ).use{row->
                    if(!row.moveToFirst())return@use null
                    val found=row.getString(0)
                    if(row.moveToNext())null else found
                }?:return false
                val linked=db.rawQuery(
                    "SELECT detail_cipher FROM agent_task_events "+
                        "WHERE task_id=? AND step_id=? "+
                        "AND event_kind='voice_ui_child_claimed' LIMIT 2",
                    arrayOf(taskId,parentStep)
                ).use{row->
                    if(!row.moveToFirst())return@use null
                    val ciphertext=row.getBlob(0)
                    if(row.moveToNext())null else JSONObject(
                        NexaConversationDb.unsealNativeTaskArguments(ciphertext))
                }?:return false
                val childTask=linked.optString("childTaskId")
                val childStep=linked.optString("childStepId")
                if(!id.matches(childTask)||!stepId.matches(childStep)||
                    linked.optString("actionKind")!="scroll_"+direction ||
                    linked.optInt("ordinal",-1)!=ordinal)
                    return false
                db.rawQuery(
                    "SELECT e.detail_cipher FROM agent_tasks t "+
                        "JOIN agent_task_steps s ON s.task_id=t.task_id "+
                        "JOIN agent_observations o ON o.step_id=s.step_id "+
                        "JOIN agent_task_events e ON e.task_id=t.task_id "+
                            "AND e.step_id=s.step_id "+
                        "WHERE t.task_id=? AND t.policy_version=? "+
                        "AND t.origin='live-voice' AND t.status='observed' "+
                        "AND s.step_id=? AND s.status='observed' "+
                        "AND o.source_kind='android_accessibility' "+
                        "AND o.verdict='ui_change_observed' "+
                        "AND e.event_kind='ui_change_observed' LIMIT 2",
                    arrayOf(childTask,NexaUiTaskAudit.POLICY,childStep)
                ).use{row->
                    if(!row.moveToFirst())return@use false
                    val record=JSONObject(NexaConversationDb
                        .unsealNativeTaskArguments(row.getBlob(0)))
                    record.length()==1 &&
                        record.optString("androidObservation")==
                            "scroll_event_observed" && !row.moveToNext()
                }
            }finally{helper.close()}
        }catch(_:Exception){false}
    }
}
