package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaVisualCaptureEpochAndroidTest {
    @Test fun oldScreenshotCallbackCannotCompleteAfterDiscardAndRearm() {
        val gate=NexaCaptureEpoch()
        val first=gate.begin()
        assertTrue(gate.current(first))
        gate.invalidate()
        val second=gate.begin()
        assertFalse(gate.current(first))
        assertTrue(gate.current(second))
    }

    @Test fun consumedCaptureCannotAcceptLateDuplicateCallback() {
        val gate=NexaCaptureEpoch()
        val first=gate.begin()
        gate.invalidate()
        assertFalse(gate.current(first))
    }
}
