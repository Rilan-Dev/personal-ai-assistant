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
 * cannot change it: the unlocked foreground Nexa UI may Pause or Resume,
 * while the owner-tapped floating overlay may only Pause. No agent tool
 * exposes this setter.
 */
internal object NexaNativeActionSafety {
    private const val STORE="nexa_native_action_safety_v1"
    private const val KEY="owner_paused_device_actions"
    private const val GENERATION="owner_pause_generation"
    private val lock=Any()

    fun isPaused(context:Context):Boolean = synchronized(lock) {
        // A corrupt or inaccessible local preference is treated as paused.
        try { context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .getBoolean(KEY,false) } catch (_:Exception){true}
    }

    fun permitsNewAction(context:Context):Boolean = !isPaused(context)

    /** Monotonic generation invalidates any previously staged Voice approval
     * even if the owner pauses and then resumes within its original TTL.
     * A preference-read failure returns a non-matching fence value.
     */
    fun pauseGeneration(context:Context):Long = synchronized(lock) {
        try { context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .getLong(GENERATION,0L) }catch (_:Exception){-1L}
    }

    fun setPaused(context:Context,paused:Boolean):Boolean = synchronized(lock) {
        try {
            val prefs=context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            val old=prefs.getLong(GENERATION,0L)
            if(old<0L || (paused && old==Long.MAX_VALUE))return@synchronized false
            val generation=if(paused)old+1L else old
            prefs.edit().putBoolean(KEY,paused)
                .putLong(GENERATION,generation).commit() &&
                prefs.getBoolean(KEY,!paused)==paused &&
                prefs.getLong(GENERATION,-1L)==generation
        }catch (_:Exception){false}
    }
}
