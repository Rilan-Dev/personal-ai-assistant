package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaSarvamVoiceIntentsAndroidTest {
    @Test fun politeOwnerVoiceRequestsProposeOnlySupportedApps() {
        for (utterance in listOf(
            "Can you please open my WhatsApp?",
            "Could you open WhatsApp for me?",
            "Hey Nexa, please open WhatsApp.",
            "Would you mind opening the WhatsApp app?",
            "Can you please i Open my WhatsApp",
            "I want you to open the whatsapp app",
            "I need you to open WhatsApp"
        )) {
            val plan = NexaVoiceIntentGate.appRequest(utterance)
            assertNotNull(utterance, plan)
            assertEquals("whatsapp", plan?.app)
            assertEquals("open whatsapp", plan?.command)
        }
        assertEquals("whatsapp business",
            NexaVoiceIntentGate.appRequest("Open WhatsApp Business")?.app)
        assertEquals("github", NexaVoiceIntentGate.appRequest("Please launch GitHub")?.app)
    }
    @Test fun unrelatedSpeechCannotTriggerDeviceLaunch() {
        for (utterance in listOf(
            "Open WhatsApp and message my mother",
            "Can you open WhatsApp then send a photo?",
            "Please do not open WhatsApp",
            "Don't open WhatsApp",
            "The message says open WhatsApp",
            "Open WhatsApp or WhatsApp Business",
            "Send WhatsApp to Mom",
            "Open Telegram",
            "Hello Nexa",
            "I think WhatsApp is installed"
        )) assertNull(utterance, NexaVoiceIntentGate.appRequest(utterance))
    }
}
