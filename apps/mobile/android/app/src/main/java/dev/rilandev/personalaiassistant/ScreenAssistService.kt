package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
    private val expiryHandler = Handler(Looper.getMainLooper())
    // No context reference: the one-shot capture is purged even if the
    // owner never returns to the application after changing screens.
    private val expire = Runnable { clear() }
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
        expiryHandler.removeCallbacks(expire)
        expiryHandler.postDelayed(expire,20_000L)
        true
    }
    fun capture(packageName: String, text: String) = synchronized(lock) {
        if (armedUntil <= android.os.SystemClock.elapsedRealtime() ||
            snapshot != null || text.isBlank()) return@synchronized
        snapshot = text.take(2000)
        sourcePackage = packageName.take(100)
        capturedAt = android.os.SystemClock.elapsedRealtime()
        armedUntil = 0
        expiryHandler.removeCallbacks(expire)
        expiryHandler.postDelayed(expire,60_000L)
    }
    fun consume(): Triple<String,String,Boolean>? = synchronized(lock) {
        val current = snapshot ?: return@synchronized null
        val expired = android.os.SystemClock.elapsedRealtime() - capturedAt > 60_000
        val pkg = sourcePackage.orEmpty()
        clear()
        if (expired) null else Triple(current, pkg, true)
    }
    fun clear(): Unit = synchronized(lock) {
        expiryHandler.removeCallbacks(expire)
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
    /**
     * Find the topmost real third-party APPLICATION window behind Nexa's
     * floating overlay. No keyguard, Android system surface, keyboard or Nexa
     * window is returned. Caller owns the returned node and must recycle it.
     */
    internal fun externalWindowRoot(): AccessibilityNodeInfo? {
        if (!ScreenAssistState.connected) return null
        val locked = (getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)
            ?.isKeyguardLocked == true
        if (locked) return null
        val allWindows = try { windows.orEmpty() } catch (_: Exception) { emptyList() }
        for (window in allWindows.sortedByDescending { it.layer }) {
            if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION)
                continue
            val candidate = try { window.root } catch (_: Exception) { null } ?: continue
            if (isPermittedExternalWindow(candidate.packageName?.toString().orEmpty()))
                return candidate
            candidate.recycle()
        }
        // On devices that do not expose interactive windows, only fall back
        // to an active root if it is independently known to be external.
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return null
        if (isPermittedExternalWindow(root.packageName?.toString().orEmpty())) return root
        root.recycle()
        return null
    }

    private fun isPermittedExternalWindow(pkg: String): Boolean =
        pkg.isNotBlank() && pkg != packageName && pkg != "android" &&
        pkg != "com.android.systemui" &&
        !pkg.startsWith("com.google.android.inputmethod") &&
        !pkg.startsWith("com.android.inputmethod")

    companion object {
        @Volatile internal var active: ScreenAssistService? = null
            private set
    }
    override fun onServiceConnected() {
        active = this
        ScreenAssistState.connected = true
    }
    override fun onInterrupt() {}
    override fun onDestroy() {
        ScreenAssistState.connected = false
        ScreenAssistState.clear()
        ScreenIconScanState.clear()
        NexaVisualIconCapture.clear()
        NexaScreenActions.reset()
        if (active === this) active = null
        super.onDestroy()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) NexaUiActionVerifier.onAccessibilityEvent(event)
        if ((!ScreenAssistState.isArmed() && !ScreenIconScanState.isArmed() && !NexaVisualIconCapture.isArmed()) || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank() || pkg == packageName || pkg == "com.android.systemui") return
        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString().orEmpty()
        if (rootPkg != pkg) return
        if (ScreenIconScanState.isArmed()) {
            val box = android.graphics.Rect()
            root.getBoundsInScreen(box)
            ScreenIconScanState.capture(pkg, ScreenIconDetector.detect(root),
                IconBox(box.left,box.top,box.right,box.bottom),root.windowId)
        }
        if (NexaVisualIconCapture.isArmed()) {
            NexaVisualIconCapture.capture(this,pkg,ScreenIconDetector.detect(root),root.windowId)
        }
        if (!ScreenAssistState.isArmed()) return
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
        value.putBoolean("iconScanArmed", ScreenIconScanState.isArmed())
        value.putBoolean("visualScanArmed", NexaVisualIconCapture.isArmed())
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
        ScreenIconScanState.clear()
        NexaVisualIconCapture.clear()
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
    /** User explicitly arms one 20-second, read-only icon scan. */
    @ReactMethod fun armIconScan(p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Start icon scanning from the visible Nexa app")
            return
        }
        ScreenAssistState.clear()
        NexaVisualIconCapture.clear()
        if (!ScreenIconScanState.arm()) {
            p.reject("ACCESSIBILITY_DISABLED", "Enable Screen Assist in Android Accessibility Settings")
            return
        }
        p.resolve(true)
    }

    @ReactMethod fun takeIconScan(p: Promise) {
        val snap = ScreenIconScanState.consume()
        if (snap == null) {
            p.resolve(null)
            return
        }
        val result = Arguments.createMap()
        result.putInt("version", 1)
        result.putString("snapshotId", snap.id)
        result.putString("packageName", snap.sourcePackage)
        result.putDouble("capturedAtWallMs", snap.capturedAtWallMs.toDouble())
        result.putInt("expiresInMs", snap.expiresInMs)
        val elements = Arguments.createArray()
        for (icon in snap.icons) {
            val item = Arguments.createMap()
            item.putString("id", icon.id)
            item.putString("label", icon.label)
            item.putString("semantic", icon.semantic)
            item.putBoolean("clickable", icon.clickable)
            item.putString("evidence", "accessibility")
            val bounds = Arguments.createMap()
            bounds.putInt("left", icon.bounds.left)
            bounds.putInt("top", icon.bounds.top)
            bounds.putInt("right", icon.bounds.right)
            bounds.putInt("bottom", icon.bounds.bottom)
            item.putMap("boundsPx", bounds)
            elements.pushMap(item)
        }
        result.putArray("icons", elements)
        snap.windowBounds?.let { b ->
            val window=Arguments.createMap()
            window.putInt("left",b.left)
            window.putInt("top",b.top)
            window.putInt("right",b.right)
            window.putInt("bottom",b.bottom)
            result.putMap("windowBoundsPx",window)
        }
        snap.windowId?.let { result.putInt("windowId",it) }
        p.resolve(result)
    }

    @ReactMethod fun clearIconScan(p: Promise) {
        ScreenIconScanState.clear()
        p.resolve(true)
    }


    /** Consent is separate from text/icon metadata capture. Image crops stay in memory. */
    @ReactMethod fun armVisualScan(p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Start screenshot fallback from visible Nexa")
            return
        }
        ScreenAssistState.clear()
        ScreenIconScanState.clear()
        if (!NexaVisualIconCapture.arm()) {
            p.reject("VISUAL_UNAVAILABLE", "Android 11+ and Screen Assist permission required")
            return
        }
        p.resolve(true)
    }
    @ReactMethod fun takeVisualScan(p: Promise) {
        val snap = NexaVisualIconCapture.consume()
        if (snap == null) { p.resolve(null); return }
        val item = Arguments.createMap()
        item.putString("snapshotId",snap.id)
        item.putString("packageName",snap.packageName)
        snap.windowId?.let { item.putInt("windowId",it) }
        item.putDouble("capturedAtWallMs",snap.createdAt.toDouble())
        item.putInt("expiresInMs",snap.expiresInMs)
        val images = Arguments.createArray()
        for (crop in snap.crops) {
            val image = Arguments.createMap()
            image.putString("iconId",crop.iconId)
            image.putString("dataUri",crop.dataUri)
            val box = Arguments.createMap()
            box.putInt("left",crop.bounds.left)
            box.putInt("top",crop.bounds.top)
            box.putInt("right",crop.bounds.right)
            box.putInt("bottom",crop.bounds.bottom)
            image.putMap("boundsPx",box)
            images.pushMap(image)
        }
        item.putArray("crops",images)
        p.resolve(item)
    }
    @ReactMethod fun clearVisualScan(p: Promise) {
        NexaVisualIconCapture.clear()
        p.resolve(true)
    }
    @ReactMethod fun clear(p: Promise) {
        ScreenAssistState.clear()
        p.resolve(true)
    }
}
