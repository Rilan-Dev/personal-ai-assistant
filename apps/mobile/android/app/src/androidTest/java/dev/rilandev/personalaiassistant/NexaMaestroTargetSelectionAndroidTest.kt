package dev.rilandev.personalaiassistant

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.rilandev.maestroportable.MaestroQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaMaestroTargetSelectionAndroidTest {
    private val viewport = Rect(0, 0, 400, 800)
    private fun candidate(label: String, left: Int, top: Int,
        editable: Boolean = false, password: Boolean = false,
        visible: Boolean = true, enabled: Boolean = true
    ) = NexaMaestroTapCandidate(
        verifiedLabel = label,
        resourceId = "com.example:id/" + label.lowercase(),
        bounds = Rect(left, top, left + 75, top + 40),
        visible = visible, editable = editable, password = password,
        clickable = true, enabled = enabled
    )
    private fun choose(query: MaestroQuery, targets: List<NexaMaestroTapCandidate>,
        expectedPkg: String = "com.example", observedPkg: String = "com.example",
        expectedWindow: Int = 9, observedWindow: Int = 9
    ) = NexaMaestroTargetSelection.choose(
        expectedPkg, expectedWindow, observedPkg, observedWindow, viewport, query, targets
    )

    @Test fun uniqueVisibleTargetUsesMaestroTextAndId() {
        val targets = listOf(candidate("Menu", 12, 20), candidate("Search", 120, 20))
        assertEquals(1, choose(MaestroQuery(text = "search",
            resourceId = "com.example:id/search"), targets))
    }

    @Test fun sameLabelTwiceNeverPicksAnArbitraryHitbox() {
        val targets = listOf(candidate("Search", 10, 20), candidate("Search", 160, 20))
        assertNull(choose(MaestroQuery(text = "Search"), targets))
    }

    @Test fun changedWindowOrPackageRejectsAStaleObservation() {
        val targets = listOf(candidate("Search", 10, 20))
        assertNull(choose(MaestroQuery(text = "Search"), targets,
            expectedPkg = "com.example", observedPkg = "com.other"))
        assertNull(choose(MaestroQuery(text = "Search"), targets,
            expectedWindow = 9, observedWindow = 10))
    }

    @Test fun editablePasswordAndOffscreenTargetsAreNeverGenericTaps() {
        val query = MaestroQuery(text = "Search")
        assertNull(choose(query, listOf(candidate("Search", 10, 20, editable = true))))
        assertNull(choose(query, listOf(candidate("Search", 10, 20, password = true))))
        assertNull(choose(query, listOf(candidate("Search", 10, 850))))
        assertNull(choose(query, listOf(candidate("Search", 10, 20, visible = false))))
        assertNull(choose(query, listOf(candidate("Search", 10, 20, enabled = false))))
    }
}
