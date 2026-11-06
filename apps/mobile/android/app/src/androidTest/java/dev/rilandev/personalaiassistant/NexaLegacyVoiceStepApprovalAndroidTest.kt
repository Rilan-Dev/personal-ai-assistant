package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic Voice steps only. No app launch, screen gesture or message send. */
@RunWith(AndroidJUnit4::class)
class NexaLegacyVoiceStepApprovalAndroidTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()

    @Test fun reviewedCompoundStepConsumesExactlyOneDurableApproval(){
        val task="task-"+UUID.randomUUID()
        val session=UUID.randomUUID().toString()
        val request="open Calendar and then show screen controls"
        val steps=listOf("open Calendar","show screen controls")
        assertTrue(NexaConversationDb.saveTask(
            context,task,session,"planned",0,request,steps))
        assertTrue(NexaLegacyVoiceTaskMirror.mayContinue(
            context,task,session,0))
        assertTrue(NexaConversationDb.saveTask(
            context,task,session,"executing",0,request,steps))
        assertFalse(NexaLegacyVoiceStepApproval.consume(
            context,task,session,0,"open another app",
            "open","com.google.android.calendar"))
        assertFalse(NexaLegacyVoiceStepApproval.consume(
            context,task,session,0,steps[0],"open",
            "com.google.android.calendar",false,"not-a-screen-digest"))
        assertTrue(NexaLegacyVoiceStepApproval.consume(
            context,task,session,0,steps[0],"open",
            "com.google.android.calendar"))
        assertFalse(NexaLegacyVoiceStepApproval.consume(
            context,task,session,0,steps[0],"open",
            "com.google.android.calendar"))
        assertFalse(NexaLegacyVoiceTaskMirror.mayContinue(
            context,task,session,0))

        NexaConversationDb(context).use{helper->
            helper.readableDatabase.rawQuery(
                "SELECT a.state,a.target_digest,a.input_digest "+
                    "FROM agent_step_approvals a JOIN agent_task_steps s "+
                    "ON s.step_id=a.step_id "+
                    "WHERE s.task_id=? AND s.ordinal=0",
                arrayOf(task)
            ).use{c->
                assertTrue(c.moveToFirst())
                assertEquals("consumed",c.getString(0))
                assertEquals(64,c.getString(1).length)
                assertEquals(64,c.getString(2).length)
                assertFalse(c.moveToNext())
            }
        }
        // Earlier approval remains consumed; a new ordinal has a different
        // step identity and still needs its own separate owner confirmation.
        assertTrue(NexaConversationDb.saveTask(
            context,task,session,"verified",1,request,steps))
        assertTrue(NexaLegacyVoiceTaskMirror.mayContinue(
            context,task,session,1))
        assertFalse(NexaLegacyVoiceStepApproval.consume(
            context,task,session,1,steps[1],"read_visible_screen_text"))
    }

    @Test fun uncertainVoiceCheckpointCanNeverReceiveAnotherApproval(){
        val task="task-"+UUID.randomUUID()
        val session=UUID.randomUUID().toString()
        val steps=listOf("open Calendar","show screen controls")
        val request=steps.joinToString(" and then ")
        assertTrue(NexaConversationDb.saveTask(
            context,task,session,"planned",0,request,steps))
        assertTrue(NexaConversationDb.saveTask(
            context,task,session,"uncertain",0,request,steps))
        assertFalse(NexaLegacyVoiceStepApproval.consume(
            context,task,session,0,steps[0],"open","com.google.android.calendar"))
    }
}
