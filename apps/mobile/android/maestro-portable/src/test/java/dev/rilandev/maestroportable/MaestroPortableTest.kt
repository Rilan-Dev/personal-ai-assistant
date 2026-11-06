package dev.rilandev.maestroportable

import maestro.Bounds
import maestro.Point
import maestro.TreeNode
import maestro.ScrollDirection
import maestro.SwipeDirection
import maestro.toSwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class MaestroPortableTest {
    @Test fun boundsCenterUsesDirectUpstreamGeometry() {
        assertEquals(Point(15, 26), Bounds(10, 20, 11, 13).center())
    }

    @Test fun upstreamTreeTraversalRetainsEveryChild() {
        val root = TreeNode(
            attributes = mutableMapOf("text" to "parent"),
            children = listOf(
                TreeNode(attributes = mutableMapOf("text" to "a")),
                TreeNode(attributes = mutableMapOf("text" to "b"))
            )
        )
        assertEquals(listOf("parent", "a", "b"), root.aggregate().map { it.attributes["text"] })
    }

    @Test fun scrollDownMeansFingerSwipesUp() {
        assertEquals(SwipeDirection.UP, ScrollDirection.DOWN.toSwipeDirection())
    }
}
