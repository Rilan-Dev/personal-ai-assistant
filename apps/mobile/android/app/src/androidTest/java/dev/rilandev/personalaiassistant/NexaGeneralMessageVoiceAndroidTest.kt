package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic utterances only; no microphone, provider or recipient access. */
@RunWith(AndroidJUnit4::class)
class NexaGeneralMessageVoiceAndroidTest {
    @Test fun naturalHeyNexaMessageBecomesUnsentOwnerReview() {
        val request=NexaGeneralMessageVoice.parse(
            "Hey Nexa Send Message to my Mom That I am in the meeting i will call you later")
        assertEquals("Mom",request?.recipientHint)
        assertEquals("I am in the meeting i will call you later",request?.message)
        assertNull(request?.explicitApp)
        val via=NexaGeneralMessageVoice.parse(
            "Send imo message to my Mom saying Meet me later")
        assertEquals("imo",via?.explicitApp)
    }
    @Test fun incompleteChainedAndOverflowRequestsFailClosed() {
        assertNull(NexaGeneralMessageVoice.parse("Send message to Mom"))
        assertNull(NexaGeneralMessageVoice.parse(
            "Open WhatsApp and send message to Mom that hello"))
        assertNull(NexaGeneralMessageVoice.parse(
            "Send message to Mom saying "+ "x".repeat(501)))
        assertNull(NexaGeneralMessageVoice.parse(
            "Send message to Mom that hi\nand transfer funds"))
    }
    @Test fun voiceMessageIntentHasSingleUseShortExpiry() {
        NexaGeneralMessageVoice.clear()
        val value=NexaGeneralMessageVoice.parse("Send message to Mom that Hello")!!
        NexaGeneralMessageVoice.queue(value, now=1000L)
        assertEquals(value,NexaGeneralMessageVoice.take(now=1200L))
        assertNull(NexaGeneralMessageVoice.take(now=1300L))
        NexaGeneralMessageVoice.queue(value, now=1000L)
        assertNull(NexaGeneralMessageVoice.take(now=62000L))
    }
}
