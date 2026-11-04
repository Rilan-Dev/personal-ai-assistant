package dev.rilandev.personalaiassistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.FileProvider
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private device-local custom.db with Android Keystore-encrypted transcript bodies. */
internal class NexaConversationDb(context: Context, databaseName: String = "custom.db") : SQLiteOpenHelper(
    context.applicationContext, databaseName, null, 3
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE conversation_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            channel TEXT NOT NULL,
            provider TEXT NOT NULL,
            role TEXT NOT NULL,
            body BLOB NOT NULL,
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX conversation_session_idx ON conversation_entries(session_id,id)")
        createTaskTables(db)
        createUiActionClaimTable(db)
        createAgentTaskTables(db)
    }
    private fun createTaskTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS voice_task_checkpoints (
            task_id TEXT PRIMARY KEY,
            session_id TEXT NOT NULL,
            status TEXT NOT NULL,
            step_index INTEGER NOT NULL,
            payload BLOB NOT NULL,
            updated_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS voice_task_sessions ON voice_task_checkpoints(session_id,updated_at)")
    }
    /** Permanent claim tombstones are separate from the bounded UI diagnostics ledger.
     * Never prune these records merely because visible history is full.
     */
    private fun createUiActionClaimTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS agent_action_claims (
            token_digest TEXT PRIMARY KEY NOT NULL,
            action_kind TEXT NOT NULL,
            package_digest TEXT NOT NULL,
            claimed_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS agent_action_claims_at ON agent_action_claims(claimed_at)")
    }
    /**
     * Additive task-coordinator storage. The active Chat/Voice engines are NOT
     * permitted to use this schema as execution authorization. Sensitive goals,
     * arguments and event detail are BLOB ciphertext written only by future
     * device-Keystore-verified stores.
     */
    private fun createAgentTaskTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS agent_tasks (
            task_id TEXT PRIMARY KEY NOT NULL,
            session_id TEXT NOT NULL,
            origin TEXT NOT NULL,
            status TEXT NOT NULL,
            policy_version TEXT NOT NULL,
            plan_revision INTEGER NOT NULL,
            goal_cipher BLOB NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS agent_tasks_session_idx ON agent_tasks(session_id,updated_at)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS agent_task_steps (
            step_id TEXT PRIMARY KEY NOT NULL,
            task_id TEXT NOT NULL REFERENCES agent_tasks(task_id) ON DELETE CASCADE,
            ordinal INTEGER NOT NULL,
            tool_id TEXT NOT NULL,
            status TEXT NOT NULL,
            risk TEXT NOT NULL,
            expected_evidence TEXT NOT NULL,
            arguments_cipher BLOB NOT NULL,
            UNIQUE(task_id,ordinal)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS agent_task_steps_idx ON agent_task_steps(task_id,ordinal)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS agent_task_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id TEXT NOT NULL REFERENCES agent_tasks(task_id) ON DELETE CASCADE,
            step_id TEXT,
            event_kind TEXT NOT NULL,
            detail_cipher BLOB,
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS agent_task_events_idx ON agent_task_events(task_id,id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS agent_mandates (
            mandate_id TEXT PRIMARY KEY NOT NULL,
            task_id TEXT NOT NULL UNIQUE REFERENCES agent_tasks(task_id) ON DELETE CASCADE,
            owner_request_id TEXT NOT NULL,
            scope_digest TEXT NOT NULL,
            policy_version TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            revoked_at INTEGER
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS agent_step_approvals (
            approval_id TEXT PRIMARY KEY NOT NULL,
            step_id TEXT NOT NULL REFERENCES agent_task_steps(step_id) ON DELETE CASCADE,
            target_digest TEXT NOT NULL,
            input_digest TEXT NOT NULL,
            state TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            consumed_at INTEGER
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS agent_step_approvals_idx ON agent_step_approvals(step_id,state)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS agent_observations (
            observation_id TEXT PRIMARY KEY NOT NULL,
            step_id TEXT NOT NULL REFERENCES agent_task_steps(step_id) ON DELETE CASCADE,
            source_kind TEXT NOT NULL,
            verdict TEXT NOT NULL,
            evidence_digest TEXT NOT NULL,
            observed_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS agent_observations_idx ON agent_observations(step_id,observed_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion !in 1..2 || newVersion != 3)
            throw IllegalStateException("Unsupported conversation database migration")
        if (oldVersion <= 1) createTaskTables(db) // Keep v1 conversations/keys.
        if (oldVersion <= 2) {
            createUiActionClaimTable(db)
            createAgentTaskTables(db) // New v3 structures only; no legacy rows altered.
        }
    }
    companion object {
        private val lock=Any()
        private const val KEY_ALIAS="NEXA_Conversation_v1"
        private fun key():SecretKey {
            val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS,null) as? SecretKey)?.let { return it }
            val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            return generator.generateKey()
        }
        private fun encrypt(text:String):ByteArray {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,key())
            return cipher.iv + cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        }
        private fun decrypt(bytes:ByteArray):String {
            require(bytes.size>28)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),StandardCharsets.UTF_8)
        }
        /** Atomically retain a one-shot native UI action identity before dispatch.
         * Failure (including storage/Keystore errors) fails closed. A claimed
         * token is not released when the diagnostic ledger trims old records.
         */
        fun reserveUiActionClaim(context:Context,tokenDigest:String,kind:String,
                                 packageDigest:String):Boolean {
            if(!tokenDigest.matches(Regex("[0-9a-f]{64}")) ||
                !packageDigest.matches(Regex("[0-9a-f]{64}")) ||
                kind !in setOf("tap","scroll_up","scroll_down","scroll_left",
                    "scroll_right","message_fill","text_fill"))return false
            return try { synchronized(lock) {
                val helper=NexaConversationDb(context)
                try {
                    helper.writableDatabase.compileStatement(
                        "INSERT OR IGNORE INTO agent_action_claims(token_digest,action_kind,package_digest,claimed_at) VALUES(?,?,?,?)"
                    ).use { statement ->
                        statement.bindString(1,tokenDigest)
                        statement.bindString(2,kind)
                        statement.bindString(3,packageDigest)
                        statement.bindLong(4,System.currentTimeMillis())
                        statement.executeInsert() != -1L
                    }
                } finally {helper.close()}
            }} catch (_:Exception) {false}
        }
        /**
         * A v3 Task is only a recorded PROPOSAL. This API grants no capability
         * and never accepts a model-authored authorization or executable status.
         * Reusing the same task ID fails closed instead of replacing the goal.
         */
        fun saveProposedAgentTask(context:Context,taskId:String,sessionId:String,
                                  origin:String,policyVersion:String,goal:String):Boolean {
            if(!taskId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{7,99}")) ||
                !sessionId.matches(Regex("[A-Za-z0-9_-]{1,100}")) ||
                origin !in setOf("chat","live-voice","assistant-role","overlay") ||
                !policyVersion.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) ||
                goal.isBlank() || goal.length>500 ||
                goal.any {it == '\u0000' || (it.code < 32 && it != '\n' && it != '\t')})
                return false
            return try { synchronized(lock) {
                val helper=NexaConversationDb(context)
                try {
                    val db=helper.writableDatabase
                    // Encrypt before opening the database transaction; never
                    // persist an unencrypted goal or a partial event.
                    val ciphertext=encrypt(goal)
                    db.beginTransaction()
                    try {
                        val now=System.currentTimeMillis()
                        val inserted=db.compileStatement(
                            "INSERT OR IGNORE INTO agent_tasks(task_id,session_id,origin,status,policy_version,plan_revision,goal_cipher,created_at,updated_at) VALUES(?,?,?,'proposed',?,1,?,?,?)"
                        ).use { statement ->
                            statement.bindString(1,taskId)
                            statement.bindString(2,sessionId)
                            statement.bindString(3,origin)
                            statement.bindString(4,policyVersion)
                            statement.bindBlob(5,ciphertext)
                            statement.bindLong(6,now)
                            statement.bindLong(7,now)
                            statement.executeInsert()
                        }
                        if(inserted==-1L)return@synchronized false
                        db.compileStatement(
                            "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) VALUES(?,NULL,'proposed',?)"
                        ).use {statement ->
                            statement.bindString(1,taskId)
                            statement.bindLong(2,now)
                            statement.executeInsert()
                        }
                        db.setTransactionSuccessful()
                        true
                    } finally {db.endTransaction()}
                } finally {helper.close()}
            }} catch (_:Exception) {false}
        }

        /**
         * Owner-only proposal lifecycle. Cancellation is a compare-and-swap:
         * a terminal proposal cannot be resumed, re-approved, or overwritten.
         * An append-only event and the state change commit together.
         * This API NEVER dispatches an Android action.
         */
        fun cancelProposedAgentTask(context:Context,taskId:String):Boolean {
            if(!taskId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{7,99}")))return false
            return try { synchronized(lock) {
                val helper=NexaConversationDb(context)
                try {
                    val db=helper.writableDatabase
                    db.beginTransaction()
                    try {
                        val now=System.currentTimeMillis()
                        val updated=db.compileStatement(
                            "UPDATE agent_tasks SET status='cancelled',updated_at=? WHERE task_id=? AND status='proposed'"
                        ).use { statement ->
                            statement.bindLong(1,now)
                            statement.bindString(2,taskId)
                            statement.executeUpdateDelete()
                        }
                        if(updated!=1)return@synchronized false
                        db.compileStatement(
                            "INSERT INTO agent_task_events(task_id,step_id,event_kind,created_at) VALUES(?,NULL,'owner_cancelled',?)"
                        ).use { statement ->
                            statement.bindString(1,taskId)
                            statement.bindLong(2,now)
                            statement.executeInsert()
                        }
                        db.setTransactionSuccessful()
                        true
                    } finally {db.endTransaction()}
                } finally {helper.close()}
            }} catch (_:Exception) {false}
        }

        /** A foreground-only caller may obtain these locally decrypted owner
         * summaries. Never include them in model tool observations or telemetry.
         */
        fun agentTaskHistory(context:Context,limit:Int):JSONArray = synchronized(lock) {
            val results=JSONArray()
            val helper=NexaConversationDb(context)
            try {
                helper.readableDatabase.rawQuery(
                    "SELECT task_id,session_id,origin,status,policy_version,goal_cipher,created_at,updated_at FROM agent_tasks ORDER BY updated_at DESC LIMIT ?",
                    arrayOf(limit.coerceIn(1,200).toString())
                ).use { cursor ->
                    while(cursor.moveToNext()) {
                        val text=try{decrypt(cursor.getBlob(5))}
                            catch (_:Exception){"[encrypted task unavailable]"}
                        results.put(JSONObject().put("taskId",cursor.getString(0))
                            .put("sessionId",cursor.getString(1))
                            .put("origin",cursor.getString(2))
                            .put("status",cursor.getString(3))
                            .put("policyVersion",cursor.getString(4))
                            .put("goal",text)
                            .put("createdAt",cursor.getLong(6))
                            .put("updatedAt",cursor.getLong(7)))
                    }
                }
            } finally {helper.close()}
            results
        }

        fun append(context:Context, session:String, channel:String, provider:String,
                   role:String, text:String) {
            if (text.isBlank() || text.length>12000 || session.length !in 1..100 ||
                channel !in setOf("chat","voice","tool") ||
                role !in setOf("user","assistant","tool") || provider.length>45) return
            try { synchronized(lock) {
                val db=NexaConversationDb(context)
                try {
                    db.writableDatabase.compileStatement(
                        "INSERT INTO conversation_entries(session_id,channel,provider,role,body,created_at) VALUES(?,?,?,?,?,?)"
                    ).use { statement ->
                        statement.bindString(1,session)
                        statement.bindString(2,channel)
                        statement.bindString(3,provider)
                        statement.bindString(4,role)
                        statement.bindBlob(5,encrypt(text))
                        statement.bindLong(6,System.currentTimeMillis())
                        statement.executeInsert()
                    }
                } finally { db.close() }
            }} catch (_:Exception) {
                // Storage errors must never terminate live microphone/model streaming.
            }
        }
        /** Encrypted durable checkpoint. Write before any dependent native action.
         * Only the owner-created task and verified observations are persisted.
         */
        fun saveTask(context:Context,taskId:String,sessionId:String,status:String,
                     stepIndex:Int,request:String,steps:List<String>,evidence:String=""):Boolean {
            if(!taskId.matches(Regex("[A-Za-z0-9-]{8,100}")) ||
                sessionId.length !in 1..100 || stepIndex !in 0..8 ||
                steps.isEmpty() || steps.size>8 || steps.any{it.length !in 1..160} ||
                request.length !in 1..500 || evidence.length>350 ||
                status !in setOf("planned","awaiting_approval","executing","verifying",
                    "verified","failed","uncertain","cancelled","completed"))return false
            return try { synchronized(lock) {
                val helper=NexaConversationDb(context)
                try {
                    val payload=JSONObject().put("request",request)
                        .put("steps",JSONArray(steps)).put("evidence",evidence)
                    helper.writableDatabase.compileStatement(
                        "INSERT OR REPLACE INTO voice_task_checkpoints(task_id,session_id,status,step_index,payload,updated_at) VALUES(?,?,?,?,?,?)"
                    ).use { statement ->
                        statement.bindString(1,taskId)
                        statement.bindString(2,sessionId)
                        statement.bindString(3,status)
                        statement.bindLong(4,stepIndex.toLong())
                        statement.bindBlob(5,encrypt(payload.toString()))
                        statement.bindLong(6,System.currentTimeMillis())
                        statement.executeInsert()
                    }
                    true
                } finally { helper.close() }
            }} catch (_:Exception) {false}
        }
        fun taskHistory(context:Context,limit:Int):JSONArray = synchronized(lock) {
            val rows=JSONArray()
            val helper=NexaConversationDb(context)
            try {
                helper.readableDatabase.rawQuery(
                    "SELECT task_id,session_id,status,step_index,payload,updated_at FROM voice_task_checkpoints ORDER BY updated_at DESC LIMIT ?",
                    arrayOf(limit.coerceIn(1,1000).toString())
                ).use { c ->
                    while(c.moveToNext()) {
                        val body=try{ JSONObject(decrypt(c.getBlob(4))) }
                            catch (_:Exception){ JSONObject().put("unavailable",true) }
                        rows.put(JSONObject().put("taskId",c.getString(0))
                            .put("sessionId",c.getString(1)).put("status",c.getString(2))
                            .put("stepIndex",c.getInt(3)).put("goal",body.optString("request"))
                            .put("steps",body.optJSONArray("steps")?:JSONArray())
                            .put("lastEvidence",body.optString("evidence"))
                            .put("updatedAt",c.getLong(5)))
                    }
                }
            } finally { helper.close() }
            val chronological=JSONArray()
            for(i in rows.length()-1 downTo 0)chronological.put(rows.getJSONObject(i))
            chronological
        }
        fun history(context:Context, limit:Int):JSONArray = synchronized(lock) {
            val rows=JSONArray()
            val helper=NexaConversationDb(context)
            try {
                helper.readableDatabase.rawQuery(
                    "SELECT id,session_id,channel,provider,role,body,created_at FROM conversation_entries ORDER BY id DESC LIMIT ?",
                    arrayOf(limit.coerceIn(1,10000).toString())
                ).use { cursor ->
                    while(cursor.moveToNext()){
                        val item=JSONObject()
                            .put("id",cursor.getLong(0))
                            .put("sessionId",cursor.getString(1))
                            .put("channel",cursor.getString(2))
                            .put("provider",cursor.getString(3))
                            .put("role",cursor.getString(4))
                            .put("createdAt",cursor.getLong(6))
                        val value=try { decrypt(cursor.getBlob(5)) }
                            catch (_:Exception) { "[encrypted entry unavailable]" }
                        rows.put(item.put("text",value))
                    }
                }
            } finally { helper.close() }
            val chronological=JSONArray()
            for(i in rows.length()-1 downTo 0) chronological.put(rows.getJSONObject(i))
            chronological
        }
        fun export(context:Context):String =
            JSONObject().put("schemaVersion",3).put("source","Nexa custom.db")
                .put("exportedAt",System.currentTimeMillis())
                .put("entries",history(context,10000))
                .put("tasks",taskHistory(context,1000))
                .put("agentTasks",agentTaskHistory(context,200))
                .put("androidProcessExits",NexaCrashDiagnostics.recent(context,12)).toString(2)
    }
}
class NexaConversationModule(private val reactContext:ReactApplicationContext):
    ReactContextBaseJavaModule(reactContext) {
    override fun getName()="PAAConversationHistory"
    /** Requires the real foreground Android activity. A stored request is
     * never a permission to start processing or a model-granted mandate.
     */
    @ReactMethod fun saveOwnerTaskRequest(goal:String,promise:Promise) {
        if(reactContext.currentActivity?.hasWindowFocus()!=true) {
            promise.reject("NOT_FOREGROUND","Open Nexa to save a task request")
            return
        }
        val request=goal.trim()
        val id="task-"+java.util.UUID.randomUUID().toString()
        val session="owner-"+java.util.UUID.randomUUID().toString()
        if(NexaConversationDb.saveProposedAgentTask(reactContext,id,session,
                "chat","owner-preview-v1",request)) promise.resolve(id)
        else promise.reject("TASK_INVALID","Task request was not saved")
    }
    @ReactMethod fun cancelOwnerTaskRequest(taskId:String,promise:Promise) {
        if(reactContext.currentActivity?.hasWindowFocus()!=true) {
            promise.reject("NOT_FOREGROUND","Open Nexa to cancel a task")
            return
        }
        promise.resolve(NexaConversationDb.cancelProposedAgentTask(reactContext,taskId))
    }
    @ReactMethod fun recentAgentTasks(limit:Int,promise:Promise) {
        if(reactContext.currentActivity?.hasWindowFocus()!=true) {
            promise.reject("NOT_FOREGROUND","Open Nexa to view private task history")
            return
        }
        try {promise.resolve(NexaConversationDb.agentTaskHistory(reactContext,limit).toString())}
        catch (_:Exception) {promise.reject("TASK_HISTORY","Cannot read private task history")}
    }
    @ReactMethod fun appendEntry(session:String,channel:String,provider:String,role:String,
                                 text:String,promise:Promise) {
        NexaConversationDb.append(reactContext,session,channel,provider,role,text)
        promise.resolve(true)
    }
    @ReactMethod fun recentEntries(limit:Int,promise:Promise) {
        try { promise.resolve(NexaConversationDb.history(reactContext,limit.coerceIn(1,200)).toString()) }
        catch (_:Exception) { promise.reject("HISTORY_READ","Cannot read local conversation history") }
    }
    @ReactMethod fun recentTasks(limit:Int,promise:Promise) {
        try { promise.resolve(NexaConversationDb.taskHistory(reactContext,limit.coerceIn(1,200)).toString()) }
        catch (_:Exception) { promise.reject("TASK_HISTORY","Cannot read local task history") }
    }
    @ReactMethod fun recentAndroidProcessExits(promise:Promise) {
        try {promise.resolve(NexaCrashDiagnostics.recent(reactContext,12).toString())}
        catch (_:Exception) {promise.reject("EXIT_REPORT","Cannot read Android process exits")}
    }
    @ReactMethod fun shareAndroidProcessExits(promise:Promise) {
        if(reactContext.currentActivity?.hasWindowFocus()!=true) {
            promise.reject("NOT_FOREGROUND","Open Nexa to export Android crash diagnostics")
            return
        }
        try {
            val directory=File(reactContext.cacheDir,"conversation-exports").apply {mkdirs()}
            val file=File(directory,"nexa-android-exits-"+System.currentTimeMillis()+".json")
            file.writeText(NexaCrashDiagnostics.ownerExport(reactContext),
                StandardCharsets.UTF_8)
            val uri=FileProvider.getUriForFile(reactContext,
                reactContext.packageName+".conversationprovider",file)
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="application/json"
                putExtra(Intent.EXTRA_STREAM,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData=ClipData.newUri(reactContext.contentResolver,
                    "Nexa Android crash diagnostics",uri)
            }
            reactContext.currentActivity?.startActivity(
                Intent.createChooser(intent,"Share Nexa Android crash diagnostics"))
            promise.resolve(true)
        } catch (_:Exception) {
            promise.reject("EXIT_EXPORT","Could not share Android crash diagnostics")
        }
    }
    @ReactMethod fun copyAllHistory(promise:Promise) {
        if (reactContext.currentActivity?.hasWindowFocus()!=true) {
            promise.reject("NOT_FOREGROUND","Open Nexa to copy private conversation history")
            return
        }
        try {
            val content=NexaConversationDb.export(reactContext)
            val clipboard=reactContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Nexa conversation history",content))
            promise.resolve(true)
        } catch (_:Exception) { promise.reject("HISTORY_COPY","Could not copy local history") }
    }
    @ReactMethod fun shareAllHistory(promise:Promise) {
        if (reactContext.currentActivity?.hasWindowFocus()!=true) {
            promise.reject("NOT_FOREGROUND","Open Nexa to export private conversation history")
            return
        }
        try {
            val exportDir=File(reactContext.cacheDir,"conversation-exports").apply { mkdirs() }
            val file=File(exportDir,"nexa-conversations-"+System.currentTimeMillis()+".json")
            file.writeText(NexaConversationDb.export(reactContext),StandardCharsets.UTF_8)
            val uri=FileProvider.getUriForFile(reactContext,
                reactContext.packageName+".conversationprovider",file)
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="application/json"
                putExtra(Intent.EXTRA_STREAM,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData=ClipData.newUri(reactContext.contentResolver,"Nexa private conversation export",uri)
            }
            reactContext.currentActivity?.startActivity(
                Intent.createChooser(intent,"Share Nexa conversation history"))
            promise.resolve(true)
        } catch (_:Exception) { promise.reject("HISTORY_EXPORT","Could not export local conversations") }
    }
}
