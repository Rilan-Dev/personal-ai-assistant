package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NexaUiActionJournalAndroidTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun encryptedJournalRejectsReplayAndRecordsOnlyOutcome() {
        val token = "test-" + UUID.randomUUID().toString()
        assertTrue(NexaUiActionJournal.claim(ctx,token,"tap","com.example.journaldemo"))
        assertFalse("At-most-once claim",NexaUiActionJournal.claim(ctx,token,"tap","com.example.journaldemo"))
        assertTrue(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
        assertFalse("Outcome cannot be finalized twice",NexaUiActionJournal.finish(ctx,token,"android_rejected"))
        val encrypted=ctx.getSharedPreferences("nexa_ui_action_ledger_v1",Context.MODE_PRIVATE)
            .getString("events","").orEmpty()
        assertFalse(encrypted.contains("com.example.journaldemo"))
        assertFalse(encrypted.contains(token))
        assertFalse(encrypted.contains("android_accepted"))
        assertNotNull(NexaUiActionJournal.summary(ctx))
    }

    @Test fun recordedChangeDoesNotCountAsTaskSuccessAndCannotBeReplayed() {
        val token = "test-" + UUID.randomUUID().toString()
        assertTrue(NexaUiActionJournal.claim(ctx,token,"scroll_down","com.example.scrolltest"))
        assertFalse(NexaUiActionJournal.markObservation(ctx,token,"observed_change"))
        assertTrue(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
        assertTrue(NexaUiActionJournal.markObservation(ctx,token,"observed_change"))
        assertFalse(NexaUiActionJournal.markObservation(ctx,token,"no_observable_change"))
        assertFalse(NexaUiActionJournal.claim(ctx,token,"scroll_down","com.example.scrolltest"))
    }

    @Test fun ownerApprovedHorizontalScrollsHaveAtMostOnceClaims() {
        for (direction in listOf("scroll_left","scroll_right")) {
            val token="test-"+UUID.randomUUID().toString()
            assertTrue(NexaUiActionJournal.claim(ctx,token,direction,"com.example.scrolltest"))
            assertFalse(NexaUiActionJournal.claim(ctx,token,direction,"com.example.scrolltest"))
            assertTrue(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
            assertTrue(NexaUiActionJournal.markObservation(ctx,token,"scroll_event_observed"))
        }
    }

    @Test fun latestResultShowsOnlyActionTypeAndObservedAndroidEvidence() {
        val token="test-"+UUID.randomUUID().toString()
        assertTrue(NexaUiActionJournal.claim(ctx,token,"scroll_right","com.example.privacytest"))
        assertTrue(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
        assertTrue(NexaUiActionJournal.markObservation(ctx,token,"scroll_event_observed"))
        val latest=NexaUiActionJournal.latestOutcome(ctx)
        assertEquals("scroll_right",latest?.first)
        assertEquals("scroll_event_observed",latest?.second)
        assertFalse(latest.toString().contains("com.example.privacytest"))
        assertFalse(latest.toString().contains(token))
    }

    @Test fun actionEvidenceLookupIsBoundToExactApprovalToken() {
        val token="test-"+UUID.randomUUID().toString()
        val other="test-"+UUID.randomUUID().toString()
        assertTrue(NexaUiActionJournal.claim(ctx,token,"tap","com.example.navigation"))
        assertEquals("claimed",NexaUiActionJournal.outcomeFor(ctx,token))
        assertNull(NexaUiActionJournal.outcomeFor(ctx,other))
        assertTrue(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
        assertEquals("android_accepted",NexaUiActionJournal.outcomeFor(ctx,token))
        assertTrue(NexaUiActionJournal.markObservation(ctx,token,"structural_change_observed"))
        assertEquals("structural_change_observed",NexaUiActionJournal.outcomeFor(ctx,token))
        assertNull(NexaUiActionJournal.outcomeFor(ctx,other))
    }

    @Test fun replayDeniedEvenAfterFortyAuditEntriesAreEvicted() {
        val first="test-"+UUID.randomUUID().toString()
        val pkg="com.example.auditretention"
        assertTrue(NexaUiActionJournal.claim(ctx,first,"tap",pkg))
        assertTrue(NexaUiActionJournal.finish(ctx,first,"android_accepted"))
        repeat(45) {
            val next="test-"+UUID.randomUUID().toString()
            assertTrue(NexaUiActionJournal.claim(ctx,next,"tap",pkg))
            assertTrue(NexaUiActionJournal.finish(ctx,next,"android_accepted"))
        }
        // The first diagnostic event is no longer present, but its durable
        // SQLite token tombstone still refuses any further dispatch.
        assertFalse("A pruned audit must not permit replay",
            NexaUiActionJournal.claim(ctx,first,"tap",pkg))
    }

    @Test fun unsafeOrMalformedClaimsAreRejected() {
        assertFalse(NexaUiActionJournal.claim(ctx,"short","tap","com.example.test"))
        assertFalse(NexaUiActionJournal.claim(ctx,UUID.randomUUID().toString(),
            "send_message","com.example.test"))
        assertFalse(NexaUiActionJournal.claim(ctx,UUID.randomUUID().toString(),"tap","invalid package name"))
    }

    @Test fun reviewedTextClaimPersistsExactBindingNotRawContentsAndCannotReplay() {
        val token="owner-"+UUID.randomUUID()
        val pkg="com.example.messagetest"
        val recipient="Mom"
        val privateText="Meeting until six, will call later"
        val reviewed=NexaMessageInputPolicy.approvedDraftDigest(
            "imo",pkg,recipient,privateText)
        assertFalse("Unbound message confirmation must fail closed",
            NexaUiActionJournal.claim(ctx,token,"message_fill",pkg))
        assertFalse(NexaUiActionJournal.claim(ctx,token,"message_fill",pkg,"invalid"))
        assertTrue(NexaUiActionJournal.claim(ctx,token,"message_fill",pkg,reviewed))
        assertFalse("One irreversible action token cannot be reused",
            NexaUiActionJournal.claim(ctx,token,"message_fill",pkg,reviewed))
        assertTrue(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
        val key=java.security.MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val taskId="task-"+UUID.nameUUIDFromBytes(
            ("task-:"+key).toByteArray(Charsets.UTF_8)).toString()
        val helper=NexaConversationDb(ctx)
        try{
            helper.readableDatabase.rawQuery(
                "SELECT a.input_digest,a.target_digest,a.state,a.consumed_at,s.arguments_cipher "+
                    "FROM agent_step_approvals a JOIN agent_task_steps s "+
                    "ON s.step_id=a.step_id WHERE s.task_id=?",
                arrayOf(taskId)
            ).use{c->
                assertTrue("Exactly one native consumed approval record",c.moveToFirst())
                assertEquals(reviewed,c.getString(0))
                assertEquals(64,c.getString(1).length)
                assertEquals("consumed",c.getString(2))
                assertTrue(c.getLong(3)>0L)
                val auditArgs=JSONObject(NexaConversationDb
                    .openNativeTaskArguments(c.getBlob(4)))
                assertEquals(setOf("kind","packageDigest","reviewedInputDigest"),
                    auditArgs.keys().asSequence().toSet())
                assertEquals(reviewed,auditArgs.getString("reviewedInputDigest"))
                assertFalse(auditArgs.toString().contains(privateText))
                assertFalse(c.moveToNext())
            }
            val entries=NexaUiTaskAudit.POLICY
            helper.readableDatabase.rawQuery(
                "SELECT policy_version FROM agent_tasks WHERE task_id=?",
                arrayOf(taskId)).use{c->
                assertTrue(c.moveToFirst())
                assertEquals(entries,c.getString(0))
            }
        }finally{helper.close()}
        assertFalse(reviewed.contains(privateText))
        assertFalse(NexaUiActionJournal.claim(ctx,token,"message_fill",pkg,
            NexaMessageInputPolicy.approvedDraftDigest("imo",pkg,"Dad",privateText)))
    }

    @Test fun genericTextClaimRequiresBoundFieldAndExactText() {
        val token="owner-"+UUID.randomUUID()
        val pkg="com.example.notes"
        val digest=NexaTextInputPolicy.approvedDraftDigest(pkg,"Notes","private note")
        assertFalse(NexaUiActionJournal.claim(ctx,token,"text_fill",pkg))
        assertTrue(NexaUiActionJournal.claim(ctx,token,"text_fill",pkg,digest))
        assertFalse(NexaUiActionJournal.claim(ctx,token,"text_fill",pkg,digest))
        assertTrue(NexaUiActionJournal.finish(ctx,token,"android_rejected"))
    }

}
