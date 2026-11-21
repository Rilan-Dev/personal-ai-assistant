package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcaDeviceSessionValidationAndroidTest {
    private val nowMillis = 1_795_219_200_000L
    private val origin = "https://aca-development.invalid"
    private val organizationId = "00000000-0000-0000-0000-000000000002"
    private val agentId = "00000000-0000-0000-0000-000000000004"

    private fun payload(checkpoint: JSONObject? = null): JSONObject = JSONObject()
        .put("version", 1)
        .put("origin", origin)
        .put("token", "nexa_m1_" + "A".repeat(43))
        .put("expiresAt", "2026-11-22T00:00:00.000Z")
        .put("organizationId", organizationId)
        .put("agentId", agentId)
        .apply { if (checkpoint != null) put("chatConversation", checkpoint) }

    private fun checkpoint(
        organization: String = organizationId,
        agent: String = agentId,
        conversation: String = "00000000-0000-0000-0000-000000000006",
    ) = JSONObject()
        .put("organizationId", organization)
        .put("agentId", agent)
        .put("conversationId", conversation)

    @Test fun acceptsCanonicalSessionAndSameScopeConversationCheckpoint() {
        assertTrue(AgentJournalModule.validateAcaDeviceSessionPayload(
            payload(checkpoint()).toString(), origin, nowMillis))
    }

    @Test fun acceptsSessionWithoutConversationCheckpoint() {
        assertTrue(AgentJournalModule.validateAcaDeviceSessionPayload(
            payload().toString(), origin, nowMillis))
    }

    @Test fun rejectsCrossAgentAndCrossOrganizationCheckpoints() {
        val otherOrganization = "00000000-0000-0000-0000-000000000008"
        val otherAgent = "00000000-0000-0000-0000-000000000009"
        assertFalse(AgentJournalModule.validateAcaDeviceSessionPayload(
            payload(checkpoint(organization = otherOrganization)).toString(), origin, nowMillis))
        assertFalse(AgentJournalModule.validateAcaDeviceSessionPayload(
            payload(checkpoint(agent = otherAgent)).toString(), origin, nowMillis))
    }

    @Test fun rejectsMalformedCheckpointUnknownFieldsAndExpiredSession() {
        assertFalse(AgentJournalModule.validateAcaDeviceSessionPayload(
            payload(JSONObject().put("organizationId", organizationId)).toString(), origin, nowMillis))
        assertFalse(AgentJournalModule.validateAcaDeviceSessionPayload(
            payload(checkpoint()).put("provider", "untrusted-override").toString(), origin, nowMillis))
        assertFalse(AgentJournalModule.validateAcaDeviceSessionPayload(
            payload().put("expiresAt", "2026-11-20T00:00:00.000Z").toString(), origin, nowMillis))
    }
}
