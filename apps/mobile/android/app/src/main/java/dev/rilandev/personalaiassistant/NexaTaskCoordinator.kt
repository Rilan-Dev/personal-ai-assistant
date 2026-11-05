package dev.rilandev.personalaiassistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Single Android authority for the first durable task kind: explicit read-only
 * battery / storage questions. This does not execute Accessibility, messages,
 * calls, settings or model-selected tools.
 *
 * All changes use the existing encrypted custom.db v3. A claimed action moves
 * to outcome_unknown BEFORE contacting Android. A crash can never replay it.
 */
internal object NexaTaskCoordinator {
    private const val POLICY = "native-fact-v1"
    private val lock = Any()
    private val battery = setOf(
        "battery", "battery status", "battery level", "battery percentage",
        "check battery", "check battery level", "check my battery", "what is my battery",
        "what is my battery percentage", "how much battery do i have",
        "how much battery is left", "what's my battery percentage"
    )
    private val storage = setOf(
        "storage", "phone storage", "storage status", "device storage",
        "check storage", "check my storage", "how much storage do i have",
        "how much storage is free", "how much storage is left",
        "what is my storage", "what is my phone storage"
    )
    internal fun classifyOwnerFact(raw: String): String? {
        if (raw.length !in 3..150 || raw.any { it.code < 32 }) return null
        val normalized = raw.trim().lowercase(Locale.ROOT)
            .removePrefix("hey nexa,").removePrefix("hey nexa ")
            .trim().trimEnd('.', '?', '!', ',').trim()
        return when (normalized) {
            in battery -> "get_battery_level"
            in storage -> "get_storage_usage"
            else -> null
        }
    }
    /** Parse a bounded, literal owner request; never a model-authored tool plan. */
    internal fun classifyOwnerReadOnlyPlan(raw:String):List<String>? {
        if(raw.length !in 15..420 || raw.any { it.code<32 })return null
        val pieces=Regex("""\s+(?:and then|then|and)\s+""",RegexOption.IGNORE_CASE)
            .split(raw.trim())
        if(pieces.size !in 2..4)return null
        val operations=pieces.map { classifyOwnerFact(it) ?:return null }
        if(operations.toSet().size!=operations.size)return null
        return operations
    }
    private data class Claim(val taskId: String, val stepId: String,
                             val operation: String, val scope: String)
    private fun id(prefix: String) = prefix + UUID.randomUUID().toString()
    private fun digest(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** Every stage is a durable, evidence-free proposal until the claim commits. */
    private fun stage(context: Context, request: String, origin: String,
                      operation: String): Claim? {
        val claim = Claim(id("task-"), id("step-"), operation, "")
        val scope = digest(claim.taskId + ":" + claim.stepId + ":" + operation + ":" + POLICY)
        if (!NexaConversationDb.saveProposedAgentTask(
                context, claim.taskId, id("owner-"), origin, POLICY, request
            )) return null
        val success = transaction(context) { db ->
            val now = System.currentTimeMillis()
            val changed = db.compileStatement(
                "UPDATE agent_tasks SET status='ready',plan_revision=plan_revision+1,updated_at=? " +
                    "WHERE task_id=? AND status='proposed' AND policy_version=?"
            ).use {
                it.bindLong(1, now); it.bindString(2, claim.taskId)
                it.bindString(3, POLICY); it.executeUpdateDelete()
            }
            check(changed == 1)
            db.execSQL(
                "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id,status,risk," +
                    "expected_evidence,arguments_cipher) VALUES(?,?,0,?,'pending','low'," +
                    "'native_reading',?)",
                arrayOf(claim.stepId, claim.taskId, operation,
                    NexaConversationDb.sealNativeTaskArguments("{}"))
            )
            db.execSQL(
                "INSERT INTO agent_mandates(mandate_id,task_id,owner_request_id,scope_digest," +
                    "policy_version,expires_at) VALUES(?,?,?,?,?,?)",
                arrayOf(id("mandate-"), claim.taskId, id("request-"), scope, POLICY,
                    now + 60_000L)
            )
            event(db, claim.taskId, claim.stepId, "planned", now)
            true
        }
        return if (success == true) claim.copy(scope = scope) else null
    }

    private fun event(db: SQLiteDatabase, taskId: String, stepId: String,
                      name: String, now: Long) {
        db.execSQL(
            "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) VALUES(?,?,?,?)",
            arrayOf(taskId, stepId, name, now)
        )
    }

    private fun <T> transaction(context: Context, action: (SQLiteDatabase) -> T): T? =
        synchronized(lock) {
            try {
                val helper = NexaConversationDb(context)
                try {
                    val db = helper.writableDatabase
                    db.beginTransaction()
                    try {
                        val result = action(db)
                        db.setTransactionSuccessful()
                        result
                    } finally { db.endTransaction() }
                } finally { helper.close() }
            } catch (_: Exception) { null }
        }

    private fun mandateValid(db: SQLiteDatabase, c: Claim): Boolean {
        db.rawQuery(
            "SELECT 1 FROM agent_mandates m JOIN agent_tasks t ON t.task_id=m.task_id " +
                "JOIN agent_task_steps s ON s.task_id=t.task_id " +
                "WHERE t.task_id=? AND s.step_id=? AND s.tool_id=? " +
                "AND t.origin IN ('chat','live-voice','assistant-role','overlay') AND t.policy_version=? " +
                "AND m.policy_version=? AND m.scope_digest=? AND m.revoked_at IS NULL " +
                "AND m.expires_at>? LIMIT 1",
            arrayOf(c.taskId, c.stepId, c.operation, POLICY, POLICY, c.scope,
                System.currentTimeMillis().toString())
        ).use { return it.moveToFirst() }
    }

    private fun claim(context: Context, c: Claim): Boolean =
        transaction(context) { db ->
            check(mandateValid(db, c))
            val now = System.currentTimeMillis()
            val task = db.compileStatement(
                "UPDATE agent_tasks SET status='running',plan_revision=plan_revision+1," +
                    "updated_at=? WHERE task_id=? AND status='ready'"
            ).use {
                it.bindLong(1, now); it.bindString(2, c.taskId); it.executeUpdateDelete()
            }
            check(task == 1)
            // The permanent claim is persisted before the Android read, never after.
            val step = db.compileStatement(
                "UPDATE agent_task_steps SET status='outcome_unknown' " +
                    "WHERE step_id=? AND task_id=? AND status='pending'"
            ).use {
                it.bindString(1, c.stepId); it.bindString(2, c.taskId)
                it.executeUpdateDelete()
            }
            check(step == 1)
            event(db, c.taskId, c.stepId, "claimed_before_read", now)
            true
        } == true

    private fun complete(context: Context, c: Claim, reading: String): Boolean =
        transaction(context) { db ->
            check(mandateValid(db, c))
            val now = System.currentTimeMillis()
            val step = db.compileStatement(
                "UPDATE agent_task_steps SET status='verified' " +
                    "WHERE step_id=? AND task_id=? AND status='outcome_unknown'"
            ).use {
                it.bindString(1, c.stepId); it.bindString(2, c.taskId)
                it.executeUpdateDelete()
            }
            check(step == 1)
            val task = db.compileStatement(
                "UPDATE agent_tasks SET status='completed',plan_revision=plan_revision+1," +
                    "updated_at=? WHERE task_id=? AND status='running'"
            ).use {
                it.bindLong(1, now); it.bindString(2, c.taskId)
                it.executeUpdateDelete()
            }
            check(task == 1)
            db.execSQL(
                "INSERT INTO agent_observations(observation_id,step_id,source_kind,verdict," +
                    "evidence_digest,observed_at) VALUES(?,?,'android_framework','verified',?,?)",
                arrayOf(id("observation-"), c.stepId, digest(reading), now)
            )
            event(db, c.taskId, c.stepId, "native_read_verified", now)
            true
        } == true

    /** A revocation cannot be undone and invalidates in-flight completion. */
    fun revokeOwnerTask(context: Context, taskId: String): Boolean {
        if (!taskId.matches(Regex("task-[0-9a-f-]{36}"))) return false
        return transaction(context) { db ->
            val now = System.currentTimeMillis()
            val changed = db.compileStatement(
                "UPDATE agent_tasks SET status='cancelled',plan_revision=plan_revision+1," +
                    "updated_at=? WHERE task_id=? AND status IN ('ready','running','paused') " +
                    "AND ((policy_version=? AND origin IN ('chat','live-voice','assistant-role','overlay')) " +
                    "OR (policy_version='owner-review-v1' " +
                    "AND origin IN ('chat','live-voice','assistant-role','overlay') " +
                    "AND session_id LIKE 'owner-%'))"
            ).use {
                it.bindLong(1, now)
                it.bindString(2, taskId)
                it.bindString(3, POLICY)
                it.executeUpdateDelete()
            }
            check(changed == 1)
            db.execSQL(
                "UPDATE agent_mandates SET revoked_at=? WHERE task_id=? " +
                    "AND policy_version IN (?,?) AND revoked_at IS NULL",
                arrayOf(now, taskId, POLICY, REVIEW_POLICY)
            )
            db.execSQL(
                "UPDATE agent_task_steps SET status='cancelled' WHERE task_id=? " +
                    "AND status IN ('pending','outcome_unknown')",
                arrayOf(taskId)
            )
            db.execSQL(
                "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) " +
                    "VALUES(?,NULL,'owner_revoked',?)", arrayOf(taskId, now)
            )
            true
        } == true
    }

    /**
     * Crash recovery: never interpret an unfinished read as success. A stale
     * pre-claimed step is permanently uncertain and its mandate is revoked.
     * No Android read is retried and no external action is dispatched.
     */
    fun recoverStaleInFlight(context:Context,staleAfterMs:Long=120_000L):Int {
        if(staleAfterMs<60_000L)return 0
        return transaction(context) {db->
            val now=System.currentTimeMillis()
            val candidates=mutableListOf<Triple<String,String,String>>()
            db.rawQuery(
                "SELECT t.task_id,s.step_id,t.policy_version FROM agent_tasks t " +
                    "JOIN agent_task_steps s ON s.task_id=t.task_id " +
                    "WHERE t.policy_version IN (?,?) AND t.status='running' " +
                    "AND s.status='outcome_unknown' AND t.updated_at<?",
                arrayOf(POLICY,REVIEW_POLICY,(now-staleAfterMs).toString())
            ).use{cursor->
                while(cursor.moveToNext())
                    candidates.add(Triple(cursor.getString(0),cursor.getString(1),
                        cursor.getString(2)))
            }
            var recovered=0
            for((taskId,stepId,policy) in candidates){
                val changed=db.compileStatement(
                    "UPDATE agent_tasks SET status='uncertain'," +
                        "plan_revision=plan_revision+1,updated_at=? " +
                        "WHERE task_id=? AND status='running' AND policy_version=?"
                ).use{stmt->
                    stmt.bindLong(1,now)
                    stmt.bindString(2,taskId)
                    stmt.bindString(3,policy)
                    stmt.executeUpdateDelete()
                }
                if(changed!=1)continue
                db.execSQL(
                    "UPDATE agent_task_steps SET status='uncertain' " +
                        "WHERE task_id=? AND status='outcome_unknown'",
                    arrayOf(taskId)
                )
                db.execSQL(
                    "UPDATE agent_mandates SET revoked_at=? WHERE task_id=? " +
                        "AND policy_version=? AND revoked_at IS NULL",
                    arrayOf(now,taskId,policy)
                )
                // Recovered tasks must not retain apparently runnable future steps.
                db.execSQL(
                    "UPDATE agent_task_steps SET status='cancelled' " +
                        "WHERE task_id=? AND status IN ('planned','pending')",
                    arrayOf(taskId)
                )
                event(db,taskId,stepId,"recovered_uncertain_no_replay",now)
                recovered++
            }
            // A reviewed plan can also stop between safe read steps, when
            // no step remains in outcome_unknown. Its expired mandate must
            // never leave a ready or running task apparently resumable.
            val expired=mutableListOf<Pair<String,String>>()
            db.rawQuery(
                "SELECT t.task_id,t.status FROM agent_tasks t "+
                    "JOIN agent_mandates m ON m.task_id=t.task_id "+
                    "WHERE t.policy_version=? AND t.status IN ('ready','running') "+
                    "AND m.revoked_at IS NULL AND m.expires_at<=? LIMIT 100",
                arrayOf(REVIEW_POLICY,now.toString())
            ).use{cursor->
                while(cursor.moveToNext())
                    expired.add(cursor.getString(0) to cursor.getString(1))
            }
            for((taskId,oldStatus) in expired){
                val newStatus=if(oldStatus=="running")"uncertain" else "cancelled"
                val updated=db.compileStatement(
                    "UPDATE agent_tasks SET status=?,plan_revision=plan_revision+1,"+
                        "updated_at=? WHERE task_id=? AND policy_version=? AND status=?"
                ).use{stmt->
                    stmt.bindString(1,newStatus)
                    stmt.bindLong(2,now)
                    stmt.bindString(3,taskId)
                    stmt.bindString(4,REVIEW_POLICY)
                    stmt.bindString(5,oldStatus)
                    stmt.executeUpdateDelete()
                }
                if(updated!=1)continue
                db.execSQL(
                    "UPDATE agent_mandates SET revoked_at=? WHERE task_id=? "+
                        "AND policy_version=? AND revoked_at IS NULL",
                    arrayOf(now,taskId,REVIEW_POLICY)
                )
                db.execSQL(
                    "UPDATE agent_task_steps SET status='uncertain' "+
                        "WHERE task_id=? AND status='outcome_unknown'",arrayOf(taskId)
                )
                db.execSQL(
                    "UPDATE agent_task_steps SET status='cancelled' "+
                        "WHERE task_id=? AND status IN ('planned','pending')",
                    arrayOf(taskId)
                )
                db.execSQL(
                    "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) "+
                        "VALUES(?,NULL,'mandate_expired_no_replay',?)",
                    arrayOf(taskId,now)
                )
                recovered++
            }
            recovered
        } ?: 0
    }

    /** Metadata-only timeline; never return arguments, raw screen data or keys. */
    fun recentOwnerTimeline(context: Context, limit: Int): org.json.JSONArray =
        synchronized(lock) {
            recoverStaleInFlight(context)
            val rows = org.json.JSONArray()
            val helper = NexaConversationDb(context)
            try {
                helper.readableDatabase.rawQuery(
                    "SELECT t.task_id,t.origin,t.status,t.plan_revision,t.updated_at," +
                        "s.step_id,s.ordinal,s.tool_id,s.status," +
                        "(SELECT o.verdict FROM agent_observations o " +
                        "WHERE o.step_id=s.step_id ORDER BY o.observed_at DESC LIMIT 1) " +
                        "FROM agent_tasks t JOIN agent_task_steps s ON s.task_id=t.task_id " +
                        "WHERE t.policy_version=? ORDER BY t.updated_at DESC,s.ordinal ASC LIMIT ?",
                    arrayOf(POLICY, limit.coerceIn(1,50).toString())
                ).use { cursor ->
                    while(cursor.moveToNext()) {
                        rows.put(org.json.JSONObject()
                            .put("taskId",cursor.getString(0))
                            .put("origin",cursor.getString(1))
                            .put("status",cursor.getString(2))
                            .put("revision",cursor.getInt(3))
                            .put("updatedAt",cursor.getLong(4))
                            .put("stepId",cursor.getString(5))
                            .put("ordinal",cursor.getInt(6))
                            .put("operation",cursor.getString(7))
                            .put("stepStatus",cursor.getString(8))
                            .put("observation",if(cursor.isNull(9)) "none" else cursor.getString(9)))
                    }
                }
            } finally { helper.close() }
            rows
        }

    private data class ReadPlan(
        val taskId:String,val steps:List<String>,
        val operations:List<String>,val scope:String
    )
    private fun stageReadPlan(context:Context,request:String,origin:String,
                              operations:List<String>):ReadPlan? {
        val taskId=id("task-")
        val stepIds=operations.map{id("step-")}
        val fingerprint=(listOf(taskId,POLICY)+
            stepIds.zip(operations).map{(step,op)->step+":"+op}).joinToString(":")
        val scope=digest(fingerprint)
        if(!NexaConversationDb.saveProposedAgentTask(
            context,taskId,id("owner-"),origin,POLICY,request))return null
        val prepared=transaction(context){db->
            val now=System.currentTimeMillis()
            val updated=db.compileStatement(
                "UPDATE agent_tasks SET status='ready',plan_revision=plan_revision+1,"+
                    "updated_at=? WHERE task_id=? AND status='proposed' AND policy_version=?"
            ).use {
                it.bindLong(1,now);it.bindString(2,taskId);it.bindString(3,POLICY)
                it.executeUpdateDelete()
            }
            check(updated==1)
            operations.forEachIndexed{position,op->
                db.execSQL(
                    "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id,status,risk,"+
                    "expected_evidence,arguments_cipher) "+
                    "VALUES(?,?,?,?,'pending','low','native_reading',?)",
                    arrayOf(stepIds[position],taskId,position,op,
                        NexaConversationDb.sealNativeTaskArguments("{}"))
                )
            }
            db.execSQL(
                "INSERT INTO agent_mandates(mandate_id,task_id,owner_request_id,scope_digest,"+
                    "policy_version,expires_at) VALUES(?,?,?,?,?,?)",
                arrayOf(id("mandate-"),taskId,id("request-"),scope,POLICY,now+60_000L)
            )
            event(db,taskId,stepIds.first(),"plan_ready",now)
            true
        }
        return if(prepared==true)ReadPlan(taskId,stepIds,operations,scope) else null
    }
    private fun mandateForPlan(db:SQLiteDatabase,plan:ReadPlan):Boolean {
        db.rawQuery(
            "SELECT 1 FROM agent_mandates m JOIN agent_tasks t ON t.task_id=m.task_id "+
            "WHERE t.task_id=? AND t.policy_version=? AND m.policy_version=? "+
            "AND t.origin IN ('chat','live-voice','assistant-role','overlay') AND m.scope_digest=? "+
            "AND m.revoked_at IS NULL AND m.expires_at>? LIMIT 1",
            arrayOf(plan.taskId,POLICY,POLICY,plan.scope,System.currentTimeMillis().toString())
        ).use {return it.moveToFirst()}
    }
    private fun claimPlanStep(context:Context,plan:ReadPlan,position:Int):Boolean=
        transaction(context){db->
            check(mandateForPlan(db,plan))
            db.rawQuery(
                "SELECT COUNT(*) FROM agent_task_steps "+
                    "WHERE task_id=? AND ordinal<? AND status!='verified'",
                arrayOf(plan.taskId,position.toString())
            ).use{check(it.moveToFirst() && it.getInt(0)==0)}
            val now=System.currentTimeMillis()
            val task=db.compileStatement(
                "UPDATE agent_tasks SET status='running',plan_revision=plan_revision+1,"+
                    "updated_at=? WHERE task_id=? AND status IN ('ready','running')"
            ).use{
                it.bindLong(1,now);it.bindString(2,plan.taskId);it.executeUpdateDelete()
            }
            check(task==1)
            val changed=db.compileStatement(
                "UPDATE agent_task_steps SET status='outcome_unknown' "+
                    "WHERE step_id=? AND task_id=? AND ordinal=? AND status='pending'"
            ).use{
                it.bindString(1,plan.steps[position]);it.bindString(2,plan.taskId)
                it.bindLong(3,position.toLong());it.executeUpdateDelete()
            }
            check(changed==1)
            event(db,plan.taskId,plan.steps[position],"claimed_before_read",now)
            true
        }==true
    private fun completePlanStep(context:Context,plan:ReadPlan,position:Int,
                                 evidence:String):Boolean=
        transaction(context){db->
            check(mandateForPlan(db,plan))
            val now=System.currentTimeMillis()
            val step=db.compileStatement(
                "UPDATE agent_task_steps SET status='verified' "+
                    "WHERE step_id=? AND task_id=? AND ordinal=? AND status='outcome_unknown'"
            ).use{
                it.bindString(1,plan.steps[position]);it.bindString(2,plan.taskId)
                it.bindLong(3,position.toLong());it.executeUpdateDelete()
            }
            check(step==1)
            val updated=db.compileStatement(
                "UPDATE agent_tasks SET status=?,plan_revision=plan_revision+1,"+
                    "updated_at=? WHERE task_id=? AND status='running'"
            ).use{
                it.bindString(1,if(position==plan.steps.lastIndex)"completed" else "running")
                it.bindLong(2,now);it.bindString(3,plan.taskId);it.executeUpdateDelete()
            }
            check(updated==1)
            db.execSQL(
                "INSERT INTO agent_observations(observation_id,step_id,source_kind,"+
                    "verdict,evidence_digest,observed_at) "+
                    "VALUES(?,?,'android_framework','verified',?,?)",
                arrayOf(id("observation-"),plan.steps[position],digest(evidence),now)
            )
            event(db,plan.taskId,plan.steps[position],"native_read_verified",now)
            true
        }==true

    /** One durable task ID, ordered dependencies and no uncertain-outcome replay. */
    fun executeOwnerReadOnlyPlan(context:Context,request:String,origin:String):String {
        if(origin !in setOf("chat","live-voice","assistant-role","overlay"))
            return "Unsupported task origin. No device action performed."
        val operations=classifyOwnerReadOnlyPlan(request)
            ?:return "Unsupported multi-step device request. No task started."
        val plan=stageReadPlan(context,request,origin,operations)
            ?:return "Could not securely store the task plan. No action performed."
        val results=mutableListOf<String>()
        for((position,operation) in operations.withIndex()){
            if(!claimPlanStep(context,plan,position))
                return "Task stopped at step "+(position+1)+": claim refused. No retry."
            val observed=NexaTaskNativeRead.observe(context,operation)
            if(observed==null)
                return "Task stopped at step "+(position+1)+": no verified reading. No retry."
            if(!completePlanStep(context,plan,position,observed))
                return "Task stopped: native step evidence could not be committed. No retry."
            results.add(observed)
        }
        return results.joinToString(" ")
    }


    /** Owner-reviewed execution of an already-admitted read-only plan.
     * Model-authored capabilities cannot bypass the native disabled gate.
     */
    private const val REVIEW_POLICY="owner-review-v1"
    private val reviewId=Regex("task-[0-9a-f-]{36}")
    private val reviewTools=NexaTaskNativeRead.proposedReadTools
    private data class ReviewedStep(val stepId:String,val ordinal:Int,
                                    val tool:String,val deps:List<String>,val seal:String,
                                    val status:String)
    private data class ReviewedPlan(val taskId:String,val origin:String,
                                     val scope:String,val steps:List<ReviewedStep>)
    private fun reviewedSteps(db:SQLiteDatabase,taskId:String,
                              allowedStates:Set<String> = setOf("planned")):List<ReviewedStep>? {
        val result=mutableListOf<ReviewedStep>()
        db.rawQuery(
            "SELECT step_id,ordinal,tool_id,status,risk,expected_evidence,arguments_cipher " +
            "FROM agent_task_steps WHERE task_id=? ORDER BY ordinal",
            arrayOf(taskId)
        ).use {cursor->
            while(cursor.moveToNext()){
                val stepId=cursor.getString(0)
                val ordinal=cursor.getInt(1)
                val tool=cursor.getString(2)
                if(ordinal!=result.size||result.size>=8||
                    !stepId.matches(Regex("step-[0-9a-f-]{36}"))||
                    tool !in reviewTools||cursor.getString(3) !in allowedStates||
                    cursor.getString(4)!="low"||
                    cursor.getString(5)!="native_reading")return null
                val cipher=cursor.getBlob(6)
                val args=org.json.JSONObject(NexaConversationDb.openNativeTaskArguments(cipher))
                if(args.length()!=3||!args.has("sourceStepId")||
                    !args.has("input")||!args.has("dependsOn")||
                    args.optString("sourceStepId").isBlank()||
                    args.optJSONObject("input")?.length()!=0)return null
                val dependencies=args.optJSONArray("dependsOn")?:return null
                if(dependencies.length()>8)return null
                val deps=mutableListOf<String>()
                for(i in 0 until dependencies.length()){
                    val dep=dependencies.optString(i)
                    if(dep in deps||result.none{it.stepId==dep})return null
                    deps.add(dep)
                }
                val seal=digest(cipher.joinToString(""){"%02x".format(it.toInt() and 255)})
                result.add(ReviewedStep(stepId,ordinal,tool,deps,seal,cursor.getString(3)))
            }
        }
        return result.takeIf{it.isNotEmpty()}
    }
    private fun scopeForReviewed(taskId:String,origin:String,
                                 steps:List<ReviewedStep>):String{
        val fingerprint=(listOf(taskId,origin,REVIEW_POLICY)+steps.map {
            it.stepId+":"+it.ordinal+":"+it.tool+":"+it.seal+
                ":"+it.deps.joinToString(",")
        }).joinToString("|")
        return digest(fingerprint)
    }
    private fun activateReviewed(context:Context,taskId:String,revision:Int):ReviewedPlan?=
        transaction(context){db->
            val origin=db.rawQuery(
                "SELECT origin FROM agent_tasks WHERE task_id=? "+
                    "AND origin IN ('chat','live-voice','assistant-role','overlay') "+
                    "AND session_id LIKE 'owner-%' AND policy_version=? "+
                    "AND status='proposed' AND plan_revision=?",
                arrayOf(taskId,REVIEW_POLICY,revision.toString())
            ).use{if(it.moveToFirst())it.getString(0) else null}
                ?:error("Owner task cannot activate")
            val steps=reviewedSteps(db,taskId)?:error("Invalid read-only plan")
            val scope=scopeForReviewed(taskId,origin,steps)
            val now=System.currentTimeMillis()
            val changed=db.compileStatement(
                "UPDATE agent_tasks SET status='ready',plan_revision=plan_revision+1,"+
                    "updated_at=? WHERE task_id=? AND status='proposed' AND "+
                    "policy_version=? AND plan_revision=?"
            ).use{
                it.bindLong(1,now);it.bindString(2,taskId)
                it.bindString(3,REVIEW_POLICY);it.bindLong(4,revision.toLong())
                it.executeUpdateDelete()
            }
            check(changed==1)
            db.execSQL(
                "UPDATE agent_task_steps SET status='pending' "+
                    "WHERE task_id=? AND status='planned'",arrayOf(taskId)
            )
            db.execSQL(
                "INSERT INTO agent_mandates(mandate_id,task_id,owner_request_id,"+
                    "scope_digest,policy_version,expires_at) VALUES(?,?,?,?,?,?)",
                arrayOf(id("mandate-"),taskId,taskId,scope,REVIEW_POLICY,now+120_000L)
            )
            event(db,taskId,steps.first().stepId,"owner_read_plan_activated",now)
            ReviewedPlan(taskId,origin,scope,steps)
        }
    private fun reviewedMandate(db:SQLiteDatabase,plan:ReviewedPlan):Boolean=
        db.rawQuery(
            "SELECT 1 FROM agent_mandates m JOIN agent_tasks t ON m.task_id=t.task_id "+
            "WHERE t.task_id=? AND t.origin=? AND t.session_id LIKE 'owner-%' "+
            "AND t.policy_version=? AND t.status IN ('ready','running') "+
            "AND m.policy_version=? AND m.owner_request_id=t.task_id "+
            "AND m.scope_digest=? AND m.revoked_at IS NULL AND m.expires_at>?",
            arrayOf(plan.taskId,plan.origin,REVIEW_POLICY,REVIEW_POLICY,plan.scope,
                System.currentTimeMillis().toString())
        ).use{it.moveToFirst()}

    /** Execution-time step/target binding. A saved mandate must not authorize
     * a replaced operation, altered arguments, or changed dependencies.
     */
    private fun reviewedStepBound(db:SQLiteDatabase,plan:ReviewedPlan,
                                  step:ReviewedStep,expectedStatus:String):Boolean {
        return db.rawQuery(
            "SELECT tool_id,ordinal,risk,expected_evidence,arguments_cipher " +
                "FROM agent_task_steps WHERE task_id=? AND step_id=? AND status=?",
            arrayOf(plan.taskId,step.stepId,expectedStatus)
        ).use{rows->
            if(!rows.moveToFirst() || rows.getString(0)!=step.tool ||
                rows.getInt(1)!=step.ordinal || rows.getString(2)!="low" ||
                rows.getString(3)!="native_reading")return@use false
            val cipher=rows.getBlob(4)
            val seal=digest(cipher.joinToString(""){"%02x".format(it.toInt() and 255)})
            if(seal!=step.seal)return@use false
            val parsed=org.json.JSONObject(NexaConversationDb.openNativeTaskArguments(cipher))
            if(parsed.length()!=3 || parsed.optJSONObject("input")?.length()!=0)
                return@use false
            val deps=parsed.optJSONArray("dependsOn")?:return@use false
            val values=(0 until deps.length()).map{deps.optString(it)}
            values==step.deps
        }
    }
    private fun claimReviewed(context:Context,plan:ReviewedPlan,step:ReviewedStep):Boolean=
        transaction(context){db->
            check(reviewedMandate(db,plan))
            check(reviewedStepBound(db,plan,step,"pending"))
            for(dep in step.deps){
                val verified=db.rawQuery(
                    "SELECT 1 FROM agent_task_steps WHERE task_id=? AND step_id=? "+
                        "AND status='verified'",arrayOf(plan.taskId,dep)
                ).use{it.moveToFirst()}
                check(verified)
            }
            val now=System.currentTimeMillis()
            val changed=db.compileStatement(
                "UPDATE agent_task_steps SET status='outcome_unknown' "+
                    "WHERE task_id=? AND step_id=? AND status='pending'"
            ).use{
                it.bindString(1,plan.taskId);it.bindString(2,step.stepId)
                it.executeUpdateDelete()
            }
            check(changed==1)
            db.execSQL(
                "UPDATE agent_tasks SET status='running',plan_revision=plan_revision+1,"+
                    "updated_at=? WHERE task_id=? AND status IN ('ready','running')",
                arrayOf(now,plan.taskId)
            )
            event(db,plan.taskId,step.stepId,"claimed_before_read",now)
            true
        }==true
    private fun verifyReviewed(context:Context,plan:ReviewedPlan,step:ReviewedStep,
                               reading:String):Boolean=
        transaction(context){db->
            check(reviewedMandate(db,plan))
            check(reviewedStepBound(db,plan,step,"outcome_unknown"))
            val now=System.currentTimeMillis()
            val changed=db.compileStatement(
                "UPDATE agent_task_steps SET status='verified' WHERE "+
                    "task_id=? AND step_id=? AND status='outcome_unknown'"
            ).use{
                it.bindString(1,plan.taskId);it.bindString(2,step.stepId)
                it.executeUpdateDelete()
            }
            check(changed==1)
            val finished=step.ordinal==plan.steps.lastIndex
            db.execSQL(
                "UPDATE agent_tasks SET status=?,plan_revision=plan_revision+1,"+
                    "updated_at=? WHERE task_id=? AND status='running'",
                arrayOf(if(finished)"completed" else "running",now,plan.taskId)
            )
            db.execSQL(
                "INSERT INTO agent_observations(observation_id,step_id,source_kind,"+
                    "verdict,evidence_digest,observed_at) "+
                    "VALUES(?,?,'android_framework','verified',?,?)",
                arrayOf(id("observation-"),step.stepId,digest(reading),now)
            )
            event(db,plan.taskId,step.stepId,"native_read_verified",now)
            true
        }==true

    /** Owner-controlled continuation ONLY after a clean verified prefix.
     * No pending plan is dispatched by this method. A separate owner Run press
     * renews the existing, unrevoked scoped mandate using optimistic revision.
     * Unknown/claimed outcomes are NEVER eligible for continuation.
     */
    private fun renewReviewed(context:Context,taskId:String,revision:Int):ReviewedPlan?=
        transaction(context){db->
            val origin=db.rawQuery(
                "SELECT origin FROM agent_tasks WHERE task_id=? "+
                    "AND origin IN ('chat','live-voice','assistant-role','overlay') "+
                    "AND session_id LIKE 'owner-%' AND policy_version=? "+
                    "AND status IN ('ready','running') AND plan_revision=?",
                arrayOf(taskId,REVIEW_POLICY,revision.toString())
            ).use{if(it.moveToFirst())it.getString(0) else null}
                ?:error("Owner task cannot continue")
            val steps=reviewedSteps(db,taskId,setOf("verified","pending"))
                ?:error("Invalid saved plan")
            var pendingSeen=false
            for(step in steps){
                if(step.status=="pending")pendingSeen=true
                else {
                    // A verified step after an unexecuted step would violate
                    // the native ordered dependency/scheduling boundary.
                    check(!pendingSeen)
                    val evidence=db.rawQuery(
                        "SELECT 1 FROM agent_observations WHERE step_id=? "+
                            "AND verdict='verified' LIMIT 1",
                        arrayOf(step.stepId)
                    ).use{it.moveToFirst()}
                    check(evidence)
                }
            }
            check(pendingSeen)
            val scope=scopeForReviewed(taskId,origin,steps)
            val now=System.currentTimeMillis()
            val mandate=db.rawQuery(
                "SELECT 1 FROM agent_mandates WHERE task_id=? "+
                    "AND owner_request_id=? AND policy_version=? "+
                    "AND scope_digest=? AND revoked_at IS NULL AND expires_at>? LIMIT 1",
                arrayOf(taskId,taskId,REVIEW_POLICY,scope,now.toString())
            ).use{it.moveToFirst()}
            check(mandate)
            val updated=db.compileStatement(
                "UPDATE agent_tasks SET plan_revision=plan_revision+1,updated_at=? "+
                    "WHERE task_id=? AND plan_revision=? AND "+
                    "policy_version=? AND status IN ('ready','running')"
            ).use{stmt->
                stmt.bindLong(1,now);stmt.bindString(2,taskId)
                stmt.bindLong(3,revision.toLong());stmt.bindString(4,REVIEW_POLICY)
                stmt.executeUpdateDelete()
            }
            check(updated==1)
            val renewed=db.compileStatement(
                "UPDATE agent_mandates SET expires_at=? WHERE task_id=? "+
                    "AND owner_request_id=? AND policy_version=? AND scope_digest=? "+
                    "AND revoked_at IS NULL AND expires_at>?"
            ).use{stmt->
                stmt.bindLong(1,now+120_000L);stmt.bindString(2,taskId)
                stmt.bindString(3,taskId);stmt.bindString(4,REVIEW_POLICY)
                stmt.bindString(5,scope);stmt.bindLong(6,now)
                stmt.executeUpdateDelete()
            }
            check(renewed==1)
            event(db,taskId,steps.first{it.status=="pending"}.stepId,
                "owner_read_plan_continued",now)
            ReviewedPlan(taskId,origin,scope,steps)
        }

    private fun executePendingReviewed(context:Context,plan:ReviewedPlan):String{
        val results=mutableListOf<String>()
        for(step in plan.steps){
            // The previous step has already been independently recorded and
            // cannot be retried or claimed a second time.
            if(step.status=="verified")continue
            if(!claimReviewed(context,plan,step))
                return "Task blocked at step "+(step.ordinal+1)+". No automatic retry."
            val reading=NexaTaskNativeRead.observe(context,step.tool)
                ?:return "Task outcome unresolved. No automatic retry."
            if(!verifyReviewed(context,plan,step,reading))
                return "Could not commit task evidence. No automatic retry."
            results.add(reading)
        }
        return results.joinToString(" ").ifBlank{"No unexecuted steps remained."}
    }

    /** Explicit owner authorization on a new, non-executable proposal. */
    fun executeReviewedReadOnlyPlan(context:Context,taskId:String,revision:Int):String{
        if(!BuildConfig.NEXA_REVIEWED_READ_PLAN_ENABLED)
            return "Reviewed task execution is disabled. No action performed."
        if(!reviewId.matches(taskId)||revision<1)
            return "Invalid reviewed task. No action performed."
        val plan=activateReviewed(context,taskId,revision)
            ?:return "Task changed or review authorization failed. No action performed."
        return executePendingReviewed(context,plan)
    }

    /** The owner explicitly requests remaining reads. Unknown actions never
     * restart, even if the owner presses Continue after a process interruption.
     */
    fun resumeReviewedReadOnlyPlan(context:Context,taskId:String,revision:Int):String{
        if(!BuildConfig.NEXA_REVIEWED_READ_PLAN_ENABLED)
            return "Reviewed task continuation is disabled. Nothing executed."
        if(!reviewId.matches(taskId)||revision<1)
            return "Invalid task identity. Nothing executed."
        val plan=renewReviewed(context,taskId,revision)
            ?:return "Continuation denied: task changed, mandate expired, revoked, or outcome uncertain. No retry."
        return executePendingReviewed(context,plan)
    }

    /**
     * Called only by a trusted explicit user-intent path. Raw model tool
     * calls cannot invoke this. There is NO general tool dispatch mechanism.
     */
    fun executeOwnerDeviceRead(context: Context, request: String, origin: String): String {
        if (origin !in setOf("chat","live-voice","assistant-role","overlay"))
            return "Unsupported request origin; no device action performed."
        val operation = classifyOwnerFact(request)
            ?: return "Not a supported device-information request; no task started."
        val c = stage(context, request, origin, operation)
            ?: return "Secure task storage unavailable. No device action performed."
        if (!claim(context, c))
            return "Could not reserve this task safely. No device action performed."
        val value = NexaTaskNativeRead.observe(context,operation)
        if (value==null)
            return "Android device reading was not verified. Task requires attention."
        return if (complete(context, c, value)) value
            else "Android reading was not recorded reliably. Task requires attention."
    }
}
