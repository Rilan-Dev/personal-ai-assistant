package dev.rilandev.personalaiassistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/**
 * Audit-only mirror for the EXISTING Chat AgentJournal action claim.
 *
 * The secure AgentJournal remains the sole approver/execution authority for
 * its tool calls. This class never dispatches Android actions, issues a mandate,
 * or interprets a settled journal entry as evidence of business completion.
 *
 * SQLite is claimed FIRST. If the later encrypted journal write fails the
 * SQLite claim remains an irreversible no-replay tombstone. Thus a crash
 * between the two storage systems can deny a retry, but cannot authorize one.
 */
internal object NexaAgentActionAudit {
    const val POLICY="chat-action-audit-v1"
    private val lock=Any()
    private val digestPattern=Regex("[0-9a-f]{64}")

    private fun sha(value:String):String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}

    private fun identity(session:String,call:String):String =
        sha("nexa-chat-action-v1:"+session.length+":"+session+":"+
            call.length+":"+call)

    private fun opaqueId(prefix:String,key:String):String =
        prefix+UUID.nameUUIDFromBytes(
            (prefix+":"+key).toByteArray(StandardCharsets.UTF_8)
        ).toString()

    private fun valid(session:String,call:String,fingerprint:String):Boolean =
        session.length in 1..256 && call.length in 1..256 &&
        fingerprint.length in 1..4096 &&
        session.none{it.code<32} && call.none{it.code<32}

    /**
     * Reserve this old Chat journal action in the canonical v3 ledger BEFORE
     * the calling bridge marks the secure journal as claimed. No model-supplied
     * task id, tool permission, or plaintext arguments are stored.
     */
    fun claim(context:Context,session:String,call:String,
              fingerprint:String,requiresApproval:Boolean):Boolean {
        if(!valid(session,call,fingerprint))return false
        return try{synchronized(lock){
            val key=identity(session,call)
            val taskId=opaqueId("task-",key)
            val stepId=opaqueId("step-",key)
            val now=System.currentTimeMillis()
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val already=db.rawQuery(
                        "SELECT 1 FROM agent_tasks WHERE task_id=? LIMIT 1",
                        arrayOf(taskId)
                    ).use{it.moveToFirst()}
                    if(already)return@synchronized false
                    val goal=NexaConversationDb.sealNativeTaskArguments(
                        "Legacy Chat tool action; owner approval belongs to AgentJournal")
                    val args=NexaConversationDb.sealNativeTaskArguments(
                        JSONObject().put("fingerprintDigest",sha(fingerprint))
                            .put("approvalRequired",requiresApproval).toString())
                    check(db.compileStatement(
                        "INSERT INTO agent_tasks(task_id,session_id,origin,status,"+
                            "policy_version,plan_revision,goal_cipher,created_at,updated_at) "+
                            "VALUES(?,?,'chat','running',?,1,?,?,?)"
                    ).use{stmt->
                        stmt.bindString(1,taskId)
                        stmt.bindString(2,"owner-chat-"+key.take(32))
                        stmt.bindString(3,POLICY)
                        stmt.bindBlob(4,goal)
                        stmt.bindLong(5,now)
                        stmt.bindLong(6,now)
                        stmt.executeInsert()!=-1L
                    })
                    check(db.compileStatement(
                        "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id,"+
                            "status,risk,expected_evidence,arguments_cipher) "+
                            "VALUES(?,?,0,'legacy.chat.action','outcome_unknown',"+
                            "?,'owner_review',?)"
                    ).use{stmt->
                        stmt.bindString(1,stepId)
                        stmt.bindString(2,taskId)
                        stmt.bindString(3,if(requiresApproval)"medium" else "low")
                        stmt.bindBlob(4,args)
                        stmt.executeInsert()!=-1L
                    })
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                            "VALUES(?,?,'chat_action_preclaimed',?)",
                        arrayOf(taskId,stepId,now)
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /**
     * Records only the old journal's disposition. "Settled" means the
     * dispatcher finished; it does NOT verify an external app, delivered
     * message, changed device fact, or completed an owner's business goal.
     */
    fun recordSettlement(context:Context,session:String,call:String,
                         fingerprint:String,uncertain:Boolean):Boolean{
        if(!valid(session,call,fingerprint))return false
        return try{synchronized(lock){
            val key=identity(session,call)
            val taskId=opaqueId("task-",key)
            val stepId=opaqueId("step-",key)
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val bound=db.rawQuery(
                        "SELECT s.arguments_cipher FROM agent_task_steps s "+
                            "JOIN agent_tasks t ON t.task_id=s.task_id "+
                            "WHERE s.step_id=? AND s.task_id=? AND "+
                            "s.status='outcome_unknown' AND t.policy_version=? "+
                            "AND t.status='running'",
                        arrayOf(stepId,taskId,POLICY)
                    ).use{row->
                        if(!row.moveToFirst())return@synchronized false
                        val data=JSONObject(NexaConversationDb
                            .openNativeTaskArguments(row.getBlob(0)))
                        data.length()==2 &&
                            digestPattern.matches(data.optString("fingerprintDigest")) &&
                            data.optString("fingerprintDigest")==sha(fingerprint)
                    }
                    if(!bound)return@synchronized false
                    val now=System.currentTimeMillis()
                    val taskState=if(uncertain)"uncertain" else "settled_unverified"
                    val stepState=if(uncertain)"uncertain" else "dispatch_settled"
                    check(db.compileStatement(
                        "UPDATE agent_tasks SET status=?,plan_revision=plan_revision+1,"+
                            "updated_at=? WHERE task_id=? AND policy_version=? AND status='running'"
                    ).use{stmt->
                        stmt.bindString(1,taskState)
                        stmt.bindLong(2,now)
                        stmt.bindString(3,taskId)
                        stmt.bindString(4,POLICY)
                        stmt.executeUpdateDelete()==1
                    })
                    check(db.compileStatement(
                        "UPDATE agent_task_steps SET status=? WHERE step_id=? "+
                            "AND task_id=? AND status='outcome_unknown'"
                    ).use{stmt->
                        stmt.bindString(1,stepState)
                        stmt.bindString(2,stepId)
                        stmt.bindString(3,taskId)
                        stmt.executeUpdateDelete()==1
                    })
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                            "VALUES(?,?,?,?)",
                        arrayOf(taskId,stepId,if(uncertain)
                            "chat_action_uncertain_no_replay" else
                            "chat_dispatch_settled_unverified",now)
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /** Crash/ANR recovery only; never dispatches another tool call. */
    fun recoverStale(context:Context,staleAfterMs:Long=120_000L):Int{
        if(staleAfterMs<60_000L)return 0
        return try{synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val tasks=mutableListOf<String>()
                    val now=System.currentTimeMillis()
                    db.rawQuery(
                        "SELECT task_id FROM agent_tasks WHERE policy_version=? "+
                            "AND status='running' AND updated_at<? LIMIT 100",
                        arrayOf(POLICY,(now-staleAfterMs).toString())
                    ).use{row->
                        while(row.moveToNext())tasks.add(row.getString(0))
                    }
                    for(taskId in tasks){
                        db.execSQL(
                            "UPDATE agent_tasks SET status='uncertain',"+
                                "plan_revision=plan_revision+1,updated_at=? "+
                                "WHERE task_id=? AND policy_version=? AND status='running'",
                            arrayOf(now,taskId,POLICY)
                        )
                        db.execSQL(
                            "UPDATE agent_task_steps SET status='uncertain' "+
                                "WHERE task_id=? AND status='outcome_unknown'",
                            arrayOf(taskId)
                        )
                        db.execSQL(
                            "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                                "VALUES(?,NULL,'chat_claim_recovered_no_replay',?)",
                            arrayOf(taskId,now)
                        )
                    }
                    db.setTransactionSuccessful()
                    tasks.size
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){0}
    }
}
