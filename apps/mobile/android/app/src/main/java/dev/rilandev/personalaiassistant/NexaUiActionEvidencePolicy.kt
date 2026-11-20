package dev.rilandev.personalaiassistant

/**
 * OS observations only. This is NOT proof of the requested task, tap target,
 * delivery, purchase or app launch. A tap transition is attributed only when a
 * permitted external window-change event matches the current external root.
 */
internal object NexaUiActionEvidencePolicy {
    /** Click events count only for the exact transient, non-text target identity. */
    fun isMatchingTapEvent(expectedIdentity:String?,observedIdentity:String?):Boolean =
        !expectedIdentity.isNullOrBlank() && expectedIdentity==observedIdentity

    /** Content events count only for the same app window and an approved tap. */
    fun isMatchingContentChangeEvent(
        kind:String,expectedPackage:String,expectedWindowId:Int,
        eventPackage:String?,eventWindowId:Int
    ):Boolean = kind=="tap" && expectedPackage.isNotBlank() &&
        eventPackage==expectedPackage && eventWindowId==expectedWindowId

    /**
     * A window ID may change during a verified launcher page swipe. That one
     * case can retain evidence only when the observed root is still from the
     * same package and the caller has independently reverified the launcher.
     */
    fun sameEvidenceContext(
        kind: String,
        expectedPackage: String,
        expectedWindowId: Int,
        observedPackage: String?,
        observedWindowId: Int,
        allowLauncherWindowChange: Boolean
    ): Boolean {
        if (expectedPackage.isBlank() || expectedPackage != observedPackage) return false
        if (expectedWindowId == observedWindowId) return true
        return kind == "scroll" && allowLauncherWindowChange
    }

    /**
     * A tap may legitimately replace its originating app window (for example,
     * a Settings search activity). Accept only the exact safe external window
     * observed both in a post-tap state event and in the current root.
     */
    fun isCorroboratedExternalWindowTransition(
        kind: String,
        expectedPackage: String,
        expectedWindowId: Int,
        eventPackage: String?,
        eventWindowId: Int?,
        observedPackage: String?,
        observedWindowId: Int,
        isPermittedExternalPackage: Boolean
    ): Boolean {
        if (kind != "tap" || !isPermittedExternalPackage ||
            expectedPackage.isBlank() || eventPackage.isNullOrBlank() ||
            observedPackage.isNullOrBlank()) return false
        if (eventPackage != observedPackage || eventWindowId != observedWindowId) return false
        return expectedPackage != observedPackage || expectedWindowId != observedWindowId
    }

    /**
     * Debug-only taps may return from the launcher to Nexa's focused owner
     * window. Credit only when the state event and current root identify the
     * exact same Nexa package/window; this never authorizes third-party reads.
     */
    fun isCorroboratedOwnerWindowTransition(
        kind: String,
        expectedPackage: String,
        expectedWindowId: Int,
        eventPackage: String?,
        eventWindowId: Int?,
        observedPackage: String?,
        observedWindowId: Int,
        ownerPackage: String,
        isDebugBuild: Boolean
    ): Boolean {
        if (!isDebugBuild || kind != "tap" || ownerPackage.isBlank() ||
            eventPackage != ownerPackage || observedPackage != ownerPackage) return false
        if (eventWindowId != observedWindowId) return false
        return expectedPackage != ownerPackage || expectedWindowId != observedWindowId
    }

    fun classify(
        kind: String, before: String, after: String?,
        scrollEvents: Int, windowEvents: Int, sameContext: Boolean = true,
        externalWindowChangeObserved: Boolean = false,
        tapEvents:Int=0,
        ownerWindowChangeObserved: Boolean = false,
        contentEvents:Int=0
    ): String {
        // A corroborated external window transition proves only that the UI
        // changed after the approved tap, never that its intended task succeeded.
        if (!sameContext) return if (kind == "tap" && after != null &&
            (externalWindowChangeObserved || ownerWindowChangeObserved)) "window_change_observed"
            else "context_changed_unverified"
        if (after == null) return "unverified"
        if (kind == "scroll" && scrollEvents > 0) return if (after != before)
            "structural_change_observed" else "scroll_event_observed"
        if (kind == "tap" && windowEvents > 0) return "window_change_observed"
        if (after != before) return "structural_change_observed"
        if (kind == "tap" && contentEvents > 0) return "content_change_observed"
        if (kind == "tap" && tapEvents > 0) return "tap_event_observed"
        return "no_observable_change"
    }
}
