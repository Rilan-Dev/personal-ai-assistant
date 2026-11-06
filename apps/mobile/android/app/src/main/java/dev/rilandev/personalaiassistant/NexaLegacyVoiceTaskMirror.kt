package dev.rilandev.personalaiassistant

import android.database.sqlite.SQLiteDatabase

/**
 * Canonical v3 metadata for the preexisting Voice multi-action cursor.
 *
 * This is a strictly AUDIT-ONLY adapter, not a second executor. A recorded
 * Voice cursor has NO mandate, step approval, replay permission, verified
 * native observation, or right to run through NexaTaskCoordinator.
 *
 * The caller writes the existing encrypted voice_task_checkpoints row and
 * this mirror in ONE SQLite transaction. Any failure rolls back both.
 */
internal object NexaLegacyVoiceTaskMirror {
    const val POLICY = "voice-checkpoint-audit-v1"
    private const val TOOL = "legacy.voice.checkpoint"
    private const val EVIDENCE = "owner_review"

    private val allowed = mapOf(
        "planned" to "planned",
        "awaiting_approval" to "awaiting_approval",
        "executing" to "executing",
        "verifying" to "verifying",
        // A Voice cursor's progress is NOT an Android observation.
        "verified" to "checkpoint_progress",
        "completed" to "checkpoint_completed",
        "failed" to "failed",
        "uncertain" to "uncertain",
        "cancelled" to "cancelled"
    )

    /**
     * Called ONLY inside NexaConversationDb.saveTask's open transaction.
     * Sensitive step text stays in the existing encrypted Voice payload.
     * The canonical task has only opaque step ordinal and audit metadata.
     */
    fun writeInTransaction(
        db: SQLiteDatabase,
        taskId:String,
        sessionId:String,
        status:String,
        stepIndex:Int,
        count:Int,
        encryptedGoal:ByteArray,
        encryptedEmptyArguments:ByteArray,
        now:Long
    ) {
        val mapped=allowed[status] ?: error("Invalid Voice checkpoint status")
        check(stepIndex in 0..count && count in 1..8)
        var existing=false
        db.rawQuery(
            "SELECT session_id,origin,policy_version FROM agent_tasks WHERE task_id=?",
            arrayOf(taskId)
        ).use { row ->
            if(row.moveToFirst()){
                existing=true
                check(row.getString(0)==sessionId)
                check(row.getString(1)=="live-voice")
                check(row.getString(2)==POLICY)
            }
        }
        if(!existing){
            val inserted=db.compileStatement(
                "INSERT INTO agent_tasks(task_id,session_id,origin,status," +
                    "policy_version,plan_revision,goal_cipher,created_at,updated_at) " +
                    "VALUES(?,?,'live-voice',?,?,1,?,?,?)"
            ).use { stmt ->
                stmt.bindString(1,taskId)
                stmt.bindString(2,sessionId)
                stmt.bindString(3,mapped)
                stmt.bindString(4,POLICY)
                stmt.bindBlob(5,encryptedGoal)
                stmt.bindLong(6,now)
                stmt.bindLong(7,now)
                stmt.executeInsert()
            }
            check(inserted!=-1L)
            for(ordinal in 0 until count){
                val stepId="step-"+java.util.UUID.randomUUID()
                val initial=if(ordinal<stepIndex)"checkpoint_advanced" else "planned"
                db.compileStatement(
                    "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id," +
                        "status,risk,expected_evidence,arguments_cipher) " +
                        "VALUES(?,?,?, ?,?,'medium',?,?)"
                ).use { stmt ->
                    stmt.bindString(1,stepId)
                    stmt.bindString(2,taskId)
                    stmt.bindLong(3,ordinal.toLong())
                    stmt.bindString(4,TOOL)
                    stmt.bindString(5,initial)
                    stmt.bindString(6,EVIDENCE)
                    stmt.bindBlob(7,encryptedEmptyArguments)
                    check(stmt.executeInsert()!=-1L)
                }
            }
        }else{
            // The previously saved step topology is immutable. Different
            // plan texts cannot repurpose an old Voice task identity.
            val recorded=db.rawQuery(
                "SELECT COUNT(*) FROM agent_task_steps WHERE task_id=?",
                arrayOf(taskId)
            ).use { row -> check(row.moveToFirst());row.getInt(0) }
            check(recorded==count)
            val updated=db.compileStatement(
                "UPDATE agent_tasks SET status=?,plan_revision=plan_revision+1,"+
                    "updated_at=? WHERE task_id=? AND session_id=? AND "+
                    "policy_version=?"
            ).use{stmt->
                stmt.bindString(1,mapped)
                stmt.bindLong(2,now)
                stmt.bindString(3,taskId)
                stmt.bindString(4,sessionId)
                stmt.bindString(5,POLICY)
                stmt.executeUpdateDelete()
            }
            check(updated==1)
        }
        db.execSQL(
            "UPDATE agent_task_steps SET status='checkpoint_advanced' "+
                "WHERE task_id=? AND ordinal<?",
            arrayOf(taskId,stepIndex)
        )
        // Keep the active ordinal distinguishable from completed/verified
        // Android work. No evidence row is created by this audit adapter.
        val current=when(status){
            "awaiting_approval" -> "awaiting_owner"
            "executing","verifying" -> "checkpoint_pending"
            "failed","uncertain","cancelled" -> "uncertain"
            else -> "planned"
        }
        db.execSQL(
            "UPDATE agent_task_steps SET status=? "+
                "WHERE task_id=? AND ordinal>=? AND status!='checkpoint_advanced'",
            arrayOf(current,taskId,stepIndex)
        )
        val event="voice_checkpoint_"+status
        db.execSQL(
            "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                "VALUES(?,NULL,?,?)",
            arrayOf(taskId,event,now)
        )
    }

    /**
     * Read-only state fence consulted BEFORE a legacy Voice cursor considers
     * its next step. This is necessary but NOT sufficient for execution:
     * owner approval, target binding and native action claims remain mandatory
     * in the existing tool-specific controllers.
     *
     * A claimed/recovered/terminal or mismatched task cannot advance.
     * The mirror can never authorize a new action or recover an old one.
     */
    fun mayContinue(context:android.content.Context,taskId:String,
                    sessionId:String,ordinal:Int):Boolean{
        if(!Regex("task-[A-Za-z0-9_-]{8,96}").matches(taskId) ||
            sessionId.length !in 1..100 || ordinal !in 0..7)return false
        return try{
            val helper=NexaConversationDb(context)
            try{
                val db=helper.readableDatabase
                val state=db.rawQuery(
                    "SELECT v.status,v.step_index,t.status FROM voice_task_checkpoints v " +
                        "JOIN agent_tasks t ON t.task_id=v.task_id " +
                        "WHERE v.task_id=? AND v.session_id=? AND t.session_id=? "+
                        "AND t.policy_version=? AND t.origin='live-voice'",
                    arrayOf(taskId,sessionId,sessionId,POLICY)
                ).use{c->
                    if(c.moveToFirst())Triple(c.getString(0),c.getInt(1),c.getString(2))
                    else null
                }?:return false
                if(state.first !in setOf("planned","awaiting_approval",
                    "executing","verifying","verified") ||
                    state.second!=ordinal ||
                    state.third!=allowed[state.first])return false
                val step=db.rawQuery(
                    "SELECT tool_id,status FROM agent_task_steps " +
                        "WHERE task_id=? AND ordinal=?",
                    arrayOf(taskId,ordinal.toString())
                ).use{c->
                    if(c.moveToFirst())Pair(c.getString(0),c.getString(1))
                    else null
                }?:return false
                if(step.first!=TOOL || step.second=="checkpoint_advanced" ||
                    step.second=="uncertain")return false
                // Once the owner has consumed this exact step's durable
                // approval, a stale Voice cursor may never propose another
                // dispatch of the same step, including after a UI reconnect.
                val stepAlreadyClaimed=db.rawQuery(
                    "SELECT 1 FROM agent_step_approvals a "+
                        "JOIN agent_task_steps s ON s.step_id=a.step_id "+
                        "WHERE s.task_id=? AND s.ordinal=? "+
                        "AND a.state='consumed' LIMIT 1",
                    arrayOf(taskId,ordinal.toString())
                ).use{it.moveToFirst()}
                if(stepAlreadyClaimed)return false
                // No legacy Voice cursor may use a final coordinator mandate
                // as a way around its own reviewed UI action policy.
                val mandate=db.rawQuery(
                    "SELECT 1 FROM agent_mandates WHERE task_id=? LIMIT 1",
                    arrayOf(taskId)
                ).use{it.moveToFirst()}
                !mandate
            }finally{helper.close()}
        }catch(_:Exception){false}
    }

    /**
     * Called once when the Android application process is created. No Voice
     * executor from a previous process can still be running. Persist a
     * permanent uncertain state BEFORE any new spoken work is admitted.
     *
     * Do not resume/replay an abandoned app launch, screen tap, message
     * composition or other Voice action. Old pre-v3 checkpoints are fenced
     * too; they remain in the original table for owner history.
     */
    fun recoverAbandonedAfterProcessRestart(context:android.content.Context):Int {
        return try {
            val helper=NexaConversationDb(context)
            try {
                val db=helper.writableDatabase
                db.beginTransaction()
                try {
                    val abandoned=mutableListOf<String>()
                    db.rawQuery(
                        "SELECT task_id FROM voice_task_checkpoints WHERE status IN "+
                            "('planned','awaiting_approval','executing','verifying','verified') "+
                            "ORDER BY updated_at LIMIT 250",
                        null
                    ).use{c->
                        while(c.moveToNext())abandoned.add(c.getString(0))
                    }
                    val now=System.currentTimeMillis()
                    var recovered=0
                    for(taskId in abandoned){
                        val changed=db.compileStatement(
                            "UPDATE voice_task_checkpoints SET status='uncertain',"+
                                "updated_at=? WHERE task_id=? AND status IN "+
                                "('planned','awaiting_approval','executing','verifying','verified')"
                        ).use{stmt->
                            stmt.bindLong(1,now)
                            stmt.bindString(2,taskId)
                            stmt.executeUpdateDelete()
                        }
                        if(changed!=1)continue
                        val mirrored=db.compileStatement(
                            "UPDATE agent_tasks SET status='uncertain',"+
                                "plan_revision=plan_revision+1,updated_at=? "+
                                "WHERE task_id=? AND policy_version=? "+
                                "AND status NOT IN ('uncertain','failed','cancelled',"+
                                "'checkpoint_completed')"
                        ).use{stmt->
                            stmt.bindLong(1,now)
                            stmt.bindString(2,taskId)
                            stmt.bindString(3,POLICY)
                            stmt.executeUpdateDelete()
                        }
                        if(mirrored==1){
                            db.execSQL(
                                "UPDATE agent_task_steps SET status='uncertain' "+
                                    "WHERE task_id=? AND status!='checkpoint_advanced'",
                                arrayOf(taskId)
                            )
                            db.execSQL(
                                "INSERT INTO agent_task_events(task_id,step_id,"+
                                    "event_kind,created_at) "+
                                    "VALUES(?,NULL,'voice_process_restart_no_replay',?)",
                                arrayOf(taskId,now)
                            )
                        }
                        recovered++
                    }
                    db.setTransactionSuccessful()
                    recovered
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }catch(_:Exception){0}
    }


    /** Explicit foreground owner privacy action for a TERMINAL Voice task.
     * Clear original request, step content and last evidence atomically in
     * both stores, retaining task IDs, step identities, action tombstones and
     * redacted lifecycle evidence needed for audit and no-replay.
     */
    fun redactTerminal(context:android.content.Context,taskId:String):Boolean {
        if(!Regex("task-[A-Za-z0-9_-]{8,96}").matches(taskId))return false
        return try {
            val helper=NexaConversationDb(context)
            try {
                val db=helper.writableDatabase
                val replacementGoal=NexaConversationDb.sealNativeTaskArguments(
                    "[details cleared]")
                db.beginTransaction()
                try {
                    val detail=db.rawQuery(
                        "SELECT t.status,t.goal_cipher," +
                            "(SELECT COUNT(*) FROM agent_task_steps s WHERE s.task_id=t.task_id)," +
                            "v.session_id FROM agent_tasks t " +
                            "JOIN voice_task_checkpoints v ON v.task_id=t.task_id " +
                            "WHERE t.task_id=? AND t.policy_version=? AND " +
                            "t.origin='live-voice' AND t.session_id=v.session_id "+
                            "AND v.status IN ('completed','failed','uncertain','cancelled') "+
                            "AND t.status IN ('checkpoint_completed','failed','uncertain','cancelled')",
                        arrayOf(taskId,POLICY)
                    ).use{c->
                        if(c.moveToFirst())Triple(c.getString(0),c.getBlob(1),c.getInt(2))
                        else null
                    }?:return false
                    val prior=NexaConversationDb.openNativeTaskArguments(detail.second)
                    if(prior=="[details cleared]" || detail.third !in 1..8)
                        return false
                    val text=org.json.JSONArray()
                    repeat(detail.third){text.put("[details cleared]")}
                    val payload=org.json.JSONObject()
                        .put("request","[details cleared]")
                        .put("steps",text).put("evidence","")
                    val encryptedPayload=NexaConversationDb.sealNativeTaskArguments(
                        payload.toString())
                    val now=System.currentTimeMillis()
                    val voice=db.compileStatement(
                        "UPDATE voice_task_checkpoints SET payload=?,updated_at=? "+
                            "WHERE task_id=? AND status IN "+
                            "('completed','failed','uncertain','cancelled')"
                    ).use{stmt->
                        stmt.bindBlob(1,encryptedPayload)
                        stmt.bindLong(2,now)
                        stmt.bindString(3,taskId)
                        stmt.executeUpdateDelete()
                    }
                    check(voice==1)
                    val canonical=db.compileStatement(
                        "UPDATE agent_tasks SET goal_cipher=?,updated_at=? "+
                            "WHERE task_id=? AND policy_version=? AND "+
                            "status IN ('checkpoint_completed','failed','uncertain','cancelled')"
                    ).use{stmt->
                        stmt.bindBlob(1,replacementGoal)
                        stmt.bindLong(2,now)
                        stmt.bindString(3,taskId)
                        stmt.bindString(4,POLICY)
                        stmt.executeUpdateDelete()
                    }
                    check(canonical==1)
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                            "VALUES(?,NULL,'owner_redacted_legacy_voice',?)",
                        arrayOf(taskId,now)
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }catch(_:Exception){false}
    }

}
