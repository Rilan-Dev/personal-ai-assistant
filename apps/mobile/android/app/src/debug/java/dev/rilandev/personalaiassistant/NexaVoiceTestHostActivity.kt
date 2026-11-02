package dev.rilandev.personalaiassistant

import android.app.Activity

/**
 * Debug-only Android instrumentation host. It deliberately does NOT extend
 * ReactActivity, so emulator tests exercise the microphone foreground service
 * without requiring Metro or a bundled React Native JavaScript entry point.
 *
 * Shipping release builds contain neither this class nor its activity entry.
 */
class NexaVoiceTestHostActivity : Activity()
