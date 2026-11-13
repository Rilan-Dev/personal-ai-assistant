package dev.rilandev.personalaiassistant

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.rilandev.maestroportable.MaestroQuery
import maestro.TreeNode

/**
 * Ephemeral, redacted Accessibility tree for owner-reviewed Maestro selectors.
 *
 * This adapter never dispatches an action. Only a node that matches one of the
 * caller's already-grounded, safe clickable candidates can be selected. It
 * preserves real parent/child paths, unlike the old flattened adapter.
 * Neither a screen's text nor an AI response grants execution authority.
 */
internal object NexaMaestroAccessibilityHierarchy {
    private const val MAX_NODES = 180
    private const val MAX_DEPTH = 24

    fun choose(
        root: AccessibilityNodeInfo,
        expectedPackage: String,
        expectedWindow: Int,
        viewport: Rect,
        query: MaestroQuery,
        targets: List<NexaMaestroTapCandidate>
    ): Int? {
        if (root.packageName?.toString() != expectedPackage ||
            root.windowId != expectedWindow || expectedWindow < 0 ||
            !root.isVisibleToUser || viewport.isEmpty ||
            targets.isEmpty() || targets.size > MAX_NODES) return null

        val observedViewport = Rect().also { root.getBoundsInScreen(it) }
        if (observedViewport != viewport) return null

        val anchorNames = listOfNotNull(
            query.belowText, query.aboveText, query.leftOfText,
            query.rightOfText, query.childOfText
        )
        if (anchorNames.any {
                NexaTaskScreenObservation.safeLabel(it) != it
            }) return null

        // A candidate has already passed Nexa's label, class, hitbox and
        // sensitive-action checks. A tree node is NOT made actionable merely
        // because it is clickable in another application.
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int): TreeNode {
            if (++visited > MAX_NODES || depth > MAX_DEPTH)
                throw IllegalStateException("Hierarchy limit")

            val visible = node.isVisibleToUser
            val sensitive = node.isPassword || node.isEditable
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val index = if (visible && !sensitive && node.isClickable &&
                node.isEnabled && !bounds.isEmpty) {
                targets.indices.filter { i ->
                    val candidate = targets[i]
                    candidate.visible && candidate.clickable && candidate.enabled &&
                        !candidate.editable && !candidate.password &&
                        candidate.bounds == bounds &&
                        candidate.resourceId == node.viewIdResourceName.orEmpty()
                }.singleOrNull()
            } else null

            val attrs = mutableMapOf(
                "bounds" to "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]",
                "visible" to visible.toString(),
                "editable" to sensitive.toString(),
                "password" to node.isPassword.toString()
            )
            if (index != null) {
                attrs["text"] = targets[index].verifiedLabel
                attrs["resource-id"] = targets[index].resourceId
            } else if (visible && !sensitive && anchorNames.isNotEmpty()) {
                // Read only the labels relevant to this explicit owner request.
                // Do not materialize conversations, editable contents, etc.
                val label = listOf(
                    node.contentDescription?.toString(),
                    node.text?.toString()
                ).firstOrNull { raw ->
                    val safe = NexaTaskScreenObservation.safeLabel(raw)
                    safe != null && anchorNames.any {
                        it.equals(safe, ignoreCase = true)
                    }
                }
                if (label != null) attrs["text"] = label.trim()
            }

            val children = mutableListOf<TreeNode>()
            if (visible && !sensitive) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    children.add(visit(child, depth + 1))
                }
            }
            return TreeNode(
                attributes = attrs,
                children = children,
                clickable = index != null,
                enabled = node.isEnabled
            )
        }

        return try {
            val observedTree = visit(root, 0)
            NexaMaestroTargetSelection.chooseFromTree(
                expectedPackage, expectedWindow,
                expectedPackage, expectedWindow,
                viewport, query, targets, observedTree
            )
        } catch (_: Exception) {
            // An incomplete, changed, overly large or inaccessible screen is
            // never converted into an arbitrary first-match selection.
            null
        }
    }
}
