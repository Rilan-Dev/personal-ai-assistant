package dev.rilandev.personalaiassistant

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No microphone, provider API or inaccessible application is used here. */
@RunWith(AndroidJUnit4::class)
class NexaScreenVoiceRoutingAndroidTest {
    @Test fun recognizedScreenQuestionsAreExplicitAndDoNotEnableUnrequestedReads() {
        assertTrue(NexaPhoneVoiceTools.wantsVisibleText("Hey Nexa read my screen"))
        assertTrue(NexaPhoneVoiceTools.wantsVisibleText("What is on my screen?"))
        assertTrue(NexaPhoneVoiceTools.wantsScreenOverview("identify screen icons"))
        assertFalse(NexaPhoneVoiceTools.wantsVisibleText("Send a message"))
        assertFalse(NexaPhoneVoiceTools.wantsVisibleText("Read my password"))
        assertEquals("search",NexaPhoneVoiceTools.clickSafeIcon("Please click the search icon"))
        assertEquals("menu",NexaPhoneVoiceTools.clickSafeIcon("Tap menu"))
        assertNull(NexaPhoneVoiceTools.clickSafeIcon("tap send"))
        assertNull(NexaPhoneVoiceTools.clickSafeIcon("tap call"))
        assertNull(NexaPhoneVoiceTools.clickSafeIcon("tap 420,320"))
    }

    @Test fun everyScrollDirectionHasAnExplicitOneShotOwnerVoiceIntent() {
        assertEquals("up",NexaPhoneVoiceTools.scrollDirection("Scroll up"))
        assertEquals("down",NexaPhoneVoiceTools.scrollDirection("Scroll down"))
        assertEquals("left",NexaPhoneVoiceTools.scrollDirection("Swipe left"))
        assertEquals("right",NexaPhoneVoiceTools.scrollDirection("Scroll right"))
        assertNull(NexaPhoneVoiceTools.scrollDirection("delete my messages and scroll down"))
        assertNull(NexaPhoneVoiceTools.scrollDirection("scroll forever"))
        assertNull(NexaPhoneVoiceTools.scrollDirection("keep scrolling"))
    }

    @Test fun batteryRequestDoesNotThrowWhenReadFromAndroidDeviceServices() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        for(request in listOf("battery","what is my battery percentage","how much battery is left")){
            val response=NexaPhoneVoiceTools.deviceFacts(ctx,request)
            assertNotNull(response)
            assertTrue(response!!.isNotBlank())
        }
        assertNotNull(NexaPhoneVoiceTools.deviceFacts(ctx,"storage"))
        assertNotNull(NexaSarvamReadTools.execute(ctx,"get_battery_level","{}","battery percentage"))
    }

    @Test fun unavailableAccessibilityCannotTriggerAScreenAction() {
        // This instrumentation test does not enable an AccessibilityService or
        // press Android's OS permission dialog. It must fail closed instead.
        assertEquals("Unsafe or unknown icon. No action was performed.",
            NexaScreenVoiceOverview.tapSafeApproved("send"))
        assertEquals("Invalid screen direction.",
            NexaScreenVoiceOverview.scrollApproved("diagonal"))
    }
}
