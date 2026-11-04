package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No real app, text box or owner data is read by these synthetic checks. */
@RunWith(AndroidJUnit4::class)
class NexaTextInputAndroidTest {
    @Test fun onlyExplicitlyNamedSafeEditableFieldsCanMatch() {
        assertTrue(NexaTextInputPolicy.matches("Search",listOf("Search"),
            true,false,true,true,true,true))
        assertTrue(NexaTextInputPolicy.matches("Notes",listOf("Notes input"),
            true,false,true,true,true,true))
        assertTrue(NexaTextInputPolicy.matches("Description",listOf("Description"),
            true,false,true,true,true,true))
        assertFalse(NexaTextInputPolicy.matches("Search",listOf("Password"),
            true,false,true,true,true,true))
        assertFalse(NexaTextInputPolicy.matches("Search",listOf("Search"),
            true,true,true,true,true,true))
        assertFalse(NexaTextInputPolicy.matches("Search",listOf("Search"),
            true,false,true,true,false,true))
        assertFalse(NexaTextInputPolicy.matches("Search",listOf("Search"),
            true,false,true,true,true,false))
        assertFalse(NexaTextInputPolicy.matches("Search",listOf("Search"),
            true,false,false,true,true,true))
        assertFalse(NexaTextInputPolicy.matches("Search",listOf("Search","Send"),
            false,false,true,true,true,true))
    }
    @Test fun dangerousFieldLabelsAreNotEligible() {
        for(field in listOf("Password","OTP","Payment amount","Bank account",
            "CVV","Verification code","Recipient","Submit","PIN","Email address")) {
            assertFalse(field,NexaTextInputPolicy.safeTarget(field))
        }
    }
    @Test fun textExistsOnlyBrieflyAndCannotBeReplayed() {
        NexaTextInputState.clear()
        assertFalse(NexaTextInputState.stage("Password","secret",now=100L))
        assertFalse(NexaTextInputState.stage("Search","",now=100L))
        assertTrue(NexaTextInputState.stage("Search","project update",now=100L))
        assertEquals("project update",NexaTextInputState.take(200L)?.exactText)
        assertNull(NexaTextInputState.take(201L))
        assertTrue(NexaTextInputState.stage("Notes","hello",now=100L))
        assertNull(NexaTextInputState.take(90_101L))
    }
    @Test fun detachedAccessibilityServiceCannotPerformTextEntry() {
        NexaTextInputState.clear()
        NexaTextInputActions.cancel()
        val fake=ScreenAssistService()
        val was=ScreenAssistState.connected
        try{
            ScreenAssistState.connected=false
            assertTrue(NexaTextInputActions.prepare(fake).contains("No active"))
            assertTrue(NexaTextInputActions.confirm(fake).contains("No pending"))
            assertTrue(NexaTextInputState.stage("Search","hello"))
            assertTrue(NexaTextInputActions.prepare(fake).contains("Screen Assist"))
            assertFalse(NexaTextInputActions.ready())
        }finally{
            NexaTextInputActions.cancel()
            NexaTextInputState.clear()
            ScreenAssistState.connected=was
        }
    }
}
