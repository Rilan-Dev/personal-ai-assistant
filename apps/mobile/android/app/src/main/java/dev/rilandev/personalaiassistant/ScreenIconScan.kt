package dev.rilandev.personalaiassistant

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/** A single user-armed, short-lived, read-only icon observation. */
internal data class IconBox(val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class ObservedIcon(
    val id: String, val label: String, val semantic: String,
    val bounds: IconBox, val clickable: Boolean,
    val resourceId: String = "", val className: String = ""
)
internal data class IconScanResult(
    val id: String, val sourcePackage: String, val capturedAtWallMs: Long,
    val expiresInMs: Int, val icons: List<ObservedIcon>,
    val windowBounds: IconBox?
)
internal object ScreenIconScanState {
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var armedUntilMs = 0L
    private var capturedAtMs = 0L
    private var capturedWallMs = 0L
    private var scan: Pair<String, List<ObservedIcon>>? = null
    private var scanId = ""
    private var capturedWindow: IconBox? = null
    private val expiry = Runnable { clear() }

    fun arm(): Boolean = synchronized(lock) {
        if (!ScreenAssistState.connected) return@synchronized false
        clearLocked()
        armedUntilMs = SystemClock.elapsedRealtime() + 20_000
        handler.postDelayed(expiry, 20_000)
        true
    }
    fun isArmed(): Boolean = synchronized(lock) {
        armedUntilMs > SystemClock.elapsedRealtime() && scan == null
    }
    fun capture(sourcePackage: String, items: List<ObservedIcon>, window: IconBox?) = synchronized(lock) {
        if (sourcePackage.isBlank() || !isArmed() || scan != null) return@synchronized
        scanId = UUID.randomUUID().toString()
        scan = sourcePackage.take(150) to items.take(64)
        capturedWindow = window?.takeIf { it.left >= 0 && it.top >= 0 &&
            it.right > it.left && it.bottom > it.top && it.right <= 20000 && it.bottom <= 20000 }
        armedUntilMs = 0
        capturedAtMs = SystemClock.elapsedRealtime()
        capturedWallMs = System.currentTimeMillis()
        handler.removeCallbacks(expiry)
        handler.postDelayed(expiry, 60_000)
    }
    fun consume(): IconScanResult? = synchronized(lock) {
        val existing = scan ?: return@synchronized null
        val remaining = (60_000 - (SystemClock.elapsedRealtime() - capturedAtMs)).toInt()
        val out = if (remaining > 0) IconScanResult(scanId, existing.first, capturedWallMs,
            remaining, existing.second, capturedWindow) else null
        clearLocked()
        out
    }
    fun clear() = synchronized(lock) { clearLocked() }
    private fun clearLocked() {
        handler.removeCallbacks(expiry)
        armedUntilMs = 0
        capturedAtMs = 0
        capturedWallMs = 0
        scan = null
        scanId = ""
        capturedWindow = null
    }
}

/** Enumerates bounded visible controls, never screen contents or input-field text. */
internal object ScreenIconDetector {
    fun detect(root: AccessibilityNodeInfo): List<ObservedIcon> {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        val matches = ArrayList<ObservedIcon>()
        queue.add(root)
        var scanned = 0
        while (queue.isNotEmpty() && scanned < 180 && matches.size < 64) {
            val node = queue.removeFirst()
            scanned++
            // Password and editable subtrees are excluded, not just their labels.
            if (!node.isPassword && !node.isEditable && node.isVisibleToUser) {
                val kind = node.className?.toString().orEmpty()
                val imageButton = kind.endsWith("ImageButton")
                val actionLabel = ScreenIconClassifier.classify(node.contentDescription)
                    ?: ScreenIconClassifier.classify(node.text)
                if (node.isClickable || imageButton) {
                    if (actionLabel != null || imageButton) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.left >= 0 && rect.top >= 0 &&
                            rect.right > rect.left && rect.bottom > rect.top &&
                            rect.right <= 20000 && rect.bottom <= 20000) {
                            val (semantic, label) = actionLabel ?: ("unknown" to "Unknown icon")
                            matches.add(ObservedIcon("icon-${matches.size}", label, semantic,
                                IconBox(rect.left, rect.top, rect.right, rect.bottom), node.isClickable,
                                node.viewIdResourceName.orEmpty().take(160),
                                kind.take(160)))
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.addLast(it) }
                }
            }
        }
        return matches
    }
}
