package dev.rilandev.personalaiassistant

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Observes whether the visible accessibility state changed after Android
 * ACCEPTED one request. This is NOT proof of semantic success or delivery.
 * Stores neither screen text nor screenshots. Never retries actions.
 */
internal object NexaUiActionVerifier {
    private val handler=Handler(Looper.getMainLooper())

    fun fingerprint(root: AccessibilityNodeInfo): String {
        val pkg=root.packageName?.toString().orEmpty()
        val window=root.windowId
        val icons=ScreenIconDetector.detect(root).take(32).joinToString("|") {
            it.semantic + ":" + it.bounds.left + "," + it.bounds.top + "," +
                it.bounds.right + "," + it.bounds.bottom
        }
        return "$pkg:$window:$icons"
    }

    fun observeAfter(service: ScreenAssistService, token: String, prior: String) {
        handler.postDelayed({
            // If the service stops or the screen is inaccessible, do not invent
            // a state. The existing Android-accepted evidence remains.
            if (!ScreenAssistState.connected || ScreenAssistService.active !== service)
                return@postDelayed
            val root=try { service.externalWindowRoot() } catch (_:Exception) { null }
                ?: return@postDelayed
            val result=try {
                if (fingerprint(root)!=prior) "observed_change"
                else "no_observable_change"
            } catch (_:Exception) { null } finally { root.recycle() }
            if (result!=null) NexaUiActionJournal.markObservation(service,token,result)
        },750L)
    }
}
