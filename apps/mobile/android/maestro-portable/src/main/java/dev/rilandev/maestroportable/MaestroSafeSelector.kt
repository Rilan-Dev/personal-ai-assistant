package dev.rilandev.maestroportable

import maestro.Bounds
import maestro.TreeNode
import java.util.ArrayDeque

/** A read-only observation, not a capability or approval to interact. */
data class MaestroScreen(
    val packageName: String,
    val windowId: Int,
    val viewport: Bounds,
    val root: TreeNode
)

/** Small, deterministic subset of Maestro tapOn selectors. No regex or scripts. */
data class MaestroQuery(
    val text: String? = null,
    val resourceId: String? = null,
    val belowText: String? = null,
    val aboveText: String? = null,
    val leftOfText: String? = null,
    val rightOfText: String? = null,
    val childOfText: String? = null,
    val minVisiblePercent: Int = 100
)

/** The caller MUST re-read and revalidate this path before any native action. */
data class MaestroSelected(val path: List<Int>, val bounds: Bounds)

/**
 * Select an unambiguous visible element from a bounded, redacted TreeNode
 * snapshot. This NEVER dispatches gestures, reads editable values or grants
 * access to an application. The live Nexa action journal remains authoritative.
 */
object MaestroSafeSelector {
    private const val MAX_NODES = 200
    private const val MAX_DEPTH = 24
    private val boundsFormat = Regex("""^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$""")

    private data class Entry(val node: TreeNode, val path: List<Int>, val bounds: Bounds?)

    private fun readable(raw: String?, max: Int): Boolean =
        raw == null || (raw.trim().length in 2..max &&
            raw.none { it.code < 32 || it.code == 127 })

    private fun box(raw: String?): Bounds? {
        val m = raw?.let { boundsFormat.matchEntire(it) } ?: return null
        val coordinates = (1..4).map { m.groupValues[it].toIntOrNull() ?: return null }
        val width = coordinates[2].toLong() - coordinates[0]
        val height = coordinates[3].toLong() - coordinates[1]
        if (width !in 1..Int.MAX_VALUE.toLong() || height !in 1..Int.MAX_VALUE.toLong())
            return null
        return Bounds(coordinates[0], coordinates[1], width.toInt(), height.toInt())
    }

    /**
     * Unlike an unconstrained UI-test selector, the default requires 100%
     * viewport visibility. Lower thresholds are explicit read-only queries:
     * execution still rechecks the full current Accessibility hitbox.
     */
    private fun within(child: Bounds?, viewport: Bounds, minPercent: Int): Boolean {
        if (child == null || minPercent !in 1..100 ||
            child.width <= 0 || child.height <= 0 ||
            viewport.width <= 0 || viewport.height <= 0) return false
        val left = maxOf(child.x.toLong(), viewport.x.toLong())
        val top = maxOf(child.y.toLong(), viewport.y.toLong())
        val right = minOf(child.x.toLong() + child.width,
            viewport.x.toLong() + viewport.width)
        val bottom = minOf(child.y.toLong() + child.height,
            viewport.y.toLong() + viewport.height)
        if (right <= left || bottom <= top) return false
        val visibleArea = (right - left).toDouble() * (bottom - top).toDouble()
        val totalArea = child.width.toDouble() * child.height.toDouble()
        return visibleArea / totalArea + 1e-10 >= minPercent / 100.0
    }

    private fun visibleSafe(entry: Entry, viewport: Bounds, minimum: Int = 100): Boolean =
        entry.node.attributes["visible"] == "true" &&
        entry.node.attributes["password"] != "true" &&
        entry.node.attributes["editable"] != "true" &&
        within(entry.bounds, viewport, minimum)

    private fun sameRow(a: Bounds?, b: Bounds?): Boolean {
        if (a == null || b == null) return false
        val start = maxOf(a.y.toLong(), b.y.toLong())
        val end = minOf(a.y.toLong() + a.height, b.y.toLong() + b.height)
        return end > start
    }

    private fun label(node: TreeNode, expected: String): Boolean =
        listOf("text", "content-desc", "label").any { key ->
            node.attributes[key]?.trim()?.equals(expected, ignoreCase = true) == true
        }

    private fun walk(root: TreeNode): List<Entry>? {
        val pending = ArrayDeque<Pair<TreeNode, List<Int>>>()
        pending.addLast(root to emptyList())
        val out = ArrayList<Entry>()
        while (pending.isNotEmpty()) {
            if (out.size >= MAX_NODES) return null
            val (node, path) = pending.removeLast()
            if (path.size > MAX_DEPTH || pending.size + out.size + node.children.size > MAX_NODES)
                return null
            out.add(Entry(node, path, box(node.attributes["bounds"])))
            for (index in node.children.indices.reversed()) {
                pending.addLast(node.children[index] to (path + index))
            }
        }
        return out
    }

    fun findUnique(
        screen: MaestroScreen,
        expectedPackage: String,
        expectedWindowId: Int,
        query: MaestroQuery
    ): MaestroSelected? {
        if (expectedPackage.isBlank() || screen.packageName != expectedPackage ||
            screen.windowId != expectedWindowId || screen.windowId < 0 ||
            query.minVisiblePercent !in 1..100 ||
            !readable(query.text, 80) || !readable(query.resourceId, 160) ||
            !readable(query.belowText, 80) || !readable(query.aboveText, 80) ||
            !readable(query.leftOfText, 80) || !readable(query.rightOfText, 80) ||
            !readable(query.childOfText, 80)) return null

        val requested = query.text?.trim()?.takeIf { it.isNotEmpty() }
        val id = query.resourceId?.trim()?.takeIf { it.isNotEmpty() }
        if (requested == null && id == null) return null
        val entries = walk(screen.root) ?: return null

        // All named anchors must resolve to one fully visible and non-sensitive
        // element. No first-match rule or indexed guess is allowed.
        fun anchor(value: String?): Entry? {
            val expected = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.filter {
                visibleSafe(it, screen.viewport) && label(it.node, expected)
            }.singleOrNull()
        }
        val anchors = listOf(query.belowText, query.aboveText, query.leftOfText,
            query.rightOfText, query.childOfText)
        if (anchors.any { it != null && it.isBlank() }) return null
        val resolved = anchors.map { if (it == null) null else anchor(it) }
        if (anchors.indices.any { anchors[it] != null && resolved[it] == null })
            return null

        val below = resolved[0]?.bounds
        val above = resolved[1]?.bounds
        val leftOf = resolved[2]?.bounds
        val rightOf = resolved[3]?.bounds
        val parent = resolved[4]
        val matches = entries.filter { entry ->
            val box = entry.bounds
            visibleSafe(entry, screen.viewport, query.minVisiblePercent) &&
            entry.node.clickable == true && entry.node.enabled == true &&
            (requested == null || label(entry.node, requested)) &&
            (id == null || entry.node.attributes["resource-id"] == id) &&
            (below == null || box!!.y.toLong() >= below.y.toLong() + below.height) &&
            (above == null || box!!.y.toLong() + box.height <= above.y.toLong()) &&
            (leftOf == null || (box!!.x.toLong() + box.width <= leftOf.x.toLong() &&
                sameRow(box, leftOf))) &&
            (rightOf == null || (box!!.x.toLong() >= rightOf.x.toLong() + rightOf.width &&
                sameRow(box, rightOf))) &&
            (parent == null || (entry.path.size > parent.path.size &&
                entry.path.take(parent.path.size) == parent.path))
        }
        return matches.singleOrNull()?.let {
            MaestroSelected(it.path.toList(), it.bounds!!)
        }
    }

}
