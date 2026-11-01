package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaOverlayCommandsAndroidTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun unknownInstructionsCannotRunAnUnlistedAction() {
        val result = NexaOverlayCommands.handle(context, "unrecognized remote control")
        assertTrue(result.contains("Unsupported local command"))
        assertTrue(result.contains("Full"))
    }

    @Test fun messagingCannotBeSentByLocalOverlayCommand() {
        val result = NexaOverlayCommands.handle(context, "send whatsapp message to mom")
        assertTrue(result.startsWith("Not sent."))
        assertTrue(result.contains("explicit send approval"))
    }

    @Test fun devicePermissionDiagnosticsAreReadOnly() {
        val result = NexaOverlayCommands.handle(context, "background status")
        assertTrue(result.contains("Microphone:"))
        assertTrue(result.contains("Wake listener:"))
        assertTrue(result.contains("Live voice:"))
    }

    @Test fun localHelpOnlyMentionsAllowlistedCommands() {
        val help = NexaOverlayCommands.handle(context,"help")
        assertTrue(help.contains("check github"))
        assertFalse(help.contains("shell command"))
    }
}
