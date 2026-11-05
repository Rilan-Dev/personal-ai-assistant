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
        "check battery", "check my battery", "what is my battery",
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
                "AND t.origin IN ('chat','live-voice') AND t.policy_version=? " +
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
                    "updated_at=? WHERE task_id=? AND policy_version=? " +
                    "AND origin IN ('chat','live-voice') AND status IN ('ready','running')"
            ).use {
                it.bindLong(1, now)
                it.bindString(2, taskId)
                it.bindString(3, POLICY)
                it.executeUpdateDelete()
            }
            check(changed == 1)
            db.execSQL(
                "UPDATE agent_mandates SET revoked_at=? WHERE task_id=? " +
                    "AND policy_version=? AND revoked_at IS NULL",
                arrayOf(now, taskId, POLICY)
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
            val candidates=mutableListOf<Pair<String,String>>()
            db.rawQuery(
                "SELECT t.task_id,s.step_id FROM agent_tasks t " +
                    "JOIN agent_task_steps s ON s.task_id=t.task_id " +
                    "WHERE t.policy_version=? AND t.status='running' " +
                    "AND s.status='outcome_unknown' AND t.updated_at<?",
                arrayOf(POLICY,(now-staleAfterMs).toString())
            ).use{cursor->
                while(cursor.moveToNext())
                    candidates.add(cursor.getString(0) to cursor.getString(1))
            }
            var recovered=0
            for((taskId,stepId) in candidates){
                val changed=db.compileStatement(
                    "UPDATE agent_tasks SET status='uncertain'," +
                        "plan_revision=plan_revision+1,updated_at=? " +
                        "WHERE task_id=? AND status='running' AND policy_version=?"
                ).use{stmt->
                    stmt.bindLong(1,now)
                    stmt.bindString(2,taskId)
                    stmt.bindString(3,POLICY)
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
                    arrayOf(now,taskId,POLICY)
                )
                event(db,taskId,stepId,"recovered_uncertain_no_replay",now)
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
                        "s.tool_id,s.status," +
                        "(SELECT o.verdict FROM agent_observations o " +
                        "WHERE o.step_id=s.step_id ORDER BY o.observed_at DESC LIMIT 1) " +
                        "FROM agent_tasks t JOIN agent_task_steps s ON s.task_id=t.task_id " +
                        "WHERE t.policy_version=? ORDER BY t.updated_at DESC LIMIT ?",
                    arrayOf(POLICY, limit.coerceIn(1,50).toString())
                ).use { cursor ->
                    while(cursor.moveToNext()) {
                        rows.put(org.json.JSONObject()
                            .put("taskId",cursor.getString(0))
                            .put("origin",cursor.getString(1))
                            .put("status",cursor.getString(2))
                            .put("revision",cursor.getInt(3))
                            .put("updatedAt",cursor.getLong(4))
                            .put("operation",cursor.getString(5))
                            .put("stepStatus",cursor.getString(6))
                            .put("observation",if(cursor.isNull(7)) "none" else cursor.getString(7)))
                    }
                }
            } finally { helper.close() }
            rows
        }

    /**
     * Called only by a trusted explicit user-intent path. Raw model tool
     * calls cannot invoke this. There is NO general tool dispatch mechanism.
     */
    fun executeOwnerDeviceRead(context: Context, request: String, origin: String): String {
        if (origin != "chat" && origin != "live-voice")
            return "Unsupported request origin; no device action performed."
        val operation = classifyOwnerFact(request)
            ?: return "Not a supported device-information request; no task started."
        val c = stage(context, request, origin, operation)
            ?: return "Secure task storage unavailable. No device action performed."
        if (!claim(context, c))
            return "Could not reserve this task safely. No device action performed."
        val command = if (operation == "get_battery_level") "battery" else "storage"
        val value = try { NexaPhoneVoiceTools.deviceFacts(context, command) }
            catch (_: Exception) { null }
        if (value.isNullOrBlank() || value.contains("unavailable", ignoreCase = true)
            || value.contains("did not provide", ignoreCase = true))
            return "Android device reading was not verified. Task requires attention."
        return if (complete(context, c, value)) value
            else "Android reading was not recorded reliably. Task requires attention."
    }
}
