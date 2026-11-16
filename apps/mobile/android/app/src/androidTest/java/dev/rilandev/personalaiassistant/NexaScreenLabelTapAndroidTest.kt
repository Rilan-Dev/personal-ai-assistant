package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaScreenLabelTapAndroidTest {
    @Test fun whatsappSearchAliasMatchesOnlyItsKnownFullAccessibleLabel() {
        assertTrue(NexaScreenLabelTap.matchesRequestedLabel(
            "com.whatsapp","Search","Ask Meta AI or Search"))
        assertTrue(NexaScreenLabelTap.matchesRequestedLabel(
            "com.whatsapp","Search","Search"))
        assertFalse(NexaScreenLabelTap.matchesRequestedLabel(
            "com.whatsapp","Search","Ask Meta AI"))
        assertFalse(NexaScreenLabelTap.matchesRequestedLabel(
            "com.whatsapp","Search","Search settings"))
        assertFalse(NexaScreenLabelTap.matchesRequestedLabel(
            "com.imo.android.imoim","Search","Ask Meta AI or Search"))
    }
}
