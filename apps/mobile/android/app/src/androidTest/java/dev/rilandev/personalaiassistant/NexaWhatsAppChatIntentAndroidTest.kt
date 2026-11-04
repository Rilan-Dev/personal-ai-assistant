package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Reproduces owner-exported symptoms using invented contact aliases only. */
@RunWith(AndroidJUnit4::class)
class NexaWhatsAppChatIntentAndroidTest {
    @Test fun fullOwnerUtteranceBecomesVerifiedTwoStepChatTask() {
        val sentence="Can you please open my WhatsApp and find the contact Nova Forever and open the conversation chat?"
        val parsed=NexaWhatsAppChatIntent.parse(sentence)
        assertEquals("Nova Forever",parsed?.recipientHint)
        val plan=NexaVoiceTaskPlan.parse(sentence)
        assertEquals(listOf("open WhatsApp","open chat with Nova Forever"),
            plan?.steps)
        assertEquals("Nova Forever",
            NexaWhatsAppChatIntent.followupRecipient("open chat with Nova Forever"))
    }
    @Test fun ownerErrorsCannotBecomeMessagingOrGenericUnsafeCommands() {
        for(text in listOf(
            "Open WhatsApp and find Nova Forever and send the message",
            "Open WhatsApp and delete chat Nova Forever",
            "Open WhatsApp and find password and open chat",
            "Open WhatsApp and find Nova Forever and open the chat and then delete",
            "Please open the chat and then pay",
            "Send WhatsApp message to Nova Forever")) {
            assertNull("No unsupported side-effect plan: "+text,
                NexaWhatsAppChatIntent.parse(text))
        }
    }
    @Test fun observedEmojiAndASROneCharacterErrorCanBeGroundedSafely() {
        assertEquals(100,NexaWhatsAppChatMatch.evidence(
            "Nova Forever","Nova Forever 🖤"))
        assertEquals(85,NexaWhatsAppChatMatch.evidence(
            "Nva Forever","Nova Forever 🖤"))
        assertEquals(0,NexaWhatsAppChatMatch.evidence(
            "map forward","Nova Forever"))
        assertEquals(0,NexaWhatsAppChatMatch.evidence(
            "other contact","Nova Forever"))
        assertEquals(0,NexaWhatsAppChatMatch.evidence(
            "Nova","Nova Forever"))
    }
}
