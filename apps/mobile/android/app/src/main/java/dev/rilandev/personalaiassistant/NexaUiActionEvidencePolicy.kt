package dev.rilandev.personalaiassistant

/**
 * OS observations only. This is NOT proof of the requested task, tap target,
 * delivery, purchase or app launch. Attribution requires the original app/window.
 */
internal object NexaUiActionEvidencePolicy {
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
