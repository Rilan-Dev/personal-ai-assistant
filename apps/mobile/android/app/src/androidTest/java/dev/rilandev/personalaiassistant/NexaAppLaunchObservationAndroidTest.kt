package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic audit and package facts only; no external app is launched. */
@RunWith(AndroidJUnit4::class)
class NexaAppLaunchObservationAndroidTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()

    private fun sha(raw:String)=MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(StandardCharsets.UTF_8))
        .joinToString(""){"%02x".format(it.toInt() and 255)}

    @Test fun onlyExactVisibleExternalPackageIsPositiveEvidence(){
        val expected="com.example.destination"
        assertEquals("destination_package_observed",
            NexaAppLaunchObservation.classify(expected,expected,true,true))
        assertEquals("context_changed_unverified",
            NexaAppLaunchObservation.classify(expected,"com.example.other",true,true))
        assertEquals("context_changed_unverified",
            NexaAppLaunchObservation.classify(expected,expected,false,true))
        assertEquals("unverified",
            NexaAppLaunchObservation.classify(expected,null,false,true))
        assertEquals("unverified",
            NexaAppLaunchObservation.classify(expected,expected,true,false))
    }

    @Test fun observedDestinationRequiresConsumedLaunchClaimAndCannotReplay(){
        assumeTrue(NexaNativeActionSafety.permitsNewAction(ctx))
        val token="test-launch-"+UUID.randomUUID()
        assertTrue(NexaUiActionJournal.claim(ctx,token,"app_launch",
            "com.example.destination",origin="overlay"))
        assertTrue(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
        assertTrue(NexaUiActionJournal.markObservation(ctx,token,
            "destination_package_observed"))
        assertFalse(NexaUiActionJournal.markObservation(ctx,token,
            "destination_package_observed"))
        assertFalse(NexaUiActionJournal.claim(ctx,token,"app_launch",
            "com.example.destination",origin="overlay"))
        val tokenDigest=sha(token)
        val taskId="task-"+UUID.nameUUIDFromBytes(
            ("task-:"+tokenDigest).toByteArray(StandardCharsets.UTF_8))
        NexaConversationDb(ctx).use { helper ->
            helper.readableDatabase.rawQuery(
                "SELECT status FROM agent_tasks WHERE task_id=?",arrayOf(taskId)
            ).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("observed",row.getString(0))
            }
            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM agent_task_events WHERE task_id=? "+
                    "AND event_kind='app_destination_observed'",arrayOf(taskId)
            ).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(1,row.getInt(0))
            }
        }
    }

    @Test fun anApprovedTapCannotImpersonateObservedDestination(){
        assumeTrue(NexaNativeActionSafety.permitsNewAction(ctx))
        val token="test-tap-"+UUID.randomUUID()
        assertTrue(NexaUiActionJournal.claim(ctx,token,"tap",
            "com.example.destination",origin="chat"))
        assertTrue(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
        assertFalse(NexaUiActionJournal.markObservation(ctx,token,
            "destination_package_observed"))
    }
}
