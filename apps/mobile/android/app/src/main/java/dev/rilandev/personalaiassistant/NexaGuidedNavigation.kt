package dev.rilandev.personalaiassistant

/**
 * A bounded, literal owner-initiated NAVIGATION GUIDE, not an autonomous action planner.
 * A guide never executes or approves anything. Each step must be inspected, selected
 * and explicitly confirmed using the separate native action controller.
 * The guide never writes screen content to persistent storage.
 */
internal class NexaGuidedNavigation {
    sealed class StartResult {
        data class Started(val description: String): StartResult()
        data class Rejected(val reason: String): StartResult()
    }
    private var steps: List<String> = emptyList()
    private var next = 0
    private var expiry = 0L
    private var boundPackage: String? = null
    private var pendingToken: String? = null
    private var pendingUntil = 0L

    fun start(raw: String, now: Long): StartResult {
        cancel()
        val normalized = raw.trim().lowercase(java.util.Locale.ROOT)
            .replace(Regex("\\s+")," ").trimEnd(' ', '.', '?', '!')
        steps = when(normalized) {
            "find search in menu", "open search from menu", "find search from menu" ->
                listOf("menu","search")
            "open settings from menu", "find settings in menu" ->
                listOf("menu","settings")
            "open menu", "find menu" -> listOf("menu")
            "find search", "open search" -> listOf("search")
            "find settings", "open settings" -> listOf("settings")
            else -> return StartResult.Rejected("Only explicit, safe menu/search/settings navigation guides are supported.")
        }
        next = 0
        expiry = now + 90_000L
        return StartResult.Started("Guided navigation: " + steps.joinToString(" → ") +
            ". Each tap requires a new inspection, selection and owner confirmation.")
    }

    fun expected(now: Long): String? {
        if (steps.isEmpty() || now >= expiry || next >= steps.size) {
            cancel()
            return null
        }
        return steps[next]
    }

    /** Refuse silently following the guide into a different application. */
    fun bindPackage(pkg: String, now: Long): Boolean {
        if (expected(now) == null || !pkg.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")))
            return false
        val current=boundPackage
        if (current == null) {
            boundPackage=pkg
            return true
        }
        return current==pkg
    }

    fun canSelect(semantic: String, now: Long): Boolean {
        val target = expected(now) ?: return false
        if(pendingToken != null) return false
        return semantic == target || (target == "menu" && semantic == "more_options")
    }

    /** Android acceptance is not proof the screen changed: hold this guide here. */
    fun afterAcceptedDispatch(semantic: String, token: String, now: Long): Boolean {
        if (!canSelect(semantic,now) || token.length !in 16..128) return false
        pendingToken=token
        pendingUntil=minOf(expiry,now+3_500L)
        return true
    }

    fun isAwaitingEvidence(now: Long): Boolean =
        expected(now) != null && pendingToken != null && now < pendingUntil

    /**
     * Advance exactly one step only after OS evidence for the SAME approved
     * token. Structural/window changes are not proof of business-task success.
     * Any missing/negative outcome or timeout ends the guide; never retry.
     */
    fun onActionEvidence(token: String, status: String?, now: Long): String {
        val original=pendingToken ?: return "ignored"
        if(now>=pendingUntil || now>=expiry) {
            cancel()
            return "blocked"
        }
        if(token!=original) return "waiting"
        return when(status) {
            "claimed","android_accepted" -> "waiting"
            "window_change_observed","structural_change_observed","observed_change" -> {
                pendingToken=null
                pendingUntil=0L
                next++
                if(next>=steps.size) "completed" else "advanced"
            }
            else -> {
                cancel()
                "blocked"
            }
        }
    }

    fun completed(): Boolean = steps.isNotEmpty() && next >= steps.size
    fun cancel() {
        steps = emptyList()
        next = 0
        expiry = 0L
        boundPackage = null
        pendingToken = null
        pendingUntil = 0L
    }
}
