package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaVoiceTaskPlanAndroidTest {
    @Test fun compoundOwnerRequestKeepsTwoSteps() {
        val plan=NexaVoiceTaskPlan.parse("Hey Nexa open WhatsApp and read my screen")
        assertNotNull(plan)
        assertEquals(listOf("open WhatsApp","read my screen"),plan!!.steps)
        val cursor=NexaVoiceTaskCursor(plan)
        assertEquals("open WhatsApp",cursor.current())
        assertNull("Android acceptance alone must not advance a task",cursor.advance(false))
        assertEquals("open WhatsApp",cursor.current())
        assertEquals("read my screen",cursor.advance(true))
        assertNull(cursor.advance(true))
        assertTrue(cursor.finished)
    }
    @Test fun doesNotSplitMessageContentsOrUnsupportedOperations() {
        assertNull(NexaVoiceTaskPlan.parse("Message mom I am on the way and see you"))
        assertNull(NexaVoiceTaskPlan.parse("Send WhatsApp to Mom and then delete the chat"))
        assertNull(NexaVoiceTaskPlan.parse("read my screen"))
        assertTrue(NexaVoiceFragmentGate.isIncomplete("Can you please open the uh"))
        assertTrue(NexaVoiceFragmentGate.isIncomplete("Open the um"))
        assertFalse(NexaVoiceFragmentGate.isIncomplete("Open Alight Motion"))
    }
    @Test fun persistentGrantsNeverAuthorizeSensitiveActions() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        NexaTestPermissionPreferences.withIsolatedVoicePermissionPolicy(context) {
            NexaVoicePermissionPolicy.clearGrants(context)
            assertEquals(NexaVoicePermissionPolicy.ASK_EVERY_TIME,
                NexaVoicePermissionPolicy.mode(context))
            assertFalse(NexaVoicePermissionPolicy.alwaysAllowed(context,"scroll_screen:left"))
            assertTrue(NexaVoicePermissionPolicy.alwaysAllow(context,"scroll_screen:left"))
            assertFalse(NexaVoicePermissionPolicy.alwaysAllowed(context,"scroll_screen:right"))
            assertTrue(NexaVoicePermissionPolicy.setMode(context,NexaVoicePermissionPolicy.TRUST_SAFE))
            assertTrue(NexaVoicePermissionPolicy.alwaysAllowed(context,"scroll_screen:right"))
            assertFalse(NexaVoicePermissionPolicy.alwaysAllowed(context,"send_message"))
            assertFalse(NexaVoicePermissionPolicy.alwaysAllow(context,"call_mom"))
            assertFalse(NexaVoicePermissionPolicy.alwaysAllowed(context,"send_message"))
            assertTrue(NexaVoicePermissionPolicy.denyCategory(context,"screen.scroll"))
            assertFalse(NexaVoicePermissionPolicy.alwaysAllowed(context,"scroll_screen:left"))
        }
    }

    @Test fun permissionPolicyInstrumentationRestoresFullAccessAndExistingGrants() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val preferences=context.getSharedPreferences(
            "nexa_voice_action_permissions_v1",Context.MODE_PRIVATE)
        NexaTestPermissionPreferences.withIsolatedVoicePermissionPolicy(context) {
            assertTrue(NexaVoicePermissionPolicy.setMode(
                context,NexaVoicePermissionPolicy.FULL_ACCESS))
            assertTrue(NexaVoicePermissionPolicy.alwaysAllow(
                context,"scroll_screen:right"))
            val selected=preferences.all.mapValues { (_, value) ->
                if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
            }
            NexaTestPermissionPreferences.withIsolatedVoicePermissionPolicy(context) {
                assertTrue(NexaVoicePermissionPolicy.setMode(
                    context,NexaVoicePermissionPolicy.TRUST_NAVIGATION))
                assertTrue(NexaVoicePermissionPolicy.alwaysAllow(
                    context,"scroll_screen:left"))
            }
            val restored=preferences.all.mapValues { (_, value) ->
                if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
            }
            assertEquals("Temporary test choices must restore Full Access and the owner's grants",
                selected,restored)
        }
    }
}
