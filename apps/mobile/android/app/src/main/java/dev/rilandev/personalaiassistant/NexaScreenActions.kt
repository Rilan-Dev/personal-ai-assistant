package dev.rilandev.personalaiassistant

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/**
 * Explicit OWNER overlay interaction only. AI tools cannot call this controller.
 * No gestures, coordinates, typing, calls, sending, deleting, or retries.
 */
internal object NexaScreenActionPolicy {
    private val safeActions = setOf("menu", "more_options", "search", "settings", "back", "close")
    fun permits(item: ObservedIcon): Boolean =
        item.clickable && item.semantic in safeActions &&
        item.bounds.right > item.bounds.left && item.bounds.bottom > item.bounds.top
}
internal data class OverlayScreenSnapshot(
    val token: String, val packageName: String, val windowId: Int,
    val expiresAt: Long, val icons: List<ObservedIcon>
)
internal object NexaScreenActions {
    private val lock = Any()
    private var current: OverlayScreenSnapshot? = null
    private var chosenId: String? = null

    fun reset() = synchronized(lock) {
        current = null
        chosenId = null
    }

    fun inspect(service: ScreenAssistService): Pair<OverlayScreenSnapshot?, String> {
        reset()
        if (!ScreenAssistState.connected) return null to "Enable Nexa Screen Assist in Android Accessibility Settings."
        val keyguard = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) return null to "Unlock your phone before inspecting."
        val root = service.rootInActiveWindow ?: return null to "No inspectable active window."
        try {
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg.isBlank() || pkg == service.packageName ||
                pkg == "com.android.systemui" || pkg == "android" ||
                pkg.startsWith("com.google.android.inputmethod")) {
                return null to "Protected, system or Nexa window; inspection refused."
            }
            val icons = ScreenIconDetector.detect(root).take(24)
            val snapshot = OverlayScreenSnapshot(UUID.randomUUID().toString(), pkg,
                root.windowId, SystemClock.elapsedRealtime() + 12_000L, icons)
            synchronized(lock) { current = snapshot }
            return snapshot to "Visible icon metadata captured. Choose an allowed action for confirmation."
        } finally {
            root.recycle()
        }
    }

    fun select(token: String, id: String): ObservedIcon? = synchronized(lock) {
        val snap = current ?: return@synchronized null
        if (token != snap.token || SystemClock.elapsedRealtime() >= snap.expiresAt) {
            current = null
            chosenId = null
            return@synchronized null
        }
        val item = snap.icons.singleOrNull { it.id == id && NexaScreenActionPolicy.permits(it) }
            ?: return@synchronized null
        chosenId = id
        item
    }

    fun selected(): ObservedIcon? = synchronized(lock) {
        val snap = current ?: return@synchronized null
        if (SystemClock.elapsedRealtime() >= snap.expiresAt) return@synchronized null
        snap.icons.singleOrNull { it.id == chosenId && NexaScreenActionPolicy.permits(it) }
    }

    /**
     * Consume the approval before attempting ACTION_CLICK. On any error, require a
     * brand-new inspect-and-approve sequence; no automatic retry after uncertainty.
     */
    fun confirm(service: ScreenAssistService, token: String): String {
        val candidate: Pair<OverlayScreenSnapshot, ObservedIcon> = synchronized(lock) {
            val snap = current
            val selected = snap?.icons?.singleOrNull { it.id == chosenId }
            current = null
            chosenId = null
            if (snap == null || selected == null || token != snap.token ||
                SystemClock.elapsedRealtime() >= snap.expiresAt ||
                !NexaScreenActionPolicy.permits(selected)) return "Approval expired or invalid. Inspect again."
            snap to selected
        }
        val (snapshot, icon) = candidate
        if (!ScreenAssistState.connected) return "Screen Assist was disabled. No action requested."
        val keyguard = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) return "Screen locked. No action requested."
        val root = service.rootInActiveWindow ?: return "Screen unavailable. No action requested."
        try {
            if (root.packageName?.toString() != snapshot.packageName ||
                root.windowId != snapshot.windowId) return "Screen changed. No action requested."
            val nodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
            nodes.add(root)
            var examined = 0
            var matched: AccessibilityNodeInfo? = null
            var duplicate = false
            while (nodes.isNotEmpty() && examined < 180) {
                val node = nodes.removeFirst()
                examined++
                val eligible = !node.isPassword && !node.isEditable && node.isVisibleToUser
                if (eligible) {
                    if (node.isClickable) {
                        val kind = ScreenIconClassifier.classify(node.contentDescription)
                            ?: ScreenIconClassifier.classify(node.text)
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        val sameBounds = bounds.left == icon.bounds.left && bounds.top == icon.bounds.top &&
                            bounds.right == icon.bounds.right && bounds.bottom == icon.bounds.bottom
                        if (sameBounds && kind?.first == icon.semantic && kind?.second == icon.label) {
                            if (matched == null) matched = node else duplicate = true
                        }
                    }
                    for (i in 0 until node.childCount) node.getChild(i)?.let { nodes.addLast(it) }
                }
            }
            // Observed target must still have exactly one matching actionable node.
            if (duplicate || matched == null) return "Target changed or ambiguous. No action requested."
            if (!NexaUiActionJournal.claim(service, snapshot.token, "tap", snapshot.packageName))
                return "Secure action journal unavailable or approval already used. No click requested."
            return if (matched.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                if (!NexaUiActionJournal.finish(service,snapshot.token,"android_accepted"))
                    "Android accepted a click request; journal outcome uncertain. No retry."
                else "Android accepted a single UI click request. Verify the result on screen."
            } else {
                NexaUiActionJournal.finish(service,snapshot.token,"android_rejected")
                "Android rejected the click. No retry attempted."
            }
        } catch (_: Exception) {
            return "Screen action could not be verified. No retry attempted."
        } finally {
            root.recycle()
        }
    }
}
