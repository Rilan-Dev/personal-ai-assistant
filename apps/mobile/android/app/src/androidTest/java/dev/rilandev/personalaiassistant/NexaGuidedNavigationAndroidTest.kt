package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Source-level acceptance targets; phone/emulator execution is owner-managed. */
@RunWith(AndroidJUnit4::class)
class NexaGuidedNavigationAndroidTest {
    @Test fun menuSearchRequiresPostActionEvidenceBeforeProgressing() {
        val flow=NexaGuidedNavigation()
        val token="00000000-0000-0000-0000-000000000001"
        assertTrue(flow.start("find search in menu",100L) is NexaGuidedNavigation.StartResult.Started)
        assertEquals("menu",flow.expected(101L))
        assertFalse(flow.canSelect("search",102L))
        assertTrue(flow.canSelect("more_options",102L))
        assertFalse(flow.afterAcceptedDispatch("search",token,102L))
        assertTrue(flow.afterAcceptedDispatch("more_options",token,103L))
        assertEquals("menu",flow.expected(104L)) // Android accepted but no visual verification yet
        assertFalse(flow.canSelect("menu",104L)) // no second click while waiting
        assertEquals("waiting",flow.onActionEvidence(token,"android_accepted",105L))
        assertEquals("waiting",flow.onActionEvidence("wrong-token","structural_change_observed",106L))
        assertEquals("menu",flow.expected(107L))
        assertEquals("advanced",flow.onActionEvidence(token,"structural_change_observed",108L))
        assertEquals("search",flow.expected(109L))
        assertTrue(flow.canSelect("search",109L))
        val second="00000000-0000-0000-0000-000000000002"
        assertTrue(flow.afterAcceptedDispatch("search",second,110L))
        assertEquals("completed",flow.onActionEvidence(second,"window_change_observed",111L))
        assertNull(flow.expected(112L))
    }

    @Test fun ineffectiveOrExpiredActionsNeverAdvanceGuidedWorkflow() {
        val flow=NexaGuidedNavigation()
        val token="00000000-0000-0000-0000-000000000003"
        assertTrue(flow.start("find search in menu",100L) is NexaGuidedNavigation.StartResult.Started)
        assertTrue(flow.afterAcceptedDispatch("menu",token,101L))
        assertEquals("blocked",flow.onActionEvidence(token,"no_observable_change",102L))
        assertNull(flow.expected(103L))
        assertTrue(flow.start("find search in menu",200L) is NexaGuidedNavigation.StartResult.Started)
        assertTrue(flow.afterAcceptedDispatch("menu",token,201L))
        assertEquals("blocked",flow.onActionEvidence(token,"android_accepted",5_000L))
        assertNull(flow.expected(5_001L))
    }

    @Test fun rejectsUnexpectedApplicationsAndExpiresOnDeadline() {
        val flow=NexaGuidedNavigation()
        assertTrue(flow.start("open settings from menu",1_000L)
            is NexaGuidedNavigation.StartResult.Started)
        assertTrue(flow.bindPackage("com.example.original",1_001L))
        assertTrue(flow.bindPackage("com.example.original",1_002L))
        assertFalse(flow.bindPackage("com.example.unexpected",1_003L))
        assertNull(flow.expected(91_000L))
        assertFalse(flow.bindPackage("com.example.original",91_001L))
    }

    @Test fun unknownAndDestructiveCommandsCannotStartGuides() {
        val flow=NexaGuidedNavigation()
        listOf("delete all messages","send text","call mom","open unknown app",
            "tap at 100 200","open settings then send message").forEach {
            assertTrue(it,flow.start(it,1_000L) is NexaGuidedNavigation.StartResult.Rejected)
            assertNull(flow.expected(1_001L))
        }
    }

    @Test fun cancellationRevokesSelectedWorkflowImmediately() {
        val flow=NexaGuidedNavigation()
        assertTrue(flow.start("open menu",10L) is NexaGuidedNavigation.StartResult.Started)
        flow.cancel()
        assertFalse(flow.canSelect("menu",11L))
    }
}
