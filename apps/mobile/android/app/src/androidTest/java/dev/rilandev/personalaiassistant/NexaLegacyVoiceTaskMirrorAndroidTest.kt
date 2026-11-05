package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** The legacy executor remains unchanged. These tests cover audit-only
 * continuity, encrypted storage and permanent no-replay boundaries.
 */
@RunWith(AndroidJUnit4::class)
class NexaLegacyVoiceTaskMirrorAndroidTest {
    private val ctx:Context get()=ApplicationProvider.getApplicationContext()

    @Test fun voiceCheckpointAndCanonicalLedgerCommitTogetherWithoutMandates() {
        val taskId="task-"+UUID.randomUUID()
        val session="voice-"+UUID.randomUUID()
        val goal="open WhatsApp then read my screen"
        val steps=listOf("open WhatsApp","read my screen")
        assertTrue(NexaConversationDb.saveTask(ctx,taskId,session,"planned",0,goal,steps))
        assertTrue(NexaConversationDb.saveTask(ctx,taskId,session,
            "awaiting_approval",0,goal,steps))
        assertTrue(NexaConversationDb.saveTask(ctx,taskId,session,
            "verified",1,goal,steps,"Foreground verified"))
        assertTrue(NexaConversationDb.saveTask(ctx,taskId,session,
            "completed",2,goal,steps,"Screen inspected"))
        val helper=NexaConversationDb(ctx)
        try{
            val db=helper.readableDatabase
            db.rawQuery("SELECT status,policy_version,origin,goal_cipher FROM agent_tasks "+
                "WHERE task_id=?",arrayOf(taskId)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("checkpoint_completed",c.getString(0))
                assertEquals(NexaLegacyVoiceTaskMirror.POLICY,c.getString(1))
                assertEquals("live-voice",c.getString(2))
                assertFalse(c.getBlob(3).contentEquals(goal.toByteArray(Charsets.UTF_8)))
            }
            db.rawQuery("SELECT ordinal,tool_id,status,arguments_cipher FROM agent_task_steps "+
                "WHERE task_id=? ORDER BY ordinal",arrayOf(taskId)).use{c->
                for(index in steps.indices){
                    assertTrue(c.moveToNext())
                    assertEquals(index,c.getInt(0))
                    assertEquals("legacy.voice.checkpoint",c.getString(1))
                    assertEquals("checkpoint_advanced",c.getString(2))
                    assertFalse(c.getBlob(3).contentEquals(steps[index].toByteArray()))
                }
                assertFalse(c.moveToNext())
            }
            for(table in listOf("agent_mandates","agent_observations","agent_step_approvals")){
                val query=when(table){
                    "agent_mandates"->"SELECT COUNT(*) FROM agent_mandates WHERE task_id=?"
                    else->"SELECT COUNT(*) FROM $table WHERE step_id IN "+
                        "(SELECT step_id FROM agent_task_steps WHERE task_id=?)"
                }
                db.rawQuery(query,arrayOf(taskId)).use{c->
                    assertTrue(c.moveToFirst())
                    assertEquals("No Voice checkpoint grants authorization: "+table,
                        0,c.getInt(0))
                }
            }
        }finally{helper.close()}
        val index=NexaUnifiedTaskTimeline.recent(ctx,60)
        val found=(0 until index.length()).map{index.getJSONObject(it)}
            .filter{it.optString("taskId")==taskId}
        assertEquals("No duplicate Voice and native records",1,found.size)
        assertEquals("legacy_voice_checkpoint",found.first().getString("source"))
        assertEquals(0,found.first().getInt("verifiedSteps"))
    }

    @Test fun oldOrForgedVoiceWritesCannotGoBackwardsOrReplaceCompletedTask() {
        val id="task-"+UUID.randomUUID()
        val session="voice-"+UUID.randomUUID()
        val steps=listOf("open GitHub","read my screen")
        val goal="open GitHub and read my screen"
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,"planned",0,goal,steps))
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,"verified",1,goal,steps))
        assertFalse(NexaConversationDb.saveTask(ctx,id,session,
            "executing",0,goal,steps,"Stale worker update"))
        assertFalse(NexaConversationDb.saveTask(ctx,id,session+"-other",
            "verified",2,goal,steps))
        assertFalse(NexaConversationDb.saveTask(ctx,id,session,
            "verified",2,"Changed task goal",steps))
        assertFalse(NexaConversationDb.saveTask(ctx,id,session,
            "verified",2,goal,listOf("open WhatsApp","read my screen")))
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,"completed",2,goal,steps))
        assertFalse(NexaConversationDb.saveTask(ctx,id,session,
            "awaiting_approval",2,goal,steps))
        val rows=NexaConversationDb.taskHistory(ctx,200)
        val row=(0 until rows.length()).map{rows.getJSONObject(it)}
            .first{it.getString("taskId")==id}
        assertEquals("completed",row.getString("status"))
        assertEquals(2,row.getInt("stepIndex"))
        assertEquals(goal,row.getString("goal"))
    }

    @Test fun legacyAuditCannotOverwriteNativeAuthorizedTaskId() {
        val id="task-"+UUID.randomUUID()
        val owner="owner-"+UUID.randomUUID()
        assertTrue(NexaConversationDb.saveProposedAgentTask(
            ctx,id,owner,"chat","owner-review-v1","Review a quote"))
        assertFalse(NexaConversationDb.saveTask(
            ctx,id,"voice-"+UUID.randomUUID(),"planned",0,
            "open WhatsApp",listOf("open WhatsApp")))
        val helper=NexaConversationDb(ctx)
        try {
            helper.readableDatabase.rawQuery("SELECT origin,policy_version "+
                "FROM agent_tasks WHERE task_id=?",arrayOf(id)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("chat",c.getString(0))
                assertEquals("owner-review-v1",c.getString(1))
            }
            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM voice_task_checkpoints WHERE task_id=?",
                arrayOf(id)
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Cross-policy write rolled back",0,c.getInt(0))
            }
        }finally{helper.close()}
    }

    @Test fun interruptedVoiceCursorIsQuarantinedOnRestartWithoutReplay() {
        val id="task-"+UUID.randomUUID()
        val session="voice-"+UUID.randomUUID()
        val goal="open GitHub and read my screen"
        val steps=listOf("open GitHub","read my screen")
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,
            "planned",0,goal,steps))
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,
            "executing",0,goal,steps))
        assertTrue(NexaLegacyVoiceTaskMirror.recoverAbandonedAfterProcessRestart(ctx)>=1)
        val helper=NexaConversationDb(ctx)
        try{
            val db=helper.readableDatabase
            db.rawQuery("SELECT status FROM voice_task_checkpoints WHERE task_id=?",
                arrayOf(id)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("uncertain",c.getString(0))
            }
            db.rawQuery("SELECT status FROM agent_tasks WHERE task_id=?",
                arrayOf(id)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("uncertain",c.getString(0))
            }
            db.rawQuery(
                "SELECT COUNT(*) FROM agent_task_events "+
                    "WHERE task_id=? AND event_kind='voice_process_restart_no_replay'",
                arrayOf(id)
            ).use{c->
                assertTrue(c.moveToFirst())
                assertEquals(1,c.getInt(0))
            }
            db.rawQuery("SELECT COUNT(*) FROM agent_mandates WHERE task_id=?",
                arrayOf(id)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals(0,c.getInt(0))
            }
        }finally{helper.close()}
        assertFalse(NexaConversationDb.saveTask(ctx,id,session,
            "completed",2,goal,steps,"Stale callback from prior process"))
    }


    @Test fun terminalVoicePrivacyRedactionKeepsAuditIdentityAndReplayFence(){
        val id="task-"+UUID.randomUUID()
        val session="voice-"+UUID.randomUUID()
        val goal="open confidential client app and inspect one private screen"
        val steps=listOf("open confidential app","read private screen")
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,"planned",0,goal,steps))
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,
            "completed",2,goal,steps,"Private screen inspected"))
        assertTrue(NexaLegacyVoiceTaskMirror.redactTerminal(ctx,id))
        assertFalse("Redaction must be one-shot",
            NexaLegacyVoiceTaskMirror.redactTerminal(ctx,id))
        val history=NexaConversationDb.taskHistory(ctx,200)
        val voice=(0 until history.length()).map{history.getJSONObject(it)}
            .first{it.getString("taskId")==id}
        assertEquals("[details cleared]",voice.getString("goal"))
        assertEquals("",voice.getString("lastEvidence"))
        assertEquals("[details cleared]",voice.getJSONArray("steps").getString(0))
        assertEquals("[details cleared]",voice.getJSONArray("steps").getString(1))
        assertFalse(history.toString().contains(goal))
        val helper=NexaConversationDb(ctx)
        try {
            val db=helper.readableDatabase
            db.rawQuery("SELECT goal_cipher,status FROM agent_tasks WHERE task_id=?",
                arrayOf(id)).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("[details cleared]",
                    NexaConversationDb.openNativeTaskArguments(c.getBlob(0)))
                assertEquals("checkpoint_completed",c.getString(1))
            }
            db.rawQuery("SELECT COUNT(*) FROM agent_task_events WHERE task_id=? "+
                "AND event_kind='owner_redacted_legacy_voice'",
                arrayOf(id)).use{c->
                assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0))
            }
            db.rawQuery("SELECT COUNT(*) FROM agent_mandates WHERE task_id=?",
                arrayOf(id)).use{c->
                assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0))
            }
        }finally{helper.close()}
        val index=NexaUnifiedTaskTimeline.recent(ctx,60)
        val row=(0 until index.length()).map{index.getJSONObject(it)}
            .first{it.getString("taskId")==id}
        assertTrue(row.getBoolean("detailsRedacted"))
        assertEquals("legacy_voice_checkpoint",row.getString("source"))
        assertEquals(0,row.getInt("verifiedSteps"))
    }

}
