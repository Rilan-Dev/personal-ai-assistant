package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NexaUnifiedTaskTimelineAndroidTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

    @Test fun nativeAndLegacyVoiceRecordsShareReadOnlyIndexWithoutLeakingGoals() {
        val native="task-"+UUID.randomUUID()
        val voice="task-"+UUID.randomUUID()
        assertTrue(NexaConversationDb.saveProposedAgentTask(
            context,native,"owner-"+UUID.randomUUID(),"chat",
            "owner-review-v1","Private chat task content"))
        assertTrue(NexaConversationDb.saveTask(
            context,voice,"voice-"+UUID.randomUUID(),"awaiting_approval",1,
            "Private voice task content",listOf("open supported app"),""))
        val summary=NexaUnifiedTaskTimeline.recent(context,60)
        val chat=NexaUnifiedTaskTimeline.find(context,native)
        val oldVoice=NexaUnifiedTaskTimeline.find(context,voice)
        assertNull(NexaUnifiedTaskTimeline.find(context,"task-no-such-id-000000000"))
        assertNotNull(chat)
        assertNotNull(oldVoice)
        assertEquals("native",chat!!.getString("source"))
        assertEquals("chat",chat.getString("origin"))
        assertEquals("legacy_voice_checkpoint",oldVoice!!.getString("source"))
        assertEquals("live-voice",oldVoice.getString("origin"))
        assertEquals("awaiting_approval",oldVoice.getString("status"))
        assertEquals("Checkpoint is not verified step evidence",0,
            oldVoice.getInt("verifiedSteps"))
        assertFalse(summary.toString().contains("Private chat task content"))
        assertFalse(summary.toString().contains("Private voice task content"))
        assertFalse(summary.toString().contains("open supported app"))
    }

    @Test fun duplicateLegacyIdDoesNotProduceTwoRowsOrAnExecutableTask() {
        val id="task-"+UUID.randomUUID()
        assertTrue(NexaConversationDb.saveProposedAgentTask(
            context,id,"owner-"+UUID.randomUUID(),"chat",
            "owner-review-v1","Owner request"))
        assertFalse("A legacy Voice writer cannot take over a native task ID",
            NexaConversationDb.saveTask(
                context,id,"voice-"+UUID.randomUUID(),"planned",0,
                "Legacy checkpoint",listOf("read phone storage"),""))
        val exact=NexaUnifiedTaskTimeline.find(context,id)
        assertNotNull(exact)
        assertEquals("native",exact!!.getString("source"))
        val result=NexaUnifiedTaskTimeline.recent(context,60)
        val matches=(0 until result.length()).map{result.getJSONObject(it)}
            .filter{it.optString("taskId")==id}
        assertTrue("Recent window may truncate, but must never duplicate an ID",
            matches.size<=1)
        if(matches.isNotEmpty())
            assertEquals("native",matches[0].getString("source"))
        val db=NexaConversationDb(context)
        try {
            db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM agent_mandates WHERE task_id=?",
                arrayOf(id)
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0,c.getInt(0))
            }
        }finally {db.close()}
    }
}
