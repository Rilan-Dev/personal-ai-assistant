package dev.rilandev.personalaiassistant

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/**
 * Durable, one-shot authorization BINDING for a legacy compound Voice step.
 *
 * This is NOT a second executor or an AI-granted mandate. The owner still
 * approves through the existing visible Voice surface, and each Android tool
 * keeps its own package/window/recipient and permission checks.
 *
 * We consume the exact command/step receipt before calling those tools. If
 * the process dies after consumption, the receipt remains and the Voice
 * checkpoint becomes uncertain. A model or restarted cursor cannot replay it.
 */
internal object NexaLegacyVoiceStepApproval {
    private val lock=Any()

    private fun digest(value:String):String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}

    private fun approvalId(taskId:String,ordinal:Int):String =
        "approval-"+UUID.nameUUIDFromBytes(
            ("nexa-voice-step-v1:"+taskId+":"+ordinal)
                .toByteArray(StandardCharsets.UTF_8)).toString()

    /**
     * Caller must have just consumed a visible owner confirmation or
     * an explicit, revocable low-risk Voice policy, not an LLM tool call.
     */
    fun consume(context:Context,taskId:String,sessionId:String,ordinal:Int,
                plannedStep:String,command:String,packageName:String=""):Boolean {
        if(!Regex("task-[A-Za-z0-9_-]{8,96}").matches(taskId)||
            sessionId.length !in 1..100 || ordinal !in 0..7 ||
            plannedStep.length !in 5..160 || command.length !in 1..500 ||
            packageName.length>200 || listOf(sessionId,plannedStep,command,packageName)
                .any{raw->raw.any{it.code<32}})
            return false
        return try{synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val stepId=db.rawQuery(
                        "SELECT v.status,v.step_index,v.payload,t.status,"+
                            "s.step_id,s.status "+
                            "FROM voice_task_checkpoints v "+
                            "JOIN agent_tasks t ON t.task_id=v.task_id "+
                            "JOIN agent_task_steps s ON s.task_id=v.task_id "+
                            "AND s.ordinal=v.step_index "+
                            "WHERE v.task_id=? AND v.session_id=? "+
                            "AND t.session_id=? AND t.origin='live-voice' "+
                            "AND t.policy_version=? AND s.ordinal=? LIMIT 2",
                        arrayOf(taskId,sessionId,sessionId,
                            NexaLegacyVoiceTaskMirror.POLICY,ordinal.toString())
                    ).use{row->
                        if(!row.moveToFirst() ||
                            row.getString(0)!="executing" ||
                            row.getInt(1)!=ordinal ||
                            row.getString(3)!="executing" ||
                            row.getString(5)!="checkpoint_pending")
                            return@synchronized false
                        val data=JSONObject(NexaConversationDb
                            .openNativeTaskArguments(row.getBlob(2)))
                        val steps=data.optJSONArray("steps")
                            ?:return@synchronized false
                        if(ordinal>=steps.length() ||
                            steps.optString(ordinal)!=plannedStep ||
                            data.optString("request").isBlank() ||
                            data.optString("request").length>500)
                            return@synchronized false
                        val id=row.getString(4)
                        if(row.moveToNext())return@synchronized false
                        id
                    }
                    val claimed=db.rawQuery(
                        "SELECT 1 FROM agent_step_approvals "+
                            "WHERE step_id=? LIMIT 1",arrayOf(stepId)
                    ).use{it.moveToFirst()}
                    if(claimed)return@synchronized false
                    val now=System.currentTimeMillis()
                    val targetDigest=digest(
                        "nexa-voice-target-v1:"+packageName+":"+plannedStep)
                    val inputDigest=digest(
                        "nexa-voice-command-v1:"+taskId+":"+sessionId+":"+
                            ordinal+":"+plannedStep+":"+command+":"+packageName)
                    check(db.compileStatement(
                        "INSERT INTO agent_step_approvals(approval_id,step_id,"+
                            "target_digest,input_digest,state,expires_at,consumed_at) "+
                            "VALUES(?,?,?,?,'consumed',?,?)"
                    ).use{stmt->
                        stmt.bindString(1,approvalId(taskId,ordinal))
                        stmt.bindString(2,stepId)
                        stmt.bindString(3,targetDigest)
                        stmt.bindString(4,inputDigest)
                        stmt.bindLong(5,now+60_000L)
                        stmt.bindLong(6,now)
                        stmt.executeInsert()!=-1L
                    })
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                            "VALUES(?,?,'voice_step_owner_review_consumed',?)",
                        arrayOf(taskId,stepId,now)
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }
}
