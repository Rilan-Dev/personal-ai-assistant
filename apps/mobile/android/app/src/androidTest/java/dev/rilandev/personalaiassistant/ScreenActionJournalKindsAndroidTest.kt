package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: an action that passes every owner approval must not be rejected
 * because its executor uses an unrecognized journal kind.
 *
 * Uses Android Keystore-backed journal, but never performs a gesture or tap.
 */
@RunWith(AndroidJUnit4::class)
class ScreenActionJournalKindsAndroidTest {
    private val ctx:Context get()=ApplicationProvider.getApplicationContext()
    private val externalPackage="com.example.externalapp"

    @Test fun approvedVisibleLabelTapCanClaimCanonicalTapKind() {
        val id=UUID.randomUUID().toString()
        assertTrue("Tap journal kind must be accepted", NexaUiActionJournal.claim(
            ctx,id,"tap",externalPackage))
        assertTrue("Approved tap must be single-use only",
            !NexaUiActionJournal.claim(ctx,id,"tap",externalPackage))
        assertTrue(NexaUiActionJournal.finish(ctx,id,"android_accepted"))
        assertEquals("android_accepted",NexaUiActionJournal.outcomeFor(ctx,id))
    }

    @Test fun approvedLauncherDirectionalGesturesCanClaimCanonicalScrollKinds() {
        for(direction in listOf("left","right")) {
            val id=UUID.randomUUID().toString()
            assertTrue("Launcher "+direction+" swipe should be journalable",
                NexaUiActionJournal.claim(ctx,id,"scroll_"+direction,externalPackage))
            assertTrue(NexaUiActionJournal.finish(ctx,id,"android_accepted"))
        }
    }

    @Test fun adHocKindsRemainRejectedWithoutWeakeningAllowlist() {
        for(kind in listOf("tap_label","launcher_swipe_left","launcher_swipe_right",
            "tap_call","send_message","grant_permission")) {
            assertFalse("Unrecognized kind should be rejected: "+kind,
                NexaUiActionJournal.claim(ctx,UUID.randomUUID().toString(),kind,externalPackage))
        }
    }
}
