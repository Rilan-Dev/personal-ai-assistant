package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No network, stored API key, microphone or provider account is required. */
@RunWith(AndroidJUnit4::class)
class NexaSarvamResponseBoundsAndroidTest {
    private fun response(text:String):Response = Response.Builder()
        .request(Request.Builder().url("https://api.sarvam.ai/v1/chat/completions").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("Synthetic")
        .body(text.toResponseBody())
        .build()

    @Test fun smallProviderResponseIsReadExactly() {
        response("{\"ok\":true}").use { reply ->
            assertEquals("{\"ok\":true}",NexaSarvamResponseBounds.textOrNull(reply,20))
        }
    }

    @Test fun errorDiagnosticMustNotReadPastFiveKilobytes() {
        response("E".repeat(5_001)).use { reply ->
            assertNull(NexaSarvamResponseBounds.textOrNull(reply,
                NexaSarvamResponseBounds.MAX_ERROR_BYTES))
        }
        response("E".repeat(5_000)).use { reply ->
            assertEquals(5_000,NexaSarvamResponseBounds.textOrNull(reply,
                NexaSarvamResponseBounds.MAX_ERROR_BYTES)?.length)
        }
    }

    @Test fun oversizedChatResponseFailsClosedBeforeParsing() {
        response("x".repeat(140_001)).use { reply ->
            assertNull(NexaSarvamResponseBounds.textOrNull(reply,
                NexaSarvamResponseBounds.MAX_CHAT_BYTES))
        }
        response("x".repeat(140_000)).use { reply ->
            assertEquals(140_000,NexaSarvamResponseBounds.textOrNull(reply,
                NexaSarvamResponseBounds.MAX_CHAT_BYTES)?.length)
        }
    }

    @Test fun invalidLimitsRejectWithoutReading() {
        response("ok").use { reply ->
            assertNull(NexaSarvamResponseBounds.textOrNull(reply,0))
            assertNull(NexaSarvamResponseBounds.textOrNull(reply,Long.MAX_VALUE))
            assertTrue(NexaSarvamResponseBounds.MAX_CHAT_BYTES >=
                NexaSarvamResponseBounds.MAX_ERROR_BYTES)
        }
    }
}
