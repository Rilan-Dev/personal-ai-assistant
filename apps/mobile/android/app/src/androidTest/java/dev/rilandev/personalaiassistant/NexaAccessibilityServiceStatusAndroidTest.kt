package dev.rilandev.personalaiassistant

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaAccessibilityServiceStatusAndroidTest {
    @Test fun enabledSettingMatchesOnlyTheExactServiceComponent() {
        val appId = BuildConfig.APPLICATION_ID
        val expected = ComponentName(
            appId,
            "dev.rilandev.personalaiassistant.ScreenAssistService")

        assertTrue(NexaAccessibilityServiceStatus.isEnabled(
            "$appId/dev.rilandev.personalaiassistant.ScreenAssistService",
            expected))
        assertFalse(NexaAccessibilityServiceStatus.isEnabled(
            "dev.rilandev.personalaiassistant.physicalqa2/dev.rilandev.personalaiassistant.ScreenAssistService",
            expected))
        assertFalse(NexaAccessibilityServiceStatus.isEnabled("", expected))
        assertFalse(NexaAccessibilityServiceStatus.isEnabled(null, expected))
    }
}
