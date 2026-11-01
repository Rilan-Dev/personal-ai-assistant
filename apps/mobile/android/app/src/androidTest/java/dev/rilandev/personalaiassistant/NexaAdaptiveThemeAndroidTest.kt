package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaAdaptiveThemeAndroidTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun paletteProducesReadableSurfaceInBothModes() {
        val prefs = app.getSharedPreferences("nexa_overlay_ui", Context.MODE_PRIVATE)
        val original = prefs.getString("theme_mode", NexaOverlayTheme.AUTO)
        try {
            prefs.edit().putString("theme_mode", NexaOverlayTheme.DARK).commit()
            val dark = NexaOverlayTheme.palette(app)
            assertTrue(dark.isDark)
            assertNotEquals(dark.title, dark.card)
            assertNotNull(NexaOverlayTheme.card(app, dark))
            assertNotNull(NexaOverlayTheme.waveform(app, dark))
            prefs.edit().putString("theme_mode", NexaOverlayTheme.LIGHT).commit()
            val light = NexaOverlayTheme.palette(app)
            assertFalse(light.isDark)
            assertNotEquals(dark.title, light.title)
            assertNotNull(NexaOverlayTheme.card(app, light))
            assertNotNull(NexaOverlayTheme.action(app, "Stop", light) {})
        } finally {
            prefs.edit().putString("theme_mode", original ?: NexaOverlayTheme.AUTO).commit()
        }
    }

    @Test fun cyclePreservesAutoDarkLightSequence() {
        val prefs = app.getSharedPreferences("nexa_overlay_ui", Context.MODE_PRIVATE)
        val original = prefs.getString("theme_mode", NexaOverlayTheme.AUTO)
        try {
            prefs.edit().putString("theme_mode", NexaOverlayTheme.AUTO).commit()
            assertEquals(NexaOverlayTheme.DARK, NexaOverlayTheme.cycleMode(app))
            assertEquals(NexaOverlayTheme.LIGHT, NexaOverlayTheme.cycleMode(app))
            assertEquals(NexaOverlayTheme.AUTO, NexaOverlayTheme.cycleMode(app))
        } finally {
            prefs.edit().putString("theme_mode", original ?: NexaOverlayTheme.AUTO).commit()
        }
    }
}
