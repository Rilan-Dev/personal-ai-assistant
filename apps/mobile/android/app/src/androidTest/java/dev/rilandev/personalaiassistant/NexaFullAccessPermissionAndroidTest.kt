package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaFullAccessPermissionAndroidTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()

    @Test fun floatingPermissionNoticeMatchesTheSelectedMode() {
        assertTrue(
            NexaToolOverlayPermissionCopy.note(NexaVoicePermissionPolicy.FULL_ACCESS)
                .contains("routine tools run without Nexa approval")
        )
        assertTrue(
            NexaToolOverlayPermissionCopy.note(NexaVoicePermissionPolicy.ASK_EVERY_TIME)
                .contains("need your approval")
        )
        assertTrue(
            NexaToolOverlayPermissionCopy.note(NexaVoicePermissionPolicy.FULL_ACCESS)
                .contains("protected sends and Android grants still apply")
        )
    }

    @Test fun fullAccessSkipsRoutineToolReviewButKeepsOutboundReview() {
        val previous=NexaVoicePermissionPolicy.mode(context)
        try {
            assertTrue(NexaVoicePermissionPolicy.setMode(
                context,NexaVoicePermissionPolicy.FULL_ACCESS))
            for(command in listOf(
                "open Clock","read_visible_screen_text","list_installed_apps",
                "scroll_screen:down","tap_visible_label:Settings",
                "open_notification_shade","open Call Recorder",
                "locate_icon:send"))
                assertTrue("Full Access should cover $command",
                    NexaVoicePermissionPolicy.alwaysAllowed(context,command))
            for(command in listOf(
                "send_message","screen.send_whatsapp","place_call",
                "calls.place","phone.place_call","wallet.make_payment"))
                assertFalse("Outbound action still needs exact review: $command",
                    NexaVoicePermissionPolicy.alwaysAllowed(context,command))
        } finally {
            assertTrue(NexaVoicePermissionPolicy.setMode(context,previous))
        }
    }

    @Test fun fullAccessAutoApprovalHonorsModePauseAndOutboundFence() {
        val previousMode=NexaVoicePermissionPolicy.mode(context)
        val previousPaused=NexaNativeActionSafety.isPaused(context)
        try {
            assertTrue(NexaVoicePermissionPolicy.setMode(
                context,NexaVoicePermissionPolicy.FULL_ACCESS))
            if(!previousPaused)assertTrue(
                "Full Access auto-approves a registered routine tool when Android is unlocked",
                NexaVoicePermissionPolicy.canExecuteFullAccess(
                    context,"device.open_app"))
            assertFalse("The native boundary must reject outbound send tools",
                NexaVoicePermissionPolicy.canExecuteFullAccess(
                    context,"screen.send_whatsapp"))
            assertFalse("The native boundary must reject call tools",
                NexaVoicePermissionPolicy.canExecuteFullAccess(
                    context,"phone.place_call"))
            assertTrue(NexaNativeActionSafety.setPaused(context,true))
            assertFalse("Owner Pause remains an emergency stop",
                NexaVoicePermissionPolicy.canExecuteFullAccess(
                    context,"device.open_app"))
            assertTrue(NexaNativeActionSafety.setPaused(context,previousPaused))
            assertTrue(NexaVoicePermissionPolicy.setMode(
                context,NexaVoicePermissionPolicy.ASK_EVERY_TIME))
            assertFalse("Full Access off restores per-tool review",
                NexaVoicePermissionPolicy.canExecuteFullAccess(
                    context,"device.open_app"))
        } finally {
            NexaVoicePermissionPolicy.setMode(context,previousMode)
            NexaNativeActionSafety.setPaused(context,previousPaused)
        }
    }
}
