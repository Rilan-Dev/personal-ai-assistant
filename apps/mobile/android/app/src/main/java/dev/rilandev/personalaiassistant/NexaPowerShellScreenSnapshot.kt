package dev.rilandev.personalaiassistant

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Short-lived, process-memory-only external screen observations for the debug
 * PowerShell client. The durable Nexa action journals receive only the opaque
 * snapshot id and selected index; labels and screen text never enter them.
 */
internal object NexaPowerShellScreenSnapshot {
    private const val TTL_MS = 30_000L
    // A safe screen observation may be reviewed after one bounded execution
    // phase and then one owner-review phase. The live target is still
    // re-resolved against the same app/window, label and bounds at dispatch.
    private const val ASSISTANT_ACTION_TTL_MS = 330_000L
    private const val MAX_SNAPSHOTS = 4
    private const val MAX_NODES = 512
    private const val MAX_TEXT_LINES = 60
    private const val MAX_TEXT_CHARS = 2_000
    private const val MAX_TARGETS = 40
    private const val MAX_ICONS = 24
    internal const val MAX_CLICKABLE_ANCESTOR_HOPS = 5

    private val unsafeLabel = Regex(
        "\\b(send|submit|pay|purchase|buy|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|passcode|pin|otp|logout|sign out|payment|bank|security|verify)\\b",
        RegexOption.IGNORE_CASE
    )
    private val safeLabel = Regex("^(?:[\\p{L}][\\p{L}\\p{N} _.-]{1,44}|[0-9])$")

    data class Claim(
        val snapshotId: String,
        val packageName: String,
        val windowId: Int,
        val structureFingerprint: String
    )

    data class Target(val label: String, val claim: Claim, val bounds: Rect)

    private data class Clickable(
        val index: Int,
        val label: String,
        val bounds: Rect,
        val kind: String,
        val role: String,
        val semantic: String?
    )

    private data class RawClickable(
        val label: String,
        val bounds: Rect,
        val kind: String,
        val role: String,
        val semantic: String?
    )

    private data class Snapshot(
        val id: String,
        val createdAt: Long,
        val ttlMs: Long,
        val packageName: String,
        val windowId: Int,
        val structureFingerprint: String,
        val text: List<String>,
        val clickables: List<Clickable>,
        val icons: List<ObservedIcon>,
        val viewport: Rect,
        val assistantComplete: Boolean
    ) {
        val claim = Claim(id, packageName, windowId, structureFingerprint)

        /** Link an icon only to one exact-bounds target already admitted by
         * the safe-label/clickable filter. Unknown icons without a unique
         * accessible target remain metadata-only.
         */
        private fun targetIndexFor(icon:ObservedIcon):Int {
            val matches=clickables.filter {
                it.bounds.left==icon.bounds.left && it.bounds.top==icon.bounds.top &&
                    it.bounds.right==icon.bounds.right && it.bounds.bottom==icon.bounds.bottom
            }
            return matches.singleOrNull()?.index ?: -1
        }

        fun toJson(): String {
            val lines = JSONArray()
            text.forEach { lines.put(it) }
            val controls = JSONArray()
            clickables.forEach { item ->
                val bounds = JSONObject()
                    .put("left", item.bounds.left)
                    .put("top", item.bounds.top)
                    .put("right", item.bounds.right)
                    .put("bottom", item.bounds.bottom)
                    .put("centerX", item.bounds.centerX())
                    .put("centerY", item.bounds.centerY())
                controls.put(JSONObject()
                    .put("index", item.index)
                    .put("label", item.label)
                    .put("kind", item.kind)
                    .put("role", item.role)
                    .put("semantic", item.semantic)
                    .put("bounds", bounds))
            }
            val iconList = JSONArray()
            icons.forEachIndexed { index, icon ->
                val targetIndex=targetIndexFor(icon)
                iconList.put(JSONObject()
                    .put("index", index)
                    .put("label", icon.label)
                    .put("semantic", icon.semantic)
                    .put("role", roleName(icon.className))
                    .put("clickable", icon.clickable)
                    .put("targetIndex", if (targetIndex >= 0) targetIndex else JSONObject.NULL)
                    .put("bounds", normalizedBounds(
                        Rect(icon.bounds.left, icon.bounds.top, icon.bounds.right, icon.bounds.bottom),
                        viewport)))
            }
            return JSONObject()
                .put("snapshotId", id)
                .put("capturedAt", createdAt)
                .put("expiresAt", createdAt + ttlMs)
                .put("packageName", packageName)
                .put("windowId", windowId)
                .put("text", lines)
                .put("clickables", controls)
                .put("icons", iconList)
                .put("truncated", !assistantComplete)
                .toString()
        }

        fun toAssistantJson(): String {
            val targets = JSONArray()
            if (assistantComplete) clickables.forEach { item ->
                targets.put(JSONObject()
                    .put("index", item.index)
                    .put("label", item.label)
                    .put("kind", item.kind)
                    .put("role", item.role)
                    .put("semantic", item.semantic)
                    .put("region", region(item.bounds, viewport))
                    .put("bounds", normalizedBounds(item.bounds, viewport)))
            }
            val iconList = JSONArray()
            icons.forEachIndexed { index, icon ->
                val targetIndex=if(assistantComplete)targetIndexFor(icon) else -1
                iconList.put(JSONObject()
                    .put("index", index)
                    .put("label", icon.label)
                    .put("semantic", icon.semantic)
                    .put("role", roleName(icon.className))
                    .put("clickable", icon.clickable)
                    .put("region", region(Rect(icon.bounds.left, icon.bounds.top,
                        icon.bounds.right, icon.bounds.bottom), viewport))
                    .put("targetIndex", if (targetIndex >= 0) targetIndex else JSONObject.NULL)
                    .put("bounds", normalizedBounds(
                        Rect(icon.bounds.left, icon.bounds.top, icon.bounds.right, icon.bounds.bottom),
                        viewport)))
            }
            return JSONObject()
                .put("snapshotId", id)
                .put("capturedAt", createdAt)
                .put("expiresAt", createdAt + ttlMs)
                .put("packageId", packageName)
                .put("targets", targets)
                .put("icons", iconList)
                .put("truncated", !assistantComplete)
                .toString()
        }
    }

    private val lock = Any()
    private val snapshots = LinkedHashMap<String, Snapshot>()

    fun capture(root: AccessibilityNodeInfo): String? = captureSnapshot(root, includeText = true)
        ?.let { snapshot ->
            JSONObject()
                .put("snapshotId", snapshot.id)
                .put("capturedAt", snapshot.createdAt)
                .put("expiresAt", snapshot.createdAt + snapshot.ttlMs)
                .put("packageName", snapshot.packageName)
                .put("windowId", snapshot.windowId)
                .put("textLineCount", snapshot.text.size)
                .put("clickableCount", snapshot.clickables.size)
                .toString()
        }

    /**
     * Model-safe, in-memory observation. It carries bounded safe targets and
     * icon semantics, never arbitrary screen text or editable field values.
     */
    fun captureAssistant(root: AccessibilityNodeInfo): String? =
        captureSnapshot(root, includeText = false,
            ttlMs = ASSISTANT_ACTION_TTL_MS)?.toAssistantJson()

    /** Debug CLI observation of Nexa itself: safe indexed targets and icons,
     * without returning the app's chat transcript as screen text. */
    internal fun captureOwnerWindow(root: AccessibilityNodeInfo): String? =
        if (BuildConfig.DEBUG && root.packageName?.toString() == BuildConfig.APPLICATION_ID)
            captureSnapshot(root, includeText = false, allowOwnerApp = true)?.toJson()
        else null

    /** Notification shade snapshot contains only bounded icon metadata, never notification text or tap targets. */
    internal fun captureAssistantNotificationShade(root: AccessibilityNodeInfo): String? =
        if (root.packageName?.toString() == "com.android.systemui")
            captureSnapshot(root, includeText = false,
                allowNotificationShade = true, systemUiSafeMode = true)?.toAssistantJson()
        else null

    /** Debug fixture only; no React Native or provider entry point calls this. */
    internal fun captureAssistantFixtureForTest(root: AccessibilityNodeInfo): String? =
        if (BuildConfig.DEBUG && isAssistantFixturePackage(root.packageName?.toString().orEmpty()))
            captureSnapshot(root, includeText = false, allowAppFixture = true,
                ttlMs = ASSISTANT_ACTION_TTL_MS)?.toAssistantJson()
        else null

    internal fun captureAssistantSystemUiFixtureForTest(root: AccessibilityNodeInfo): String? =
        if (BuildConfig.DEBUG && isAssistantFixturePackage(root.packageName?.toString().orEmpty()))
            captureSnapshot(root, includeText = false, allowAppFixture = true,
                allowNotificationShade = true, systemUiSafeMode = true)?.toAssistantJson()
        else null

    private fun isAssistantFixturePackage(packageName:String):Boolean =
        packageName==BuildConfig.APPLICATION_ID ||
            packageName=="${BuildConfig.APPLICATION_ID}.test"

    private fun captureSnapshot(root: AccessibilityNodeInfo, includeText: Boolean,
                                allowAppFixture:Boolean=false,
                                allowNotificationShade:Boolean=false,
                                systemUiSafeMode:Boolean=false,
                                allowOwnerApp:Boolean=false,
                                ttlMs:Long=TTL_MS): Snapshot? {
        val pkg = root.packageName?.toString().orEmpty()
        val windowId = root.windowId
        if (!root.isVisibleToUser ||
            !(isExternalPackage(pkg) ||
                (allowOwnerApp && pkg == BuildConfig.APPLICATION_ID) ||
                (allowAppFixture && isAssistantFixturePackage(pkg)) ||
                (allowNotificationShade && pkg == "com.android.systemui")) ||
            windowId < 0) return null
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        if (rootBounds.isEmpty) return null

        val text = LinkedHashSet<String>()
        val clickables = LinkedHashMap<String, RawClickable>()
        val detectedIcons = ScreenIconDetector.detect(root).take(MAX_ICONS)
        val icons = if (systemUiSafeMode) detectedIcons.filter {
            NexaKeyActionSurfacePolicy.allowsNotificationShadeIcon(it.semantic)
        } else detectedIcons
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var inspected = 0
        while (queue.isNotEmpty() && inspected++ < MAX_NODES &&
            (!includeText || text.sumOf { it.length } < MAX_TEXT_CHARS) &&
            clickables.size < MAX_TARGETS * 3) {
            val node = queue.removeFirst()
            try {
                if (!node.isVisibleToUser || node.isPassword || node.isEditable) continue

                val textLabel = normalize(node.text?.toString().orEmpty())
                val descriptionLabel = normalize(node.contentDescription?.toString().orEmpty())
                val displayLabel = if (systemUiSafeMode) "" else
                    textLabel.ifEmpty { descriptionLabel }
                val label = if (systemUiSafeMode) "" else
                    safeTargetLabel(textLabel, descriptionLabel).orEmpty()
                if (includeText && displayLabel.isNotEmpty() && displayLabel !in text &&
                    text.size < MAX_TEXT_LINES) {
                    val remaining = MAX_TEXT_CHARS - text.sumOf { it.length }
                    if (remaining > 0) text.add(displayLabel.take(remaining))
                }

                if (label.isNotEmpty()) {
                    var target: AccessibilityNodeInfo? = node
                    var targetOwned = false
                    var depth = 0
                    while (target != null && depth++ < MAX_CLICKABLE_ANCESTOR_HOPS &&
                        !target.isClickable) {
                        val parent = target.parent
                        if (targetOwned) try { target.recycle() } catch (_: Exception) {}
                        target = parent
                        targetOwned = target != null
                    }
                    try {
                        if (target != null && target.isEnabled && target.isClickable &&
                            !target.isEditable && !target.isPassword) {
                            val bounds = Rect().also { target.getBoundsInScreen(it) }
                            if (!bounds.isEmpty && rootBounds.contains(bounds)) {
                                val key = listOf(label, bounds.left, bounds.top,
                                    bounds.right, bounds.bottom).joinToString(":")
                                val className = target.className?.toString().orEmpty()
                                val semantic = ScreenIconClassifier.classify(label)?.first
                                clickables.putIfAbsent(key, RawClickable(label, Rect(bounds),
                                    if (className.endsWith("ImageButton")) "icon" else "control",
                                    roleName(className), semantic))
                            }
                        }
                    } finally {
                        if (targetOwned) try { target?.recycle() } catch (_: Exception) {}
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::addLast)
                }
            } finally {
                if (node !== root) try { node.recycle() } catch (_: Exception) {}
            }
        }
        while (queue.isNotEmpty()) {
            try { queue.removeFirst().recycle() } catch (_: Exception) {}
        }

        icons.filter { !systemUiSafeMode && NexaScreenActionPolicy.permits(it) &&
            safeLabel.matches(it.label) && NexaTaskScreenObservation.safeLabel(it.label) != null
        }.forEach { icon ->
            val bounds = Rect(icon.bounds.left, icon.bounds.top,
                icon.bounds.right, icon.bounds.bottom)
            if (rootBounds.contains(bounds)) {
                val key = listOf(icon.label, bounds.left, bounds.top,
                    bounds.right, bounds.bottom).joinToString(":")
                clickables.putIfAbsent(key, RawClickable(icon.label, bounds, "icon",
                    roleName(icon.className), icon.semantic))
            }
        }

        // Identical visible labels are not safe index targets: the existing
        // label-based Nexa executor must be able to ground one unique control.
        val labelCounts = clickables.values.groupingBy {
            it.label.lowercase(java.util.Locale.ROOT)
        }.eachCount()
        val uniqueTargets = clickables.values
            .filter { labelCounts[it.label.lowercase(java.util.Locale.ROOT)] == 1 }
            .take(MAX_TARGETS)
            .mapIndexed { index, item -> Clickable(index, item.label, Rect(item.bounds),
                item.kind, item.role, item.semantic) }
        val assistantComplete = queue.isEmpty() && inspected <= MAX_NODES &&
            clickables.size < MAX_TARGETS * 3
        val snapshot = Snapshot(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            ttlMs = ttlMs,
            packageName = pkg,
            windowId = windowId,
            structureFingerprint = NexaUiActionVerifier.fingerprint(root),
            text = if (includeText) text.toList() else emptyList(),
            clickables = uniqueTargets,
            icons = icons,
            viewport = Rect(rootBounds),
            assistantComplete = assistantComplete
        )
        synchronized(lock) {
            pruneLocked(System.currentTimeMillis())
            snapshots[snapshot.id] = snapshot
            while (snapshots.size > MAX_SNAPSHOTS) {
                snapshots.remove(snapshots.keys.first())
            }
        }
        return snapshot
    }

    fun read(id: String): String? = synchronized(lock) {
        val now = System.currentTimeMillis()
        pruneLocked(now)
        snapshots[id]?.takeIf { now - it.createdAt <= it.ttlMs }?.toJson()
    }

    /** Resolve the reviewed label and geometry in the same external window.
     * Dynamic app content elsewhere must not invalidate a stable target.
     */
    fun resolveCurrent(service: ScreenAssistService, id: String, index: Int): Target? {
        val snapshot = synchronized(lock) {
            val now = System.currentTimeMillis()
            pruneLocked(now)
            snapshots[id]?.takeIf { now - it.createdAt <= it.ttlMs }
        } ?: return null
        val clickable = snapshot.clickables.getOrNull(index) ?: return null
        val root = rootForClaim(service,snapshot.claim) ?: return null
        return try {
            if (!sameWindow(root, snapshot.claim))
                null
            else Target(clickable.label, snapshot.claim, Rect(clickable.bounds))
        } catch (_: Exception) {
            null
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }

    /** A model may act only on a complete safe snapshot and its exact label. */
    fun resolveAssistantCurrent(service: ScreenAssistService, id: String,
                                index: Int, label: String): Target? {
        if (label.length !in 1..45 || !safeLabel.matches(label) ||
            unsafeLabel.containsMatchIn(label) ||
            NexaTaskScreenObservation.safeLabel(label) == null) return null
        val snapshot = synchronized(lock) {
            val now = System.currentTimeMillis()
            pruneLocked(now)
            snapshots[id]?.takeIf { now - it.createdAt <= it.ttlMs && it.assistantComplete }
        } ?: return null
        val clickable = snapshot.clickables.getOrNull(index)
            ?.takeIf { it.label.equals(label, ignoreCase = true) } ?: return null
        val root = rootForClaim(service,snapshot.claim) ?: return null
        return try {
            if (!sameWindow(root, snapshot.claim)) null
            else Target(clickable.label, snapshot.claim, Rect(clickable.bounds))
        } catch (_: Exception) { null }
        finally { try { root.recycle() } catch (_: Exception) {} }
    }

    fun isCurrent(service: ScreenAssistService, id: String): Boolean {
        val snapshot = synchronized(lock) {
            val now = System.currentTimeMillis()
            pruneLocked(now)
            snapshots[id]?.takeIf { now - it.createdAt <= it.ttlMs }
        } ?: return false
        val root = rootForClaim(service,snapshot.claim) ?: return false
        return try {
            root.packageName?.toString() == snapshot.packageName &&
                root.windowId == snapshot.windowId &&
                NexaUiActionVerifier.fingerprint(root) == snapshot.structureFingerprint
        } catch (_: Exception) {
            false
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }

    fun unchanged(root: AccessibilityNodeInfo, claim: Claim): Boolean =
        sameWindow(root, claim) &&
            NexaUiActionVerifier.fingerprint(root) == claim.structureFingerprint

    fun sameWindow(root: AccessibilityNodeInfo, claim: Claim): Boolean =
        root.packageName?.toString() == claim.packageName &&
            root.windowId == claim.windowId

    /** Reacquire the same scoped surface that produced the snapshot. Nexa's
     * own app window is actionable only in a debug build and only for a stored
     * claim naming this exact application package. */
    private fun rootForClaim(service:ScreenAssistService,
                             claim:Claim):AccessibilityNodeInfo? =
        if(BuildConfig.DEBUG && claim.packageName==BuildConfig.APPLICATION_ID)
            service.assistantOwnerWindowRoot()
        else service.externalWindowRoot()

    private fun pruneLocked(now: Long) {
        snapshots.entries.removeAll { now - it.value.createdAt > it.value.ttlMs }
    }

    private fun normalize(value: String): String = value
        .replace(Regex("[\\u0000-\\u001f\\u007f]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(500)

    /** Keep visible text as the primary target name. A clean content description
     * may replace it only when the text is absent or differs solely by leading /
     * trailing decoration, and only when neither candidate is sensitive. This
     * handles launcher labels such as "● Nexa AI" without laundering controls
     * whose visible text is unsafe (for example, "Send" with a generic desc).
     */
    private fun safeTargetLabel(text: String, contentDescription: String): String? {
        fun safe(value: String): Boolean = value.isNotEmpty() &&
            safeLabel.matches(value) && !unsafeLabel.containsMatchIn(value) &&
            NexaTaskScreenObservation.safeLabel(value) != null

        if (safe(text)) return text
        if (text.isEmpty()) return contentDescription.takeIf(::safe)
        if (unsafeLabel.containsMatchIn(text)) return null

        val undecorated = text.trim { !it.isLetterOrDigit() }
        return contentDescription.takeIf {
            safe(it) && undecorated.isNotEmpty() &&
                undecorated.equals(it, ignoreCase = true)
        }
    }

    private fun roleName(value: String): String =
        value.substringAfterLast('.').takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9]{0,39}")) }
            ?: "View"

    private fun normalizedBounds(bounds: Rect, viewport: Rect): JSONObject {
        fun x(value: Int) = (((value - viewport.left).toDouble() * 1000.0) /
            viewport.width().coerceAtLeast(1)).toInt().coerceIn(0, 1000)
        fun y(value: Int) = (((value - viewport.top).toDouble() * 1000.0) /
            viewport.height().coerceAtLeast(1)).toInt().coerceIn(0, 1000)
        return JSONObject().put("left", x(bounds.left)).put("top", y(bounds.top))
            .put("right", x(bounds.right)).put("bottom", y(bounds.bottom))
    }

    private fun region(bounds: Rect, viewport: Rect): String {
        val x = bounds.centerX() - viewport.left
        val y = bounds.centerY() - viewport.top
        val horizontal = when {
            x < viewport.width() / 3 -> "left"
            x > viewport.width() * 2 / 3 -> "right"
            else -> "center"
        }
        val vertical = when {
            y < viewport.height() / 3 -> "top"
            y > viewport.height() * 2 / 3 -> "bottom"
            else -> "middle"
        }
        return "$vertical $horizontal"
    }

    private fun isExternalPackage(value: String): Boolean =
        value.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")) &&
            value != "android" &&
            value != "com.android.systemui" &&
            value != "com.android.inputmethod.latin" &&
            value != "com.google.android.inputmethod.latin" &&
            !value.startsWith("dev.rilandev.personalaiassistant")
}
