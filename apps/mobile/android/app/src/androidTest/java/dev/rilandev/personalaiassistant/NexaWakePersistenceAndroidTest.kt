package dev.rilandev.personalaiassistant

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaWakePersistenceAndroidTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun wakePreferenceSurvivesObjectRecreationWithoutGrantingAudio() {
        val original = NexaWakeWordService.enabled(ctx)
        try {
            NexaWakeWordService.persistEnabled(ctx, true)
            assertTrue(NexaWakeWordService.enabled(ctx))
            NexaWakeWordService.persistEnabled(ctx, false)
            assertFalse(NexaWakeWordService.enabled(ctx))
        } finally {
            NexaWakeWordService.persistEnabled(ctx, original)
        }
    }

    @Test fun politeBraveCommandResolvesToAllowlistedAppOnly() {
        val answer = NexaOverlayCommands.handle(ctx, "can you please open my brave app?")
        assertTrue("Expected Brave-specific action, got: $answer", answer.contains("Brave"))
        assertTrue(NexaOverlayCommands.handle(ctx, "send whatsapp message to mom").startsWith("Not sent."))
        assertTrue(NexaOverlayCommands.handle(ctx, "erase all apps").contains("Unsupported"))
    }

    @Test fun rebootRecoveryDoesNotEnableWakeWithoutPriorOptIn() {
        val original = NexaWakeWordService.enabled(ctx)
        try {
            NexaWakeWordService.persistEnabled(ctx, false)
            NexaWakeBootReceiver().onReceive(ctx,
                Intent(Intent.ACTION_BOOT_COMPLETED))
            assertFalse(NexaWakeWordService.enabled(ctx))
            assertFalse(NexaWakeWordService.listening)
        } finally {
            NexaWakeWordService.persistEnabled(ctx, original)
        }
    }
}
