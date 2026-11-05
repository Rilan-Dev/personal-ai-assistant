package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Durable post-process-death reconciliation, with no repeat Android read. */
@RunWith(AndroidJUnit4::class)
class NexaTaskCoordinatorRecoveryAndroidTest {
    @Test fun staleClaimBecomesUncertainAndMandateIsRevokedExactlyOnce() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val task="task-"+UUID.randomUUID()
        val step="step-"+UUID.randomUUID()
        val policy="native-fact-v1"
        assertTrue(NexaConversationDb.saveProposedAgentTask(
            ctx,task,"owner-"+UUID.randomUUID(),"chat",policy,"Battery reading"))
        val helper=NexaConversationDb(ctx)
        try {
            val db=helper.writableDatabase
            val ago=System.currentTimeMillis()-300_000L
            db.execSQL("UPDATE agent_tasks SET status='running',updated_at=? WHERE task_id=?",
                arrayOf(ago,task))
            db.execSQL(
                "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id,status,risk,expected_evidence,arguments_cipher) " +
                    "VALUES(?,?,0,'get_battery_level','outcome_unknown','low','native_reading',?)",
                arrayOf(step,task,NexaConversationDb.sealNativeTaskArguments("{}"))
            )
            db.execSQL(
                "INSERT INTO agent_mandates(mandate_id,task_id,owner_request_id,scope_digest,policy_version,expires_at) " +
                    "VALUES(?,?,?,?,?,?)",
                arrayOf("mandate-"+UUID.randomUUID(),task,"request-"+UUID.randomUUID(),
                    "a".repeat(64),policy,System.currentTimeMillis()+600_000L)
            )
        } finally {helper.close()}

        assertEquals(1,NexaTaskCoordinator.recoverStaleInFlight(ctx,60_000L))
        assertEquals("No double recovery",0,
            NexaTaskCoordinator.recoverStaleInFlight(ctx,60_000L))
        val review=NexaConversationDb(ctx)
        try {
            val db=review.readableDatabase
            db.rawQuery("SELECT status FROM agent_tasks WHERE task_id=?",arrayOf(task)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("uncertain",c.getString(0))
            }
            db.rawQuery("SELECT status FROM agent_task_steps WHERE step_id=?",arrayOf(step)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("uncertain",c.getString(0))
            }
            db.rawQuery("SELECT revoked_at FROM agent_mandates WHERE task_id=?",arrayOf(task)).use{c->
                assertTrue(c.moveToFirst())
                assertTrue("Mandate is revoked",c.getLong(0)>0)
            }
            db.rawQuery(
                "SELECT COUNT(*) FROM agent_task_events WHERE task_id=? AND event_kind='recovered_uncertain_no_replay'",
                arrayOf(task)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals(1,c.getInt(0))
            }
        } finally{review.close()}
    }
}
