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

}
