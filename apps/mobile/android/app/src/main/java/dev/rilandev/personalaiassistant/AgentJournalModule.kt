package dev.rilandev.personalaiassistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Device-local encrypted journal. Commit an irreversible action claim before dispatch. */
class AgentJournalModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    private val prefs = context.getSharedPreferences("paa_secure_journal_v1", Context.MODE_PRIVATE)
    // Shared across React Native bridge re-instantiations inside this process.
    // Multi-process mode is intentionally not supported.
    internal companion object {
        val lock = Any()
        /** Keystore-only access for an owner-started native Realtime session. */
        fun openAiVoiceKey(ctx: Context): String? = synchronized(lock) {
            val digest=MessageDigest.getInstance("SHA-256").digest(
                "private:provider-config:".toByteArray(StandardCharsets.UTF_8))
            val name=Base64.encodeToString(digest,
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
            val encrypted=ctx.getSharedPreferences("paa_secure_journal_v1",
                Context.MODE_PRIVATE).getString(name,null) ?: return@synchronized null
            val all=Base64.decode(encrypted,Base64.DEFAULT)
            require(all.size>28)
            val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key=ks.getKey("PAA_Journal_v1",null) as? SecretKey ?: return@synchronized null
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key,
                GCMParameterSpec(128,all.copyOfRange(0,12)))
            val decoded=String(cipher.doFinal(all.copyOfRange(12,all.size)),
                StandardCharsets.UTF_8)
            val data=JSONObject(decoded)
            if (data.optString("provider")!="openai") return@synchronized null
            data.optString("apiKey").takeIf { it.length in 12..512 }
        }
    }
    override fun getName() = "PAAAgentJournal"
    private fun rejectSafe(p: Promise, code: String, e: Exception) {
        // Safe diagnostic: only exception CLASS, never key, fingerprint,
        // request, ciphertext, or exception message.
        val category = e.javaClass.simpleName.ifBlank { "UnknownException" }
        Log.e("PAAAgentJournal", "$code: $category")
        p.reject(code, "Secure journal unavailable ($category)")
    }

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val found = ks.getKey("PAA_Journal_v1", null)
        if (found is SecretKey) return found
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder("PAA_Journal_v1",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return g.generateKey()
    }
    private fun name(prefix: String, session: String, call: String = ""): String {
        require(session.isNotBlank() && session.length <= 128 && call.length <= 128)
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "$prefix:$session:$call".toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }
    private fun encrypt(text: String): String {
        // Android Keystore AES-GCM defaults to randomized-encryption-required.
        // It REJECTS a caller-supplied IV for encryption. Let the provider
        // generate the IV and store it alongside the authenticated ciphertext.
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val iv = cipher.iv
        check(iv.size == 12) { "Unexpected AES-GCM IV size" }
        val ciphertext = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }
    private fun decrypt(text: String): String {
        val data = Base64.decode(text, Base64.DEFAULT)
        require(data.size > 28)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return String(c.doFinal(data.copyOfRange(12, data.size)), StandardCharsets.UTF_8)
    }
    private fun read(k: String): String? = prefs.getString(k, null)?.let { decrypt(it) }
    private fun write(k: String, v: String) {
        check(prefs.edit().putString(k, encrypt(v)).commit()) { "Durable commit failed" }
    }
    private fun action(s: String, c: String) = name("action", s, c)
    private fun record(status: String, fp: String) =
        JSONObject().put("status", status).put("fingerprint", fp).toString()
    private fun existing(k: String): JSONObject? = read(k)?.let { JSONObject(it) }

    /**
     * Owner-entered BYOK model configuration lives only in Android Keystore
     * AES-GCM encrypted private preferences. No OS cloud backup or plaintext
     * AsyncStorage, logging, crash analytics or source control copies.
     * React Native receives the key only for direct, owner-initiated HTTPS API calls.
     */
    @ReactMethod fun saveProviderSettings(data: String, p: Promise) {
        try {
            require(data.toByteArray(StandardCharsets.UTF_8).size <= 2048)
            val obj = JSONObject(data)
            require(obj.optInt("version") == 1)
            require(obj.length() == 4)
            require(obj.optString("provider") in listOf("openai", "gemini", "groq", "openrouter"))
            val model = obj.getString("model")
            require(model.matches(Regex("[A-Za-z0-9._:/-]{2,96}")))
            val key = obj.getString("apiKey")
            require(key.length in 12..512 && key == key.trim())
            synchronized(lock) {
                write(name("private", "provider-config"), data)
            }
            // Changes to provider credentials must terminate a session that
            // may still be streaming under the prior configuration.
            reactApplicationContext.stopService(
                android.content.Intent(reactApplicationContext, RealtimeVoiceService::class.java))
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p, "PROVIDER_SETTINGS_SAVE", e) }
    }
    @ReactMethod fun loadProviderSettings(p: Promise) {
        try {
            synchronized(lock) { p.resolve(read(name("private", "provider-config"))) }
        } catch (e: Exception) { rejectSafe(p, "PROVIDER_SETTINGS_READ", e) }
    }
    @ReactMethod fun clearProviderSettings(p: Promise) {
        try {
            synchronized(lock) {
                check(prefs.edit().remove(name("private", "provider-config")).commit())
            }
            // Revoking the selected API key also terminates a voice service
            // that might still be running in the background.
            reactApplicationContext.stopService(
                android.content.Intent(reactApplicationContext, RealtimeVoiceService::class.java))
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p, "PROVIDER_SETTINGS_CLEAR", e) }
    }

    @ReactMethod fun newSessionId(p: Promise) { p.resolve(UUID.randomUUID().toString()) }

    @ReactMethod fun loadSession(s: String, p: Promise) {
        try { synchronized(lock) { p.resolve(read(name("session", s))) } }
        catch (e: Exception) { rejectSafe(p, "JOURNAL_READ", e) }
    }
    @ReactMethod fun latestSession(p: Promise) {
        try { synchronized(lock) { p.resolve(read(name("latest", "device"))) } }
        catch (e: Exception) { rejectSafe(p, "JOURNAL_READ", e) }
    }
    @ReactMethod fun saveSession(s: String, data: String, p: Promise) {
        try {
            require(data.toByteArray(StandardCharsets.UTF_8).size <= 64 * 1024)
            val json = JSONObject(data)
            require(json.optInt("version") == 1 && json.optString("sessionId") == s)
            synchronized(lock) {
                // One SharedPreferences transaction makes latest pointer and
                // encrypted task snapshot durable together.
                val sessionKey = name("session", s)
                val latestKey = name("latest", "device")
                check(prefs.edit()
                    .putString(sessionKey, encrypt(data))
                    .putString(latestKey, encrypt(s))
                    .commit()) { "Atomic checkpoint write failed" }
            }
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p, "JOURNAL_WRITE", e) }
    }
    @ReactMethod fun prepareApproval(s: String, c: String, fp: String, p: Promise) {
        try {
            require(fp.isNotBlank() && fp.length <= 4096)
            synchronized(lock) {
                val k = action(s,c)
                val old = existing(k)
                if (old == null) {
                    write(k, record("pending", fp)); p.resolve(true)
                } else p.resolve(old.optString("fingerprint") == fp &&
                    old.optString("status") in listOf("pending", "approved"))
            }
        } catch (e: Exception) { rejectSafe(p, "APPROVAL_PREPARE", e) }
    }
    @ReactMethod fun approveAction(s: String, c: String, fp: String, p: Promise) {
        try {
            synchronized(lock) {
                val k = action(s,c)
                val old = existing(k)
                if (old?.optString("status") == "pending" &&
                    old.optString("fingerprint") == fp) {
                    write(k, record("approved", fp)); p.resolve(true)
                } else p.resolve(false)
            }
        } catch (e: Exception) { rejectSafe(p, "APPROVAL", e) }
    }
    @ReactMethod fun claimAction(s: String, c: String, fp: String,
                                requiresApproval: Boolean, p: Promise) {
        try {
            require(fp.isNotBlank() && fp.length <= 4096)
            synchronized(lock) {
                val k = action(s,c)
                val old = existing(k)
                val permitted = if (requiresApproval)
                    old?.optString("status") == "approved" &&
                        old.optString("fingerprint") == fp
                    else old == null
                if (!permitted) p.resolve(false)
                else {
                    // Synchronous encrypted commit precedes the native tool invocation.
                    write(k, record("claimed", fp)); p.resolve(true)
                }
            }
        } catch (e: Exception) { rejectSafe(p, "CLAIM", e) }
    }
    @ReactMethod fun finishAction(s: String, c: String, uncertain: Boolean, p: Promise) {
        try {
            synchronized(lock) {
                val k = action(s,c)
                val old = existing(k)
                check(old?.optString("status") == "claimed")
                write(k, record(if (uncertain) "uncertain" else "settled",
                    old.getString("fingerprint")))
            }
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p, "RECONCILE", e) }
    }
}
