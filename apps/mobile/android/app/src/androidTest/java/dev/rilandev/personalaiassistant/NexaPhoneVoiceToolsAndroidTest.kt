package dev.rilandev.personalaiassistant

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only offline native checks. No AI key, call, microphone, message,
 * Accessibility gesture or arbitrary package execution is permitted.
 */
@RunWith(AndroidJUnit4::class)
class NexaPhoneVoiceToolsAndroidTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()

    @Test fun ownerCanAskForBatteryAndStorageWithoutAdditionalRuntimePermissions() {
        val battery=NexaPhoneVoiceTools.deviceFacts(app,"what is my battery percentage")
        assertNotNull(battery)
        assertTrue(battery!!.contains("battery") || battery.contains("Battery"))
        val storage=NexaPhoneVoiceTools.deviceFacts(app,"how much storage is free")
        assertNotNull(storage)
        assertTrue(storage!!.contains("GB"))
        assertNull(NexaPhoneVoiceTools.deviceFacts(app,"send a WhatsApp message"))
    }

    @Test fun installedAppDiscoveryDoesNotExecuteAnythingOrGuessCommands() {
        assertNull(NexaPhoneVoiceTools.findLaunchableApp(app,"open whatsapp and send a message"))
        assertNull(NexaPhoneVoiceTools.findLaunchableApp(app,"delete all my photos"))
        assertNull(NexaPhoneVoiceTools.findLaunchableApp(app,"open settings"))
        assertNull(NexaPhoneVoiceTools.findLaunchableApp(app,"open app"))
        assertNull(NexaPhoneVoiceTools.findLaunchableApp(app,"call +919876543210"))
    }

    @Test fun sarvamModelFunctionSchemaIsReadOnlyAndArgumentsAreValidated() {
        val defs=NexaSarvamReadTools.definitions()
        assertEquals(3,defs.length())
        val expected=setOf("get_device_status","get_battery_level","get_storage_usage")
        val actual=(0 until defs.length()).map { i ->
            val tool=defs.getJSONObject(i)
            assertEquals("function",tool.getString("type"))
            val fn=tool.getJSONObject("function")
            assertEquals(false,fn.getJSONObject("parameters").getBoolean("additionalProperties"))
            fn.getString("name")
        }.toSet()
        assertEquals(expected,actual)
        assertTrue(NexaSarvamReadTools.execute(app,"get_storage_usage","{}").contains("GB"))
        assertTrue(NexaSarvamReadTools.execute(app,"get_battery_level","{}").isNotBlank())
        assertTrue(NexaSarvamReadTools.execute(app,"dispatch_gesture","{}").startsWith("Rejected"))
        assertTrue(NexaSarvamReadTools.execute(app,"get_storage_usage","{\"path\":\"/secret\"}").startsWith("Rejected"))
    }
}
