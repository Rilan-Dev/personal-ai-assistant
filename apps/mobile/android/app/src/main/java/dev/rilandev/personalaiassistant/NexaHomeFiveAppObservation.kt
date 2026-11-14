package dev.rilandev.personalaiassistant

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Phase 1 of owner-initiated home-screen AI acceptance:
 * observe exactly five app-icon categories WITHOUT starting any app.
 *
 * Accessibility is user-enabled by Android; this does not grant it. Only
 * app-label equality is examined, and only the five aggregate match counts
 * are returned. No private screen content, other labels, coordinates,
 * contacts, messages, call logs, screenshots or window text are retained.
 */
internal object NexaHomeFiveAppObservation {
    internal data class Target(val key:String,val labels:Set<String>)
    internal val targets=listOf(
        Target("WhatsApp",setOf("whatsapp")),
        Target("IMO",setOf("imo","imo messenger")),
        Target("Contacts",setOf("contacts")),
        Target("Calls",setOf("phone","dialer","calls")),
        Target("Clock/Alarm",setOf("clock","alarm","alarms"))
    )
    internal fun normalized(value:String?):String? {
        if(value==null||value.length !in 2..45)return null
        val cleaned=value.lowercase(Locale.ROOT).trim()
            .replace(Regex("""\s+""")," ")
        if(cleaned.any{it.code<32 || it=='@' || it.isDigit()})return null
        return cleaned
    }
    internal fun match(value:String?):String? {
        val text=normalized(value) ?:return null
        return targets.singleOrNull{ text in it.labels }?.key
    }

    fun observe(context:Context):String {
        val service=ScreenAssistService.active
            ?:return "HOME_PILOT: Android Nexa Accessibility is disabled. No home observation."
        if(!ScreenAssistState.connected || service!==ScreenAssistService.active)
            return "HOME_PILOT: Android Nexa Accessibility is disconnected."
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return "HOME_PILOT: No eligible unlocked launcher window."
        return try {
            if(!NexaLauncherSwipe.isHome(service,root) ||
                !root.isVisibleToUser || root.windowId<0)
                return "HOME_PILOT: Open the Android home screen first. No other app was read."
            val viewport=Rect().also{root.getBoundsInScreen(it)}
            if(viewport.isEmpty)return "HOME_PILOT: Launcher geometry unavailable."
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            val hits=targets.associate{it.key to linkedSetOf<String>()}.toMutableMap()
            var visited=0
            while(queue.isNotEmpty() && visited++<450) {
                val node=queue.removeFirst()
                if(!node.isVisibleToUser || node.isPassword || node.isEditable)continue
                val key=match(node.contentDescription?.toString())
                    ?:match(node.text?.toString())
                if(key!=null && node.isEnabled){
                    // Count unique visible *icon regions*; a label under its
                    // clickable parent is still one icon, not two contacts.
                    val box=Rect().also{node.getBoundsInScreen(it)}
                    if(!box.isEmpty && viewport.contains(box)) {
                        val current=node.takeIf{it.isClickable}
                            ?:try{node.parent?.takeIf{it.isClickable && it.isEnabled}}
                                catch(_:Exception){null}
                        if(current!=null){
                            val region=Rect().also{current.getBoundsInScreen(it)}
                            if(!region.isEmpty && viewport.contains(region))
                                hits.getValue(key).add(
                                    "${region.left}:${region.top}:${region.right}:${region.bottom}")
                        }
                    }
                }
                for(i in 0 until node.childCount)
                    node.getChild(i)?.let{queue.addLast(it)}
            }
            // An incomplete tree could hide a duplicate; fail closed.
            if(queue.isNotEmpty())
                "HOME_PILOT: Launcher tree incomplete. No icon was approved."
            else {
                val result=targets.joinToString("; ") { target ->
                    val count=hits.getValue(target.key).size
                    target.key+"="+when(count){
                        0 -> "not visible on current page"
                        1 -> "one visible icon"
                        else -> "ambiguous ("+count+")"
                    }
                }
                "HOME_PILOT_READ_ONLY: "+result+
                    ". No taps, swipes, app launches, calls, messages or alarms performed."
            }
        }catch(_:Exception) {
            "HOME_PILOT: Android launcher changed or denied inspection."
        }finally{try{root.recycle()}catch(_:Exception){}}
    }
}
