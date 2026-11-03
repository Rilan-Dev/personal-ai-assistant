package dev.rilandev.personalaiassistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Tool-calling contract for Sarvam Chat Completions.
 * Model proposals do not execute Android operations: the owner-intent gate
 * and the native one-time approval gate both have to pass.
 */
internal object NexaSarvamActionTools {
    private val requestTools=setOf(
        "request_open_app","request_open_installed_app",
        "request_read_visible_screen","request_visible_icons",
        "request_locate_visible_icon","request_scroll_screen",
        "request_tap_safe_icon","request_tap_visible_label",
        "request_chrome_search","request_open_notification_shade","request_go_back",
        "request_list_installed_apps")
    fun isAction(name:String):Boolean=name in requestTools

    /** Translate OpenAI Realtime's flat request definitions into Sarvam's
     * documented OpenAI-compatible nested Chat Completions tool shape.
     */
    fun definitions():JSONArray=JSONArray().apply {
        val read=NexaSarvamReadTools.definitions()
        for(i in 0 until read.length())put(read.getJSONObject(i))
        val actions=NexaLiveToolPolicy.definitions()
        for(i in 0 until actions.length()) {
            val original=actions.getJSONObject(i)
            val name=original.optString("name")
            if(name !in requestTools)continue
            put(JSONObject().put("type","function").put("function",
                JSONObject().put("name",name)
                    .put("description",original.optString("description"))
                    .put("parameters",original.getJSONObject("parameters"))))
        }
    }
    fun ownerRequested(name:String,arguments:String,utterance:String):Boolean {
        if(name !in requestTools || arguments.length>1024 ||
            utterance.length !in 3..450)return false
        val input=try{JSONObject(arguments)}catch(_:Exception){return false}
        val q=utterance.lowercase(Locale.ROOT).trim()
            .replace(Regex("""^hey\s+nexa[,!\s]*"""),"")
        if(Regex("""\b(don't|do not|never|without|instead of|stop|cancel)\b""")
                .containsMatchIn(q))return false
        fun asks(vararg verbs:String):Boolean=
            verbs.any{ Regex("""\b"""+Regex.escape(it)+"""\b""").containsMatchIn(q) }
        fun value(key:String):String?=
            if(input.length()==1 && input.has(key) && input.opt(key) is String)
                input.optString(key).lowercase(Locale.ROOT).trim() else null
        return when(name) {
            "request_read_visible_screen" ->
                input.length()==0 && (asks("read","describe","inspect","check","see") ||
                    q.contains("what is on") || q.contains("what are you seeing")) &&
                (asks("screen","display") || q.contains("current app") ||
                    q.contains("seeing here") || q.contains("on here"))
            "request_visible_icons" ->
                input.length()==0 && asks("see","list","show","inspect","check","identify") &&
                    asks("icons","buttons","controls")
            "request_list_installed_apps" ->
                input.length()==0 && (q.contains("installed apps") ||
                    q.contains("apps on my phone"))
            "request_locate_visible_icon" -> {
                val icon=value("icon")
                icon!=null && (asks("find","where","locate","show") &&
                    (q.contains(icon.replace('_',' ')) || q.contains("icon")))
            }
            "request_scroll_screen" -> {
                val direction=value("direction")
                direction in setOf("up","down","left","right") &&
                    asks("scroll","swipe","slide","move","navigate","travel") &&
                    q.contains(direction!!)
            }
            "request_tap_safe_icon" -> {
                val icon=value("icon")
                icon in setOf("menu","search","settings","back","close") &&
                    asks("tap","click","press","select","pick") &&
                    (q.contains(icon!!) || q.contains("that icon"))
            }
            "request_tap_visible_label" -> {
                val label=value("label")
                label!=null && label.length in 2..45 &&
                    asks("tap","click","press","select","pick","focus") &&
                    (q.contains(label) ||
                        label.contains("search") && q.contains("search"))
            }
            "request_chrome_search" -> {
                val query=value("query")
                query!=null && NexaBrowserTaskActions.safeQuery(query)!=null &&
                    NexaPhoneVoiceTools.chromeSearchQuery(utterance)==query
            }
            "request_open_notification_shade" ->
                input.length()==0 && NexaPhoneVoiceTools.wantsNotificationShade(utterance)
            "request_go_back" ->
                input.length()==0 && NexaPhoneVoiceTools.wantsGoBack(utterance)
            "request_open_app","request_open_installed_app" -> {
                val target=value("app")
                target!=null && target.length in 2..55 &&
                    asks("open","launch","start") && q.contains(target)
            }
            else -> false
        }
    }
}
