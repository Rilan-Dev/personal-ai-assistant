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
    @Test fun relativeAboveAndRightResolveOnlyOneControl() {
        val s = screen(
            node("Footer", "[100,500][300,530]", clickable=false),
            node("Open", "[110,420][180,460]"),
            node("Open", "[110,550][180,590]"),
            node("Folder", "[20,430][70,460]", clickable=false)
        )
        assertEquals(listOf(1), MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(
                text="Open",aboveText="Footer"))?.path)
        assertEquals(listOf(1), MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(
                text="Open",rightOfText="Folder"))?.path)
    }

    @Test fun hierarchyScopedChildSelectionRequiresUniqueVisibleParent() {
        val menu = node("Account", "[5,60][370,240]", clickable=false)
            .copy(children=listOf(node("Edit", "[40,100][125,145]")))
        val profile = node("Profile", "[5,290][370,460]", clickable=false)
            .copy(children=listOf(node("Edit", "[40,330][125,375]")))
        val s = screen(menu,profile)
        assertNull(MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(text="Edit")))
        assertEquals(listOf(0,0),MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(text="Edit",childOfText="Account"))?.path)
        assertEquals(listOf(1,0),MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(text="Edit",childOfText="Profile"))?.path)
    }

    @Test fun duplicateOrSensitiveRelativeAnchorsFailClosed() {
        val s=screen(
            node("Options","[10,10][140,40]",clickable=false),
            node("Options","[10,50][140,80]",clickable=false),
            node("Open","[160,90][250,140]"),
            node("Secret","[10,180][140,210]",clickable=false,password=true)
        )
        assertNull(MaestroSafeSelector.findUnique(s,"com.example.messaging",17,
            MaestroQuery(text="Open",belowText="Options")))
        assertNull(MaestroSafeSelector.findUnique(s,"com.example.messaging",17,
            MaestroQuery(text="Open",rightOfText="Secret")))
        assertNull(MaestroSafeSelector.findUnique(s,"com.example.messaging",17,
            MaestroQuery(text="Open",childOfText="Missing")))
    }

    @Test fun minimumVisibilityThresholdIsExplicitAndBounded() {
        val s=screen(node("Open","[50,770][150,830]"))
        assertNull(MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(text="Open")))
        assertNotNull(MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(text="Open",minVisiblePercent=50)))
        assertNull(MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(text="Open",minVisiblePercent=51)))
        assertNull(MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(text="Open",minVisiblePercent=0)))
        assertNull(MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(text="Open",minVisiblePercent=101)))
    }

    @Test fun conflictingRelationsCannotProduceAnActionTarget() {
        val s=screen(node("Top","[10,100][90,130]",clickable=false),
            node("Open","[120,160][200,200]"))
        assertNull(MaestroSafeSelector.findUnique(
            s,"com.example.messaging",17,MaestroQuery(
                text="Open",belowText="Top",aboveText="Top")))
    }

}
