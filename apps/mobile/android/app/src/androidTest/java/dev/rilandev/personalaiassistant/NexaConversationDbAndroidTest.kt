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
        assertEquals(2,exported.getInt("schemaVersion"))
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
    @Test fun rejectsUnknownChannelsAndInvalidRoles() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val session="android-reject-"+UUID.randomUUID()
        NexaConversationDb.append(context,session,"other","local","user","should-not-persist")
        NexaConversationDb.append(context,session,"chat","local","admin","should-not-persist")
        val current=NexaConversationDb.history(context,200).toString()
        assertFalse(current.contains(session))
    }
}
