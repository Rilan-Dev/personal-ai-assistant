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

    fun canSelect(semantic: String, now: Long): Boolean = expected(now) == semantic

    /** Only call after the native controller ACCEPTED the owner's one-time tap. */
    fun afterConfirmedClick(semantic: String, now: Long): Boolean {
        if (!canSelect(semantic,now)) return false
        next++
        return true
    }

    fun completed(): Boolean = steps.isNotEmpty() && next >= steps.size
    fun cancel() { steps = emptyList(); next = 0; expiry = 0L }
}
