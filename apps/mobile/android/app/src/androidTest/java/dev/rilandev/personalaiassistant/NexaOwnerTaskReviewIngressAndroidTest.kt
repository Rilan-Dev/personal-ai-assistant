package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaOwnerTaskReviewIngressAndroidTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

    @After fun clear(){NexaOwnerTaskReview.clear()}

    @Test fun allNativeInputSurfacesPreserveOriginWhenOwnerSavesOnce(){
        for(origin in listOf("live-voice","assistant-role","overlay","chat")){
            NexaOwnerTaskReview.clear()
            val goal="Follow up on owner request from "+origin
            assertTrue(NexaOwnerTaskReview.queue(goal,origin,1_000L))
            val receipt=NexaOwnerTaskReview.takeEnvelope(1_100L)
            assertNotNull(receipt)
            assertEquals(goal,receipt!!.getString("goal"))
            assertEquals(origin,receipt.getString("origin"))
            val token=receipt.getString("token")
            assertNull("One-time presentation receipt",
                NexaOwnerTaskReview.takeEnvelope(1_200L))
            val id=NexaOwnerTaskReview.saveViewed(context,token,goal,1_300L)
            assertNotNull("Owner Save creates a native task",id)
            assertNull("Consumed receipt cannot create a duplicate",
                NexaOwnerTaskReview.saveViewed(context,token,goal,1_400L))
            val db=NexaConversationDb(context)
            try{
                db.readableDatabase.rawQuery(
                    "SELECT origin,status,policy_version,session_id FROM agent_tasks WHERE task_id=?",
                    arrayOf(id)
                ).use{row->
                    assertTrue(row.moveToFirst())
                    assertEquals(origin,row.getString(0))
                    assertEquals("proposed",row.getString(1))
                    assertEquals("owner-review-v1",row.getString(2))
                    assertTrue(row.getString(3).startsWith("owner-"))
                }
                db.readableDatabase.rawQuery(
                    "SELECT COUNT(*) FROM agent_mandates WHERE task_id=?",
                    arrayOf(id)
                ).use{row->
                    assertTrue(row.moveToFirst())
                    assertEquals("Review never grants execution mandate",0,row.getInt(0))
                }
            }finally{db.close()}
        }
    }

    @Test fun forgedOrEditedHandoffCannotMintAnOriginOrDuplicateTask(){
        val goal="Make a note to review expenses"
        assertFalse(NexaOwnerTaskReview.queue(goal,"provider-minted",1_000L))
        assertTrue(NexaOwnerTaskReview.queue(goal,"live-voice",1_000L))
        assertFalse("Only one pending user review at a time",
            NexaOwnerTaskReview.queue("Another task request","overlay",1_100L))
        val receipt=NexaOwnerTaskReview.takeEnvelope(1_200L)!!
        val token=receipt.getString("token")
        assertFalse(NexaOwnerTaskReview.queue("Another task request","overlay",1_250L))
        assertNull(NexaOwnerTaskReview.saveViewed(context,
            "00000000-0000-0000-0000-000000000000",goal,1_300L))
        assertNull(NexaOwnerTaskReview.saveViewed(context,
            token,"Changed content not in owner's original request",1_300L))
        assertNotNull(NexaOwnerTaskReview.saveViewed(context,token,goal,1_300L))
    }

    @Test fun tokenCannotBePresentedAfterDeadlineOrUsedAfterClear(){
        val goal="Prepare my review notes"
        assertTrue(NexaOwnerTaskReview.queue(goal,"assistant-role",1_000L))
        assertNull(NexaOwnerTaskReview.takeEnvelope(62_000L))
        assertTrue(NexaOwnerTaskReview.queue(goal,"overlay",63_000L))
        val next=NexaOwnerTaskReview.takeEnvelope(63_100L)!!
        assertNull(NexaOwnerTaskReview.saveViewed(context,
            next.getString("token"),goal,364_000L))
        NexaOwnerTaskReview.clear()
        assertNull(NexaOwnerTaskReview.saveViewed(context,
            next.getString("token"),goal,63_200L))
    }

    @Test fun malformedControlCharactersAndLongGoalsCannotQueue(){
        assertFalse(NexaOwnerTaskReview.queue("Hi","chat",1_000L))
        assertFalse(NexaOwnerTaskReview.queue("A".repeat(321),"chat",1_000L))
        assertFalse(NexaOwnerTaskReview.queue("Review\nprivate","chat",1_000L))
        assertNull(NexaOwnerTaskReview.takeEnvelope(1_100L))
    }

    @Test fun voiceTaskCanBeCorrectedCancelledAndRedactedWithOwnerControls(){
        val goal="Review supplier documents with owner"
        assertTrue(NexaOwnerTaskReview.queue(goal,"live-voice",1_000L))
        val receipt=NexaOwnerTaskReview.takeEnvelope(1_100L)!!
        val id=NexaOwnerTaskReview.saveViewed(context,
            receipt.getString("token"),goal,1_200L)!!
        val history=NexaConversationDb.agentTaskHistory(context,50,manualOnly=true)
        val saved=(0 until history.length()).map{history.getJSONObject(it)}
            .firstOrNull{it.getString("taskId")==id}
        assertNotNull(saved)
        assertEquals("live-voice",saved!!.getString("origin"))
        assertTrue(NexaTaskLifecycle.reviseOwnerProposal(context,id,1,
            "Corrected owner request for supplier review"))
        assertTrue(NexaConversationDb.cancelProposedAgentTask(
            context,id,"owner-review-v1"))
        assertTrue(NexaConversationDb.redactCancelledOwnerProposal(context,id))
        val after=NexaConversationDb.agentTaskHistory(context,50,manualOnly=true)
        val redacted=(0 until after.length()).map{after.getJSONObject(it)}
            .first{it.getString("taskId")==id}
        assertEquals("[details cleared]",redacted.getString("goal"))
        assertEquals("cancelled",redacted.getString("status"))
        assertFalse(NexaTaskLifecycle.reviseOwnerProposal(context,id,2,
            "Do not resurrect a cancelled task"))
    }


    @Test fun deliberateDiscardReleasesQueueButNeverCreatesARecord() {
        val goal="Review tomorrow's project estimate"
        assertTrue(NexaOwnerTaskReview.queue(goal,"overlay",1_000L))
        val receipt=NexaOwnerTaskReview.takeEnvelope(1_100L)!!
        val token=receipt.getString("token")
        assertFalse(NexaOwnerTaskReview.discardViewed(
            "00000000-0000-0000-0000-000000000000"))
        assertTrue(NexaOwnerTaskReview.discardViewed(token))
        assertFalse(NexaOwnerTaskReview.discardViewed(token))
        assertNull(NexaOwnerTaskReview.saveViewed(context,token,goal,1_200L))
        assertTrue(NexaOwnerTaskReview.queue("Review a different request",
            "assistant-role",1_300L))
    }

}
