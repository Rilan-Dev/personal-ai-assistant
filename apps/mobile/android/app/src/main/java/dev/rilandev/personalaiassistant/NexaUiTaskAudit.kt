package dev.rilandev.personalaiassistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Audit-only bridge for the EXISTING owner-approved Android UI action executor.
 *
 * The caller must first reserve the irreversible token in agent_action_claims.
 * Creation of the audit task is performed inside that same SQLite transaction.
 * This class NEVER creates a mandate, grants an approval, dispatches a gesture,
 * types, launches apps or sends messages. It is NOT an action authorization API.
 *
 * "android_accepted" means only OS dispatch. Even observed screen changes
 * cannot attest completion of a business task, message delivery or the
 * requested navigation. Audit entries are not eligible for coordinator replay.
 */
internal object NexaUiTaskAudit {
    const val POLICY="ui-action-audit-v1"
    private val sha=Regex("[0-9a-f]{64}")
    private val allowed=setOf("tap","scroll_up","scroll_down","scroll_left",
        "scroll_right","message_fill","text_fill")
    private val observed=setOf("observed_change","scroll_event_observed",
        "window_change_observed","structural_change_observed")
    private val uncertain=setOf("unknown","no_observable_change",
        "context_changed_unverified","unverified")
    private val denied=setOf("android_rejected")
    private val accepted=setOf("android_accepted")
    private val lock=Any()

    private fun id(prefix:String,tokenDigest:String):String =
        prefix+UUID.nameUUIDFromBytes((prefix+":"+tokenDigest)
            .toByteArray(StandardCharsets.UTF_8)).toString()
    private fun digest(raw:String):String=MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(StandardCharsets.UTF_8))
        .joinToString(""){"%02x".format(it.toInt() and 255)}

    /** Must be called only inside the caller's outer SQLite transaction. */
    fun createInTransaction(db:SQLiteDatabase,tokenDigest:String,
                            actionKind:String,packageDigest:String,now:Long,
                            reviewedInputDigest:String?=null):Boolean{
        if(!sha.matches(tokenDigest)||!sha.matches(packageDigest)||
            actionKind !in allowed ||
            (actionKind in setOf("message_fill","text_fill") &&
                !sha.matches(reviewedInputDigest.orEmpty())) ||
            (actionKind !in setOf("message_fill","text_fill") &&
                reviewedInputDigest!=null))return false
        val taskId=id("task-",tokenDigest)
        val stepId=id("step-",tokenDigest)
        // The exact package and user-visible labels never enter this task.
        // The preexisting UI action tombstone separately binds their digests.
        val goal=NexaConversationDb.sealNativeTaskArguments(
            "Owner-approved UI action: "+actionKind)
        val args=NexaConversationDb.sealNativeTaskArguments(
            JSONObject().put("kind",actionKind)
                .put("packageDigest",packageDigest).toString())
        db.compileStatement(
            "INSERT INTO agent_tasks(task_id,session_id,origin,status,policy_version,"+
                "plan_revision,goal_cipher,created_at,updated_at) "+
                "VALUES(?,?,'overlay','running',?,1,?,?,?)"
        ).use{stmt->
            stmt.bindString(1,taskId)
            stmt.bindString(2,"owner-ui-"+tokenDigest.take(24))
            stmt.bindString(3,POLICY)
            stmt.bindBlob(4,goal)
            stmt.bindLong(5,now)
            stmt.bindLong(6,now)
            check(stmt.executeInsert()!=-1L)
        }
        db.compileStatement(
            "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id,status,"+
                "risk,expected_evidence,arguments_cipher) "+
                "VALUES(?,?,0,?,'outcome_unknown','medium','ui_observation',?)"
        ).use{stmt->
            stmt.bindString(1,stepId)
            stmt.bindString(2,taskId)
            stmt.bindString(3,"ui."+actionKind)
            stmt.bindBlob(4,args)
            check(stmt.executeInsert()!=-1L)
        }
        // The existing UI controller's owner confirmation was already
        // consumed at the permanent, one-shot claim boundary. Record the
        // EXACT claimed kind + opaque token + target-package digest as a
        // per-step AUDIT receipt. It cannot itself grant execution.
        db.compileStatement(
            "INSERT INTO agent_step_approvals(approval_id,step_id,target_digest,"+
                "input_digest,state,expires_at,consumed_at) "+
                "VALUES(?,?,?,?,'consumed',?,?)"
        ).use{stmt->
            stmt.bindString(1,id("approval-",tokenDigest))
            stmt.bindString(2,stepId)
            stmt.bindString(3,packageDigest)
            stmt.bindString(4,reviewedInputDigest ?:
                digest("ui-action-v1:"+actionKind+":"+tokenDigest))
            stmt.bindLong(5,now+300_000L)
            stmt.bindLong(6,now)
            check(stmt.executeInsert()!=-1L)
        }
        db.execSQL(
            "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                "VALUES(?,?,'ui_action_claimed',?)",arrayOf(taskId,stepId,now)
        )
        return true
    }

    /**
     * The outcome is only valid for the exact already-consumed UI claim.
     * A fabricated audit task without the per-step receipt, a swapped
     * package, or an altered action kind can never receive verified progress.
     */
    private fun receiptBound(db:SQLiteDatabase,taskId:String,stepId:String,
                             tokenDigest:String):Boolean {
        return db.rawQuery(
            "SELECT s.arguments_cipher,a.target_digest,a.input_digest,"+
                "a.state,a.consumed_at FROM agent_task_steps s "+
                "JOIN agent_step_approvals a ON a.step_id=s.step_id "+
                "WHERE s.task_id=? AND s.step_id=? "+
                "AND s.status='outcome_unknown' LIMIT 2",
            arrayOf(taskId,stepId)
        ).use{row->
            if(!row.moveToFirst())return@use false
            val args=JSONObject(NexaConversationDb
                .openNativeTaskArguments(row.getBlob(0)))
            val kind=args.optString("kind")
            val pkg=args.optString("packageDigest")
            val valid=args.length()==2 && kind in allowed &&
                sha.matches(pkg) && row.getString(1)==pkg &&
                row.getString(2)==digest("ui-action-v1:"+kind+":"+tokenDigest) &&
                row.getString(3)=="consumed" && row.getLong(4)>0L
            valid && !row.moveToNext()
        }
    }

    /** Audit an Android dispatch result or a separate Accessibility observation.
     * A positive UI change remains "observed", never business-task "completed".
     */
    fun recordOutcome(context:Context,tokenDigest:String,outcome:String):Boolean{
        if(!sha.matches(tokenDigest) ||
            outcome !in observed && outcome !in uncertain &&
            outcome !in denied && outcome !in accepted)return false
        val taskId=id("task-",tokenDigest)
        val stepId=id("step-",tokenDigest)
        val taskState=when(outcome){
            in observed->"observed"
            in denied->"failed"
            in uncertain->"uncertain"
            else->"running"
        }
        val stepState=when(outcome){
            in observed->"observed"
            in denied->"rejected"
            in uncertain->"uncertain"
            else->"outcome_unknown"
        }
        val event=when(outcome){
            in observed->"ui_change_observed"
            in denied->"ui_dispatch_rejected"
            in uncertain->"ui_outcome_uncertain"
            else->"ui_dispatch_accepted"
        }
        return try{synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try {
                    val now=System.currentTimeMillis()
                    if(!receiptBound(db,taskId,stepId,tokenDigest))
                        return@synchronized false
                    val changed=db.compileStatement(
                        "UPDATE agent_tasks SET status=?,plan_revision=plan_revision+1,"+
                            "updated_at=? WHERE task_id=? AND policy_version=? "+
                            "AND status='running'"
                    ).use{stmt->
                        stmt.bindString(1,taskState)
                        stmt.bindLong(2,now)
                        stmt.bindString(3,taskId)
                        stmt.bindString(4,POLICY)
                        stmt.executeUpdateDelete()
                    }
                    if(changed!=1)return@synchronized false
                    val step=db.compileStatement(
                        "UPDATE agent_task_steps SET status=? WHERE task_id=? "+
                            "AND step_id=? AND status='outcome_unknown'"
                    ).use{stmt->
                        stmt.bindString(1,stepState)
                        stmt.bindString(2,taskId)
                        stmt.bindString(3,stepId)
                        stmt.executeUpdateDelete()
                    }
                    check(step==1)
                    if(outcome in observed){
                        // A screen changed; do not infer which task the screen
                        // change fulfilled. No raw visual evidence is persisted.
                        db.execSQL(
                            "INSERT INTO agent_observations(observation_id,step_id,"+
                                "source_kind,verdict,evidence_digest,observed_at) "+
                                "VALUES(?,?,'android_accessibility','ui_change_observed',?,?)",
                            arrayOf(id("observation-",tokenDigest),stepId,
                                digest(tokenDigest+":"+outcome),now)
                        )
                    }
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                            "VALUES(?,?,?,?)",arrayOf(taskId,stepId,event,now)
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /** No automatic replay of a claimed external UI action, ever.
     * Stale dispatch-accepted actions become uncertain rather than completed.
     */
    fun recoverStale(context:Context,staleAfterMs:Long=120_000L):Int{
        if(staleAfterMs<60_000L)return 0
        return try{synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try {
                    val now=System.currentTimeMillis()
                    val taskIds=mutableListOf<String>()
                    db.rawQuery(
                        "SELECT task_id FROM agent_tasks WHERE policy_version=? "+
                            "AND status='running' AND updated_at<? LIMIT 60",
                        arrayOf(POLICY,(now-staleAfterMs).toString())
                    ).use{cursor->
                        while(cursor.moveToNext())taskIds.add(cursor.getString(0))
                    }
                    var changed=0
                    for(taskId in taskIds){
                        val updated=db.compileStatement(
                            "UPDATE agent_tasks SET status='uncertain',"+
                                "plan_revision=plan_revision+1,updated_at=? "+
                                "WHERE task_id=? AND policy_version=? AND status='running'"
                        ).use{stmt->
                            stmt.bindLong(1,now)
                            stmt.bindString(2,taskId)
                            stmt.bindString(3,POLICY)
                            stmt.executeUpdateDelete()
                        }
                        if(updated!=1)continue
                        db.execSQL(
                            "UPDATE agent_task_steps SET status='uncertain' "+
                                "WHERE task_id=? AND status='outcome_unknown'",
                            arrayOf(taskId)
                        )
                        db.execSQL(
                            "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                                "VALUES(?,NULL,'ui_audit_recovered_uncertain',?)",
                            arrayOf(taskId,now)
                        )
                        changed++
                    }
                    db.setTransactionSuccessful()
                    changed
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){0}
    }
}
