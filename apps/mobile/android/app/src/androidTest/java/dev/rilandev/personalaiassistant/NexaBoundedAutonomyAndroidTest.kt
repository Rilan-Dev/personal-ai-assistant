package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One multi-step owner request can reuse opt-in low-risk navigation grants,
 * but must never treat the model's plan, a merely accepted Android action, or
 * a safe-navigation preference as consent to send, type, read or pay.
 */
@RunWith(AndroidJUnit4::class)
class NexaBoundedAutonomyAndroidTest {
    private val ctx get()=ApplicationProvider.getApplicationContext<Context>()

    @Test fun threeSafeStepsNeedFreshEvidenceBeforeAdvancing() {
        val plan=NexaVoiceTaskPlan.parse(
            "Hey Nexa open Chrome and scroll down and tap search")
        assertNotNull(plan)
        assertEquals(listOf("open Chrome","scroll down","tap search"),plan!!.steps)
        val cursor=NexaVoiceTaskCursor(plan)
        assertEquals("open Chrome",cursor.current())
        assertNull("Android accepted is not independently verified",cursor.advance(false))
        assertEquals("open Chrome",cursor.current())
        assertEquals("scroll down",cursor.advance(true))
        assertNull(cursor.advance(false))
        assertEquals("scroll down",cursor.current())
        assertEquals("tap search",cursor.advance(true))
        assertNull(cursor.advance(true))
        assertTrue(cursor.finished)
    }

    @Test fun trustedModeReusesOnlyNavigationApprovalsAndRespectsRevocations() {
        try {
            NexaVoicePermissionPolicy.clearGrants(ctx)
            assertTrue(NexaVoicePermissionPolicy.setMode(ctx,NexaVoicePermissionPolicy.TRUST_NAVIGATION))
            for(command in listOf("open Chrome","scroll_screen:down","tap_safe_icon:search")) {
                assertTrue("Low-risk navigation is opted in: $command",
                    NexaVoicePermissionPolicy.alwaysAllowed(ctx,command))
            }
            // Android Accessibility must still be granted and active to act.
            val connected=ScreenAssistState.connected &&
                ScreenAssistService.active!=null
            if(!connected) {
                assertFalse(NexaVoicePermissionPolicy.canExecute(ctx,"scroll_screen:down"))
                assertFalse(NexaVoicePermissionPolicy.canExecute(ctx,"tap_safe_icon:search"))
            }
            for(command in listOf("send_message","message_fill","text_fill",
                "read_visible_screen_text","list_installed_apps","tap_visible_label:Send",
                "open_notification_shade","delete_message")) {
                assertFalse("Sensitive actions never inherit navigation trust: $command",
                    NexaVoicePermissionPolicy.alwaysAllowed(ctx,command))
            }
            assertTrue(NexaVoicePermissionPolicy.denyCategory(ctx,"screen.navigate"))
            assertFalse(NexaVoicePermissionPolicy.alwaysAllowed(ctx,"tap_safe_icon:search"))
        } finally {
            NexaVoicePermissionPolicy.clearGrants(ctx)
        }
    }

    @Test fun compoundRequestCannotSmuggleFinalSendOrFieldTyping() {
        for(raw in listOf(
            "Open WhatsApp and send message to Mom that I am in a meeting",
            "Open WhatsApp and type hello into the Message field",
            "Open Chrome and tap Send",
            "Open settings and pay the invoice",
            "Read screen and delete the file"
        )) {
            assertNull("No automatic multi-action high-risk execution: $raw",
                NexaVoiceTaskPlan.parse(raw))
        }
    }
}
