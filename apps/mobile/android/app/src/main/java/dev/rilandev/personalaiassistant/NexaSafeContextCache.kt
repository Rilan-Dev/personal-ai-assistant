package dev.rilandev.personalaiassistant

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Explicit owner opt-in for an EPHEMERAL safe-control overview while the
 * floating assistant is visible. Never records raw screen text, contacts,
 * messages, editable fields, screenshots or coordinates. Never sends the
 * observation to an AI provider automatically, and never grants OS access.
 */
internal object NexaSafeContextCache {
    private const val STORE="nexa_safe_overlay_context_v1"
    private const val KEY="owner_armed"
    private const val TTL_MS=8_000L
    private const val THROTTLE_MS=1_500L
    private val lock=Any()
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task,"nexa-ephemeral-safe-context").apply { isDaemon=true }
    }
    private var epoch=0L
    private var lastAttempt=0L
    private var snapshot:String?=null
    private var expiresAt=0L

    fun ownerEnabled(context:Context):Boolean=try{
        context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .getBoolean(KEY,false)
    }catch(_:Exception){false}

    fun active(context:Context):Boolean =
        ownerEnabled(context) && Settings.canDrawOverlays(context) &&
        NexaToolOverlayService.visible &&
        ScreenAssistState.connected && ScreenAssistService.active!=null &&
        !NexaNativeActionSafety.isPaused(context)

    fun setOwnerEnabled(context:Context,enabled:Boolean):Boolean {
        // A persisted Nexa preference cannot substitute for Android grants.
        if(enabled && (!Settings.canDrawOverlays(context) ||
            !ScreenAssistState.connected || ScreenAssistService.active==null))return false
        val saved=try{
            context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
                .edit().putBoolean(KEY,enabled).commit()
        }catch(_:Exception){false}
        if(!enabled || !saved)clear()
        return saved
    }

    fun clear()=synchronized(lock){
        epoch++
        snapshot=null;expiresAt=0L;lastAttempt=0L
    }

    /** Called only by an already user-enabled AccessibilityService on a
     * window event. No continuous polling, no UI gesture or request replay.
     */
    fun capture(context:Context,eventPackage:String) {
        if(!active(context)) {clear();return}
        if(!eventPackage.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")))
            return
        val now=SystemClock.elapsedRealtime()
        val generation=synchronized(lock){
            if(now-lastAttempt in 0 until THROTTLE_MS)return
            lastAttempt=now
            epoch
        }
        // OEM Binder traversal on the Accessibility callback's MAIN thread
        // can ANR Android SystemUI. Serialize bounded metadata reads on one
        // private worker, without blocking event delivery or spawning a thread
        // per event. Owner revocation/overlay removal cancels stale results.
        try{worker.execute {
            val observed=try{NexaTaskScreenObservation.observe(context)}
                catch(_:Exception){null} ?:return@execute
            if(!observed.startsWith("Visible non-sensitive controls in $eventPackage: ") ||
                !active(context))return@execute
            synchronized(lock){
                if(generation!=epoch)return@synchronized
                snapshot=observed.take(750)
                expiresAt=SystemClock.elapsedRealtime()+TTL_MS
            }
        }}catch(_:Exception){clear()}
    }

    fun peek(context:Context):String? {
        if(!active(context)){clear();return null}
        return synchronized(lock) {
            if(SystemClock.elapsedRealtime()>=expiresAt){
                snapshot=null;expiresAt=0L;null
            }else snapshot
        }
    }
}
