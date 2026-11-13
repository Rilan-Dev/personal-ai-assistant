package dev.rilandev.personalaiassistant

import android.graphics.Rect
import dev.rilandev.maestroportable.MaestroQuery
import dev.rilandev.maestroportable.MaestroSafeSelector
import dev.rilandev.maestroportable.MaestroScreen
import maestro.Bounds
import maestro.TreeNode

/**
 * Read-only adapter from native, ALREADY-GROUNDED tap candidates to the pinned
 * Maestro Kotlin models. This is not a new device-action executor or grant.
 *
 * verifiedLabel is assigned only AFTER Nexa's existing screen-label safety
 * checks match an observed non-sensitive node. The adapter does not inspect
 * message contents, passwords, or any extra screen text.
 */
internal data class NexaMaestroTapCandidate(
    val verifiedLabel: String,
    val resourceId: String,
    val bounds: Rect,
    val visible: Boolean,
    val editable: Boolean,
    val password: Boolean,
    val clickable: Boolean,
    val enabled: Boolean
)

internal object NexaMaestroTargetSelection {
    private const val MAX_CANDIDATES = 200

    fun sameWindow(expectedPackage: String, expectedWindow: Int,
                   actualPackage: String, actualWindow: Int): Boolean =
        expectedPackage.isNotBlank() && expectedPackage == actualPackage &&
            expectedWindow >= 0 && expectedWindow == actualWindow

    /**
     * Returns only a candidate index. NexaScreenLabelTap still owns the
     * Accessibility window, one-time approval, journal and actual ACTION_CLICK.
     */
    fun choose(
        expectedPackage: String, expectedWindow: Int,
        observedPackage: String, observedWindow: Int,
        viewport: Rect, query: MaestroQuery,
        targets: List<NexaMaestroTapCandidate>,
        anchors: List<NexaMaestroTapCandidate> = emptyList()
    ): Int? {
        if (!sameWindow(expectedPackage, expectedWindow, observedPackage, observedWindow) ||
            viewport.width() <= 0 || viewport.height() <= 0 ||
            targets.isEmpty() || targets.size + anchors.size > MAX_CANDIDATES) return null

        // Anchor nodes are observation-only and cannot become action targets.
        // Their positions are compared by the verified Maestro selector.
        val observed = TreeNode(children = (targets + anchors).map { target ->
            val box = target.bounds
            TreeNode(
                attributes = mutableMapOf(
                    "text" to target.verifiedLabel,
                    "resource-id" to target.resourceId,
                    "bounds" to "[${box.left},${box.top}][${box.right},${box.bottom}]",
                    "visible" to target.visible.toString(),
                    "editable" to target.editable.toString(),
                    "password" to target.password.toString()
                ),
                clickable = target.clickable,
                enabled = target.enabled
            )
        })
        val screen = MaestroScreen(
            observedPackage, observedWindow,
            Bounds(viewport.left, viewport.top, viewport.width(), viewport.height()),
            observed
        )
        return MaestroSafeSelector.findUnique(screen, expectedPackage, expectedWindow, query)
            ?.path?.singleOrNull()?.takeIf { it in targets.indices }
    }

    /**
     * A second, hierarchy-aware read-only gate. The caller supplies a real
     * parent/child tree; only already-reviewed native candidates may be
     * returned, and a coordinate match must be unique. A hierarchy match
     * cannot itself authorize an Accessibility click.
     */
    fun chooseFromTree(
        expectedPackage: String, expectedWindow: Int,
        observedPackage: String, observedWindow: Int,
        viewport: Rect, query: MaestroQuery,
        targets: List<NexaMaestroTapCandidate>, tree: TreeNode
    ): Int? {
        if (!sameWindow(expectedPackage, expectedWindow, observedPackage, observedWindow) ||
            viewport.isEmpty || targets.isEmpty() || targets.size > MAX_CANDIDATES)
            return null
        val selected = MaestroSafeSelector.findUnique(
            MaestroScreen(observedPackage, observedWindow,
                Bounds(viewport.left, viewport.top, viewport.width(), viewport.height()), tree),
            expectedPackage, expectedWindow, query
        ) ?: return null
        return targets.withIndex().filter { (_, candidate) ->
            candidate.visible && candidate.clickable && candidate.enabled &&
                !candidate.editable && !candidate.password &&
                candidate.bounds.left == selected.bounds.x &&
                candidate.bounds.top == selected.bounds.y &&
                candidate.bounds.width() == selected.bounds.width &&
                candidate.bounds.height() == selected.bounds.height
        }.singleOrNull()?.index
    }
}
