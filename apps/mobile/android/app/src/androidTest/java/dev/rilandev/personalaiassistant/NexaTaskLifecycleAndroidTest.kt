package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NexaTaskLifecycleAndroidTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

    private fun manualTask(policy:String="owner-review-v1"):String {
        val id="task-"+UUID.randomUUID()
        assertTrue(NexaConversationDb.saveProposedAgentTask(context,
            id,"owner-"+UUID.randomUUID(),"chat",policy,"Original secret goal"))
        return id
    }
    private fun draftPlan(taskId:String):String=JSONObject()
        .put("version",1)
        .put("taskId",taskId)
        .put("ownerRequestId",taskId)
        .put("origin","chat")
        .put("policyVersion","owner-review-v1")
        .put("expiresAtMs",System.currentTimeMillis()+120_000)
        .put("steps",JSONArray().put(JSONObject()
            .put("stepId","step-0001")
            .put("toolId","device.battery_status")
            .put("input",JSONObject())
            .put("dependsOn",JSONArray())
            .put("risk","low")
            .put("expectedEvidence","native_reading")
            .put("requiresApproval",false)))
        .put("executable",false).toString()

    @Test fun revisionIsCompareAndSwapAndHistoryContainsNoSecrets() {
        val id=manualTask()
        assertTrue(NexaTaskLifecycle.reviseOwnerProposal(
            context,id,1,"Corrected private goal"))
        assertFalse("Old revision must not overwrite latest correction",
            NexaTaskLifecycle.reviseOwnerProposal(context,id,1,"Stale correction"))
        val detail=NexaTaskLifecycle.ownerReviewDetail(context,id)
        assertNotNull(detail)
        assertEquals(2,detail!!.getInt("revision"))
        assertEquals("proposed",detail.getString("status"))
        assertFalse("No goal or argument contents in metadata-only history",
            detail.toString().contains("Corrected private goal"))
        val events=detail.getJSONArray("events")
        assertEquals("owner_revised_plan_invalidated",
            events.getJSONObject(0).getString("kind"))
        val latest=NexaConversationDb.agentTaskHistory(context,50,manualOnly=true)
        val row=(0 until latest.length()).map{latest.getJSONObject(it)}
            .firstOrNull{it.optString("taskId")==id}
        assertNotNull(row)
        assertEquals("Corrected private goal",row!!.getString("goal"))
        assertEquals(2,row.getInt("revision"))
    }

    @Test fun correctingReviewTaskInvalidatesEveryUnclaimedDraftStep() {
        val id=manualTask()
        assertTrue(NexaTaskStore.admitOwnerReadOnlyPlan(context,draftPlan(id)))
        assertEquals(1,NexaTaskStore.readSafeStepSummary(context,id).length())
        assertTrue(NexaTaskLifecycle.reviseOwnerProposal(
            context,id,1,"Different request, all tentative steps invalid"))
        assertEquals(0,NexaTaskStore.readSafeStepSummary(context,id).length())
        val details=NexaTaskLifecycle.ownerReviewDetail(context,id)
        assertEquals(0,details!!.getJSONArray("steps").length())
        // A newly corrected owner proposal may still be reviewed; it remains
        // non-executable and a prior draft can never be replayed.
        assertTrue(NexaTaskStore.admitOwnerReadOnlyPlan(context,draftPlan(id)))
        assertEquals(1,NexaTaskStore.readSafeStepSummary(context,id).length())
    }

    @Test fun refusesCorrectionAfterMandateOrCancellation() {
        val mandated=manualTask()
        val helper=NexaConversationDb(context)
        try {
            helper.writableDatabase.execSQL(
                "INSERT INTO agent_mandates(mandate_id,task_id,owner_request_id,"+
                    "scope_digest,policy_version,expires_at) VALUES(?,?,?,?,?,?)",
                arrayOf("mandate-"+UUID.randomUUID(),mandated,"request-"+UUID.randomUUID(),
                    "0".repeat(64),"owner-review-v1",System.currentTimeMillis()+60_000L)
            )
        } finally {helper.close()}
        assertFalse(NexaTaskLifecycle.reviseOwnerProposal(
            context,mandated,1,"Cannot change mandated task"))
        val cancelled=manualTask()
        assertTrue(NexaConversationDb.cancelProposedAgentTask(
            context,cancelled,"owner-review-v1"))
        assertFalse(NexaTaskLifecycle.reviseOwnerProposal(
            context,cancelled,1,"Cannot revive cancellation"))
    }

    @Test fun ownerReviewDoesNotExposeVoiceTaskOrProviderIdentity() {
        val voice="task-"+UUID.randomUUID()
        assertTrue(NexaConversationDb.saveProposedAgentTask(context,
            voice,"voice-"+UUID.randomUUID(),"live-voice",
            "owner-review-v1","Private live voice goal"))
        assertNull(NexaTaskLifecycle.ownerReviewDetail(context,voice))
        assertFalse(NexaTaskLifecycle.reviseOwnerProposal(
            context,voice,1,"Provider-forged correction"))
        assertNull(NexaTaskLifecycle.ownerReviewDetail(context,"bad"))
        assertFalse(NexaTaskLifecycle.reviseOwnerProposal(
            context,"bad",1,"Malformed identity"))
    }

    @Test fun legacyManualPreviewCanBeCorrectedButNeverExecuted() {
        val id=manualTask("owner-preview-v1")
        assertTrue(NexaTaskLifecycle.reviseOwnerProposal(
            context,id,1,"Older owner note corrected"))
        val details=NexaTaskLifecycle.ownerReviewDetail(context,id)
        assertEquals(2,details!!.getInt("revision"))
        assertEquals(0,details.getJSONArray("steps").length())
    }
}
