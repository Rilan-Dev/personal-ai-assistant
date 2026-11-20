package dev.rilandev.personalaiassistant

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Bounded, read-only accessibility hierarchy for an explicitly invoked QA
 * tool. IDs are snapshot-local child-index paths; Android resource/unique IDs
 * are included when the active app exposes them.
 */
internal object NexaAccessibilityHierarchySnapshot {
    private const val MAX_NODES = 512
    private const val MAX_NODE_TEXT_CHARS = 700
    private const val MAX_TOTAL_TEXT_CHARS = 40_000
    private const val MAX_ACTIONS_PER_NODE = 12
    private const val MAX_JSON_CHARS = 450_000

    private data class Pending(
        val node: AccessibilityNodeInfo,
        val id: String,
        val parentId: String?,
        val owned: Boolean
    )

    private data class NodeValue(
        val id: String,
        val parentId: String?,
        val className: String,
        val resourceId: String?,
        val uniqueId: String?,
        val text: String?,
        val contentDescription: String?,
        val hintText: String?,
        val valueRedacted: Boolean,
        val bounds: Rect?,
        val visible: Boolean,
        val enabled: Boolean,
        val clickable: Boolean,
        val longClickable: Boolean,
        val focusable: Boolean,
        val focused: Boolean,
        val accessibilityFocused: Boolean,
        val editable: Boolean,
        val password: Boolean,
        val scrollable: Boolean,
        val checkable: Boolean,
        val checked: Boolean,
        val selected: Boolean,
        val heading: Boolean,
        val contextClickable: Boolean,
        val actions: List<Pair<Int, String?>>
    )

    fun capture(root: AccessibilityNodeInfo): String? = capture(root, allowAppFixture = false)

    /** Debug instrumentation only; the provider and CLI call [capture]. */
    internal fun captureFixtureForTest(root: AccessibilityNodeInfo): String? =
        if (BuildConfig.DEBUG && isFixturePackage(root.packageName?.toString().orEmpty()))
            capture(root, allowAppFixture = true)
        else null

    private fun capture(root: AccessibilityNodeInfo, allowAppFixture: Boolean): String? {
        val packageName = root.packageName?.toString().orEmpty()
        if (!root.isVisibleToUser ||
            !(isExternalPackage(packageName) ||
                (allowAppFixture && isFixturePackage(packageName)))) return null
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        if (root.windowId < 0 || rootBounds.isEmpty) return null

        val visited = mutableListOf<NodeValue>()
        val queue = java.util.ArrayDeque<Pending>()
        queue.add(Pending(root, "0", null, owned = false))
        var truncated = false
        var textRemaining = MAX_TOTAL_TEXT_CHARS
        var textTruncated = false

        fun boundedText(raw: CharSequence?): String? {
            val normalized = normalize(raw?.toString().orEmpty())
            if (normalized.isEmpty()) return null
            if (textRemaining <= 0) {
                textTruncated = true
                return null
            }
            val value = normalized.take(minOf(MAX_NODE_TEXT_CHARS, textRemaining))
            if (value.length < normalized.length) textTruncated = true
            textRemaining -= value.length
            return value
        }

        while (queue.isNotEmpty()) {
            if (visited.size >= MAX_NODES) {
                truncated = true
                break
            }
            val current = queue.removeFirst()
            val node = current.node
            try {
                val visible = node.isVisibleToUser
                val editable = node.isEditable
                val password = node.isPassword
                val redactValue = editable || password || !visible
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                    .takeUnless { it.isEmpty }
                val className = node.className?.toString()?.take(180).orEmpty()
                val safeHint = if (visible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    boundedText(node.hintText) else null
                val safeText = if (redactValue) null else boundedText(node.text)
                val safeDescription = if (redactValue) null else boundedText(node.contentDescription)
                val resourceId = try { node.viewIdResourceName?.take(240) }
                    catch (_: Exception) { null }
                val uniqueId = if (Build.VERSION.SDK_INT >= 33) {
                    try { node.uniqueId?.take(240) } catch (_: Exception) { null }
                } else null
                val actionList = try {
                    node.actionList.take(MAX_ACTIONS_PER_NODE).map { action ->
                        action.id to action.label?.toString()?.let(::normalize)?.take(120)
                    }
                } catch (_: Exception) { emptyList() }

                visited.add(NodeValue(
                    id = current.id,
                    parentId = current.parentId,
                    className = className,
                    resourceId = resourceId,
                    uniqueId = uniqueId,
                    text = safeText,
                    contentDescription = safeDescription,
                    hintText = safeHint,
                    valueRedacted = redactValue,
                    bounds = bounds,
                    visible = visible,
                    enabled = node.isEnabled,
                    clickable = node.isClickable,
                    longClickable = node.isLongClickable,
                    focusable = node.isFocusable,
                    focused = node.isFocused,
                    accessibilityFocused = node.isAccessibilityFocused,
                    editable = editable,
                    password = password,
                    scrollable = node.isScrollable,
                    checkable = node.isCheckable,
                    checked = node.isChecked,
                    selected = node.isSelected,
                    heading = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isHeading,
                    contextClickable = node.isContextClickable,
                    actions = actionList
                ))

                val childCount = node.childCount
                if (childCount > MAX_NODES) truncated = true
                for (index in 0 until minOf(childCount, MAX_NODES)) {
                    val child = try { node.getChild(index) } catch (_: Exception) { null } ?: continue
                    if (visited.size + queue.size < MAX_NODES) {
                        queue.add(Pending(child, "${current.id}.$index", current.id, owned = true))
                    } else {
                        truncated = true
                        try { child.recycle() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
                truncated = true
            } finally {
                if (current.owned) try { node.recycle() } catch (_: Exception) {}
            }
        }
        while (queue.isNotEmpty()) {
            try { queue.removeFirst().node.recycle() } catch (_: Exception) {}
        }

        val snapshotId = UUID.randomUUID().toString()
        val capturedAt = System.currentTimeMillis()
        fun encode(): String {
            val childIds = visited.groupBy { it.parentId }
            val nodes = JSONArray()
            for (item in visited) {
                val children = JSONArray()
                childIds[item.id].orEmpty().forEach { children.put(it.id) }
                val bounds = item.bounds?.let {
                    JSONObject()
                        .put("left", it.left).put("top", it.top)
                        .put("right", it.right).put("bottom", it.bottom)
                        .put("centerX", it.centerX()).put("centerY", it.centerY())
                } ?: JSONObject.NULL
                val actions = JSONArray()
                item.actions.forEach { (id, label) ->
                    actions.put(JSONObject().put("id", id)
                        .put("label", label ?: JSONObject.NULL))
                }
                val role = item.className.substringAfterLast('.')
                    .takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9]{0,39}")) } ?: "View"
                nodes.put(JSONObject()
                    .put("id", item.id)
                    .put("parentId", item.parentId ?: JSONObject.NULL)
                    .put("childIds", children)
                    .put("className", item.className)
                    .put("role", role)
                    .put("resourceId", item.resourceId ?: JSONObject.NULL)
                    .put("uniqueId", item.uniqueId ?: JSONObject.NULL)
                    .put("text", item.text ?: JSONObject.NULL)
                    .put("contentDescription", item.contentDescription ?: JSONObject.NULL)
                    .put("hintText", item.hintText ?: JSONObject.NULL)
                    .put("valueRedacted", item.valueRedacted)
                    .put("bounds", bounds)
                    .put("visible", item.visible)
                    .put("enabled", item.enabled)
                    .put("clickable", item.clickable)
                    .put("longClickable", item.longClickable)
                    .put("focusable", item.focusable)
                    .put("focused", item.focused)
                    .put("accessibilityFocused", item.accessibilityFocused)
                    .put("editable", item.editable)
                    .put("password", item.password)
                    .put("scrollable", item.scrollable)
                    .put("checkable", item.checkable)
                    .put("checked", item.checked)
                    .put("selected", item.selected)
                    .put("heading", item.heading)
                    .put("contextClickable", item.contextClickable)
                    .put("imageLike", item.className.contains("Image", ignoreCase = true))
                    .put("actions", actions))
            }
            return JSONObject()
                .put("schemaVersion", 1)
                .put("snapshotId", snapshotId)
                .put("capturedAt", capturedAt)
                .put("expiresAt", capturedAt + 30_000L)
                .put("packageName", packageName)
                .put("windowId", root.windowId)
                .put("viewport", JSONObject()
                    .put("left", rootBounds.left).put("top", rootBounds.top)
                    .put("right", rootBounds.right).put("bottom", rootBounds.bottom)
                    .put("width", rootBounds.width()).put("height", rootBounds.height()))
                .put("coordinateSystem", JSONObject()
                    .put("origin", "top-left")
                    .put("xDirection", "right")
                    .put("yDirection", "down")
                    .put("unit", "screen-pixels"))
                .put("nodeCount", visited.size)
                .put("truncated", truncated)
                .put("textTruncated", textTruncated)
                .put("editableValuePolicy", "editable and password values are redacted; field hints and widget metadata remain")
                .put("nodes", nodes)
                .toString()
        }

        var encoded = encode()
        while (encoded.length > MAX_JSON_CHARS && visited.size > 1) {
            visited.removeAt(visited.lastIndex)
            truncated = true
            encoded = encode()
        }
        return encoded
    }

    private fun normalize(value: String): String = value
        .replace(Regex("[\\u0000-\\u001f\\u007f]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isExternalPackage(value: String): Boolean =
        value.isNotBlank() && value != BuildConfig.APPLICATION_ID &&
            value != "android" && value != "com.android.systemui" &&
            value.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}"))

    private fun isFixturePackage(value: String): Boolean =
        value == BuildConfig.APPLICATION_ID || value == "${BuildConfig.APPLICATION_ID}.test"
}
