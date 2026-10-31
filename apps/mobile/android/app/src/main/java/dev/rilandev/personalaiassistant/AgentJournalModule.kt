package dev.rilandev.personalaiassistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Device-local encrypted journal. Commit an irreversible action claim before dispatch. */
class AgentJournalModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    private val prefs = context.getSharedPreferences("paa_secure_journal_v1", Context.MODE_PRIVATE)
    private val lock = Any()
    override fun getName() = "PAAAgentJournal"

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
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secret(), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(text.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP)
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

    @ReactMethod fun newSessionId(p: Promise) { p.resolve(UUID.randomUUID().toString()) }

    @ReactMethod fun loadSession(s: String, p: Promise) {
        try { synchronized(lock) { p.resolve(read(name("session", s))) } }
        catch (_: Exception) { p.reject("JOURNAL_READ", "Secure journal read failed") }
    }
    @ReactMethod fun latestSession(p: Promise) {
        try { synchronized(lock) { p.resolve(read(name("latest", "device"))) } }
        catch (_: Exception) { p.reject("JOURNAL_READ", "Secure journal read failed") }
    }
    @ReactMethod fun saveSession(s: String, data: String, p: Promise) {
        try {
            require(data.toByteArray(StandardCharsets.UTF_8).size <= 64 * 1024)
            val json = JSONObject(data)
            require(json.optInt("version") == 1 && json.optString("sessionId") == s)
            synchronized(lock) {
                write(name("session", s), data)
                write(name("latest", "device"), s)
            }
            p.resolve(true)
        } catch (_: Exception) { p.reject("JOURNAL_WRITE", "Secure journal write failed") }
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
        } catch (_: Exception) { p.reject("APPROVAL_PREPARE", "Approval prepare failed") }
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
        } catch (_: Exception) { p.reject("APPROVAL", "Approval failed") }
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
        } catch (_: Exception) { p.reject("CLAIM", "Action reservation failed") }
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
        } catch (_: Exception) { p.reject("RECONCILE", "Action state uncertain") }
    }
}
