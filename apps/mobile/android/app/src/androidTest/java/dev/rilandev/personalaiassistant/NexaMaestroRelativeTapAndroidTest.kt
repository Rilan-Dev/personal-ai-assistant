package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaMaestroRelativeTapAndroidTest {
    @Test fun parsesOwnerRequestedRelativeControlWithoutExecuting() {
        val found=NexaMaestroRelativeTap.parse("Hey Nexa, please tap Open below Recent")
        assertNotNull(found)
        assertEquals("Open",found?.target)
        assertEquals("Recent",found?.anchor)
        assertEquals("below",found?.direction)
        assertEquals(found,NexaMaestroRelativeTap.fromAction(found!!.toAction()))
    }

    @Test fun acceptsOnlyFourSimpleGroundedRelationships() {
        for((word,direction) in listOf(
            "below" to "below","above" to "above",
            "left of" to "left","right of" to "right")) {
            assertEquals(direction,NexaMaestroRelativeTap.parse(
                "tap Open $word Recent")?.direction)
        }
    }

    @Test fun explicitNestedTapRequiresBoundedOwnerReviewAndRealAncestry() {
        val parsed=NexaMaestroRelativeTap.parse("tap Open inside Recent")
        assertNotNull(parsed)
        assertEquals("child",parsed?.direction)
        assertEquals(parsed,NexaMaestroRelativeTap.fromAction(parsed!!.toAction()))
        assertTrue(NexaMaestroRelativeTap.looksRelative("tap Open inside Password"))
        assertNull(NexaMaestroRelativeTap.parse("tap Open inside Password"))
        assertNull(NexaMaestroRelativeTap.parse("tap Send inside Recent"))
        assertNull(NexaMaestroRelativeTap.parse("tap Open inside Recent and pay"))
        assertNull(NexaMaestroRelativeTap.fromAction("tap_relative:child|Open|Account"))
        assertNull(NexaMaestroRelativeTap.fromAction("tap_relative:child|Open|Recent|extra"))
    }

    @Test fun rejectsSensitiveAmbiguousAndForgedActions() {
        val rejected=listOf(
            "tap Send below Recent",
            "tap Open below Password",
            "tap Open below Recent and send it",
            "tap Open below Recent|evil",
            "tap Open below Recent\nthen tap Pay",
            "tap Open below Open below Recent",
            "tap Open below 123456",
            "tap Open below Account",
            "tap Open below Allow",
            "tap Open below Recent followed by delete"
        )
        rejected.forEach { assertNull(it,NexaMaestroRelativeTap.parse(it)) }
        assertNull(NexaMaestroRelativeTap.fromAction("tap_relative:below|Open|Recent|evil"))
        assertNull(NexaMaestroRelativeTap.fromAction("tap_relative:below|Send|Recent"))
        assertNull(NexaMaestroRelativeTap.fromAction("tap_relative:unknown|Open|Recent"))
    }
    @Test fun malformedRelativeInstructionsNeverDowngradeToGenericTap() {
        for(text in listOf(
            "tap Open below Account",
            "tap Open below Recent and send it",
            "tap Open left of Password",
            "tap Pay right of Recent"
        )) {
            assertTrue(text,NexaMaestroRelativeTap.looksRelative(text))
            assertNull(text,NexaMaestroRelativeTap.parse(text))
        }
        assertFalse(NexaMaestroRelativeTap.looksRelative("tap Search"))
        assertFalse(NexaMaestroRelativeTap.looksRelative("check my battery"))
    }

}
