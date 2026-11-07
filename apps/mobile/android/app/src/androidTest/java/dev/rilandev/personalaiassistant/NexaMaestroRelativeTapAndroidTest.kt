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
}
