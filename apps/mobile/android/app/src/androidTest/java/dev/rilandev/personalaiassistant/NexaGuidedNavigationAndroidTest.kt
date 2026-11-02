package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Source-level acceptance targets; phone/emulator execution is owner-managed. */
@RunWith(AndroidJUnit4::class)
class NexaGuidedNavigationAndroidTest {
    @Test fun menuSearchRequiresOrderAndFreshConfirmationPerStep() {
        val flow=NexaGuidedNavigation()
        assertTrue(flow.start("find search in menu",100L) is NexaGuidedNavigation.StartResult.Started)
        assertEquals("menu",flow.expected(101L))
        assertFalse(flow.canSelect("search",102L))
        assertTrue(flow.canSelect("more_options",102L))
        assertFalse(flow.afterConfirmedClick("search",102L))
        assertTrue(flow.afterConfirmedClick("more_options",103L))
        assertEquals("search",flow.expected(104L))
        assertFalse(flow.afterConfirmedClick("menu",105L))
        assertTrue(flow.afterConfirmedClick("search",106L))
        assertNull(flow.expected(107L))
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
