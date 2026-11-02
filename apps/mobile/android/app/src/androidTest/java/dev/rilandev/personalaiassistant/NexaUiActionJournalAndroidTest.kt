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

    @Test fun unsafeOrMalformedClaimsAreRejected() {
        assertFalse(NexaUiActionJournal.claim(ctx,"short","tap","com.example.test"))
        assertFalse(NexaUiActionJournal.claim(ctx,UUID.randomUUID().toString(),
            "send_message","com.example.test"))
        assertFalse(NexaUiActionJournal.claim(ctx,UUID.randomUUID().toString(),"tap","invalid package name"))
    }
}
