package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Named-key fallback is limited to the owner window or the real notification shade. */
@RunWith(AndroidJUnit4::class)
class NexaKeyActionSurfacePolicyAndroidTest {
    @Test fun ownerWindowCanUseNamedHomeAndNotificationsWithoutExternalAppRoot() {
        assertEquals("dev.rilandev.personalaiassistant.debug",
            NexaKeyActionSurfacePolicy.fallbackPackage(
                "home", "dev.rilandev.personalaiassistant.debug",
                ownerWindowFocused=true, notificationShadeVisible=false))
        assertEquals("com.android.systemui",
            NexaKeyActionSurfacePolicy.fallbackPackage(
                "notifications", "dev.rilandev.personalaiassistant.debug",
                ownerWindowFocused=true, notificationShadeVisible=false))
    }

    @Test fun notificationShadeCanBeDismissedOnlyWithNamedNavigation() {
        for (name in listOf("home", "back")) {
            assertEquals(name, "com.android.systemui",
                NexaKeyActionSurfacePolicy.fallbackPackage(
                    name, "dev.rilandev.personalaiassistant.debug",
                    ownerWindowFocused=false, notificationShadeVisible=true))
        }
        assertNull(NexaKeyActionSurfacePolicy.fallbackPackage(
            "notifications", "dev.rilandev.personalaiassistant.debug",
            ownerWindowFocused=false, notificationShadeVisible=false))
    }

    @Test fun noFallbackExistsForUnknownKeysOrUnverifiedSurfaces() {
        for (name in listOf("enter", "delete", "raw_keycode_3", "")) {
            assertNull(NexaKeyActionSurfacePolicy.fallbackPackage(
                name, "dev.rilandev.personalaiassistant.debug",
                ownerWindowFocused=true, notificationShadeVisible=true))
        }
        assertNull(NexaKeyActionSurfacePolicy.fallbackPackage(
            "home", "dev.rilandev.personalaiassistant.debug",
            ownerWindowFocused=false, notificationShadeVisible=false))
    }

    @Test fun onlyNamedNotificationShadeWindowsQualifyForSystemUiObservation() {
        assertTrue(NexaKeyActionSurfacePolicy.isNotificationShade(
            "com.android.systemui", "NotificationShade"))
        assertTrue(NexaKeyActionSurfacePolicy.isNotificationShade(
            "com.android.systemui", "Quick Settings"))
        assertFalse(NexaKeyActionSurfacePolicy.isNotificationShade(
            "com.android.systemui", "PermissionController"))
        assertFalse(NexaKeyActionSurfacePolicy.isNotificationShade(
            "com.example.app", "NotificationShade"))
    }

    @Test fun notificationPanelResourceIdsIdentifyShadeWhenWindowTitleIsMissing() {
        assertTrue(NexaKeyActionSurfacePolicy.isNotificationShade(
            "com.android.systemui", "",
            setOf("com.android.systemui:id/notification_panel")))
        assertTrue(NexaKeyActionSurfacePolicy.isNotificationShade(
            "com.android.systemui", "",
            setOf("com.android.systemui:id/quick_settings_container")))
        assertFalse(NexaKeyActionSurfacePolicy.isNotificationShade(
            "com.android.systemui", "", emptySet()))
        assertFalse(NexaKeyActionSurfacePolicy.isNotificationShade(
            "com.example.app", "",
            setOf("com.android.systemui:id/notification_panel")))
    }
}
