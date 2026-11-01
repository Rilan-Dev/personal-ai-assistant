package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaRealtimeToolAndroidTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun readToolsHaveNoApprovalOrUnexpectedArguments() {
        val status = NexaLiveToolPolicy.decide(context, "get_device_status", "{}")
        assertTrue(status is NexaLiveToolPolicy.Decision.Immediate)
        assertTrue((status as NexaLiveToolPolicy.Decision.Immediate).result.contains("Microphone"))
        val rejected = NexaLiveToolPolicy.decide(context, "get_battery_level", "{\"extra\":1}")
        assertTrue((rejected as NexaLiveToolPolicy.Decision.Immediate).result.contains("Rejected"))
    }

    @Test fun appOpeningMustWaitForOwnerApprovalAndStayAllowlisted() {
        val request = NexaLiveToolPolicy.decide(context, "request_open_app", "{\"app\":\"brave\"}")
        assertTrue(request is NexaLiveToolPolicy.Decision.NeedsApproval)
        assertEquals("open brave", (request as NexaLiveToolPolicy.Decision.NeedsApproval).command)
        for (app in listOf("settings", "banking", "messages")) {
            val denied = NexaLiveToolPolicy.decide(context, "request_open_app", "{\"app\":\"$app\"}")
            assertTrue(denied is NexaLiveToolPolicy.Decision.Immediate)
        }
        assertTrue(NexaLiveToolPolicy.decide(context, "send_message", "{}")
            is NexaLiveToolPolicy.Decision.Immediate)
    }

    @Test fun silenceGateClosesAnIdleSessionButActivityExtendsIt() {
        val gate = NexaVoiceIdleGate()
        val silence = ByteArray(1280)
        gate.reset(1000L)
        repeat(6) { gate.observePcm(silence, silence.size, 20_000L + it * 40) }
        assertTrue(gate.inactive(47_000L))

        gate.reset(1000L)
        val speech = ByteArray(1280)
        for (i in speech.indices step 2) {
            speech[i] = 0x00
            speech[i+1] = 0x10
        }
        repeat(6) { gate.observePcm(speech, speech.size, 20_000L + it * 40) }
        assertFalse(gate.inactive(60_000L))
        assertTrue(gate.inactive(70_000L))
    }
}
