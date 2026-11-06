package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The old encrypted Chat journal owns approvals; these are task-ledger
 * regression checks only. No device action, model call, or message is sent.
 */
@RunWith(AndroidJUnit4::class)
class NexaAgentActionAuditAndroidTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()

    private fun digest(value:String)=MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(""){"%02x".format(it.toInt() and 255)}

    private fun sessionId(raw:String)="owner-chat-"+
        digest("nexa-chat-request-v1:"+raw.length+":"+raw).take(32)

    @Test fun oneChatOwnerRequestRetainsOneTaskAcrossMultipleSteps(){
        val session=UUID.randomUUID().toString()
        val first=UUID.randomUUID().toString()
        val second=UUID.randomUUID().toString()
        assertTrue(NexaAgentActionAudit.claim(ctx,session,first,"first-bound-fp",true))
        // While the first action's Android outcome is unknown, no second
        // step may claim execution authority, regardless of its approval.
        assertFalse(NexaAgentActionAudit.claim(ctx,session,second,"second-bound-fp",true))
        assertFalse(NexaAgentActionAudit.recordSettlement(
            ctx,session,first,"altered-fp",false))
        assertTrue(NexaAgentActionAudit.recordSettlement(
            ctx,session,first,"first-bound-fp",false))
        assertTrue(NexaAgentActionAudit.claim(ctx,session,second,"second-bound-fp",true))
        assertFalse(NexaAgentActionAudit.claim(ctx,session,first,"first-bound-fp",true))
        assertTrue(NexaAgentActionAudit.recordSettlement(
            ctx,session,second,"second-bound-fp",false))

        NexaConversationDb(ctx).use{helper->
            helper.readableDatabase.rawQuery(
                "SELECT t.status,COUNT(s.step_id) FROM agent_tasks t "+
                    "JOIN agent_task_steps s ON s.task_id=t.task_id "+
                    "WHERE t.session_id=? AND t.policy_version=? "+
                    "GROUP BY t.task_id",
                arrayOf(sessionId(session),NexaAgentActionAudit.POLICY)
            ).use{cursor->
                assertTrue(cursor.moveToFirst())
                assertEquals("settled_unverified",cursor.getString(0))
                assertEquals(2,cursor.getInt(1))
                assertFalse("Multiple task IDs for one owner request",cursor.moveToNext())
            }
        }
    }

    @Test fun anUncertainChatStepPermanentlyFencesLaterSteps(){
        val session=UUID.randomUUID().toString()
        val first=UUID.randomUUID().toString()
        val second=UUID.randomUUID().toString()
        assertTrue(NexaAgentActionAudit.claim(ctx,session,first,"fp",false))
        assertTrue(NexaAgentActionAudit.recordSettlement(
            ctx,session,first,"fp",true))
        assertFalse(NexaAgentActionAudit.claim(ctx,session,second,"new-fp",false))
        assertFalse(NexaAgentActionAudit.recordSettlement(
            ctx,session,first,"fp",false))
        NexaConversationDb(ctx).use{helper->
            helper.readableDatabase.rawQuery(
                "SELECT status FROM agent_tasks WHERE session_id=? "+
                    "AND policy_version=?",
                arrayOf(sessionId(session),NexaAgentActionAudit.POLICY)
            ).use{cursor->
                assertTrue(cursor.moveToFirst())
                assertEquals("uncertain",cursor.getString(0))
            }
        }
    }
}
