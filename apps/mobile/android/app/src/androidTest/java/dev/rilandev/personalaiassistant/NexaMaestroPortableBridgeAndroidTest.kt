package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.rilandev.maestroportable.MaestroQuery
import dev.rilandev.maestroportable.MaestroSafeSelector
import dev.rilandev.maestroportable.MaestroScreen
import maestro.Bounds
import maestro.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaMaestroPortableBridgeAndroidTest {
    @Test fun directUpstreamModelsAreConsumableByNexaAndroidTestApk() {
        val root = TreeNode(children = listOf(TreeNode(
            attributes = mutableMapOf(
                "text" to "Search", "resource-id" to "com.example:id/search",
                "bounds" to "[10,20][70,70]", "visible" to "true"
            ),
            clickable = true, enabled = true
        )))
        val screen = MaestroScreen("com.example", 21, Bounds(0, 0, 500, 900), root)
        val selected = MaestroSafeSelector.findUnique(
            screen, "com.example", 21,
            MaestroQuery(text = "Search", resourceId = "com.example:id/search")
        )
        assertEquals(listOf(0), selected?.path)
        assertEquals(Bounds(10, 20, 60, 50), selected?.bounds)
    }
}
