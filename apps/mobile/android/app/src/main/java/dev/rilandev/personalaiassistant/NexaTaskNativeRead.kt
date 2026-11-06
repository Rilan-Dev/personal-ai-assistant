package dev.rilandev.personalaiassistant

import android.content.Context

/**
 * Trusted, bounded, no-side-effect device-fact adapter shared by the native
 * coordinator's direct, ordered and owner-reviewed task paths.
 *
 * Planner-supplied tool IDs never select an arbitrary Android method. Only
 * the explicit registry below can produce a native observation.
 * This adapter never performs Accessibility, external-app actions or sending.
 */
internal object NexaTaskNativeRead {
    private val mapping = mapOf(
        "get_battery_level" to "battery",
        "get_storage_usage" to "storage",
        "device.battery_status" to "battery",
        "device.storage_status" to "storage",
        "device.status" to "device status",
        "get_visible_controls" to "visible screen controls",
        "device.visible_controls" to "visible screen controls"
    )
    val proposedReadTools: Set<String> =
        setOf("device.status", "device.battery_status", "device.storage_status",
            "device.visible_controls")

    fun observe(context: Context, trustedToolId: String): String? {
        val exactCommand = mapping[trustedToolId] ?: return null
        val result = try {
            if(exactCommand=="visible screen controls")
                NexaTaskScreenObservation.observe(context)
            else NexaPhoneVoiceTools.deviceFacts(context, exactCommand)
        }
            catch (_:Exception) { null }
        if (result.isNullOrBlank() || result.length > 1000 ||
            result.contains("unavailable", ignoreCase=true) ||
            result.contains("did not provide", ignoreCase=true) ||
            result.contains("did not allow", ignoreCase=true) ||
            result.contains("could not", ignoreCase=true))
            return null
        return result
    }
}
