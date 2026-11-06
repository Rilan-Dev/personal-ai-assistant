package dev.rilandev.personalaiassistant

import android.content.Context

/**
 * Owner-controlled local emergency stop for Nexa-initiated third-party actions.
 *
 * This is a permanent runtime deny fence in addition to the build-time feature
 * flags, not a permission grant. Pausing blocks NEW claims, app launches, UI
 * edits and OS navigation from Chat, Voice and overlay; it cannot undo actions
 * already dispatched before a pause.
 *
 * The preference is deliberately local to the device. The model/provider
 * cannot set or clear it. Only an unlocked, foreground Nexa UI may call
 * setPaused() through the React Native owner control.
 */
internal object NexaNativeActionSafety {
    private const val STORE="nexa_native_action_safety_v1"
    private const val KEY="owner_paused_device_actions"
    private val lock=Any()

    fun isPaused(context:Context):Boolean = synchronized(lock) {
        // A corrupt or inaccessible local preference is treated as paused.
        try { context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .getBoolean(KEY,false) } catch (_:Exception){true}
    }

    fun permitsNewAction(context:Context):Boolean = !isPaused(context)

    fun setPaused(context:Context,paused:Boolean):Boolean = synchronized(lock) {
        try {
            val prefs=context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY,paused).commit() &&
                prefs.getBoolean(KEY,!paused)==paused
        }catch (_:Exception){false}
    }
}
