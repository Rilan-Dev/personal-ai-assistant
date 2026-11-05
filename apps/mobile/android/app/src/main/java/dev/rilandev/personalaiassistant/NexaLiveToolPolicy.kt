package dev.rilandev.personalaiassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Provider tool requests are untrusted. Android grants only one reviewed action. */
internal object NexaLiveToolPolicy {
    sealed class Decision {
        data class Immediate(val result:String):Decision()
        data class NeedsApproval(
            val app:String, val command:String,
            val installedApp:NexaPhoneVoiceTools.AppChoice?=null
        ):Decision()
    }
    private fun noArgs(name:String,description:String):JSONObject =
        JSONObject().put("type","function").put("name",name).put("description",description)
            .put("parameters",JSONObject().put("type","object")
                .put("properties",JSONObject()).put("additionalProperties",false))
    private fun option(name:String,description:String,key:String,values:List<String>?):JSONObject {
        val property=JSONObject().put("type","string")
        if(values!=null)property.put("enum",JSONArray(values))
        return JSONObject().put("type","function").put("name",name)
            .put("description",description)
            .put("parameters",JSONObject().put("type","object")
                .put("properties",JSONObject().put(key,property))
                .put("required",JSONArray().put(key)).put("additionalProperties",false))
    }
    fun definitions():JSONArray = JSONArray().apply {
        put(noArgs("get_device_status","Read current Nexa permission and listening status."))
        put(noArgs("get_battery_level","Read phone battery percentage."))
        put(noArgs("get_storage_usage","Read free, used and total phone storage."))
        put(option("request_open_app",
            "Ask owner to open Brave, GitHub or WhatsApp. Requires visible approval.",
            "app",listOf("brave","github","whatsapp")))
        put(option("request_open_installed_app",
            "Request to open a named installed launcher app. The owner must approve on screen; do not claim it opened.",
            "app",null))
        put(option("request_chrome_search",
            "Request one owner-approved Chrome Google search for the user's exact public query. Never claim an article was read or selected.",
            "query",null))
        put(noArgs("request_open_notification_shade",
            "Request one owner-approved Android notification shade opening. Never read notification contents."))
        put(noArgs("request_go_back",
            "Request one owner-approved Android Back navigation; verify the resulting screen separately."))
        put(noArgs("request_list_installed_apps",
            "Ask permission to read a limited list of launcher app names. Names are shared with provider only after approval."))
        put(noArgs("request_read_visible_screen",
            "Ask owner for one-time screen-text inspection through Android Accessibility. Excludes editable/password fields; only after approval."))
        put(noArgs("request_visible_icons",
            "Request one read-only inspection of labeled icons in current app, after owner approval."))
        put(option("request_locate_visible_icon",
            "Request one read-only lookup of a visible icon. No click.",
            "icon",listOf("menu","search","settings","back","close","call",
                "video_call","send","attach","camera","microphone")))
        put(option("request_scroll_screen",
            "Request a single approved scroll up/down/left/right on an actually scrollable foreground app. Never claim completion without evidence.",
            "direction",listOf("up","down","left","right")))
        put(option("request_tap_safe_icon",
            "Request an owner-approved, fresh screen-grounded tap on a harmless navigation icon.",
            "icon",listOf("menu","search","settings","back","close")))
        put(option("request_tap_visible_label",
            "Request one owner-approved tap on a uniquely visible, accessible, safe text-labeled control; never arbitrary coordinates or sensitive actions.",
            "label",null))
    }
    fun decide(context:Context,name:String,rawArguments:String):Decision {
        if(rawArguments.length>1024)return Decision.Immediate("Rejected: tool input too large")
        val args=try { JSONObject(rawArguments) } catch (_:Exception) {
            return Decision.Immediate("Rejected: invalid tool arguments")
        }
        fun single(key:String):String? =
            if(args.length()==1 && args.has(key) && args.opt(key) is String)
                args.optString(key) else null
        fun available():Boolean = ScreenAssistState.connected && ScreenAssistService.active!=null
        val screenRequired=setOf("request_read_visible_screen","request_visible_icons",
            "request_locate_visible_icon","request_scroll_screen","request_tap_safe_icon",
            "request_tap_visible_label")
        if(name in screenRequired && !available())return Decision.Immediate(
            "Screen Assist is OFF. Open Nexa Tools, enable Screen Assist in Android Accessibility Settings, and retry. No screen action performed.")
        return when(name) {
            "get_device_status","get_battery_level","get_storage_usage" ->
                if(args.length()!=0)Decision.Immediate("Rejected: unexpected arguments")
                else Decision.Immediate(NexaTaskNativeRead.observe(context,
                    when(name) {
                        "get_device_status"->"device.status"
                        "get_storage_usage"->"device.storage_status"
                        else->"device.battery_status"
                    }) ?: "Android device status unavailable. No verified reading.")
            "request_chrome_search" -> {
                val query=single("query").orEmpty()
                if(NexaBrowserTaskActions.safeQuery(query)==null)
                    Decision.Immediate("Rejected: invalid public Chrome search query")
                else Decision.NeedsApproval("Chrome search: $query","chrome_search:$query")
            }
            "request_open_notification_shade" ->
                if(args.length()!=0)Decision.Immediate("Rejected: unexpected arguments")
                else if(!available())Decision.Immediate("Screen Assist is OFF. No notification shade action.")
                else Decision.NeedsApproval("notification shade","open_notification_shade")
            "request_go_back" ->
                if(args.length()!=0)Decision.Immediate("Rejected: unexpected arguments")
                else if(!available())Decision.Immediate("Screen Assist is OFF. No Back action.")
                else Decision.NeedsApproval("back navigation","go_back")
            "request_list_installed_apps" ->
                if(args.length()!=0)Decision.Immediate("Rejected: unexpected arguments")
                else Decision.NeedsApproval("launcher app list","list_installed_apps")
            "request_read_visible_screen" ->
                if(args.length()!=0)Decision.Immediate("Rejected: unexpected arguments")
                else Decision.NeedsApproval("visible screen labels","read_visible_screen_text")
            "request_visible_icons" ->
                if(args.length()!=0)Decision.Immediate("Rejected: unexpected arguments")
                else Decision.NeedsApproval("visible screen icons","observe_screen_icons")
            "request_locate_visible_icon" -> {
                val icon=single("icon")
                if(icon !in setOf("menu","search","settings","back","close",
                    "call","video_call","send","attach","camera","microphone"))
                    Decision.Immediate("Rejected: unknown icon")
                else Decision.NeedsApproval("$icon icon","locate_icon:$icon")
            }
            "request_scroll_screen" -> {
                val direction=single("direction")
                if(direction !in setOf("up","down","left","right"))
                    Decision.Immediate("Rejected: invalid scroll direction")
                else Decision.NeedsApproval("$direction scroll","scroll_screen:$direction")
            }
            "request_tap_safe_icon" -> {
                val icon=single("icon")
                if(icon !in setOf("menu","search","settings","back","close"))
                    Decision.Immediate("Rejected: unsafe icon")
                else Decision.NeedsApproval("$icon icon","tap_safe_icon:$icon")
            }
            "request_tap_visible_label" -> {
                val label=single("label")?.trim().orEmpty()
                if(label.length !in 2..45 || !label.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _-]{1,44}")) ||
                    Regex("""\b(send|submit|pay|buy|purchase|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|otp|logout)\b""",
                        RegexOption.IGNORE_CASE).containsMatchIn(label))
                    Decision.Immediate("Rejected: unsafe or invalid tap label")
                else Decision.NeedsApproval(label,"tap_visible_label:$label")
            }
            "request_open_app" -> {
                val app=single("app")
                if(app !in setOf("brave","github","whatsapp"))
                    Decision.Immediate("Rejected: app is not allowlisted")
                else Decision.NeedsApproval(app!!,"open $app")
            }
            "request_open_installed_app" -> {
                val app=single("app")?.trim().orEmpty()
                if(app.length !in 2..55 || !app.matches(Regex("[A-Za-z0-9][A-Za-z0-9 ._+-]{1,54}")))
                    return Decision.Immediate("Rejected: invalid app name")
                when(val found=NexaPhoneVoiceTools.findLaunchableApp(context,"open $app")) {
                    is NexaPhoneVoiceTools.Decision.Immediate -> Decision.Immediate(found.result)
                    is NexaPhoneVoiceTools.Decision.NeedsApproval ->
                        Decision.NeedsApproval(found.app.label,"open",found.app)
                    else -> Decision.Immediate("Android could not find that launcher app.")
                }
            }
            else -> Decision.Immediate("Rejected: unknown tool")
        }
    }
}
