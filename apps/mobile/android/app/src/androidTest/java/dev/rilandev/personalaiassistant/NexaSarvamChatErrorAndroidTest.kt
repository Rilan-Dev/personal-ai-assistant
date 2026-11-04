package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic Sarvam HTTP errors. No real API key, speech or external call. */
@RunWith(AndroidJUnit4::class)
class NexaSarvamChatErrorAndroidTest {
    @Test fun unclassified400DoesNotLeakPrivateProviderErrorOrRetry() {
        val raw="""{"error":{"code":"invalid_request_error","message":"secret-key-123 is invalid"}}"""
        val d=NexaSarvamChatError.classify(400,raw)
        assertEquals("invalid_request",d.code)
        assertFalse(d.safeTextOnlyRetry)
        assertFalse(d.explanation.contains("secret"))
        assertFalse(d.explanation.contains("123"))
    }
    @Test fun onlyExplicitToolSchemaRejectionAllowsOneTextOnlyRetry() {
        val d=NexaSarvamChatError.classify(400,
            """{"error":{"code":"invalid_request_error","message":"tools not supported"}}""")
        assertEquals("unsupported_tool_schema",d.code)
        assertTrue(d.safeTextOnlyRetry)
        assertFalse(NexaSarvamChatError.classify(400,"not-json").safeTextOnlyRetry)
        assertEquals("context_limit",
            NexaSarvamChatError.classify(400,
                """{"error":{"message":"maximum context length exceeded"}}""").code)
        assertFalse(NexaSarvamChatError.classify(429,"{}").safeTextOnlyRetry)
    }
    @Test fun credentialsAndProviderErrorsNeverEchoRawPayload() {
        for(code in listOf(401,403,429,500,502,503)) {
            val d=NexaSarvamChatError.classify(code,
                """{"error":{"message":"private@example.com token_please_do_not_log"}}""")
            assertFalse(d.safeTextOnlyRetry)
            assertFalse(d.explanation.contains("private"))
            assertFalse(d.explanation.contains("token_"))
        }
    }
}
