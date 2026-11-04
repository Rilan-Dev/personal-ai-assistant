package dev.rilandev.personalaiassistant

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * In-memory, single-use, expiry-bounded voice-to-WhatsApp review handoff.
 * No number, contact database, message dispatch, or shared preferences.
 */
internal object NexaVoiceDraftState {
    private val lock = Any()
    private var draft: Pair<String, String>? = null
    private var expiresAt: Long = 0L
    private val grammar = Regex(
        """^(?:hey\s+nexa[,!]?\s+)?(?:(?:can you|could you|please)\s+)?(?:send|prepare)\s+(?:a\s+)?whatsapp\s+(?:message\s+)?to\s+(?:my\s+)?([a-z][a-z -]{0,49}?)\s+(?:saying|that says)\s+(.{1,500})$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): Pair<String, String>? {
        if (raw.length > 700 || raw.contains('\n') || raw.contains('\r')) return null
        val parts = grammar.matchEntire(raw.trim()) ?: return null
        val hint = parts.groupValues[1].trim()
        val message = parts.groupValues[2].trim().trim('"', '“', '”')
        if (hint.isEmpty() || message.isEmpty() || message.length > 500 ||
            message.any { it.code < 32 }) return null
        return Pair(hint, message)
    }

    fun queue(intent: Pair<String, String>, now: Long = android.os.SystemClock.elapsedRealtime()) {
        synchronized(lock) {
            draft = intent
            expiresAt = now + 60_000L
        }
    }
    fun take(now: Long = android.os.SystemClock.elapsedRealtime()): Pair<String, String>? =
        synchronized(lock) {
            val value = if (now <= expiresAt) draft else null
            draft = null
            expiresAt = 0L
            value
        }
    fun clear() = synchronized(lock) { draft = null; expiresAt = 0L }
}

/** Explicit owner actions, not part of the AI provider tool registry. */
class NexaToolOverlayModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName() = "PAANexaToolOverlay"

    @ReactMethod fun getStatus(p: Promise) {
        val result = Arguments.createMap()
        result.putBoolean("granted", Settings.canDrawOverlays(context))
        result.putBoolean("visible", NexaToolOverlayService.visible)
        p.resolve(result)
    }

    @ReactMethod fun requestPermission(p: Promise) {
        val activity = context.currentActivity
        if (activity == null || activity.isFinishing ||
            context.lifecycleState != com.facebook.react.common.LifecycleState.RESUMED) {
            p.reject("NOT_FOREGROUND", "Open Nexa to enable display-over-other-apps access")
            return
        }
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            // On Android 11+, the OS may ignore the package URI.
            if (android.os.Build.VERSION.SDK_INT < 30) {
                intent.data = Uri.parse("package:" + context.packageName)
            }
            activity.startActivity(intent)
            p.resolve("settings_opened")
        } catch (_: Exception) {
            p.reject("OVERLAY_SETTINGS_UNAVAILABLE",
                "Your Android device has no overlay permission settings activity")
        }
    }

    @ReactMethod fun show(p: Promise) {
        val activity = context.currentActivity
        if (activity == null || activity.isFinishing ||
            context.lifecycleState != com.facebook.react.common.LifecycleState.RESUMED) {
            p.reject("NOT_FOREGROUND", "Show the floating tool bubble only from visible Nexa")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            p.reject("OVERLAY_PERMISSION_MISSING",
                "Enable Display over other apps in Android Settings first")
            return
        }
        try {
            context.startService(Intent(context, NexaToolOverlayService::class.java).apply {
                action = NexaToolOverlayService.ACTION_SHOW
            })
            p.resolve(true) // Intent dispatched; service reports actual visibility later
        } catch (_: Exception) {
            p.reject("OVERLAY_START_DENIED", "Android could not show Nexa's tool bubble")
        }
    }

    @ReactMethod fun hide(p: Promise) {
        try {
            context.stopService(Intent(context, NexaToolOverlayService::class.java))
            p.resolve(true)
        } catch (_: Exception) {
            p.reject("OVERLAY_STOP_DENIED", "Could not hide the tool bubble")
        }
    }

    @ReactMethod fun consumePendingTextEntry(p: Promise) {
        val pending=NexaVoiceTextIntent.take()
        if(pending==null){p.resolve(null);return}
        val result=Arguments.createMap()
        result.putString("targetLabel",pending.targetLabel)
        result.putString("exactText",pending.exactText)
        result.putBoolean("reviewOnly",true)
        p.resolve(result)
    }

    @ReactMethod fun consumePendingMessageRequest(p: Promise) {
        val pending=NexaGeneralMessageVoice.take()
        if(pending==null) {p.resolve(null);return}
        val result=Arguments.createMap()
        result.putString("recipientHint",pending.recipientHint)
        result.putString("text",pending.message)
        result.putString("explicitApp",pending.explicitApp)
        result.putBoolean("reviewOnly",true)
        p.resolve(result)
    }

    @ReactMethod fun consumePendingVoiceDraft(p: Promise) {
        val pending = NexaVoiceDraftState.take()
        if (pending == null) { p.resolve(null); return }
        val result = Arguments.createMap()
        result.putString("contactHint", pending.first)
        result.putString("message", pending.second)
        result.putString("mode", "review_only")
        p.resolve(result)
    }

    @ReactMethod fun consumePendingTab(p: Promise) {
        val prefs = context.getSharedPreferences("nexa_overlay", android.content.Context.MODE_PRIVATE)
        val tab = prefs.getString("pending_tab", "") ?: ""
        prefs.edit().remove("pending_tab").apply()
        p.resolve(if (tab in setOf("chat","voice","tools")) tab else "")
    }
}
