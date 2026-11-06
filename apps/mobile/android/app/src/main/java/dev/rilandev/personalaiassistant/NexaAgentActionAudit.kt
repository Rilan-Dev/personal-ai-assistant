package dev.rilandev.personalaiassistant

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

/**
 * The EXISTING Chat AgentJournal remains the sole approval / tool-execution
 * authority. This is an AUDIT-ONLY mirror in the canonical encrypted task DB.
 *
 * One owner request is represented by one agent-task identity derived from
 * the Chat session. Subsequent tool calls in that request append bounded
 * steps, never create competing task authorities. A call's SQLite claim is
 * committed BEFORE AgentJournal's encrypted action-claim write. If either
 * store fails, the claimed action is never replayed automatically.
 *
 * A "settled" tool dispatch is NOT independently verified device action,
 * external-app navigation, provider delivery, or business-task completion.
 */
internal object NexaAgentActionAudit {
    const val POLICY="chat-action-audit-v1"
    private val lock=Any()
    private const val MAX_STEPS=8
    private val shaPattern=Regex("[0-9a-f]{64}")
    private val claimedStates=setOf("running","settled_unverified")

    private fun sha(text:String):String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}

    private fun sessionKey(session:String):String =
        sha("nexa-chat-request-v1:"+session.length+":"+session)

    private fun callKey(session:String,call:String):String =
        sha("nexa-chat-action-v1:"+session.length+":"+session+":"+
            call.length+":"+call)

    private fun opaqueId(prefix:String,key:String):String =
        prefix+UUID.nameUUIDFromBytes(
            (prefix+":"+key).toByteArray(StandardCharsets.UTF_8)).toString()

    private fun valid(session:String,call:String,fingerprint:String):Boolean =
        session.length in 1..256 && call.length in 1..256 &&
        fingerprint.length in 1..4096 &&
        session.none{it.code<32} && call.none{it.code<32}

    /** A durable claim is a no-replay fence, not a grant or an executor. */
    fun claim(context:Context,session:String,call:String,
              fingerprint:String,requiresApproval:Boolean):Boolean{
        if(!valid(session,call,fingerprint))return false
        val generation=if(requiresApproval)
            NexaNativeActionSafety.approvalFence(context) ?:return false else -1L
        return try{synchronized(lock){
            val ownerKey=sessionKey(session)
            val actionKey=callKey(session,call)
            val taskId=opaqueId("task-",ownerKey)
            val stepId=opaqueId("step-",actionKey)
            val opaqueSession="owner-chat-"+ownerKey.take(32)
            val now=System.currentTimeMillis()
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val prior=db.rawQuery(
                        "SELECT session_id,origin,policy_version,status FROM agent_tasks "+
                            "WHERE task_id=?",arrayOf(taskId)
                    ).use{row->
                        if(!row.moveToFirst())null
                        else listOf(row.getString(0),row.getString(1),
                            row.getString(2),row.getString(3))
                    }
                    if(prior==null){
                        val goal=NexaConversationDb.sealNativeTaskArguments(
                            "Chat request: individual tool settlements remain unverified")
                        check(db.compileStatement(
                            "INSERT INTO agent_tasks(task_id,session_id,origin,status,"+
                                "policy_version,plan_revision,goal_cipher,created_at,updated_at) "+
                                "VALUES(?,?,'chat','running',?,1,?,?,?)"
                        ).use{stmt->
                            stmt.bindString(1,taskId)
                            stmt.bindString(2,opaqueSession)
                            stmt.bindString(3,POLICY)
                            stmt.bindBlob(4,goal)
                            stmt.bindLong(5,now)
                            stmt.bindLong(6,now)
                            stmt.executeInsert()!=-1L
                        })
                    }else{
                        if(prior.size!=4 || prior[0]!=opaqueSession ||
                            prior[1]!="chat" || prior[2]!=POLICY ||
                            prior[3] !in claimedStates)return@synchronized false
                        // No second action while the preceding step is claimed,
                        // uncertain, rejected, or yet to be reconciled.
                        val unsafe=db.rawQuery(
                            "SELECT 1 FROM agent_task_steps WHERE task_id=? "+
                                "AND status NOT IN ('dispatch_settled','ui_observed') LIMIT 1",
                            arrayOf(taskId)
                        ).use{it.moveToFirst()}
                        if(unsafe)return@synchronized false
                        // A settled native UI dispatch is not enough to move
                        // to the next step. Its exact native observation must
                        // have been committed before another claim is possible.
                        val unverifiedUi=db.rawQuery(
                            "SELECT 1 FROM agent_task_events e WHERE e.task_id=? "+
                                "AND e.event_kind='chat_native_ui_reserved' "+
                                "AND NOT EXISTS (SELECT 1 FROM agent_task_events o "+
                                "WHERE o.task_id=e.task_id AND o.step_id=e.step_id "+
                                "AND o.event_kind='chat_native_ui_observed') LIMIT 1",
                            arrayOf(taskId)
                        ).use{it.moveToFirst()}
                        if(unverifiedUi)return@synchronized false
                        check(db.compileStatement(
                            "UPDATE agent_tasks SET status='running',"+
                                "plan_revision=plan_revision+1,updated_at=? "+
                                "WHERE task_id=? AND policy_version=? "+
                                "AND status IN ('running','settled_unverified')"
                        ).use{stmt->
                            stmt.bindLong(1,now)
                            stmt.bindString(2,taskId)
                            stmt.bindString(3,POLICY)
                            stmt.executeUpdateDelete()==1
                        })
                    }
                    val ordinal=db.rawQuery(
                        "SELECT COUNT(*) FROM agent_task_steps WHERE task_id=?",
                        arrayOf(taskId)
                    ).use{row->
                        check(row.moveToFirst())
                        row.getInt(0)
                    }
                    if(ordinal>=MAX_STEPS)return@synchronized false
                    val already=db.rawQuery(
                        "SELECT 1 FROM agent_task_steps WHERE step_id=? LIMIT 1",
                        arrayOf(stepId)
                    ).use{it.moveToFirst()}
                    if(already)return@synchronized false
                    val args=NexaConversationDb.sealNativeTaskArguments(
                        JSONObject().put("fingerprintDigest",sha(fingerprint))
                            .put("approvalRequired",requiresApproval)
                            .put("pauseGeneration",generation).toString()
                    )
                    check(db.compileStatement(
                        "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id,"+
                            "status,risk,expected_evidence,arguments_cipher) "+
                            "VALUES(?,?,?,'legacy.chat.action','outcome_unknown',"+
                            "?,'owner_review',?)"
                    ).use{stmt->
                        stmt.bindString(1,stepId)
                        stmt.bindString(2,taskId)
                        stmt.bindLong(3,ordinal.toLong())
                        stmt.bindString(4,if(requiresApproval)"medium" else "low")
                        stmt.bindBlob(5,args)
                        stmt.executeInsert()!=-1L
                    })
                    if(requiresApproval) {
                        // The OLD encrypted AgentJournal already checked an
                        // exact owner approval for this same fingerprint.
                        // Mirror its consumption, never issue a new approval.
                        check(db.compileStatement(
                            "INSERT INTO agent_step_approvals(approval_id,step_id,"+
                                "target_digest,input_digest,state,expires_at,consumed_at) "+
                                "VALUES(?,?,?,?,'consumed',?,?)"
                        ).use{stmt->
                            stmt.bindString(1,opaqueId("approval-",actionKey))
                            stmt.bindString(2,stepId)
                            stmt.bindString(3,sha("nexa-chat-target-v1:"+fingerprint))
                            stmt.bindString(4,sha("nexa-chat-owner-approval-v1:"+fingerprint))
                            stmt.bindLong(5,now+300_000L)
                            stmt.bindLong(6,now)
                            stmt.executeInsert()!=-1L
                        })
                    }
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

    /** The old journal's disposition is recorded, NOT a completed action. */
    fun recordSettlement(context:Context,session:String,call:String,
                         fingerprint:String,uncertain:Boolean):Boolean{
        if(!valid(session,call,fingerprint))return false
        return try{synchronized(lock){
            val taskId=opaqueId("task-",sessionKey(session))
            val stepId=opaqueId("step-",callKey(session,call))
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val bound=db.rawQuery(
                        "SELECT s.arguments_cipher FROM agent_task_steps s "+
                            "JOIN agent_tasks t ON t.task_id=s.task_id "+
                            "WHERE s.step_id=? AND s.task_id=? "+
                            "AND s.status='outcome_unknown' AND t.policy_version=? "+
                            "AND t.status='running'",
                        arrayOf(stepId,taskId,POLICY)
                    ).use{row->
                        if(!row.moveToFirst())return@synchronized false
                        val data=JSONObject(NexaConversationDb
                            .openNativeTaskArguments(row.getBlob(0)))
                        data.length()==3 &&
                            shaPattern.matches(data.optString("fingerprintDigest")) &&
                            data.optString("fingerprintDigest")==sha(fingerprint)
                    }
                    if(!bound)return@synchronized false
                    // A previously approved tool must still have the exact
                    // consumed approval proof, with no substituted fingerprint.
                    val approvalBound=db.rawQuery(
                        "SELECT s.arguments_cipher,a.input_digest,a.state "+
                            "FROM agent_task_steps s LEFT JOIN agent_step_approvals a "+
                            "ON a.step_id=s.step_id WHERE s.step_id=? "+
                            "AND s.task_id=? LIMIT 2",
                        arrayOf(stepId,taskId)
                    ).use{row->
                        if(!row.moveToFirst())return@synchronized false
                        val args=JSONObject(NexaConversationDb
                            .openNativeTaskArguments(row.getBlob(0)))
                        val required=args.optBoolean("approvalRequired",false)
                        val matches=if(required)
                            row.getString(2)=="consumed" &&
                                row.getString(1)==sha(
                                    "nexa-chat-owner-approval-v1:"+fingerprint)
                        else row.isNull(1)
                        matches && !row.moveToNext()
                    }
                    if(!approvalBound)return@synchronized false
                    val now=System.currentTimeMillis()
                    val taskState=if(uncertain)"uncertain" else "settled_unverified"
                    val uiObserved=if(uncertain)false else db.rawQuery(
                        "SELECT 1 FROM agent_task_events WHERE task_id=? "+
                            "AND step_id=? AND event_kind='chat_native_ui_observed' LIMIT 1",
                        arrayOf(taskId,stepId)
                    ).use{it.moveToFirst()}
                    val stepState=when{
                        uncertain->"uncertain"
                        uiObserved->"ui_observed"
                        else->"dispatch_settled"
                    }
                    check(db.compileStatement(
                        "UPDATE agent_task_steps SET status=? WHERE step_id=? "+
                            "AND task_id=? AND status='outcome_unknown'"
                    ).use{stmt->
                        stmt.bindString(1,stepState)
                        stmt.bindString(2,stepId)
                        stmt.bindString(3,taskId)
                        stmt.executeUpdateDelete()==1
                    })
                    check(db.compileStatement(
                        "UPDATE agent_tasks SET status=?,plan_revision=plan_revision+1,"+
                            "updated_at=? WHERE task_id=? AND policy_version=? "+
                            "AND status='running'"
                    ).use{stmt->
                        stmt.bindString(1,taskState)
                        stmt.bindLong(2,now)
                        stmt.bindString(3,taskId)
                        stmt.bindString(4,POLICY)
                        stmt.executeUpdateDelete()==1
                    })
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                            "VALUES(?,?,?,?)",
                        arrayOf(taskId,stepId,if(uncertain)
                            "chat_action_uncertain_no_replay" else if(uiObserved)
                            "chat_native_ui_observed_settled" else
                            "chat_dispatch_settled_unverified",now)
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /**
     * Make the actual Chat Accessibility effect a CHILD of the already
     * owner-approved, preclaimed AgentHarness step. This never mints an
     * approval or trusts a provider's claim. The exact fingerprint includes
     * session, call ID, allowlisted tool ID and all raw reviewed arguments.
     * An irreversible reservation event is committed before any UI effect.
     *
     * A duplicate invocation for the SAME Chat step is rejected even while
     * its outer AgentJournal state is still "claimed". Neither SQLite task
     * metadata nor event names retain raw contact, screen or text details.
     */
    fun reserveReviewedUiStep(context:Context,session:String,call:String,
                              fingerprint:String,toolId:String,
                              requested:JSONObject):Boolean{
        if(!valid(session,call,fingerprint) ||
            toolId !in setOf("screen.tap_label","screen.scroll","screen.fill_text",
                "screen.open_whatsapp_chat","screen.compose_whatsapp") ||
            !NexaNativeActionSafety.permitsNewAction(context))return false
        return try{synchronized(lock){
            val bound=JSONArray(fingerprint)
            if(bound.length()!=4 || bound.optString(0)!=session ||
                bound.optString(1)!=call || bound.optString(2)!=toolId ||
                requested.length() !in 1..2)return@synchronized false
            val args=bound.optJSONObject(3) ?:return@synchronized false
            if(args.length()!=requested.length())return@synchronized false
            for(key in requested.keys()){
                if(!args.has(key) || args.opt(key)!=requested.opt(key))
                    return@synchronized false
            }
            val allowedArgs=when(toolId){
                "screen.tap_label"->args.length()==1 &&
                    args.optString("label").length in 2..45
                "screen.scroll"->args.length()==1 &&
                    args.optString("direction") in setOf("up","down","left","right")
                "screen.fill_text"->args.length()==2 &&
                    args.optString("targetLabel").length in 2..60 &&
                    args.optString("exactText").length in 1..500
                "screen.open_whatsapp_chat"->args.length()==1 &&
                    args.optString("recipientHint").matches(
                        Regex("[\\p{L}][\\p{L}\\p{N} '-]{1,49}"))
                "screen.compose_whatsapp"->args.length()==2 &&
                    args.optString("recipientHint").matches(
                        Regex("[\\p{L}][\\p{L}\\p{N} '-]{1,49}")) &&
                    args.optString("exactText").length in 1..500
                else->false
            }
            if(!allowedArgs)return@synchronized false
            val taskId=opaqueId("task-",sessionKey(session))
            val stepId=opaqueId("step-",callKey(session,call))
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val now=System.currentTimeMillis()
                    val validClaim=db.rawQuery(
                        "SELECT s.arguments_cipher,a.input_digest,a.state,"+
                            "a.consumed_at,a.expires_at FROM agent_tasks t "+
                            "JOIN agent_task_steps s ON s.task_id=t.task_id "+
                            "JOIN agent_step_approvals a ON a.step_id=s.step_id "+
                            "WHERE t.task_id=? AND t.policy_version=? "+
                            "AND t.origin='chat' AND t.status='running' "+
                            "AND s.step_id=? AND s.status='outcome_unknown' "+
                            "AND s.tool_id='legacy.chat.action' LIMIT 2",
                        arrayOf(taskId,POLICY,stepId)
                    ).use{row->
                        if(!row.moveToFirst())false
                        else{
                            val proof=JSONObject(NexaConversationDb
                                .openNativeTaskArguments(row.getBlob(0)))
                            val matches=proof.length()==3 &&
                                proof.optBoolean("approvalRequired",false) &&
                                proof.optLong("pauseGeneration",-1L)>=0L &&
                                proof.optLong("pauseGeneration",-1L)==
                                    NexaNativeActionSafety.approvalFence(context) &&
                                proof.optString("fingerprintDigest")==sha(fingerprint) &&
                                row.getString(1)==sha(
                                    "nexa-chat-owner-approval-v1:"+fingerprint) &&
                                row.getString(2)=="consumed" &&
                                row.getLong(3)>0L && row.getLong(4)>now
                            matches && !row.moveToNext()
                        }
                    }
                    if(!validClaim)return@synchronized false
                    val already=db.rawQuery(
                        "SELECT 1 FROM agent_task_events WHERE task_id=? "+
                            "AND step_id=? AND event_kind='chat_native_ui_reserved' LIMIT 1",
                        arrayOf(taskId,stepId)
                    ).use{it.moveToFirst()}
                    if(already)return@synchronized false
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                            "VALUES(?,?,'chat_native_ui_reserved',?)",
                        arrayOf(taskId,stepId,now)
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /**
     * Append narrow, native-produced evidence to the already reserved
     * owner-approved Chat step. A model result cannot call this method.
     * This records only UI change, not correctness of a conversation or send.
     *
     * tap/scroll require the exact private native gesture token; a fabricated
     * JS "success" result is insufficient. Text requires that the native
     * field controller has already verified exact text in the empty field.
     */
    fun recordReviewedUiEvidence(context:Context,session:String,call:String,
                                  fingerprint:String,toolId:String,
                                  evidence:String,gestureToken:String?=null):Boolean {
        if(!valid(session,call,fingerprint) ||
            toolId !in setOf("screen.tap_label","screen.scroll","screen.fill_text",
                "screen.open_whatsapp_chat","screen.compose_whatsapp"))
            return false
        val expectedKind=when(toolId){
            "screen.tap_label","screen.open_whatsapp_chat"->"tap"
            "screen.scroll"->{
                val args=try{JSONArray(fingerprint).optJSONObject(3)}
                    catch(_:Exception){null} ?:return false
                "scroll_"+args.optString("direction")
            }
            "screen.compose_whatsapp"->"message_fill"
            else->"text_fill"
        }
        if(toolId=="screen.fill_text"||toolId=="screen.compose_whatsapp"){
            if(evidence!="exact_text_visible" || gestureToken!=null)return false
        }else if(toolId=="screen.open_whatsapp_chat"){
            if(evidence!="verified_chat_header")return false
            if(gestureToken!=null && NexaUiActionJournal.outcomeFor(
                    context,gestureToken,"tap") !in setOf(
                    "android_accepted","observed_change",
                    "structural_change_observed","window_change_observed"))
                return false
        }else{
            if(evidence!="observed_change" ||
                gestureToken.isNullOrBlank() ||
                NexaUiActionJournal.outcomeFor(context,gestureToken,expectedKind) !in
                    setOf("observed_change","structural_change_observed",
                        "window_change_observed","scroll_event_observed"))
                return false
        }
        return try{synchronized(lock){
            val taskId=opaqueId("task-",sessionKey(session))
            val stepId=opaqueId("step-",callKey(session,call))
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val found=db.rawQuery(
                        "SELECT s.arguments_cipher FROM agent_tasks t "+
                            "JOIN agent_task_steps s ON s.task_id=t.task_id "+
                            "WHERE t.task_id=? AND t.policy_version=? "+
                            "AND t.origin='chat' AND t.status='running' "+
                            "AND s.step_id=? AND s.status='outcome_unknown' LIMIT 2",
                        arrayOf(taskId,POLICY,stepId)
                    ).use{row->
                        if(!row.moveToFirst())false
                        else{
                            val args=JSONObject(NexaConversationDb
                                .openNativeTaskArguments(row.getBlob(0)))
                            args.optString("fingerprintDigest")==sha(fingerprint) &&
                                !row.moveToNext()
                        }
                    }
                    if(!found)return@synchronized false
                    val previous=db.rawQuery(
                        "SELECT event_kind FROM agent_task_events "+
                            "WHERE task_id=? AND step_id=? "+
                            "AND event_kind IN ('chat_native_ui_reserved',"+
                            "'chat_native_ui_observed')",
                        arrayOf(taskId,stepId)
                    ).use{row->
                        val kinds=mutableSetOf<String>()
                        while(row.moveToNext())kinds.add(row.getString(0))
                        kinds
                    }
                    if("chat_native_ui_reserved" !in previous ||
                        "chat_native_ui_observed" in previous)return@synchronized false
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                            "VALUES(?,?,'chat_native_ui_observed',?)",
                        arrayOf(taskId,stepId,System.currentTimeMillis())
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /** Recovery only, never reissues a tool call. */
    fun recoverStale(context:Context,staleAfterMs:Long=120_000L):Int{
        if(staleAfterMs<60_000L)return 0
        return try{synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val now=System.currentTimeMillis()
                    val stale=mutableListOf<String>()
                    db.rawQuery(
                        "SELECT task_id FROM agent_tasks WHERE policy_version=? "+
                            "AND status='running' AND updated_at<? LIMIT 100",
                        arrayOf(POLICY,(now-staleAfterMs).toString())
                    ).use{row->while(row.moveToNext())stale.add(row.getString(0))}
                    for(taskId in stale){
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
                    stale.size
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){0}
    }
}
