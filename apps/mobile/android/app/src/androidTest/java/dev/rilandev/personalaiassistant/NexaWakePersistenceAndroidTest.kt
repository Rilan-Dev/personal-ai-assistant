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
        val wasListening = NexaWakeWordService.listening
        try {
            NexaWakeWordService.persistEnabled(ctx, false)
            NexaWakeBootReceiver().onReceive(ctx,
                Intent(Intent.ACTION_BOOT_COMPLETED))
            assertFalse(NexaWakeWordService.enabled(ctx))
            assertEquals("A boot receiver call must not start or stop an existing session",
                wasListening,NexaWakeWordService.listening)
        } finally {
            NexaWakeWordService.persistEnabled(ctx, original)
        }
    }

    @Test fun legacyWakePreferenceMustBeReauthorizedAfterUpgrade() {
        val preferences = ctx.getSharedPreferences("nexa_wake_preferences", Context.MODE_PRIVATE)
        val oldFlag = preferences.getBoolean("wake_enabled", false)
        val oldMode = preferences.getString("activation_mode_v2", null)
        try {
            // Simulate upgrade from a build that stored only wake_enabled.
            preferences.edit().putBoolean("wake_enabled", true)
                .remove("activation_mode_v2").commit()
            assertEquals(NexaWakeWordService.MODE_GESTURE, NexaWakeWordService.mode(ctx))
            assertFalse(NexaWakeWordService.enabled(ctx))
            NexaWakeWordService.persistEnabled(ctx, true)
            assertEquals(NexaWakeWordService.MODE_CONTINUOUS, NexaWakeWordService.mode(ctx))
            assertTrue(NexaWakeWordService.enabled(ctx))
            NexaWakeWordService.persistEnabled(ctx, false)
            assertEquals(NexaWakeWordService.MODE_GESTURE, NexaWakeWordService.mode(ctx))
        } finally {
            val restore = preferences.edit().putBoolean("wake_enabled", oldFlag)
            if (oldMode == null) restore.remove("activation_mode_v2")
            else restore.putString("activation_mode_v2", oldMode)
            restore.commit()
        }
    }
}
