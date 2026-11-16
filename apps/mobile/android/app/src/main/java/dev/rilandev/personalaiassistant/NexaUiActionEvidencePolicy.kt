package dev.rilandev.personalaiassistant

/**
 * OS observations only. This is NOT proof of the requested task, tap target,
 * delivery, purchase or app launch. Attribution requires the original app/window.
 */
internal object NexaUiActionEvidencePolicy {
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

    fun classify(
        kind: String, before: String, after: String?,
        scrollEvents: Int, windowEvents: Int, sameContext: Boolean = true
    ): String {
        // Other apps/windows cannot provide evidence for this one-time approval.
        if (!sameContext) return "context_changed_unverified"
        if (after == null) return "unverified"
        if (kind == "scroll" && scrollEvents > 0) return "scroll_event_observed"
        if (kind == "tap" && windowEvents > 0) return "window_change_observed"
        if (after != before) return "structural_change_observed"
        return "no_observable_change"
    }
}
