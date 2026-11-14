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
    val expiresAt: Long, val icons: List<ObservedIcon>, val pauseGeneration: Long
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
        val root = service.externalWindowRoot() ?: return null to "No inspectable external application window."
        try {
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg.isBlank() || pkg == service.packageName ||
                pkg == "com.android.systemui" || pkg == "android" ||
                pkg.startsWith("com.google.android.inputmethod")) {
                return null to "Protected, system or Nexa window; inspection refused."
            }
            val icons = ScreenIconDetector.detect(root).take(24)
            val generation=NexaNativeActionSafety.approvalFence(service)
                ?:return null to "Device actions are paused. No action review started."
            val snapshot = OverlayScreenSnapshot(UUID.randomUUID().toString(), pkg,
                root.windowId, SystemClock.elapsedRealtime() + 12_000L, icons,generation)
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
    /**
     * Reuse one conservative target resolver at review and at FINAL dispatch.
     * It rejects ambiguous and truncated accessibility trees. A geometry-only
     * match is never sufficient to authorize an irreversible click.
     */
    internal fun uniqueCurrentTarget(root: AccessibilityNodeInfo, icon: ObservedIcon): AccessibilityNodeInfo? {
        val nodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
        nodes.add(root)
        var examined = 0
        var matched: AccessibilityNodeInfo? = null
        var count = 0
        // Real OEM application trees commonly include far more than 180
        // nodes. Keep a firm bound, but allow complete uniqueness checks on
        // larger screens instead of rejecting every target as truncated.
        while (nodes.isNotEmpty() && examined++ < 512) {
            val node = nodes.removeFirst()
            // A structural Accessibility root/container may report disabled
            // even while its descendants are independent, enabled buttons.
            // Never prune such a container: enforce enabled on the actual
            // clicked control, while still excluding sensitive/editable/
            // invisible subtrees from traversal.
            if (!node.isVisibleToUser || node.isPassword || node.isEditable)
                continue
            if (node.isClickable && node.isEnabled) {
                val kind = ScreenIconClassifier.classify(node.contentDescription)
                    ?: ScreenIconClassifier.classify(node.text)
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                val sameBounds = bounds.left == icon.bounds.left &&
                    bounds.top == icon.bounds.top &&
                    bounds.right == icon.bounds.right &&
                    bounds.bottom == icon.bounds.bottom
                val sameResource = icon.resourceId.isEmpty() ||
                    node.viewIdResourceName.orEmpty() == icon.resourceId
                val sameClass = icon.className.isEmpty() ||
                    node.className?.toString() == icon.className
                if (sameBounds && sameResource && sameClass &&
                    kind?.first == icon.semantic && kind.second == icon.label) {
                    count++
                    if (count != 1) return null
                    matched = node
                }
            }
            for (i in 0 until node.childCount)
                node.getChild(i)?.let { nodes.addLast(it) }
        }
        return if (nodes.isEmpty() && count == 1) matched else null
    }

    /**
     * A reviewed safe icon can move or be recycled before Android dispatch.
     * Consume approval and journal claim once, then reacquire the same native
     * source INSIDE the owner Pause fence. Unknown outcomes are never replayed.
     */
    fun confirm(service: ScreenAssistService, token: String, origin:String="overlay"): String {
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
        if(!NexaNativeActionSafety.approvalFenceCurrent(service,snapshot.pauseGeneration))
            return "Owner paused device actions since inspection. Review a fresh icon."
        if (!ScreenAssistState.connected || ScreenAssistService.active !== service)
            return "Screen Assist was disabled. No action requested."
        val keyguard = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true)
            return "Screen locked. No action requested."
        val root = try { service.externalWindowRoot() } catch (_: Exception) { null }
            ?: return "External screen unavailable. No action requested."
        var claimed = false
        try {
            if (root.packageName?.toString() != snapshot.packageName ||
                root.windowId != snapshot.windowId || !root.isVisibleToUser)
                return "Screen changed. No action requested."
            val target = uniqueCurrentTarget(root, icon)
                ?: return "Target changed, incomplete or ambiguous. No action requested."
            if (!NexaUiActionJournal.claim(service, snapshot.token, "tap", snapshot.packageName,
                    origin=origin))
                return "Secure action journal unavailable or approval already used. No click requested."
            claimed = true
            val before = NexaUiActionVerifier.fingerprint(root)
            NexaUiActionVerifier.begin(snapshot.token,"tap",snapshot.packageName,root.windowId,before)
            var stale = false
            var enteredNativeAction = false
            var outcomeUnknown = false
            val clicked = NexaNativeActionSafety.dispatchWithFence(
                service,snapshot.pauseGeneration) {
                val fresh = try {
                    if (!ScreenAssistState.connected || ScreenAssistService.active !== service ||
                        keyguard?.isKeyguardLocked == true) null
                    else service.externalWindowRoot()
                } catch (_: Exception) { null }
                if (fresh == null) {
                    stale = true
                    false
                } else try {
                    if (fresh.packageName?.toString() != snapshot.packageName ||
                        fresh.windowId != snapshot.windowId || !fresh.isVisibleToUser) {
                        stale = true
                        false
                    } else {
                        val current = uniqueCurrentTarget(fresh,icon)
                        if (current == null || current != target) {
                            stale = true
                            false
                        } else {
                            enteredNativeAction = true
                            current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }
                } catch (_: Exception) {
                    if (enteredNativeAction) outcomeUnknown = true else stale = true
                    false
                } finally {
                    try { fresh.recycle() } catch (_: Exception) {}
                }
            }
            if (clicked == null) {
                NexaUiActionVerifier.cancel(snapshot.token)
                NexaUiActionJournal.finish(service,snapshot.token,"android_rejected")
                claimed = false
                return "Owner paused actions since review. No click dispatched."
            }
            return if (clicked) {
                NexaUiActionVerifier.observeAfter(service,snapshot.token,before)
                val finished=NexaUiActionJournal.finish(service,snapshot.token,"android_accepted")
                claimed = false
                if (!finished)
                    "Android accepted a click request; journal outcome uncertain. No retry."
                else "Android accepted a single UI click request. Verify the result on screen."
            } else {
                NexaUiActionVerifier.cancel(snapshot.token)
                NexaUiActionJournal.finish(service,snapshot.token,
                    if (outcomeUnknown) "unknown" else "android_rejected")
                claimed = false
                if (outcomeUnknown)
                    "Android click outcome is uncertain. Inspect the screen manually; no automatic retry."
                else if (stale)
                    "Safe icon, app or window changed before dispatch. No click or retry."
                else "Android rejected the click. No retry attempted."
            }
        } catch (_: Exception) {
            NexaUiActionVerifier.cancel(snapshot.token)
            if (claimed) try {
                NexaUiActionJournal.finish(service,snapshot.token,"unknown")
            } catch (_: Exception) {}
            return "Screen action outcome uncertain. Inspect manually; no retry attempted."
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }

}
