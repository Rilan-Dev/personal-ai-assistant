package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Real Android keystore + durable claim regression. Never starts WhatsApp,
 * requests Accessibility, clicks another app, types or sends a message.
 */
@RunWith(AndroidJUnit4::class)
class NexaWhatsAppClaimRecoveryAndroidTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()

    @Test fun uncertainPostClaimOutcomeCannotBeReplayedOrReclassifiedAsSuccess() {
        val wasPaused=NexaNativeActionSafety.isPaused(ctx)
        try {
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            val token="whatsapp-"+UUID.randomUUID().toString()
            assertTrue(NexaUiActionJournal.claim(ctx,token,"tap","com.whatsapp",
                origin="overlay"))
            assertTrue(NexaUiActionJournal.finish(ctx,token,"unknown"))
            assertEquals("unknown",NexaUiActionJournal.outcomeFor(ctx,token))
            assertFalse(NexaUiActionJournal.claim(ctx,token,"tap","com.whatsapp",
                origin="overlay"))
            // The terminal audit record is not a dispatch authorization.
            assertFalse(NexaUiActionJournal.finish(ctx,token,"android_accepted"))
            assertEquals("unknown",NexaUiActionJournal.outcomeFor(ctx,token))
        } finally {
            assertTrue(NexaNativeActionSafety.setPaused(ctx,wasPaused))
        }
    }
}
