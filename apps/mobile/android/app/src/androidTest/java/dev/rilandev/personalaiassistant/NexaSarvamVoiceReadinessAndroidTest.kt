package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/**
 * Offline native checks: no provider key, network connection or microphone.
 * Actual account authorization is tested with the user's rotated key locally.
 */
@RunWith(AndroidJUnit4::class)
class NexaSarvamVoiceReadinessAndroidTest {
    @Test fun signedCallUrlCanBeCheckedWithoutLeakingToken() {
        val payload = JSONObject()
            .put("url", "wss://voice.sarvam.ai/session?token=never-log-this")
            .put("reference_id", "test-interaction-reference")
            .toString()
        val result = SarvamVoiceReadiness.classify(200, payload)
        assertTrue(result.ready)
        assertEquals("AUTHORIZED", result.code)
        assertFalse(result.explanation.contains("never-log-this"))
        assertFalse(result.explanation.contains("test-interaction-reference"))
    }

    @Test fun malformedOrUntrustedSignedUrlMustNotBeUsed() {
        for (url in listOf(
            "ws://voice.sarvam.ai/unencrypted",
            "wss://evil.example/session",
            "wss://voice.sarvam.ai.evil.example/session",
            "wss://user@voice.sarvam.ai/session",
            "wss://voice.sarvam.ai:8888/session"
        )) {
            val payload = JSONObject().put("url", url).put("reference_id", "reference")
            assertEquals("BAD_SIGNED_URL",
                SarvamVoiceReadiness.classify(200, payload.toString()).code)
        }
        assertEquals("MALFORMED", SarvamVoiceReadiness.classify(200, "not JSON").code)
    }

    @Test fun authorizationFailuresExposeOnlySafeCodes() {
        assertEquals("AUTH", SarvamVoiceReadiness.classify(401,"private").code)
        assertEquals("AGENT_NOT_FOUND", SarvamVoiceReadiness.classify(404,"private").code)
        assertEquals("RATE_LIMIT", SarvamVoiceReadiness.classify(429,"private").code)
        assertEquals("INVALID_AGENT", SarvamVoiceReadiness.classify(422,"private").code)
        assertEquals("PROVIDER_ERROR", SarvamVoiceReadiness.classify(503,"private").code)
    }
}
