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

    @Test fun unsafeOrMalformedClaimsAreRejected() {
        assertFalse(NexaUiActionJournal.claim(ctx,"short","tap","com.example.test"))
        assertFalse(NexaUiActionJournal.claim(ctx,UUID.randomUUID().toString(),
            "send_message","com.example.test"))
        assertFalse(NexaUiActionJournal.claim(ctx,UUID.randomUUID().toString(),"tap","invalid package name"))
    }
}
