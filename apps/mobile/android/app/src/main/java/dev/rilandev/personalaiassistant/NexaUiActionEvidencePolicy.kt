package dev.rilandev.personalaiassistant

/** OS evidence classification only, never proof that a requested task completed. */
internal object NexaUiActionEvidencePolicy {
    fun classify(kind: String, before: String, after: String?, scrollEvents: Int, windowEvents: Int): String {
        if (kind == "scroll" && scrollEvents > 0) return "scroll_event_observed"
        if (kind == "tap" && windowEvents > 0) return "window_change_observed"
        if (after == null) return "unverified"
        if (after != before) return "structural_change_observed"
        return "no_observable_change"
    }
}
