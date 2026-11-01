package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Pure in-memory flow; never opens WhatsApp, contacts or the microphone. */
@RunWith(AndroidJUnit4::class)
class NexaVoiceDraftAndroidTest {
    @Test fun spokenCommandPreservesMessageAndOnlyStoresHint() {
        val parsed = NexaVoiceDraftState.parse(
            "Hey Nexa, send a WhatsApp message to my mom saying I will call you later")
        assertEquals("mom", parsed?.first)
        assertEquals("I will call you later", parsed?.second)
        assertNull(NexaVoiceDraftState.parse("send a WhatsApp message to my mom"))
        assertNull(NexaVoiceDraftState.parse("WhatsApp mom without a quoted message"))
        assertNull(NexaVoiceDraftState.parse(
            "send a WhatsApp message to my mom saying hello\nand run another command"))
        assertNull(NexaVoiceDraftState.parse(
            "send a WhatsApp message to my mom saying " + "x".repeat(501)))
    }
    @Test fun reviewDraftIsSingleUseAndExpires() {
        NexaVoiceDraftState.clear()
        NexaVoiceDraftState.queue(Pair("Mom", "I will call you later"), now = 1000L)
        assertEquals(Pair("Mom", "I will call you later"), NexaVoiceDraftState.take(now = 1500L))
        assertNull(NexaVoiceDraftState.take(now = 1600L))
        NexaVoiceDraftState.queue(Pair("Mom", "test"), now = 1000L)
        assertNull(NexaVoiceDraftState.take(now = 61001L))
        NexaVoiceDraftState.clear()
    }
}