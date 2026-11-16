package dev.rilandev.personalaiassistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

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
    private const val MAX_OBSERVATION_WAIT_MS=6_000L
    private const val OBSERVATION_POLL_INTERVAL_MS=250L

    internal enum class PollDecision {
        RETRY,
        DESTINATION_PACKAGE_OBSERVED,
        NOT_OBSERVED,
    }

    /** A transient launcher/transition window must not terminate an accepted
     * launch. Only an exact visible destination wins; owner Pause or the
     * elapsed-time limit ends the bounded, package-only observation.
     */
    internal fun decisionForObservation(authorized:Boolean,
        destinationObserved:Boolean,elapsedMs:Long):PollDecision = when {
        !authorized -> PollDecision.NOT_OBSERVED
        destinationObserved -> PollDecision.DESTINATION_PACKAGE_OBSERVED
        elapsedMs>=MAX_OBSERVATION_WAIT_MS -> PollDecision.NOT_OBSERVED
        else -> PollDecision.RETRY
    }

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

    /** Observe only the exact external package, without collecting text or
     * dispatching another Intent. The one-shot launch is already consumed;
     * temporary missing/mismatched roots are sampled until success, Pause or
     * the deadline, so slow OEM app startup cannot poison the task early.
     */
    fun waitUntilExpectedVisible(context:Context,expectedPackage:String,
        authorized:()->Boolean,onResult:(Boolean)->Unit) {
        val app=context.applicationContext
        if(!expectedPackage.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}"))) {
            onResult(false)
            return
        }
        val startedAt=SystemClock.elapsedRealtime()
        var completed=false
        val poll=object:Runnable {
            override fun run() {
                if(completed)return
                val remainsAuthorized=try{authorized()}catch(_:Exception){false}
                val destinationObserved=remainsAuthorized &&
                    expectedVisibleNow(app,expectedPackage)
                val elapsed=SystemClock.elapsedRealtime()-startedAt
                when(decisionForObservation(remainsAuthorized,
                    destinationObserved,elapsed)) {
                    PollDecision.DESTINATION_PACKAGE_OBSERVED -> {
                        completed=true
                        onResult(true)
                    }
                    PollDecision.NOT_OBSERVED -> {
                        completed=true
                        onResult(false)
                    }
                    PollDecision.RETRY -> {
                        val remaining=(MAX_OBSERVATION_WAIT_MS-elapsed)
                            .coerceAtLeast(1L)
                        handler.postDelayed(this,
                            minOf(OBSERVATION_POLL_INTERVAL_MS,remaining))
                    }
                }
            }
        }
        handler.post(poll)
    }

    fun observeAfter(context:Context,token:String,expectedPackage:String,
                     reviewGeneration:Long) {
        val app=context.applicationContext
        waitUntilExpectedVisible(app,expectedPackage,{
            NexaNativeActionSafety.approvalFenceCurrent(app,reviewGeneration)
        }) { observed ->
            NexaUiActionJournal.markObservation(app,token,
                if(observed) "destination_package_observed" else "unverified")
        }
    }
}
