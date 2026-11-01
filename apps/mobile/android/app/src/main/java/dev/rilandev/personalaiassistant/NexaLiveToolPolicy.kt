package dev.rilandev.personalaiassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Narrow on-device execution gate for OpenAI Realtime function calls.
 * This is NOT the React Native AgentHarness. It cannot grant permissions,
 * send messages or invoke arbitrary Accessibility/global UI actions.
 * The model proposes; Android validates; application launches require an
 * explicit owner tap in the system assistant voice plate.
 */
internal object NexaLiveToolPolicy {
    sealed class Decision {
        data class Immediate(val result: String) : Decision()
        data class NeedsApproval(val app: String, val command: String) : Decision()
    }
    fun definitions(): JSONArray {
        val tools = JSONArray()
        for (name in listOf("get_device_status", "get_battery_level")) {
            tools.put(JSONObject()
                .put("type", "function")
                .put("name", name)
                .put("description", if (name == "get_device_status")
                    "Read Nexa's Android permission, assistant and listening status."
                    else "Read the phone's battery percentage.")
                .put("parameters", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject())
                    .put("additionalProperties", false)))
        }
        tools.put(JSONObject()
            .put("type", "function")
            .put("name", "request_open_app")
            .put("description", "Request permission to open an allowlisted Android app. The owner must explicitly approve on screen before it opens. This tool cannot message contacts.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("app", JSONObject()
                    .put("type", "string")
                    .put("enum", JSONArray(listOf("brave", "github", "whatsapp")))))
                .put("required", JSONArray(listOf("app")))
                .put("additionalProperties", false)))
        return tools
    }

    fun decide(context: Context, name: String, rawArguments: String): Decision {
        if (rawArguments.length > 1024) return Decision.Immediate("Rejected: tool input too large")
        val args = try { JSONObject(rawArguments) } catch (_: Exception) {
            return Decision.Immediate("Rejected: invalid tool arguments")
        }
        return when (name) {
            "get_device_status", "get_battery_level" -> {
                if (args.length() != 0) Decision.Immediate("Rejected: unexpected arguments")
                else Decision.Immediate(NexaOverlayCommands.handle(context,
                    if (name == "get_device_status") "device status" else "battery"))
            }
            "request_open_app" -> {
                if (args.length() != 1 || !args.has("app"))
                    return Decision.Immediate("Rejected: invalid app request")
                val app = args.optString("app")
                if (app !in setOf("brave", "github", "whatsapp"))
                    Decision.Immediate("Rejected: app is not on Nexa's allowlist")
                else Decision.NeedsApproval(app, "open $app")
            }
            else -> Decision.Immediate("Rejected: unknown tool")
        }
    }
}
