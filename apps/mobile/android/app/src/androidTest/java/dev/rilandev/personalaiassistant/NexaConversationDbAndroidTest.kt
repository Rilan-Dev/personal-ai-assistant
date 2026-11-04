package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NexaConversationDbAndroidTest {
    @Test fun encryptedConversationRoundTripAndExport() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val session="android-test-"+UUID.randomUUID()
        val unique="unique-private-conversation-"+UUID.randomUUID()
        NexaConversationDb.append(context,session,"voice","sarvam-api","user",unique)
        val history=NexaConversationDb.history(context,200)
        val item=(0 until history.length()).map { history.getJSONObject(it) }
            .firstOrNull { it.optString("sessionId")==session }
        assertNotNull("Conversation must persist",item)
        assertEquals(unique,item!!.optString("text"))
        assertEquals("voice",item.optString("channel"))
        assertEquals("sarvam-api",item.optString("provider"))
        val db=NexaConversationDb(context)
        try {
            db.readableDatabase.rawQuery(
                "SELECT body FROM conversation_entries WHERE session_id=?",arrayOf(session)
            ).use {
                assertTrue(it.moveToFirst())
                val ciphertext=it.getBlob(0)
                assertFalse(ciphertext.contentEquals(unique.toByteArray(Charsets.UTF_8)))
                assertTrue(ciphertext.size>unique.toByteArray(Charsets.UTF_8).size)
            }
        } finally {db.close()}
        val exported=JSONObject(NexaConversationDb.export(context))
        assertEquals(3,exported.getInt("schemaVersion"))
        assertTrue(exported.has("tasks"))
        assertTrue(exported.getJSONArray("entries").toString().contains(unique))
    }

    @Test fun persistsDurableTaskCheckpointWithExistingConversationData() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val id="task-"+UUID.randomUUID()
        val session="voice-test-"+UUID.randomUUID()
        val goal="open WhatsApp and read my screen"
        val steps=listOf("open WhatsApp","read my screen")
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,
            "planned",0,goal,steps))
        assertTrue(NexaConversationDb.saveTask(ctx,id,session,
            "completed",2,goal,steps,"Verified foreground and visible screen labels"))
        val saved=NexaConversationDb.taskHistory(ctx,200)
        val entry=(0 until saved.length()).map{saved.getJSONObject(it)}
            .firstOrNull{it.optString("taskId")==id}
        assertNotNull("Task must survive a new SQLite helper",entry)
        assertEquals("completed",entry!!.optString("status"))
        assertEquals(2,entry.optInt("stepIndex"))
        assertEquals(goal,entry.optString("goal"))
        assertFalse(NexaConversationDb.saveTask(ctx,id,session,
            "sent",2,goal,steps))
    }
    @Test fun v3ClaimTombstonePersistsIndependentlyOfAudits() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val id=UUID.randomUUID().toString().replace("-","").repeat(2)
        val packageDigest=UUID.randomUUID().toString().replace("-","").repeat(2)
        assertTrue(NexaConversationDb.reserveUiActionClaim(ctx,id,"tap",packageDigest))
        assertFalse("Existing durable claim must never be reused",
            NexaConversationDb.reserveUiActionClaim(ctx,id,"tap",packageDigest))
        val helper=NexaConversationDb(ctx)
        try {
            assertEquals(3,helper.readableDatabase.version)
            helper.readableDatabase.rawQuery(
                "SELECT token_digest,package_digest FROM agent_action_claims WHERE token_digest=?",
                arrayOf(id)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(id,cursor.getString(0))
                assertEquals(packageDigest,cursor.getString(1))
            }
        } finally {helper.close()}
        assertFalse(NexaConversationDb.reserveUiActionClaim(ctx,"bad","tap",packageDigest))
    }

    @Test fun isolatedV1AndV2DatabaseMigrateWithoutLosingRows() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        for(oldVersion in 1..2) {
            val name="nexa-migration-test-"+UUID.randomUUID()+".db"
            val old=ctx.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
            try {
                old.execSQL("""CREATE TABLE conversation_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL, channel TEXT NOT NULL,
                    provider TEXT NOT NULL, role TEXT NOT NULL,
                    body BLOB NOT NULL, created_at INTEGER NOT NULL
                )""")
                old.execSQL("""INSERT INTO conversation_entries(
                    session_id,channel,provider,role,body,created_at
                ) VALUES('legacy','chat','test','user',X'010203',1)""")
                if(oldVersion==2) {
                    old.execSQL("""CREATE TABLE voice_task_checkpoints (
                        task_id TEXT PRIMARY KEY, session_id TEXT NOT NULL,
                        status TEXT NOT NULL, step_index INTEGER NOT NULL,
                        payload BLOB NOT NULL, updated_at INTEGER NOT NULL
                    )""")
                    old.execSQL("""INSERT INTO voice_task_checkpoints(
                        task_id,session_id,status,step_index,payload,updated_at
                    ) VALUES('task-legacy','legacy','planned',0,X'0A0B',1)""")
                }
                old.setVersion(oldVersion)
            } finally {old.close()}
            val upgraded=NexaConversationDb(ctx,name)
            try {
                val db=upgraded.writableDatabase
                assertEquals(3,db.version)
                db.rawQuery("SELECT COUNT(*) FROM conversation_entries WHERE session_id='legacy'",null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1,it.getInt(0))
                }
                db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='agent_action_claims'",null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1,it.getInt(0))
                }
                db.rawQuery("SELECT COUNT(*) FROM voice_task_checkpoints",null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(if(oldVersion==2) 1 else 0,it.getInt(0))
                }
                for(table in listOf("agent_tasks","agent_task_steps","agent_task_events",
                    "agent_mandates","agent_step_approvals","agent_observations")) {
                    db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                        arrayOf(table)).use { c ->
                        assertTrue(c.moveToFirst())
                        assertEquals("Missing v3 task table $table",1,c.getInt(0))
                    }
                }
                db.rawQuery("PRAGMA foreign_keys",null).use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("Child task rows require real foreign keys",1,c.getInt(0))
                }

            } finally {
                upgraded.close()
                ctx.deleteDatabase(name)
            }
        }
    }

    @Test fun v3TaskStepsRejectOrphanForeignKeys() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val name="nexa-v3-task-fk-"+UUID.randomUUID()+".db"
        val helper=NexaConversationDb(ctx,name)
        try {
            val db=helper.writableDatabase
            assertEquals(3,db.version)
            var refused=false
            try {
                db.execSQL("""INSERT INTO agent_task_steps(
                    step_id,task_id,ordinal,tool_id,status,risk,expected_evidence,arguments_cipher
                ) VALUES('step-orphan','nonexistent',0,'device.battery_status',
                    'planned','low','native_reading',X'0102')""")
            }catch(_:android.database.sqlite.SQLiteConstraintException) {
                refused=true
            }
            assertTrue("Orphan native task step must be blocked",refused)
            db.rawQuery("SELECT COUNT(*) FROM agent_task_steps",null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0,c.getInt(0))
            }
        } finally {
            helper.close()
            ctx.deleteDatabase(name)
        }
    }

    @Test fun v3ProposedTaskIsEncryptedAndNeverExecutable() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val task="task-"+UUID.randomUUID().toString()
        val session="chat-"+UUID.randomUUID().toString()
        val secretGoal="private task goal "+UUID.randomUUID()
        assertTrue(NexaConversationDb.saveProposedAgentTask(
            ctx,task,session,"chat","policy-v1",secretGoal))
        assertFalse("Task ID reuse must not overwrite a proposal",
            NexaConversationDb.saveProposedAgentTask(
                ctx,task,session,"chat","policy-v1","another goal"))
        assertFalse(NexaConversationDb.saveProposedAgentTask(
            ctx,"bad",session,"chat","policy-v1",secretGoal))
        assertFalse(NexaConversationDb.saveProposedAgentTask(
            ctx,"task-"+UUID.randomUUID(),session,"provider","policy-v1",secretGoal))
        val helper=NexaConversationDb(ctx)
        try {
            helper.readableDatabase.rawQuery(
                "SELECT status,goal_cipher,plan_revision FROM agent_tasks WHERE task_id=?",
                arrayOf(task)).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("proposed",c.getString(0))
                val encrypted=c.getBlob(1)
                assertFalse("No plaintext persisted",
                    encrypted.contentEquals(secretGoal.toByteArray(Charsets.UTF_8)))
                assertFalse(String(encrypted,Charsets.ISO_8859_1).contains(secretGoal))
                assertEquals(1,c.getInt(2))
            }
            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM agent_task_steps WHERE task_id=?",arrayOf(task)).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Proposal must not self-schedule execution",0,c.getInt(0))
            }
        } finally {helper.close()}
    }

    @Test fun v3ProposalAndCancellationHaveAtomicOwnerEvents() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val task="task-"+UUID.randomUUID()
        val session="owner-"+UUID.randomUUID()
        val goal="private request "+UUID.randomUUID()
        assertTrue(NexaConversationDb.saveProposedAgentTask(
            ctx,task,session,"chat","owner-preview-v1",goal))
        assertFalse("A second submit cannot replace or duplicate owner events",
            NexaConversationDb.saveProposedAgentTask(
                ctx,task,session,"chat","owner-preview-v1","replacement"))
        val initial=NexaConversationDb.agentTaskHistory(ctx,40)
        val record=(0 until initial.length()).map{initial.getJSONObject(it)}
            .firstOrNull{it.optString("taskId")==task}
        assertNotNull("Owner task history must include new proposal",record)
        assertEquals("proposed",record!!.getString("status"))
        assertEquals(goal,record.getString("goal"))
        assertEquals("owner-preview-v1",record.getString("policyVersion"))
        assertTrue("Owner may cancel an unexecuted proposal",
            NexaConversationDb.cancelProposedAgentTask(ctx,task))
        assertFalse("Cancellation is terminal and must not replay",
            NexaConversationDb.cancelProposedAgentTask(ctx,task))
        assertFalse("An old task ID may never be reused",
            NexaConversationDb.saveProposedAgentTask(
                ctx,task,session,"chat","owner-preview-v1",goal))
        assertFalse("Unknown task cannot be cancelled",
            NexaConversationDb.cancelProposedAgentTask(
                ctx,"task-"+UUID.randomUUID()))
        val helper=NexaConversationDb(ctx)
        try {
            helper.readableDatabase.rawQuery(
                "SELECT status,goal_cipher FROM agent_tasks WHERE task_id=?",
                arrayOf(task)).use{ c->
                assertTrue(c.moveToFirst())
                assertEquals("cancelled",c.getString(0))
                assertFalse(c.getBlob(1).contentEquals(goal.toByteArray(Charsets.UTF_8)))
            }
            helper.readableDatabase.rawQuery(
                "SELECT event_kind FROM agent_task_events WHERE task_id=? ORDER BY id",
                arrayOf(task)).use{ c->
                assertTrue(c.moveToFirst())
                assertEquals("proposed",c.getString(0))
                assertTrue(c.moveToNext())
                assertEquals("owner_cancelled",c.getString(0))
                assertFalse("Reject duplicate transitions",c.moveToNext())
            }
            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM agent_task_steps WHERE task_id=?",
                arrayOf(task)).use{ c->
                assertTrue(c.moveToFirst())
                assertEquals("Only owner note, never native action",0,c.getInt(0))
            }
        } finally {helper.close()}
    }

    @Test fun rejectsUnknownChannelsAndInvalidRoles() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val session="android-reject-"+UUID.randomUUID()
        NexaConversationDb.append(context,session,"other","local","user","should-not-persist")
        NexaConversationDb.append(context,session,"chat","local","admin","should-not-persist")
        val current=NexaConversationDb.history(context,200).toString()
        assertFalse(current.contains(session))
    }
}
