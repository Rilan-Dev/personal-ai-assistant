package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Reproduces owner-exported symptoms using invented contact aliases only. */
@RunWith(AndroidJUnit4::class)
class NexaWhatsAppChatIntentAndroidTest {
    @Test fun fullOwnerUtteranceBecomesVerifiedTwoStepChatTask() {
        val sentence="Can you please open my WhatsApp and find the contact Mah Forever and open the conversation chat?"
        val parsed=NexaWhatsAppChatIntent.parse(sentence)
        assertEquals("Mah Forever",parsed?.recipientHint)
        val plan=NexaVoiceTaskPlan.parse(sentence)
        assertEquals(listOf("open WhatsApp","open chat with Mah Forever"),
            plan?.steps)
        assertEquals("Mah Forever",
            NexaWhatsAppChatIntent.followupRecipient("open chat with Mah Forever"))
    }
    @Test fun ownerErrorsCannotBecomeMessagingOrGenericUnsafeCommands() {
        for(text in listOf(
            "Open WhatsApp and find Mah Forever and send the message",
            "Open WhatsApp and delete chat Mah Forever",
            "Open WhatsApp and find password and open chat",
            "Open WhatsApp and find Mah Forever and open the chat and then delete",
            "Please open the chat and then pay",
            "Send WhatsApp message to Mah Forever")) {
            assertNull("No unsupported side-effect plan: "+text,
                NexaWhatsAppChatIntent.parse(text))
        }
    }
    @Test fun observedEmojiAndASROneCharacterErrorCanBeGroundedSafely() {
        assertEquals(100,NexaWhatsAppChatMatch.evidence(
            "Mah Forever","Mah Forever 🖤"))
        assertEquals("Exact short aliases can be selected only when unique",100,
            NexaWhatsAppChatMatch.evidence("Mom","Mom"))
        assertEquals("Short aliases never receive fuzzy permission",0,
            NexaWhatsAppChatMatch.evidence("Mom","Mum"))
        assertEquals(85,NexaWhatsAppChatMatch.evidence(
            "Nva Forever","Mah Forever 🖤"))
        assertEquals(0,NexaWhatsAppChatMatch.evidence(
            "map forward","Mah Forever"))
        assertEquals(0,NexaWhatsAppChatMatch.evidence(
            "other contact","Mah Forever"))
        assertEquals(0,NexaWhatsAppChatMatch.evidence(
            "Mah","Mah Forever"))
    }
}
