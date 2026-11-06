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
        /** Keystore-only access to the owner-selected chat provider for live voice. */
        internal data class VoiceCredentials(
            val provider: String, val apiKey: String, val orgId: String = "",
            val workspaceId: String = "", val appId: String = "",
            val userId: String = ""
        )
        private fun privateName(prefix: String): String {
            val digest=MessageDigest.getInstance("SHA-256").digest(
                "private:$prefix:".toByteArray(StandardCharsets.UTF_8))
            return Base64.encodeToString(digest,
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        }
        private fun readEncrypted(ctx: Context, key: String): String? {
            val encrypted=ctx.getSharedPreferences("paa_secure_journal_v1",
                Context.MODE_PRIVATE).getString(privateName(key),null) ?: return null
            val all=Base64.decode(encrypted,Base64.DEFAULT)
            require(all.size>28)
            val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secret=ks.getKey("PAA_Journal_v1",null) as? SecretKey ?: return null
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,secret,
                GCMParameterSpec(128,all.copyOfRange(0,12)))
            return String(cipher.doFinal(all.copyOfRange(12,all.size)),StandardCharsets.UTF_8)
        }
        fun openSarvamApiKey(ctx: Context): String? = synchronized(lock) {
            val raw=readEncrypted(ctx,"provider-config") ?: return@synchronized null
            val data=JSONObject(raw)
            val key=data.optString("apiKey")
            if(data.optString("provider")!="sarvam" || key.length !in 12..512)
                null else key
        }
        /** Read-only authorization never starts microphone and does not
         * require consent. Audio start must explicitly require consent.
         */
        fun openSarvamVoicePreflightCredentials(ctx: Context): VoiceCredentials? = synchronized(lock) {
            val voiceRaw=readEncrypted(ctx,"sarvam-voice") ?: return@synchronized null
            val voice=JSONObject(voiceRaw)
            val org=voice.optString("orgId")
            val work=voice.optString("workspaceId")
            val app=voice.optString("appId")
            val user=voice.optString("userId")
            if (listOf(org,work,app,user).any { !it.matches(Regex("[A-Za-z0-9_-]{4,128}")) })
                return@synchronized null
            // Voice Agents workspace credentials are not interchangeable with
            // a Model API chat-completion subscription key.
            val dedicated=readEncrypted(ctx,"sarvam-voice-key")
            val legacy=if(dedicated==null) {
                val saved=readEncrypted(ctx,"provider-config")
                saved?.let {
                    val p=JSONObject(it)
                    p.optString("apiKey").takeIf { key ->
                        p.optString("provider")=="sarvam" &&
                        key.length in 12..512 &&
                        !key.startsWith("sk_samvaad_")
                    }
                }
            } else null
            val key=dedicated ?: legacy ?: return@synchronized null
            if (key.length !in 12..512) return@synchronized null
            VoiceCredentials("sarvam",key,org,work,app,user)
        }
        fun hasSarvamVoiceKey(ctx: Context): Boolean = synchronized(lock) {
            val value=readEncrypted(ctx,"sarvam-voice-key")
            value != null && value.matches(Regex("sk_samvaad_[A-Za-z0-9_-]{16,500}"))
        }
        fun sarvamStreamingConsent(ctx: Context): Boolean = synchronized(lock) {
            readEncrypted(ctx,"sarvam-voice-streaming-consent")=="accepted-model-api-v1"
        }
        /** Voice uses only the owner-selected Model API subscription key.
         * Workspace/Agent identifiers and old Samvaad keys are NEVER read
         * by the active realtime pipeline. No silent fallback providers.
         */
        fun openVoiceCredentials(ctx: Context): VoiceCredentials? = synchronized(lock) {
            val raw=readEncrypted(ctx,"provider-config") ?: return@synchronized null
            val data=JSONObject(raw)
            val provider=data.optString("provider")
            if(provider!="sarvam" && provider!="openai") return@synchronized null
            val key=data.optString("apiKey")
            if(key.length !in 12..512 || key.startsWith("sk_samvaad_"))
                return@synchronized null
            if(provider=="sarvam") {
                if(!sarvamStreamingConsent(ctx)) return@synchronized null
                return@synchronized VoiceCredentials("sarvam-api",key)
            }
            VoiceCredentials("openai",key)
        }
        fun openAiVoiceKey(ctx: Context): String? =
            openVoiceCredentials(ctx)?.takeIf { it.provider=="openai" }?.apiKey
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
    /**
     * ACA device-only session. Separate key namespace from BYOK provider keys.
     * The email password is NEVER persisted. The bearer token uses Android
     * Keystore AES-GCM, with no cloud backup or plaintext logging.
     */
    @ReactMethod fun saveAcaSession(data: String, p: Promise) {
        try {
            require(data.toByteArray(StandardCharsets.UTF_8).size <= 1200)
            val obj = JSONObject(data)
            require(obj.length() == 6 && obj.optInt("version") == 1)
            require(obj.optString("origin") == "https://aca.starcitytechnical.com")
            require(obj.optString("token").matches(Regex("nexa_m1_[A-Za-z0-9_-]{43}")))
            // Avoid java.time APIs that require Android API 26 on older phones.
            val expires = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(obj.getString("expiresAt"))
            require(expires != null && expires.time > System.currentTimeMillis())
            for (field in listOf("organizationId", "agentId")) {
                val value = obj.getString(field)
                require(value.isEmpty() || value.matches(Regex("[0-9a-fA-F-]{36}")))
            }
            synchronized(lock) { write(name("private", "aca-device-session"), data) }
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p, "ACA_DEVICE_SESSION_SAVE", e) }
    }

    @ReactMethod fun loadAcaSession(p: Promise) {
        try {
            synchronized(lock) { p.resolve(read(name("private", "aca-device-session"))) }
        } catch (e: Exception) { rejectSafe(p, "ACA_DEVICE_SESSION_READ", e) }
    }

    @ReactMethod fun clearAcaSession(p: Promise) {
        try {
            synchronized(lock) {
                check(prefs.edit().remove(name("private", "aca-device-session")).commit())
            }
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p, "ACA_DEVICE_SESSION_CLEAR", e) }
    }

    @ReactMethod fun saveProviderSettings(data: String, p: Promise) {
        try {
            require(data.toByteArray(StandardCharsets.UTF_8).size <= 2048)
            val obj = JSONObject(data)
            require(obj.optInt("version") == 1)
            require(obj.length() == 4)
            require(obj.optString("provider") in listOf("openai", "gemini", "groq", "openrouter", "sarvam"))
            val model = obj.getString("model")
            require(model.matches(Regex("[A-Za-z0-9._:/-]{2,96}")))
            val key = obj.getString("apiKey")
            require(key.length in 12..512 && key == key.trim())
            require(!key.startsWith("sk_samvaad_"))
            synchronized(lock) {
                val old = read(name("private","provider-config"))
                write(name("private", "provider-config"), data)
                if(old != data) write(name("private","sarvam-voice-streaming-consent"),"declined")
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

    /** Samvaad workspace key: device-only, never exposed back to JavaScript. */
    @ReactMethod fun saveSarvamVoiceWorkspaceKey(key: String, p: Promise) {
        try {
            require(key.matches(Regex("sk_samvaad_[A-Za-z0-9_-]{16,500}")))
            synchronized(lock) {
                write(name("private","sarvam-voice-key"),key)
                // A replacement key requires renewed streaming consent.
                write(name("private","sarvam-voice-streaming-consent"),"declined")
            }
            reactApplicationContext.stopService(
                android.content.Intent(reactApplicationContext,RealtimeVoiceService::class.java))
            p.resolve(true)
        } catch(e: Exception) { rejectSafe(p,"SARVAM_WORKSPACE_KEY_SAVE",e) }
    }
    @ReactMethod fun sarvamVoiceWorkspaceKeyPresent(p: Promise) {
        try { p.resolve(hasSarvamVoiceKey(reactApplicationContext)) }
        catch(e: Exception) { rejectSafe(p,"SARVAM_WORKSPACE_KEY_READ",e) }
    }
    @ReactMethod fun setSarvamVoiceStreamingConsent(accepted: Boolean, p: Promise) {
        try {
            synchronized(lock) {
                write(name("private","sarvam-voice-streaming-consent"),
                    if(accepted) "accepted-model-api-v1" else "declined")
            }
            if(!accepted) reactApplicationContext.stopService(
                android.content.Intent(reactApplicationContext,RealtimeVoiceService::class.java))
            p.resolve(true)
        } catch(e: Exception) { rejectSafe(p,"SARVAM_CONSENT_SAVE",e) }
    }
    @ReactMethod fun sarvamVoiceStreamingConsentStatus(p: Promise) {
        try { p.resolve(sarvamStreamingConsent(reactApplicationContext)) }
        catch(e: Exception) { rejectSafe(p,"SARVAM_CONSENT_READ",e) }
    }
    @ReactMethod fun clearSarvamVoiceWorkspaceKey(p: Promise) {
        try {
            synchronized(lock) {
                check(prefs.edit()
                    .remove(name("private","sarvam-voice-key"))
                    .remove(name("private","sarvam-voice-streaming-consent")).commit())
            }
            reactApplicationContext.stopService(
                android.content.Intent(reactApplicationContext,RealtimeVoiceService::class.java))
            p.resolve(true)
        } catch(e: Exception) { rejectSafe(p,"SARVAM_WORKSPACE_KEY_CLEAR",e) }
    }

    /** Sarvam managed Voice Agent IDs are kept AES-GCM encrypted, separate
     * from the provider key. No transcripts or audio are stored here. */
    @ReactMethod fun saveSarvamVoiceAgent(orgId: String, workspaceId: String,
                                          appId: String, p: Promise) {
        try {
            require(listOf(orgId,workspaceId,appId).all {
                it.matches(Regex("[A-Za-z0-9_-]{4,128}"))
            })
            synchronized(lock) {
                val old=read(name("private","sarvam-voice"))
                val existing=old?.let { JSONObject(it).optString("userId") }
                val userId=existing?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{4,128}")) }
                    ?: UUID.randomUUID().toString()
                val data=JSONObject().put("orgId",orgId)
                    .put("workspaceId",workspaceId).put("appId",appId)
                    .put("userId",userId).toString()
                write(name("private","sarvam-voice"),data)
            }
            reactApplicationContext.stopService(
                android.content.Intent(reactApplicationContext,RealtimeVoiceService::class.java))
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p,"SARVAM_VOICE_CONFIG",e) }
    }
    @ReactMethod fun loadSarvamVoiceAgent(p: Promise) {
        try {
            synchronized(lock) {
                val saved=read(name("private","sarvam-voice"))
                val result=saved?.let {
                    val parsed=JSONObject(it)
                    JSONObject().put("orgId",parsed.getString("orgId"))
                        .put("workspaceId",parsed.getString("workspaceId"))
                        .put("appId",parsed.getString("appId")).toString()
                }
                p.resolve(result)
            }
        } catch (e: Exception) { rejectSafe(p,"SARVAM_VOICE_READ",e) }
    }
    @ReactMethod fun clearSarvamVoiceAgent(p: Promise) {
        try {
            synchronized(lock) {
                check(prefs.edit()
                    .remove(name("private","sarvam-voice"))
                    .remove(name("private","sarvam-voice-key"))
                    .remove(name("private","sarvam-voice-streaming-consent")).commit())
            }
            reactApplicationContext.stopService(
                android.content.Intent(reactApplicationContext,RealtimeVoiceService::class.java))
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p,"SARVAM_VOICE_CLEAR",e) }
    }

    /** An explicit app choice for a user-defined recipient alias, not a contact
     * identity or proof of consent to transmit. Stored only in Android Keystore
     * AES-GCM private preferences; no phone numbers or messages are remembered.
     */
    private fun messageAlias(raw: String): String {
        val alias=raw.lowercase(java.util.Locale.ROOT).trim().replace(Regex("\\s+")," ")
        require(alias.matches(Regex("[a-z][a-z0-9 '-]{1,49}")))
        return alias
    }
    private fun messageApp(value: String): String {
        require(value in setOf("whatsapp","whatsapp-business","sms","imo","botim"))
        return value
    }
    private val allowedMessageApps=setOf("whatsapp","whatsapp-business","sms","imo","botim")
    /** A remembered channel is a bounded, owner-confirmed HINT, never a
     * matching contact identity or permission to send. Legacy preferences
     * remain readable until their owner updates or forgets them.
     */
    private fun messageAppRecord(raw:String?):JSONObject?{
        if(raw==null)return null
        if(raw in allowedMessageApps)return JSONObject()
            .put("version",1).put("appId",raw)
            .put("source","legacy_owner_choice")
            .put("savedAt",0L).put("expiresAt",0L)
        return try {
            val j=JSONObject(raw)
            val id=j.optString("appId")
            val saved=j.optLong("savedAt",-1L)
            val end=j.optLong("expiresAt",-1L)
            if(j.optInt("version")!=2 || id !in allowedMessageApps ||
                j.optString("source")!="owner_confirmed" ||
                saved<=0L || end<=saved || end-saved>365L*86_400_000L)
                null else j
        }catch(_:Exception){null}
    }
    private fun saveMessageAppChoice(alias:String,app:String,days:Int):Boolean {
        require(days in 1..365)
        val key=name("private","message-app:"+messageAlias(alias))
        val id=messageApp(app)
        val now=System.currentTimeMillis()
        val payload=JSONObject().put("version",2).put("appId",id)
            .put("source","owner_confirmed")
            .put("savedAt",now).put("expiresAt",now+days*86_400_000L)
        return synchronized(lock){write(key,payload.toString());true}
    }
    @ReactMethod fun rememberMessageApp(recipientAlias:String,appId:String,p:Promise){
        try {
            p.resolve(saveMessageAppChoice(recipientAlias,appId,90))
        }catch(e:Exception){rejectSafe(p,"MESSAGE_MEMORY_WRITE",e)}
    }
    /** Optional owner-selected retention. Does not change any pending draft,
     * recipient, permission, or already prepared message.
     */
    @ReactMethod fun rememberMessageAppForDays(recipientAlias:String,
                                               appId:String,days:Int,p:Promise){
        try {
            p.resolve(saveMessageAppChoice(recipientAlias,appId,days))
        }catch(e:Exception){rejectSafe(p,"MESSAGE_MEMORY_WRITE",e)}
    }
    @ReactMethod fun rememberedMessageApp(recipientAlias:String,p:Promise){
        try {
            val key=name("private","message-app:"+messageAlias(recipientAlias))
            val record=synchronized(lock){
                val item=messageAppRecord(read(key))
                if(item!=null && item.optLong("expiresAt")>0L &&
                    item.optLong("expiresAt")<=System.currentTimeMillis()){
                    check(prefs.edit().remove(key).commit())
                    null
                } else item
            }
            p.resolve(record?.optString("appId"))
        }catch(e:Exception){rejectSafe(p,"MESSAGE_MEMORY_READ",e)}
    }
    /** Provenance and expiry without returning an alias, phone number,
     * contact identifier, message body, or ciphertext.
     */
    @ReactMethod fun messageAppPreferenceDetails(recipientAlias:String,p:Promise){
        try {
            val key=name("private","message-app:"+messageAlias(recipientAlias))
            val record=synchronized(lock){
                val item=messageAppRecord(read(key))
                if(item!=null && item.optLong("expiresAt")>0L &&
                    item.optLong("expiresAt")<=System.currentTimeMillis()){
                    check(prefs.edit().remove(key).commit())
                    null
                }else item
            }
            p.resolve(record?.toString())
        }catch(e:Exception){rejectSafe(p,"MESSAGE_MEMORY_READ",e)}
    }
    @ReactMethod fun forgetMessageApp(recipientAlias: String, p: Promise) {
        try {
            val key=name("private","message-app:"+messageAlias(recipientAlias))
            synchronized(lock) {check(prefs.edit().remove(key).commit())}
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p,"MESSAGE_MEMORY_CLEAR",e) }
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
                else if (!NexaAgentActionAudit.claim(
                        reactApplicationContext,s,c,fp,requiresApproval)) {
                    // Canonical durable ledger is the first no-replay fence.
                    // Do not dispatch when that reservation is unavailable.
                    p.resolve(false)
                } else {
                    // Existing secure journal still owns final action approval.
                    // If this write fails the earlier SQLite claim remains a
                    // terminal no-replay tombstone, never a reusable grant.
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
                // Record only dispatch settlement, never external fulfillment.
                // A failed audit write leaves the encrypted action already
                // claimed, so Android cannot receive an automatic replay.
                check(NexaAgentActionAudit.recordSettlement(
                    reactApplicationContext,s,c,
                    old.getString("fingerprint"),uncertain))
                write(k, record(if (uncertain) "uncertain" else "settled",
                    old.getString("fingerprint")))
            }
            p.resolve(true)
        } catch (e: Exception) { rejectSafe(p, "RECONCILE", e) }
    }
}
