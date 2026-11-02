package dev.rilandev.personalaiassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Actual Sarvam Chat Completions function tools, in the documented nested
 * OpenAI-compatible wire shape. These functions are READ-ONLY and execute on
 * the device; they cannot click, send a message, grant permissions, or access
 * the full screen. Android, not the language model, is the authority.
 */
internal object NexaSarvamReadTools {
    fun definitions():JSONArray {
        val names=listOf(
            "get_battery_level" to "Read the owner's phone battery percentage.",
            "get_storage_usage" to "Read free, used and total internal device storage.",
            "get_device_status" to "Read current Nexa permission and wake status."
        )
        val tools=JSONArray()
        names.forEach { (name,description) ->
            tools.put(JSONObject().put("type","function")
                .put("function",JSONObject().put("name",name)
                    .put("description",description)
                    .put("parameters",JSONObject().put("type","object")
                        .put("properties",JSONObject())
                        .put("additionalProperties",false))))
        }
        return tools
    }
    fun execute(context:Context,name:String,args:String):String {
        if(args.length>512)return "Rejected: tool arguments exceed limit."
        val json=try { JSONObject(args) } catch (_:Exception) {
            return "Rejected: invalid tool arguments."
        }
        if(json.length()!=0)return "Rejected: unexpected tool arguments."
        val command=when(name) {
            "get_battery_level" -> "battery"
            "get_storage_usage" -> "storage"
            "get_device_status" -> "device status"
            else -> return "Rejected: unknown or non-read-only tool."
        }
        return try {
            NexaPhoneVoiceTools.deviceFacts(context,command)?.take(380)
                ?: "Android could not read the requested device status."
        }catch (_:Exception){
            "The requested Android status is currently unavailable."
        }
    }
}
