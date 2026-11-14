package dev.rilandev.personalaiassistant

import android.content.Context
import android.app.KeyguardManager

/** Persistent owner choices for *non-sensitive* operations.
 * Android permission grants and app-specific consent are always checked separately.
 */
internal object NexaVoicePermissionPolicy {
    const val ASK_EVERY_TIME="ask_every_time"
    const val TRUST_SAFE="trusted_safe"
    const val TRUST_NAVIGATION="trusted_navigation"
    private val allowedModes=setOf(ASK_EVERY_TIME,TRUST_SAFE,TRUST_NAVIGATION)
    private const val STORE="nexa_voice_action_permissions_v1"

    fun mode(context:Context):String {
        val value=context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .getString("mode",ASK_EVERY_TIME).orEmpty()
        return value.takeIf{it in allowedModes} ?: ASK_EVERY_TIME
    }
    fun setMode(context:Context,value:String):Boolean {
        if(value !in allowedModes)return false
        return context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
            .putString("mode",value).commit()
    }
    fun category(command:String):String = when {
        command=="read_visible_screen_text" || command=="observe_screen_icons" ||
            command.startsWith("locate_icon:") -> "screen.read"
        command=="list_installed_apps" -> "apps.list"
        command.startsWith("scroll_screen:") -> "screen.scroll"
        command.startsWith("tap_safe_icon:") -> "screen.navigate"
        command.startsWith("tap_visible_label:") -> "screen.tap_label"
        command=="open" || command.startsWith("open ") -> "apps.open"
        else -> "sensitive"
    }
    /** Screen text/app inventory, arbitrary labeled taps, contacts, calls,
     * messages and payments always require direct review. Persisted grants
     * can reduce prompts only for bounded, non-sensitive navigation.
     */
    fun isSafeCategory(category:String):Boolean=category in setOf(
        "screen.scroll","screen.navigate","apps.open")
    fun alwaysAllowed(context:Context,command:String):Boolean {
        val key=category(command)
        if(!isSafeCategory(key))return false
        val m=mode(context)
        if(m==ASK_EVERY_TIME)return false
        if(context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
                .getBoolean("deny:$key",false))return false
        if(context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
                .getBoolean("allow:$key",false))return true
        return m==TRUST_NAVIGATION && key in setOf(
            "screen.scroll","screen.navigate","apps.open")
    }
    fun alwaysAllow(context:Context,command:String):Boolean {
        val key=category(command)
        if(!isSafeCategory(key))return false
        return context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
            .putBoolean("allow:$key",true).remove("deny:$key").commit()
    }
    fun denyCategory(context:Context,category:String):Boolean {
        if(!isSafeCategory(category))return false
        return context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
            .remove("allow:$category").putBoolean("deny:$category",true).commit()
    }
    fun clearGrants(context:Context):Boolean =
        context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().clear().commit()
    /** Used by voice, floating Chat/Tools and any future common harness.
     * This is an in-app preference, NEVER a substitute for Android's grants.
     * Even trusted low-risk navigation must obey current owner Pause and
     * a connected user-enabled AccessibilityService for screen actions.
     */
    fun canExecute(context:Context,command:String):Boolean {
        if(!NexaNativeActionSafety.permitsNewAction(context) ||
            !alwaysAllowed(context,command))return false
        // Persistent trust never unlocks the phone or authorizes an action
        // while the owner is away from an unlocked Android session.
        val unlocked=try {
            val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguard?.isKeyguardLocked == false
        }catch(_:Exception){false}
        if(!unlocked)return false
        return category(command) !in setOf(
            "screen.scroll","screen.navigate","screen.tap_label","screen.read") ||
            ScreenAssistState.connected && ScreenAssistService.active!=null
    }
}
