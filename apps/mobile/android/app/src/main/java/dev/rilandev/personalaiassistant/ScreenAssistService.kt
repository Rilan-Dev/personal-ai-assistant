package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * User-armed, single-use screen text assistance.
 * No autonomous screen watch, overlay, gestures, clicks or model requests.
 * Reads bounded, visible, non-editable accessibility text once, in memory.
 */
internal object ScreenAssistState {
    private val lock = Any()
    @Volatile var connected: Boolean = false
    private var armedUntil: Long = 0
    private var snapshot: String? = null
    private var sourcePackage: String? = null
    private var capturedAt: Long = 0

    fun arm(): Boolean = synchronized(lock) {
        if (!connected) return@synchronized false
        snapshot = null
        sourcePackage = null
        capturedAt = 0
        armedUntil = android.os.SystemClock.elapsedRealtime() + 20_000
        true
    }
    fun capture(packageName: String, text: String) = synchronized(lock) {
        if (armedUntil <= android.os.SystemClock.elapsedRealtime() ||
            snapshot != null || text.isBlank()) return@synchronized
        snapshot = text.take(2000)
        sourcePackage = packageName.take(100)
        capturedAt = android.os.SystemClock.elapsedRealtime()
        armedUntil = 0
    }
    fun consume(): Triple<String,String,Boolean>? = synchronized(lock) {
        val current = snapshot ?: return@synchronized null
        val expired = android.os.SystemClock.elapsedRealtime() - capturedAt > 60_000
        val pkg = sourcePackage.orEmpty()
        clear()
        if (expired) null else Triple(current, pkg, true)
    }
    fun clear() = synchronized(lock) {
        snapshot = null
        sourcePackage = null
        capturedAt = 0
        armedUntil = 0
    }
    fun isArmed(): Boolean = synchronized(lock) {
        armedUntil > android.os.SystemClock.elapsedRealtime()
    }
}

class ScreenAssistService : AccessibilityService() {
    override fun onServiceConnected() { ScreenAssistState.connected = true }
    override fun onInterrupt() {}
    override fun onDestroy() {
        ScreenAssistState.connected = false
        ScreenAssistState.clear()
        super.onDestroy()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ScreenAssistState.isArmed() || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank() || pkg == packageName || pkg == "com.android.systemui") return
        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString().orEmpty()
        if (rootPkg != pkg) return
        val text = StringBuilder()
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < 120 && text.length < 2000) {
            val node = pending.removeFirst()
            visited++
            // Never capture passwords or editable fields, including chat input,
            // OTP inputs and text-entry editors. Still treat any captured
            // visible text as sensitive and potentially untrusted.
            if (!node.isPassword && !node.isEditable && node.isVisibleToUser) {
                val label = node.text?.toString() ?: node.contentDescription?.toString().orEmpty()
                val clean = label.replace(Regex("[\\p{Cntrl}]+"), " ").trim().take(160)
                if (clean.isNotBlank() && text.length + clean.length + 1 <= 2000) {
                    text.append(clean).append('\n')
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { pending.add(it) }
                }
            }
        }
        if (text.isNotBlank()) ScreenAssistState.capture(pkg, text.toString())
    }
}

class ScreenAssistModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName(): String = "PAAScreenAssist"

    @ReactMethod fun getStatus(p: Promise) {
        val value = Arguments.createMap()
        value.putBoolean("enabled", ScreenAssistState.connected)
        value.putBoolean("armed", ScreenAssistState.isArmed())
        p.resolve(value)
    }

    @ReactMethod fun openAccessibilitySettings(p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Open the app to configure accessibility")
            return
        }
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            p.resolve(true)
        } catch (_: Exception) {
            p.reject("SETTINGS_UNAVAILABLE", "Could not open accessibility settings")
        }
    }

    @ReactMethod fun armOneScreen(p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Arm capture from the visible app")
            return
        }
        if (!ScreenAssistState.arm()) {
            p.reject("ACCESSIBILITY_DISABLED", "Enable Screen Assist in Android Accessibility Settings")
            return
        }
        p.resolve(true)
    }

    @ReactMethod fun takeCapturedText(p: Promise) {
        val item = ScreenAssistState.consume()
        if (item == null) {
            p.resolve(null)
        } else {
            val result = Arguments.createMap()
            result.putString("text", item.first)
            result.putString("packageName", item.second)
            result.putBoolean("ownerArmed", item.third)
            p.resolve(result)
        }
    }
    @ReactMethod fun clear(p: Promise) {
        ScreenAssistState.clear()
        p.resolve(true)
    }
}
