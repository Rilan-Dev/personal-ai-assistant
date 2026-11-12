package dev.rilandev.personalaiassistant

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.rilandev.maestroportable.MaestroQuery
import maestro.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Real tree-path semantics with NO simulated click or elevated permissions. */
@RunWith(AndroidJUnit4::class)
class NexaMaestroHierarchyGateAndroidTest {
    private val viewport = Rect(0, 0, 400, 800)
    private val targets = listOf(
        NexaMaestroTapCandidate("Open", "com.example:id/open",
            Rect(120, 110, 220, 150), true, false, false, true, true),
        NexaMaestroTapCandidate("Open", "com.example:id/open",
            Rect(120, 330, 220, 370), true, false, false, true, true)
    )

    private fun node(label: String, bounds: String, clickable: Boolean = false,
                     children: List<TreeNode> = emptyList()): TreeNode = TreeNode(
        attributes = mutableMapOf(
            "text" to label,
            "resource-id" to if (clickable) "com.example:id/open" else "",
            "bounds" to bounds,
            "visible" to "true",
            "password" to "false",
            "editable" to "false"
        ),
        children = children,
        clickable = clickable,
        enabled = true
    )

    private fun tree(): TreeNode = TreeNode(children = listOf(
        node("Recent", "[0,50][350,210]", children = listOf(
            node("Open", "[120,110][220,150]", clickable = true)
        )),
        node("Archived", "[0,260][350,440]", children = listOf(
            node("Open", "[120,330][220,370]", clickable = true)
        ))
    ))

    private fun choose(query: MaestroQuery, root: TreeNode = tree(),
                       observedWindow: Int = 8): Int? =
        NexaMaestroTargetSelection.chooseFromTree(
            "com.example", 8, "com.example", observedWindow,
            viewport, query, targets, root
        )

    @Test fun actualAncestryDisambiguatesIdenticalButtons() {
        assertNull(choose(MaestroQuery(text = "Open")))
        assertEquals(0, choose(MaestroQuery(text = "Open",
            childOfText = "Recent")))
        assertEquals(1, choose(MaestroQuery(text = "Open",
            childOfText = "Archived")))
    }

    @Test fun coordinateProximityCannotImpersonateHierarchy() {
        val flat = TreeNode(children = listOf(
            node("Recent", "[0,50][350,210]"),
            node("Open", "[120,110][220,150]", clickable = true),
            node("Open", "[120,330][220,370]", clickable = true)
        ))
        assertNull(choose(MaestroQuery(text = "Open", childOfText = "Recent"), flat))
        // A previously selected path is not an authority when the hitbox moves.
        val moved = TreeNode(children = listOf(
            node("Recent", "[0,50][350,210]", children = listOf(
                node("Open", "[125,110][225,150]", clickable = true)
            ))
        ))
        assertNull(choose(MaestroQuery(text = "Open", childOfText = "Recent"), moved))
    }

    @Test fun duplicateAnchorAndChangedWindowCannotChoose() {
        val repeated = TreeNode(children = listOf(
            node("Recent", "[0,50][350,210]", children = listOf(
                node("Open", "[120,110][220,150]", clickable = true)
            )),
            node("Recent", "[0,260][350,440]", children = listOf(
                node("Open", "[120,330][220,370]", clickable = true)
            ))
        ))
        assertNull(choose(MaestroQuery(text = "Open", childOfText = "Recent"), repeated))
        assertNull(choose(MaestroQuery(text = "Open",
            childOfText = "Recent"), observedWindow = 9))
    }
}
