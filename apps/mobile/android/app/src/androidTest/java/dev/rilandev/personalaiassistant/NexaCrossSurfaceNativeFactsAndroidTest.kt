package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Same evidence/claim lifecycle for explicit device questions from all
 * trusted Android surfaces. No Accessibility, messages or model tool calls.
 */
@RunWith(AndroidJUnit4::class)
class NexaCrossSurfaceNativeFactsAndroidTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

    private fun count(origin:String):Int {
        val db=NexaConversationDb(context)
        try{
            return db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM agent_tasks WHERE origin=? AND policy_version='native-fact-v1'",
                arrayOf(origin)
            ).use{c->assertTrue(c.moveToFirst());c.getInt(0)}
        }finally{db.close()}
    }

    @Test fun directOverlayReadIsRecordedUnderSameNativeCoordinator() {
        val before=count("overlay")
        val output=NexaOverlayCommands.handle(context,"check battery","overlay")
        assertTrue(output.isNotBlank())
        assertEquals(before+1,count("overlay"))
        val db=NexaConversationDb(context)
        try{
            db.readableDatabase.rawQuery(
                "SELECT t.task_id,t.status,s.tool_id,s.status FROM agent_tasks t " +
                    "JOIN agent_task_steps s ON s.task_id=t.task_id " +
                    "WHERE t.origin='overlay' AND t.policy_version='native-fact-v1' " +
                    "ORDER BY t.created_at DESC LIMIT 1",emptyArray()
            ).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("get_battery_level",c.getString(2))
                assertTrue(c.getString(3) in setOf("verified","outcome_unknown"))
            }
        }finally{db.close()}
    }

    @Test fun assistantRoleMultipleFactsUseOneOrderedDurableTask() {
        val before=count("assistant-role")
        val output=NexaOverlayCommands.handle(context,
            "check battery and check storage","assistant-role")
        assertTrue(output.isNotBlank())
        assertEquals(before+1,count("assistant-role"))
        val db=NexaConversationDb(context)
        try{
            db.readableDatabase.rawQuery(
                "SELECT t.task_id FROM agent_tasks t WHERE t.origin='assistant-role' " +
                    "AND t.policy_version='native-fact-v1' ORDER BY t.created_at DESC LIMIT 1",
                emptyArray()
            ).use{task->
                assertTrue(task.moveToFirst())
                val id=task.getString(0)
                db.readableDatabase.rawQuery(
                    "SELECT ordinal,tool_id FROM agent_task_steps WHERE task_id=? ORDER BY ordinal",
                    arrayOf(id)
                ).use{steps->
                    assertTrue(steps.moveToFirst())
                    assertEquals(0,steps.getInt(0))
                    assertEquals("get_battery_level",steps.getString(1))
                    assertTrue(steps.moveToNext())
                    assertEquals(1,steps.getInt(0))
                    assertEquals("get_storage_usage",steps.getString(1))
                    assertFalse(steps.moveToNext())
                }
            }
        }finally{db.close()}
    }

    @Test fun unsupportedSpeechNeverGetsAReadOnlyNativeTask() {
        val before=count("overlay")
        assertNull(NexaTaskCoordinator.classifyOwnerFact(
            "send a message to any contact"))
        NexaOverlayCommands.handle(context,"send a message to any contact","overlay")
        assertEquals(before,count("overlay"))
    }
}
