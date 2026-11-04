package dev.rilandev.personalaiassistant

import android.view.accessibility.AccessibilityEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Safe, synthetic Accessibility callbacks. No third-party app is read or touched. */
@RunWith(AndroidJUnit4::class)
class NexaScreenAssistEventSafetyAndroidTest {
    @Test fun detachedServiceIgnoresWindowChangesWithoutTerminatingTheProcess() {
        val service = ScreenAssistService()
        val initialConnection = ScreenAssistState.connected
        val event = AccessibilityEvent.obtain(
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = "com.example.unrelated"
        try {
            ScreenAssistState.connected = true
            org.junit.Assert.assertTrue(ScreenAssistState.arm())
            // A callback can arrive after Android has invalidated its window
            // connection. It must be handled without dereferencing the activity.
            service.onAccessibilityEvent(event)
            service.onAccessibilityEvent(null)
            // Some OEMs dispatch stale events after an app window closes.
            // The returned snapshot must remain empty, never a guessed state.
            repeat(64) {
                val transient = AccessibilityEvent.obtain(
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                try {
                    transient.packageName = "com.example.unrelated"
                    service.onAccessibilityEvent(transient)
                } finally {
                    transient.recycle()
                }
            }
            assertNull("No external screen snapshot was permitted", ScreenAssistState.consume())
        } finally {
            ScreenAssistState.clear()
            ScreenAssistState.connected = initialConnection
            event.recycle()
        }
    }
}
