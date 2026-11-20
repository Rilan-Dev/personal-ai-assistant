package dev.rilandev.personalaiassistant

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaScreenScrollActionPolicyAndroidTest {
    @Test fun verticalDirectionsPreferSpatialActionsAndKeepLogicalFallbacks() {
        val up=AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val down=AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        val backward=AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val forward=AccessibilityNodeInfo.ACTION_SCROLL_FORWARD

        assertEquals(up,NexaScreenScroll.selectScrollAction("up",setOf(up,backward)))
        assertEquals(backward,NexaScreenScroll.selectScrollAction("up",setOf(backward)))
        assertEquals(down,NexaScreenScroll.selectScrollAction("down",setOf(down,forward)))
        assertEquals(forward,NexaScreenScroll.selectScrollAction("down",setOf(forward)))
    }

    @Test fun scrollDirectionRejectsOppositeActionsAndUnsupportedContainers() {
        val up=AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val down=AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        val backward=AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val forward=AccessibilityNodeInfo.ACTION_SCROLL_FORWARD

        assertNull(NexaScreenScroll.selectScrollAction("up",setOf(down,forward)))
        assertNull(NexaScreenScroll.selectScrollAction("down",setOf(up,backward)))
        assertNull(NexaScreenScroll.selectScrollAction("diagonal",setOf(up,down,backward,forward)))
    }

    @Test fun horizontalDirectionsUseOnlyTheirSpatialAction() {
        val left=AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
        val right=AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
        assertEquals(left,NexaScreenScroll.selectScrollAction("left",setOf(left,right)))
        assertEquals(right,NexaScreenScroll.selectScrollAction("right",setOf(left,right)))
    }
}
