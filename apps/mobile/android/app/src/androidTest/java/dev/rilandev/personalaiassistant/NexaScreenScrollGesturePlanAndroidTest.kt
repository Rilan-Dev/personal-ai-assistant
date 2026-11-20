package dev.rilandev.personalaiassistant

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaScreenScrollGesturePlanAndroidTest {
    private val appBounds = Rect(0, 0, 720, 1600)

    @Test fun allDirectionsPlanOneSwipeWithExpectedFingerTravel() {
        val down = plan("down")
        val up = plan("up")
        val left = plan("left")
        val right = plan("right")

        assertTrue(down.startY > down.endY)
        assertTrue(up.startY < up.endY)
        assertTrue(left.startX > left.endX)
        assertTrue(right.startX < right.endX)
        listOf(down, up, left, right).forEach { swipe ->
            assertTrue(swipe.startX in 24f..696f)
            assertTrue(swipe.endX in 24f..696f)
            assertTrue(swipe.startY in 32f..1568f)
            assertTrue(swipe.endY in 32f..1568f)
        }
    }

    @Test fun verticalSwipeRoutesAroundTheVisibleFloatingBubble() {
        val bubble = Rect(580, 150, 710, 280)
        val swipe = NexaScreenScrollGesturePlanner.plan(appBounds, "down", listOf(bubble), 1f)

        assertNotNull(swipe)
        assertFalse(intersectsStroke(swipe!!, bubble, 12))
    }

    @Test fun blockedViewportAndInvalidRequestsDoNotProduceGestures() {
        val fullWidthBlock = Rect(0, 500, 720, 1000)
        assertNull(NexaScreenScrollGesturePlanner.plan(appBounds, "down", listOf(fullWidthBlock), 1f))
        assertNull(NexaScreenScrollGesturePlanner.plan(Rect(0, 0, 100, 200), "down", emptyList(), 1f))
        assertNull(NexaScreenScrollGesturePlanner.plan(appBounds, "diagonal", emptyList(), 1f))
        assertNull(NexaScreenScrollGesturePlanner.plan(appBounds, "down", emptyList(), 0f))
    }

    private fun plan(direction: String): NexaScreenScrollGesturePlan =
        NexaScreenScrollGesturePlanner.plan(appBounds, direction, emptyList(), 1f)
            ?: error("Expected a safe $direction swipe plan")

    private fun intersectsStroke(swipe: NexaScreenScrollGesturePlan, block: Rect, margin: Int): Boolean {
        val expanded = Rect(block).apply { inset(-margin, -margin) }
        val path = Rect(
            minOf(swipe.startX, swipe.endX).toInt(),
            minOf(swipe.startY, swipe.endY).toInt(),
            maxOf(swipe.startX, swipe.endX).toInt() + 1,
            maxOf(swipe.startY, swipe.endY).toInt() + 1,
        )
        return Rect.intersects(path, expanded)
    }
}
