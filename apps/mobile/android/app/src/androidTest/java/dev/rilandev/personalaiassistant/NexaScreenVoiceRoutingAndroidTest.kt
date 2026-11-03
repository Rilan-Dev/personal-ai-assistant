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
        assertTrue(NexaPhoneVoiceTools.wantsVisibleText("Can you please read my screen?"))
        assertEquals("down",NexaPhoneVoiceTools.scrollDirection("Could you please scroll down?"))
        assertEquals("search",NexaPhoneVoiceTools.clickSafeIcon("Would you please click the search icon?"))
        assertTrue(NexaPhoneVoiceTools.wantsScreenOverview("identify screen icons"))
        assertFalse(NexaPhoneVoiceTools.wantsVisibleText("Send a message"))
        assertFalse(NexaPhoneVoiceTools.wantsVisibleText("Read my password"))
        assertEquals("search",NexaPhoneVoiceTools.clickSafeIcon("Please click the search icon"))
        assertEquals("menu",NexaPhoneVoiceTools.clickSafeIcon("Tap menu"))
        assertEquals("menu",NexaPhoneVoiceTools.clickSafeIcon("Tap menu button"))
        assertEquals("settings",NexaPhoneVoiceTools.clickSafeIcon("Tap on the settings icon"))
        assertEquals("back",NexaPhoneVoiceTools.clickSafeIcon("Press back"))
        assertEquals("ask meta ai or search",
            NexaPhoneVoiceTools.tapVisibleLabelIntent("Tap the Ask Meta AI or search input box"))
        assertNull(NexaPhoneVoiceTools.clickSafeIcon("tap send"))
        assertNull(NexaPhoneVoiceTools.clickSafeIcon("tap call"))
        assertNull(NexaPhoneVoiceTools.clickSafeIcon("tap 420,320"))
    }

    @Test fun everyScrollDirectionHasAnExplicitOneShotOwnerVoiceIntent() {
        assertEquals("up",NexaPhoneVoiceTools.scrollDirection("Scroll up"))
        assertEquals("down",NexaPhoneVoiceTools.scrollDirection("Scroll down"))
        assertEquals("left",NexaPhoneVoiceTools.scrollDirection("Swipe left"))
        assertEquals("right",NexaPhoneVoiceTools.scrollDirection("Scroll right"))
        assertEquals("left",NexaPhoneVoiceTools.scrollDirection("Could you please swipe left on my screen?"))
        assertTrue(NexaPhoneVoiceTools.wantsVisibleText("Can you check the current screen?"))
        assertTrue(NexaPhoneVoiceTools.wantsInstalledApps("Show installed apps"))
        assertEquals("profile",NexaPhoneVoiceTools.tapVisibleLabelIntent("Tap the profile button"))
        assertNull(NexaPhoneVoiceTools.tapVisibleLabelIntent("tap send"))
        assertNull(NexaPhoneVoiceTools.tapVisibleLabelIntent("tap 420,320"))
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
