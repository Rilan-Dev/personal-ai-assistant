package dev.rilandev.personalaiassistant

import android.content.Context
import android.os.SystemClock

/**
 * A short-lived in-memory binding of ONE owner-reviewed Voice screen action
 * to the exact observed external app/window/structure.
 *
 * A later tool may never reuse a voice approval against a different app,
 * newly opened conversation, changed control layout or stale observation.
 * This does not execute gestures, authorize restricted controls, or persist
 * private screen text. Tool-specific native permission/target checks remain.
 */
internal object NexaVoiceScreenBinding {
    data class Bound(
        val packageName:String,
        val windowId:Int,
        val structureDigest:String,
        val observedAtMs:Long
    )

    fun requires(command:String):Boolean =
        command.startsWith("tap_visible_label:") ||
        command.startsWith("tap_safe_icon:") ||
        command.startsWith("scroll_screen:") ||
        command.startsWith("open_whatsapp_chat:") ||
        command.startsWith("locate_icon:") ||
        command=="go_back" || command=="read_visible_screen_text" ||
        command=="observe_screen_icons"

    fun capture(context:Context):Bound?{
        val service=ScreenAssistService.active ?:return null
        if(!ScreenAssistState.connected)return null
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return null
        return try{
            val pkg=root.packageName?.toString().orEmpty()
            if(pkg==context.packageName || pkg=="android" ||
                pkg=="com.android.systemui" ||
                !pkg.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")) ||
                root.windowId<0)return null
            val structure=NexaUiActionVerifier.fingerprint(root)
            if(!structure.matches(Regex("[0-9a-f]{64}")))return null
            Bound(pkg,root.windowId,structure,SystemClock.elapsedRealtime())
        }catch(_:Exception){null}
        finally{try{root.recycle()}catch(_:Exception){}}
    }

    fun stillMatches(context:Context,expected:Bound?):Boolean{
        if(expected==null)return false
        val age=SystemClock.elapsedRealtime()-expected.observedAtMs
        if(age !in 0..60_000L)return false
        val current=capture(context) ?:return false
        return current.packageName==expected.packageName &&
            current.windowId==expected.windowId &&
            current.structureDigest==expected.structureDigest
    }
}
