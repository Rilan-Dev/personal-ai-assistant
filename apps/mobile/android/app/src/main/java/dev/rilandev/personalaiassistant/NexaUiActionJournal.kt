package dev.rilandev.personalaiassistant

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Bounded, encrypted UI evidence; irreversible claims live in custom.db v3.
 *
 * A token must be claimed AND committed to disk before dispatch. No raw screen
 * text, UI labels, package IDs, coordinates, screenshots, or provider output
 * are persisted. A corrupt/locked keystore or storage write fails closed.
 */
internal object NexaUiActionJournal {
    private const val STORAGE = "nexa_ui_action_ledger_v1"
    private const val PAYLOAD = "events"
    private const val KEY_ALIAS = "Nexa_UI_Action_Ledger_v1"
    private const val MAX_ENTRIES = 40
    private val lock = Any()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val found = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (found != null) return found
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun seal(input: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == 12)
        return Base64.encodeToString(iv + cipher.doFinal(input.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP)
    }

    private fun unseal(ciphertext: String): String {
        val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8)
    }

    private fun digest(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun read(ctx: Context): JSONArray {
        val value = ctx.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
            .getString(PAYLOAD,null) ?: return JSONArray()
        return JSONArray(unseal(value))
    }

    private fun store(ctx: Context, entries: JSONArray): Boolean {
        val retained = JSONArray()
        for (i in maxOf(0,entries.length()-MAX_ENTRIES) until entries.length()) {
            retained.put(entries.getJSONObject(i))
        }
        val encrypted = seal(retained.toString())
        return ctx.getSharedPreferences(STORAGE,Context.MODE_PRIVATE)
            .edit().putString(PAYLOAD, encrypted).commit()
    }

    /** Commits before touching another app; false blocks the action entirely. */
    fun claim(ctx: Context, approvalToken: String, kind: String, packageName: String,
              reviewedInputDigest:String?=null,origin:String="overlay"): Boolean =
        synchronized(lock) {
            if (approvalToken.length !in 16..128 ||
                kind !in setOf("tap","scroll_up","scroll_down","scroll_left","scroll_right","message_fill","text_fill","app_launch") ||
                origin !in setOf("chat","live-voice","assistant-role","overlay") ||
                !packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")) ||
                (kind in setOf("message_fill","text_fill") &&
                    !Regex("[0-9a-f]{64}").matches(reviewedInputDigest.orEmpty())) ||
                (kind !in setOf("message_fill","text_fill") && reviewedInputDigest!=null)) {
                return@synchronized false
            }
            if (!NexaNativeActionSafety.permitsNewAction(ctx)) return@synchronized false
            try {
                val id = digest(approvalToken)
                val entries = read(ctx)
                for (i in 0 until entries.length()) {
                    if (entries.getJSONObject(i).optString("id") == id) return@synchronized false
                }
                // Commit a durable, unique SQLite tombstone BEFORE changing the
                // bounded diagnostic ledger or touching the external app.
                // If the diagnostic write fails, the claim is still consumed.
                if(!NexaConversationDb.reserveUiActionClaim(
                    ctx,id,kind,digest(packageName),reviewedInputDigest,origin))
                    return@synchronized false
                entries.put(JSONObject()
                    .put("id",id)
                    .put("kind",kind)
                    .put("packageDigest",digest(packageName))
                    .put("at",System.currentTimeMillis())
                    .put("status","claimed"))
                store(ctx,entries)
            } catch (_: Exception) { false }
        }

    /** Outcome is only a dispatch observation; not a business-task success. */
    fun finish(ctx: Context, approvalToken: String, outcome: String): Boolean =
        synchronized(lock) {
            if (outcome !in setOf("android_accepted","android_rejected",
                    "unknown","observed_change","no_observable_change"))
                return@synchronized false
            try {
                val id = digest(approvalToken)
                val entries = read(ctx)
                var found = false
                for (i in 0 until entries.length()) {
                    val record = entries.getJSONObject(i)
                    if (record.optString("id") == id && record.optString("status") == "claimed") {
                        record.put("status",outcome)
                        found = true
                        break
                    }
                }
                val persisted=found && store(ctx,entries)
                // Record a separate audit-only task transition. A successful
                // Android dispatch remains unverified and non-replayable.
                persisted && NexaUiTaskAudit.recordOutcome(ctx,id,outcome)
            } catch (_: Exception) { false }
        }

    /**
     * Upgrade an accepted dispatch to a *visual change observation*. Does not
     * claim that a call, navigation, or user task succeeded.
     */
    fun markObservation(ctx: Context, approvalToken: String, observation: String): Boolean =
        synchronized(lock) {
            if (observation !in setOf("observed_change","no_observable_change",
                    "scroll_event_observed","window_change_observed",
                    "structural_change_observed","context_changed_unverified",
                    "destination_package_observed","unverified"))
                return@synchronized false
            try {
                val id=digest(approvalToken)
                val entries=read(ctx)
                var found=false
                for (i in 0 until entries.length()) {
                    val item=entries.getJSONObject(i)
                    if(item.optString("id")==id &&
                        item.optString("status")=="android_accepted" &&
                        (observation!="destination_package_observed" ||
                            item.optString("kind")=="app_launch")) {
                        item.put("status",observation)
                        found=true
                        break
                    }
                }
                val persisted=found && store(ctx,entries)
                persisted && NexaUiTaskAudit.recordOutcome(ctx,id,observation)
            } catch (_: Exception) { false }
        }

    /** Exact one-time approval lookup. Only the status is returned, never UI data. */
    fun outcomeFor(ctx: Context, approvalToken: String, kind:String="tap"): String? = synchronized(lock) {
        if(kind !in setOf("tap","scroll_up","scroll_down","scroll_left","scroll_right")) return@synchronized null
        if(approvalToken.length !in 16..128) return@synchronized null
        try {
            val id=digest(approvalToken)
            val entries=read(ctx)
            var status: String?=null
            for(i in 0 until entries.length()) {
                val event=entries.getJSONObject(i)
                if(event.optString("id")==id && event.optString("kind")==kind) {
                    status=event.optString("status")
                    break
                }
            }
            status
        }catch(_:Exception){ null }
    }

    /** Most recent native action evidence, without icon, package, token or geometry. */
    fun latestOutcome(ctx: Context): Pair<String,String>? = synchronized(lock) {
        try {
            val events=read(ctx)
            if(events.length()==0) null
            else {
                val event=events.getJSONObject(events.length()-1)
                val kind=event.optString("kind")
                val status=event.optString("status")
                if(kind !in setOf("tap","scroll_up","scroll_down","scroll_left","scroll_right") ||
                    status !in setOf("claimed","android_accepted","android_rejected",
                        "unknown","observed_change","no_observable_change",
                        "scroll_event_observed","window_change_observed",
                        "structural_change_observed","unverified","context_changed_unverified"))
                    null
                else kind to status
            }
        } catch (_: Exception) { null }
    }

    /** Return only aggregate status counts; the event details stay encrypted. */
    fun summary(ctx: Context): Map<String, Int>? = synchronized(lock) {
        try {
            val entries = read(ctx)
            val summary = mutableMapOf<String,Int>()
            for (i in 0 until entries.length()) {
                val name = entries.getJSONObject(i).optString("status","unknown")
                summary[name]=(summary[name] ?: 0)+1
            }
            summary.toMap()
        } catch (_: Exception) { null }
    }
}
