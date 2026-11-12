package dev.rilandev.personalaiassistant

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * After ONE already-approved app launch, collect an optional package-only
 * Accessibility receipt. This never taps, reads screen text, dispatches an
 * Intent, chooses a target or retries an uncertain launch.
 *
 * The durable app_launch claim and android_accepted receipt MUST already
 * exist; NexaUiActionJournal enforces a one-time, launch-kind-bound upgrade.
 * Process death or missing Accessibility leaves the claimed launch uncertain.
 */
internal object NexaAppLaunchObservation {
    private val handler=Handler(Looper.getMainLooper())

    internal fun classify(expectedPackage:String,observedPackage:String?,
                          visible:Boolean,connected:Boolean):String {
        if(!connected || observedPackage.isNullOrBlank())return "unverified"
        return if(visible && observedPackage==expectedPackage)
            "destination_package_observed"
        else "context_changed_unverified"
    }

    /** Read-only current-window check for sequencing the NEXT reviewed step.
     * Intent dispatch itself is NEVER proof of the destination. Android may
     * place a permission prompt, resolver, lock screen or different app on top.
     * Only the identical visible package qualifies; no screen text is read.
     */
    fun expectedVisibleNow(context:Context,expectedPackage:String):Boolean {
        if(!expectedPackage.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")))
            return false
        val service=ScreenAssistService.active
        if(!ScreenAssistState.connected || service==null)return false
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return false
        return try{
            classify(expectedPackage,root.packageName?.toString(),
                root.isVisibleToUser,true)=="destination_package_observed"
        }catch(_:Exception){false}
        finally{try{root.recycle()}catch(_:Exception){}}
    }

    fun observeAfter(context:Context,token:String,expectedPackage:String) {
        val app=context.applicationContext
        handler.postDelayed({
            val service=ScreenAssistService.active
            val connected=ScreenAssistState.connected && service!=null
            val root=if(connected)try{service?.externalWindowRoot()}
                catch(_:Exception){null} else null
            val observation=try {
                root?.packageName?.toString() to (root?.isVisibleToUser==true)
            }catch(_:Exception){null to false}
            finally{try{root?.recycle()}catch(_:Exception){}}
            NexaUiActionJournal.markObservation(app,token,
                classify(expectedPackage,observation.first,observation.second,connected))
        },1200L)
    }
}
