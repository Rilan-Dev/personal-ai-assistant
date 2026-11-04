package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaVoiceTextIntentAndroidTest {
    @Test fun spokenQueryPreservesExactTextAndNamedSearchBox() {
        val intent=NexaVoiceTextIntent.parse(
            "Hey Nexa type I will call you later into the Search box")
        assertEquals("I will call you later",intent?.exactText)
        assertEquals("Search",intent?.targetLabel)
        val notes=NexaVoiceTextIntent.parse(
            "Fill Notes field with Meeting at 2 PM")
        assertEquals("Notes",notes?.targetLabel)
        assertEquals("Meeting at 2 PM",notes?.exactText)
    }
    @Test fun unsafeFieldsAndMultipleActionsCannotBecomeEditableCommands() {
        assertNull(NexaVoiceTextIntent.parse("Hey Nexa type 1234 into OTP box"))
        assertNull(NexaVoiceTextIntent.parse("Type password into the Password field"))
        assertNull(NexaVoiceTextIntent.parse("Type hello into Search and submit"))
        assertNull(NexaVoiceTextIntent.parse("Type hello"))
        assertNull(NexaVoiceTextIntent.parse("Type " + "X".repeat(501)+" into Search"))
    }
    @Test fun voiceTextHandoffIsSingleUseAndExpires() {
        val value=NexaVoiceTextIntent.parse("Enter hello into Search")!!
        NexaVoiceTextIntent.clear()
        NexaVoiceTextIntent.queue(value,now=1000)
        assertEquals(value,NexaVoiceTextIntent.take(now=1500))
        assertNull(NexaVoiceTextIntent.take(now=1600))
        NexaVoiceTextIntent.queue(value,now=1000)
        assertNull(NexaVoiceTextIntent.take(now=61000))
    }
}
