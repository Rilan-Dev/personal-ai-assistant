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
internal class NexaConversationDb(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "custom.db", null, 3
) {
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
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion !in 1..2 || newVersion != 3)
            throw IllegalStateException("Unsupported conversation database migration")
        if (oldVersion <= 1) createTaskTables(db) // Keep v1 conversations/keys.
        if (oldVersion <= 2) createUiActionClaimTable(db) // Add only; no v2 rows altered.
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
                .put("androidProcessExits",NexaCrashDiagnostics.recent(context,12)).toString(2)
    }
}
class NexaConversationModule(private val reactContext:ReactApplicationContext):
    ReactContextBaseJavaModule(reactContext) {
    override fun getName()="PAAConversationHistory"
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
