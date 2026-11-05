package dev.rilandev.personalaiassistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase-2 owner-controlled task revisions and provenance.
 *
 * This is a review-only state machine. It does NOT grant a mandate, issue an
 * approval, mark a step verified, dispatch Android tools or resume execution.
 * The existing native coordinator remains the sole device-action authority.
 */
internal object NexaTaskLifecycle {
    private val lock=Any()
    private val taskId=Regex("task-[0-9a-f-]{36}")
    private val ownerSession=Regex("owner-[A-Za-z0-9_-]{8,94}")
    private val safeGoal=Regex("[^\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f]{1,500}")
    private const val POLICY="owner-review-v1"
    private const val PREVIEW="owner-preview-v1"

    private fun manualOwner(db:SQLiteDatabase,id:String):Boolean =
        db.rawQuery(
            "SELECT session_id FROM agent_tasks WHERE task_id=? AND origin IN ('chat','live-voice','assistant-role','overlay') " +
                "AND policy_version IN (?,?)",
            arrayOf(id,POLICY,PREVIEW)
        ).use { cursor ->
            cursor.moveToFirst() && ownerSession.matches(cursor.getString(0))
        }

    /**
     * Optimistic version check prevents concurrent corrections from silently
     * overwriting each other. Tentative read-only steps are cleared because
     * they were bound to the old owner goal; prior revisions stay in the
     * immutable event journal, without retaining plaintext goal contents.
     */
    fun reviseOwnerProposal(context:Context,id:String,expectedRevision:Int,newGoal:String):Boolean {
        val goal=newGoal.trim()
        if(!taskId.matches(id)||expectedRevision !in 1..1_000_000 ||
            goal.isBlank()||!safeGoal.matches(goal))return false
        return try{synchronized(lock){
            val sealed=NexaConversationDb.sealNativeTaskArguments(goal)
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    if(!manualOwner(db,id))return@synchronized false
                    // Never rewrite the goal after an approval, observation or
                    // mandate. Those imply a different native authority.
                    val fenced=db.rawQuery(
                        "SELECT " +
                            "(SELECT COUNT(*) FROM agent_mandates WHERE task_id=?)," +
                            "(SELECT COUNT(*) FROM agent_step_approvals WHERE step_id IN " +
                                "(SELECT step_id FROM agent_task_steps WHERE task_id=?))," +
                            "(SELECT COUNT(*) FROM agent_observations WHERE step_id IN " +
                                "(SELECT step_id FROM agent_task_steps WHERE task_id=?))," +
                            "(SELECT COUNT(*) FROM agent_task_steps WHERE task_id=? " +
                                "AND status<>'planned')",
                        arrayOf(id,id,id,id)
                    ).use { c ->
                        !c.moveToFirst() || (0..3).any { c.getLong(it)!=0L }
                    }
                    if(fenced)return@synchronized false
                    val now=System.currentTimeMillis()
                    val changed=db.compileStatement(
                        "UPDATE agent_tasks SET goal_cipher=?,plan_revision=plan_revision+1," +
                            "updated_at=? WHERE task_id=? AND plan_revision=? " +
                            "AND status='proposed' AND origin IN ('chat','live-voice','assistant-role','overlay') " +
                            "AND policy_version IN (?,?)"
                    ).use { stmt->
                        stmt.bindBlob(1,sealed)
                        stmt.bindLong(2,now)
                        stmt.bindString(3,id)
                        stmt.bindLong(4,expectedRevision.toLong())
                        stmt.bindString(5,POLICY)
                        stmt.bindString(6,PREVIEW)
                        stmt.executeUpdateDelete()
                    }
                    if(changed!=1)return@synchronized false
                    // Safe only because every step was unclaimed and had no
                    // approval, observation, or mandate (checked above).
                    db.execSQL(
                        "DELETE FROM agent_task_steps WHERE task_id=? AND status='planned'",
                        arrayOf(id)
                    )
                    db.execSQL(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) " +
                            "VALUES(?,NULL,'owner_revised_plan_invalidated',?)",
                        arrayOf(id,now)
                    )
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /** Focused owner review: metadata only, never plaintext step arguments,
     * screen, contact information or the encrypted goal itself.
     */
    fun ownerReviewDetail(context:Context,id:String):JSONObject? {
        if(!taskId.matches(id))return null
        return try{synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.readableDatabase
                if(!manualOwner(db,id))return@synchronized null
                val result=db.rawQuery(
                    "SELECT status,plan_revision,updated_at FROM agent_tasks " +
                        "WHERE task_id=? AND origin IN ('chat','live-voice','assistant-role','overlay') AND policy_version IN (?,?)",
                    arrayOf(id,POLICY,PREVIEW)
                ).use { cursor->
                    if(!cursor.moveToFirst())return@synchronized null
                    JSONObject().put("taskId",id)
                        .put("status",cursor.getString(0))
                        .put("revision",cursor.getInt(1))
                        .put("updatedAt",cursor.getLong(2))
                }
                // Dependency edges are decrypted and evaluated only inside
                // Kotlin; the UI receives bounded metadata and no raw input.
                val steps=NexaTaskStore.reviewDependencies(context,id)
                val events=JSONArray()
                db.rawQuery(
                    "SELECT event_kind,created_at FROM agent_task_events " +
                        "WHERE task_id=? ORDER BY id DESC LIMIT 40",
                    arrayOf(id)
                ).use { cursor->
                    while(cursor.moveToNext()) {
                        val kind=cursor.getString(0)
                        events.put(JSONObject().put("kind",
                            if(kind.length<=80&&kind.matches(Regex("[a-z0-9_]+")))kind
                            else "private_event")
                            .put("createdAt",cursor.getLong(1)))
                    }
                }
                result.put("steps",steps).put("events",events)
            }finally{helper.close()}
        }}catch(_:Exception){null}
    }
}
