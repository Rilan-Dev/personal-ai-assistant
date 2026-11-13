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

/** Real Android persistent journal, no contact, composer or external app touch. */
@RunWith(AndroidJUnit4::class)
class NexaMessageFillClaimRecoveryAndroidTest {
    @Test fun claimedFillCannotBeRetriedOrPromotedAfterUnknown() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val initialPause=NexaNativeActionSafety.isPaused(ctx)
        try {
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            val approval="fill-"+UUID.randomUUID()
            val contentHash=NexaMessageInputPolicy.approvedDraftDigest(
                "whatsapp","com.whatsapp","Test Contact","Synthetic only")
            assertTrue(NexaUiActionJournal.claim(ctx,approval,"message_fill",
                "com.whatsapp",contentHash,origin="overlay"))
            assertTrue(NexaUiActionJournal.finish(ctx,approval,"unknown"))
            assertFalse(NexaUiActionJournal.claim(ctx,approval,"message_fill",
                "com.whatsapp",contentHash,origin="overlay"))
            assertFalse(NexaUiActionJournal.finish(ctx,approval,"android_accepted"))
            assertFalse(NexaUiActionJournal.markObservation(ctx,approval,"observed_change"))
            val status=NexaUiActionJournal.summary(ctx)
            assertTrue(status!=null && (status["unknown"]?:0)>=1)
        } finally {
            assertTrue(NexaNativeActionSafety.setPaused(ctx,initialPause))
        }
    }

    @Test fun unreviewedOrMalformedTextHashCannotClaim() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val initialPause=NexaNativeActionSafety.isPaused(ctx)
        try {
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            val token="fill-"+UUID.randomUUID()
            assertFalse(NexaUiActionJournal.claim(ctx,token,"message_fill",
                "com.whatsapp",null,origin="overlay"))
            assertFalse(NexaUiActionJournal.claim(ctx,token,"message_fill",
                "com.whatsapp","not-sha256",origin="overlay"))
        }finally{assertTrue(NexaNativeActionSafety.setPaused(ctx,initialPause))}
    }
}
