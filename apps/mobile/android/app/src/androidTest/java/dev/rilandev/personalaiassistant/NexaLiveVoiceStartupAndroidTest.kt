package dev.rilandev.personalaiassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Execute the real Android service startup, with a debug-only native foreground
 * Activity on the CI emulator (no React Native/Metro dependency). No API credentials are injected and no provider call is
 * allowed. Tests missing-configuration startup and repeated STOP safety.
 *
 * This cannot reproduce a crash specific to the owner's Sarvam credentials
 * or OEM phone. Such a failure still needs redacted device logcat.
 */
@RunWith(AndroidJUnit4::class)
class NexaLiveVoiceStartupAndroidTest {
    @Test fun liveServiceHandlesMissingCredentialsAndRepeatedStopWithoutAppCrash() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val instrument = InstrumentationRegistry.getInstrumentation()
        // These are test-only Android instrumentation grants on an emulator,
        // not an app runtime permission bypass.
        instrument.uiAutomation.grantRuntimePermission(
            app.packageName, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            instrument.uiAutomation.grantRuntimePermission(
                app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assertEquals(PackageManager.PERMISSION_GRANTED,
            app.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        assertTrue("Without an active wake FGS, the microphone is released",
            NexaWakeWordService.liveMicReleased())
        val activity = ActivityScenario.launch(NexaVoiceTestHostActivity::class.java)
        try {
            activity.onActivity { owner ->
                owner.startForegroundService(Intent(owner,
                    RealtimeVoiceService::class.java).apply {
                    action = RealtimeVoiceService.ACTION_START
                })
            }
            val cutoff = SystemClock.elapsedRealtime() + 7_000L
            while (SystemClock.elapsedRealtime() < cutoff &&
                RealtimeVoiceService.status == "connecting") {
                SystemClock.sleep(120L)
            }
            assertNotEquals("No configured provider must never activate live audio",
                "listening", RealtimeVoiceService.status)
            for (i in 1..2) {
                app.startService(Intent(app, RealtimeVoiceService::class.java).apply {
                    action = RealtimeVoiceService.ACTION_STOP
                })
            }
            SystemClock.sleep(180L)
            assertNotEquals("listening", RealtimeVoiceService.status)
            assertTrue(NexaWakeWordService.liveMicReleased())
        } finally {
            app.stopService(Intent(app, RealtimeVoiceService::class.java))
            activity.close()
        }
    }
}
