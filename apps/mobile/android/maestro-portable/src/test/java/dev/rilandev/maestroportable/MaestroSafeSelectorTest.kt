package dev.rilandev.maestroportable

import maestro.Bounds
import maestro.TreeNode
import org.junit.Assert.*
import org.junit.Test

class MaestroSafeSelectorTest {
    private fun node(
        label: String,
        bounds: String,
        id: String = "",
        clickable: Boolean = true,
        editable: Boolean = false,
        password: Boolean = false,
        visible: Boolean = true
    ) = TreeNode(
        attributes = mutableMapOf(
            "text" to label,
            "resource-id" to id,
            "bounds" to bounds,
            "visible" to visible.toString(),
            "editable" to editable.toString(),
            "password" to password.toString()
        ),
        clickable = clickable,
        enabled = true
    )

    private fun screen(vararg nodes: TreeNode) = MaestroScreen(
        packageName = "com.example.messaging",
        windowId = 17,
        viewport = Bounds(0, 0, 400, 800),
        root = TreeNode(children = nodes.toList())
    )

    @Test fun matchesExactVisibleTextAndResourceId() {
        val s = screen(
            node("Search", "[5,10][55,50]", "com.example:id/search"),
            node("Menu", "[100,10][150,50]", "com.example:id/menu")
        )
        val found = MaestroSafeSelector.findUnique(
            s, "com.example.messaging", 17, MaestroQuery(
                text = "search", resourceId = "com.example:id/search")
        )
        assertNotNull(found)
        assertEquals(listOf(0), found!!.path)
        assertEquals(Bounds(5, 10, 50, 40), found.bounds)
    }

    @Test fun duplicatedOrUnknownTargetIsRejectedWithoutGuessing() {
        val s = screen(node("Continue", "[10,20][90,60]"),
            node("Continue", "[120,20][200,60]"))
        assertNull(MaestroSafeSelector.findUnique(
            s, "com.example.messaging", 17, MaestroQuery(text="Continue")))
        assertNull(MaestroSafeSelector.findUnique(
            s, "com.example.messaging", 17, MaestroQuery(text="Missing")))
    }

    @Test fun packageAndWindowAreBoundToSingleObservation() {
        val s = screen(node("Open", "[10,20][90,60]"))
        assertNull(MaestroSafeSelector.findUnique(
            s, "com.other.app", 17, MaestroQuery(text="Open")))
        assertNull(MaestroSafeSelector.findUnique(
            s, "com.example.messaging", 18, MaestroQuery(text="Open")))
    }

    @Test fun sensitiveHiddenAndOffscreenControlsAreExcluded() {
        val s = screen(
            node("Password", "[10,20][90,60]", password = true),
            node("Search", "[10,70][90,110]", editable = true),
            node("Submit", "[10,120][90,160]", visible = false),
            node("Next", "[10,850][90,890]")
        )
        for(label in listOf("Password", "Search", "Submit", "Next")) {
            assertNull(MaestroSafeSelector.findUnique(
                s, "com.example.messaging", 17, MaestroQuery(text=label)))
        }
    }

    @Test fun relativeBelowAnchorDistinguishesTwoMatchingControls() {
        val s = screen(
            node("Recent", "[10,100][90,125]", clickable = false),
            node("Open", "[120,20][170,60]"),
            node("Open", "[120,180][170,220]")
        )
        val found = MaestroSafeSelector.findUnique(
            s, "com.example.messaging", 17,
            MaestroQuery(text="Open", belowText="Recent")
        )
        assertEquals(listOf(2), found?.path)
    }

    @Test fun unprovenEnabledStateMustNeverSelectTarget() {
        val unsafe = TreeNode(
            attributes = mutableMapOf(
                "text" to "Open", "bounds" to "[10,20][90,60]",
                "visible" to "true", "editable" to "false", "password" to "false"
            ), clickable = true, enabled = null
        )
        val s = screen(unsafe)
        assertNull(MaestroSafeSelector.findUnique(
            s, "com.example.messaging", 17, MaestroQuery(text = "Open")
        ))
    }
}
