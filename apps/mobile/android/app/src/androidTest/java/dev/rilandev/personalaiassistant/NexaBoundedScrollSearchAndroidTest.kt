package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaBoundedScrollSearchAndroidTest {
    private val pkg = "com.example.messages"
    private val window = 12
    private val generation = 7L
    private val first = "scroll-11111111-aaaa-4444-8888-111111111111"
    private val second = "scroll-22222222-aaaa-4444-8888-222222222222"

    @Test fun strictOwnerGrammarRejectsUnsafeAndOversizedLabels() {
        val request = NexaBoundedScrollSearch.parse("Please scroll down until Search")
        assertEquals("down", request?.direction)
        assertEquals("Search", request?.label)
        assertNull(NexaBoundedScrollSearch.parse("scroll down until Send"))
        assertNull(NexaBoundedScrollSearch.parse("scroll right until OTP"))
        assertNull(NexaBoundedScrollSearch.parse("scroll down until https://example.com"))
        assertNull(NexaBoundedScrollSearch.parse("scroll down until 12345678"))
        assertNull(NexaBoundedScrollSearch.parse("scroll down then tap Search"))
    }

    @Test fun oneScrollRequiresEvidenceBeforeAnotherCanBeConfirmed() {
        val guide = NexaBoundedScrollSearch()
        val request = NexaBoundedScrollSearch.parse("scroll down until Search")!!
        assertTrue(guide.start(request, pkg, window, generation, 1000L))
        assertTrue(guide.confirmable(pkg, window, generation, 1050L))
        assertTrue(guide.onApprovedScroll(first, pkg, window, generation, 1100L))
        assertFalse(guide.confirmable(pkg, window, generation, 1120L))
        assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
            guide.onEvidence(first, "android_accepted", pkg, window, generation, 0, 1200L))
        assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
            guide.onEvidence(first, "scroll_event_observed", pkg, window, generation, 0, 1450L))
        assertEquals(NexaBoundedScrollSearch.Progress.NEXT_APPROVAL,
            guide.onEvidence(first, "scroll_event_observed", pkg, window, generation, 0, 1750L))
        assertTrue(guide.confirmable(pkg, window, generation, 1800L))
        assertFalse(guide.onApprovedScroll(first, pkg, window, generation, 1550L))
        assertTrue(guide.onApprovedScroll(second, pkg, window, generation, 1550L))
        assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
            guide.onEvidence(second, "scroll_event_observed", pkg, window, generation,
                1, 1900L, "10:20:110:60"))
        assertEquals(NexaBoundedScrollSearch.Progress.FOUND,
            guide.onEvidence(second, "scroll_event_observed", pkg, window, generation,
                1, 2200L, "10:20:110:60"))
        assertFalse(guide.confirmable(pkg, window, generation, 2250L))
    }

    @Test fun packageWindowAndPauseChangesFailClosed() {
        val request = NexaBoundedScrollSearch.parse("scroll left until Menu")!!
        val guide = NexaBoundedScrollSearch()
        assertTrue(guide.start(request, pkg, window, generation, 1000L))
        assertFalse(guide.confirmable("com.other", window, generation, 1020L))
        assertFalse(guide.confirmable(pkg, window + 1, generation, 1020L))
        assertFalse(guide.confirmable(pkg, window, generation + 1, 1020L))
        assertTrue(guide.onApprovedScroll(first, pkg, window, generation, 1030L))
        assertEquals(NexaBoundedScrollSearch.Progress.BLOCKED,
            guide.onEvidence(first, "scroll_event_observed", "com.other", window, generation, 1, 1200L))
        assertFalse(guide.active(1300L))
    }

    @Test fun acceptedDispatchWithoutObservedScrollCannotAdvance() {
        val guide = NexaBoundedScrollSearch()
        assertTrue(guide.start(NexaBoundedScrollSearch.parse("scroll down until Settings")!!,
            pkg, window, generation, 1000L))
        assertTrue(guide.onApprovedScroll(first, pkg, window, generation, 1100L))
        assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
            guide.onEvidence(first, "claimed", pkg, window, generation, 0, 1200L))
        assertEquals(NexaBoundedScrollSearch.Progress.BLOCKED,
            guide.onEvidence(first, "no_observable_change", pkg, window, generation, 0, 1400L))
        assertFalse(guide.active(1500L))
    }

    @Test fun repeatedScrollHasFiniteFourStepLimit() {
        val guide = NexaBoundedScrollSearch()
        assertTrue(guide.start(NexaBoundedScrollSearch.parse("scroll up until Settings")!!,
            pkg, window, generation, 1000L))
        for (step in 1..4) {
            val token = "scroll-44444444-aaaa-4444-8888-${step.toString().padStart(12, '0')}"
            val now = 1100L + step * 400L
            assertTrue(guide.confirmable(pkg, window, generation, now))
            assertTrue(guide.onApprovedScroll(token, pkg, window, generation, now))
            val expected = if (step < 4) NexaBoundedScrollSearch.Progress.NEXT_APPROVAL
                else NexaBoundedScrollSearch.Progress.LIMIT
            assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
                guide.onEvidence(token, "scroll_event_observed",
                    pkg, window, generation, 0, now + 50L))
            assertEquals(expected, guide.onEvidence(token, "scroll_event_observed",
                pkg, window, generation, 0, now + 350L))
        }
        assertFalse(guide.active(3000L))
    }

    @Test fun expiryAndAmbiguityStopWithNoRetry() {
        val guide = NexaBoundedScrollSearch()
        assertFalse(guide.start(NexaBoundedScrollSearch.Request("Search", "down"),
            "", window, generation, 1000L))
        assertTrue(guide.start(NexaBoundedScrollSearch.parse("scroll down until Search")!!,
            pkg, window, generation, 1000L))
        assertFalse(guide.confirmable(pkg, window, generation, 100_001L))
        assertFalse(guide.active(100_001L))
        assertTrue(guide.start(NexaBoundedScrollSearch.parse("scroll down until Search")!!,
            pkg, window, generation, 110_000L))
        assertTrue(guide.onApprovedScroll(first, pkg, window, generation, 110_100L))
        assertEquals(NexaBoundedScrollSearch.Progress.BLOCKED,
            guide.onEvidence(first, "scroll_event_observed", pkg, window, generation, 2, 110_200L))
        assertFalse(guide.active(110_300L))
    }

    @Test fun layoutMustSettleWithSameGroundedBoundsBeforeFound() {
        val guide = NexaBoundedScrollSearch()
        assertTrue(guide.start(NexaBoundedScrollSearch.parse("scroll down until Search")!!,
            pkg, window, generation, 1000L))
        assertTrue(guide.onApprovedScroll(first,pkg,window,generation,1100L))
        assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
            guide.onEvidence(first,"scroll_event_observed",pkg,window,generation,
                1,1400L,"10:20:80:70"))
        assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
            guide.onEvidence(first,"scroll_event_observed",pkg,window,generation,
                1,1500L,"10:20:80:70"))
        assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
            guide.onEvidence(first,"scroll_event_observed",pkg,window,generation,
                1,1800L,"11:20:81:70"))
        assertEquals(NexaBoundedScrollSearch.Progress.FOUND,
            guide.onEvidence(first,"scroll_event_observed",pkg,window,generation,
                1,2100L,"11:20:81:70"))
        assertFalse(guide.active(2150L))
    }

    @Test fun noUnidentifiedOrExpiredTargetMayProduceSuccess() {
        val guide=NexaBoundedScrollSearch()
        val request=NexaBoundedScrollSearch.parse("scroll down until Settings")!!
        assertTrue(guide.start(request,pkg,window,generation,1000L))
        assertTrue(guide.onApprovedScroll(first,pkg,window,generation,1100L))
        assertEquals(NexaBoundedScrollSearch.Progress.BLOCKED,
            guide.onEvidence(first,"scroll_event_observed",pkg,window,generation,1,1400L))
        assertFalse(guide.active(1500L))
        assertTrue(guide.start(request,pkg,window,generation,2000L))
        assertTrue(guide.onApprovedScroll(second,pkg,window,generation,2100L))
        assertEquals(NexaBoundedScrollSearch.Progress.WAITING,
            guide.onEvidence(second,"scroll_event_observed",pkg,window,generation,0,2200L))
        assertEquals(NexaBoundedScrollSearch.Progress.BLOCKED,
            guide.onEvidence(second,"scroll_event_observed",pkg,window,generation,
                0,6600L))
    }

}
