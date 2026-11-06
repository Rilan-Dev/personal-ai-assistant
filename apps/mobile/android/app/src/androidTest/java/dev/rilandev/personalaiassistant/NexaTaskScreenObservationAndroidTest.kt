package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic controls: these tests never inspect real apps or screenshots. */
@RunWith(AndroidJUnit4::class)
class NexaTaskScreenObservationAndroidTest {
    @Test fun nativeOwnerIntentSelectsOnlyBoundedScreenObservation() {
        for(goal in listOf("visible screen controls","inspect visible controls",
            "what buttons are on my screen","show screen controls")) {
            assertEquals("get_visible_controls",
                NexaTaskCoordinator.classifyOwnerFact(goal))
        }
        assertNull(NexaTaskCoordinator.classifyOwnerFact(
            "read the OTP from my bank app"))
        assertNull(NexaTaskCoordinator.classifyOwnerFact(
            "click on Send and take a screenshot"))
        assertEquals(listOf("get_battery_level","get_visible_controls"),
            NexaTaskCoordinator.classifyOwnerReadOnlyPlan(
                "check battery and show screen controls"))
    }

    @Test fun onlyShortNonsensitiveActionLabelsSurvive() {
        for(label in listOf("Search","Menu","Continue","Next","Open settings",
            "المزيد","அடுத்தது")) {
            assertNotNull(label,NexaTaskScreenObservation.safeLabel(label))
        }
        for(label in listOf("Password","Transfer Money","Enter OTP 123456",
            "Call Mom","Send","Delete","Allow permission","Inbox @company.com",
            "https://example.com","123456","Your verification code is 412441",
            "Message body","A".repeat(40),"Open\nhttps://evil.test")) {
            assertNull(label,NexaTaskScreenObservation.safeLabel(label))
        }
    }

    @Test fun onlyVisibleNonEditableButtonsCanBecomeControlHints() {
        assertTrue(NexaTaskScreenObservation.safeControl(
            "android.widget.Button",true,true,true,false,false))
        assertTrue(NexaTaskScreenObservation.safeControl(
            "android.widget.ImageButton",true,true,true,false,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.EditText",true,true,true,true,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.TextView",false,true,true,false,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.Button",true,false,true,false,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.Button",true,true,false,false,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.Button",true,true,true,false,true))
    }

    @Test fun missingAccessibilityNeverCreatesUnverifiedContent() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        if(ScreenAssistService.active==null) {
            assertNull(NexaTaskScreenObservation.observe(ctx))
        }
    }
}
