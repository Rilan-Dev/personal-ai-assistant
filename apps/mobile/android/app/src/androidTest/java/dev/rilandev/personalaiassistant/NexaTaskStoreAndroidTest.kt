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

/** Native verification of the Phase-2 non-executable, durable task plan store. */
@RunWith(AndroidJUnit4::class)
class NexaTaskStoreAndroidTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun newTask(origin:String="chat",policy:String="owner-review-v1",
                        session:String="owner-"+UUID.randomUUID()):String {
        val id="task-"+UUID.randomUUID()
        assertTrue(NexaConversationDb.saveProposedAgentTask(
            context,id,session,origin,policy,"Private owner request "+UUID.randomUUID()))
        return id
    }
    private fun step(id:String="step-0001",tool:String="device.battery_status",
                     dependencies:List<String> = emptyList()):JSONObject =
        JSONObject().put("stepId",id).put("toolId",tool)
            .put("input",JSONObject())
            .put("dependsOn",JSONArray(dependencies))
            .put("risk","low")
            .put("expectedEvidence","native_reading")
            .put("requiresApproval",false)

    private fun plan(taskId:String,steps:List<JSONObject> = listOf(step())):JSONObject =
        JSONObject().put("version",1)
            .put("taskId",taskId)
            .put("ownerRequestId",taskId)
            .put("origin","chat")
            .put("policyVersion","owner-review-v1")
            .put("expiresAtMs",System.currentTimeMillis()+120_000L)
            .put("steps",JSONArray(steps))
            .put("executable",false)

    @Test fun ownerTaskReadPlanIsSavedAtomicallyAndNeverAuthorizesExecution() {
        val id=newTask()
        val p=plan(id,listOf(step(),step("step-0002","device.status",listOf("step-0001"))))
        assertTrue(NexaTaskStore.admitOwnerReadOnlyPlan(context,p.toString()))
        assertFalse("A plan cannot be admitted twice",
            NexaTaskStore.admitOwnerReadOnlyPlan(context,p.toString()))
        val summary=NexaTaskStore.readSafeStepSummary(context,id)
        assertEquals(2,summary.length())
        assertEquals("planned",summary.getJSONObject(0).getString("status"))
        assertEquals("device.status",summary.getJSONObject(1).getString("toolId"))
        val db=NexaConversationDb(context)
        try {
            db.readableDatabase.rawQuery(
                "SELECT status,plan_revision FROM agent_tasks WHERE task_id=?",
                arrayOf(id)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("proposed",c.getString(0))
                assertEquals(1,c.getInt(1))
            }
            db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM agent_mandates WHERE task_id=?",arrayOf(id)
            ).use{c->
                assertTrue(c.moveToFirst())
                assertEquals(0,c.getInt(0))
            }
            db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM agent_step_approvals WHERE step_id IN (SELECT step_id FROM agent_task_steps WHERE task_id=?)",
                arrayOf(id)
            ).use{c->
                assertTrue(c.moveToFirst())
                assertEquals(0,c.getInt(0))
            }
            db.readableDatabase.rawQuery(
                "SELECT event_kind,detail_cipher FROM agent_task_events WHERE task_id=? ORDER BY id",
                arrayOf(id)
            ).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("proposed",c.getString(0))
                assertTrue(c.moveToNext())
                assertEquals("read_plan_admitted",c.getString(0))
                assertNotNull(c.getBlob(1))
                assertFalse(c.moveToNext())
            }
        } finally {db.close()}
    }

    @Test fun rejectsProviderAuthorityForgingOrUnsafeToolWithoutPartialSteps() {
        val id=newTask()
        val forged=plan(id,listOf(step(tool="messaging.send")))
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,forged.toString()))
        forged.put("steps",JSONArray().put(step().put("requiresApproval",true)))
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,forged.toString()))
        forged.put("steps",JSONArray().put(step()))
        forged.put("executable",true)
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,forged.toString()))
        forged.put("executable",false)
        forged.put("ownerRequestId","provider-owner-identity")
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,forged.toString()))
        forged.put("ownerRequestId",id)
        forged.put("approved",true)
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,forged.toString()))
        assertEquals(0,NexaTaskStore.readSafeStepSummary(context,id).length())
    }

    @Test fun refusesCrossOriginTaskAndCancelledOrExpiredPlans() {
        val voice=newTask(origin="live-voice",policy="owner-review-v1",
            session="voice-"+UUID.randomUUID())
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,plan(voice).toString()))
        val cancelled=newTask()
        assertTrue(NexaConversationDb.cancelProposedAgentTask(context,cancelled))
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,plan(cancelled).toString()))
        val expired=newTask()
        val old=plan(expired).put("expiresAtMs",System.currentTimeMillis()-1_000)
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,old.toString()))
    }

    @Test fun rejectsInvalidDependencyWithoutWritingPartialPlan() {
        val id=newTask()
        val dangling=plan(id,listOf(step(),
            step("step-0002","device.status",listOf("step-not-found"))))
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,dangling.toString()))
        val circular=plan(id,listOf(
            step("step-0001","device.status",listOf("step-0002")),
            step("step-0002","device.status",listOf("step-0001"))))
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,circular.toString()))
        assertEquals(0,NexaTaskStore.readSafeStepSummary(context,id).length())
    }

    @Test fun cancellationRemainsTerminalAfterReadOnlyPlanStored() {
        val id=newTask()
        assertTrue(NexaTaskStore.admitOwnerReadOnlyPlan(context,plan(id).toString()))
        assertTrue(NexaConversationDb.cancelProposedAgentTask(
            context,id,"owner-review-v1"))
        assertFalse(NexaTaskStore.admitOwnerReadOnlyPlan(context,plan(id).toString()))
        assertFalse(NexaConversationDb.cancelProposedAgentTask(
            context,id,"owner-review-v1"))
        assertEquals(1,NexaTaskStore.readSafeStepSummary(context,id).length())
    }
}
