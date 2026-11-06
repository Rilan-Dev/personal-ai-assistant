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
    val belowText: String? = null
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

    private fun within(child: Bounds?, viewport: Bounds): Boolean {
        if (child == null || viewport.width <= 0 || viewport.height <= 0) return false
        val xEnd = child.x.toLong() + child.width - 1
        val yEnd = child.y.toLong() + child.height - 1
        if (xEnd > Int.MAX_VALUE || yEnd > Int.MAX_VALUE) return false
        return viewport.contains(child.x, child.y) &&
            viewport.contains(xEnd.toInt(), yEnd.toInt())
    }

    private fun visibleSafe(entry: Entry, viewport: Bounds): Boolean =
        entry.node.attributes["visible"] == "true" &&
        entry.node.attributes["password"] != "true" &&
        entry.node.attributes["editable"] != "true" &&
        within(entry.bounds, viewport)

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
            !readable(query.text, 80) || !readable(query.resourceId, 160) ||
            !readable(query.belowText, 80)) return null
        val requested = query.text?.trim()?.takeIf { it.isNotEmpty() }
        val id = query.resourceId?.trim()?.takeIf { it.isNotEmpty() }
        if (requested == null && id == null) return null

        val entries = walk(screen.root) ?: return null
        val anchors = query.belowText?.trim()?.let { anchor ->
            entries.filter { visibleSafe(it, screen.viewport) && label(it.node, anchor) }
        }
        if (anchors != null && anchors.size != 1) return null
        val bottom = anchors?.singleOrNull()?.bounds?.let { it.y.toLong() + it.height }

        val matches = entries.filter { entry ->
            visibleSafe(entry, screen.viewport) &&
            entry.node.clickable == true && entry.node.enabled == true &&
            (requested == null || label(entry.node, requested)) &&
            (id == null || entry.node.attributes["resource-id"] == id) &&
            (bottom == null || entry.bounds!!.y.toLong() >= bottom)
        }
        return matches.singleOrNull()?.let { MaestroSelected(it.path.toList(), it.bounds!!) }
    }
}
