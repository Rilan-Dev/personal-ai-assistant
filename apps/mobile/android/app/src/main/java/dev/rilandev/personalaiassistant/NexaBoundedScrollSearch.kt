package dev.rilandev.personalaiassistant

import java.util.Locale

/**
 * Bounded in-memory owner guide. Never performs a gesture, mints an approval,
 * reads private fields, or continues after uncertain evidence. The existing
 * ScreenScroll controller and encrypted journal remain the only action path.
 */
internal class NexaBoundedScrollSearch {
    data class Request(val label: String, val direction: String)
    enum class Progress { WAITING, NEXT_APPROVAL, FOUND, LIMIT, BLOCKED }

    companion object {
        const val MAX_SCROLLS = 4
        private const val TASK_MS = 90_000L
        private const val EVIDENCE_MS = 4_500L
        // A single scroll event is not proof that the layout stopped moving.
        private const val MIN_STABLE_MS = 250L
        private val grammar = Regex(
            """^(?:(?:hey\s+nexa[,!]?\s+)?(?:please\s+)?)scroll\s+(up|down|left|right)\s+until\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        private val allowedPackage = Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")

        fun parse(query: String): Request? {
            val raw = query.trim()
            if (raw.length !in 18..110 || raw.any { it.code < 32 }) return null
            val match = grammar.matchEntire(raw) ?: return null
            val label = NexaTaskScreenObservation.safeLabel(
                match.groupValues[2].trim().trimEnd('.', '?', '!').trim()
            ) ?: return null
            return Request(label, match.groupValues[1].lowercase(Locale.ROOT))
        }
    }

    private data class Session(
        val request: Request,
        val packageName: String,
        val windowId: Int,
        val pauseGeneration: Long,
        val expiresAt: Long,
        var attempts: Int = 0,
        var pendingToken: String? = null,
        var evidenceExpiresAt: Long = 0L,
        var stableCount: Int? = null,
        var stableBoundsKey: String? = null,
        var stableSince: Long = 0L,
        val claimedTokens: MutableSet<String> = linkedSetOf()
    )
    private var session: Session? = null

    fun cancel() { session = null }

    fun start(request: Request, packageName: String, windowId: Int,
              pauseGeneration: Long, now: Long): Boolean {
        cancel()
        if (!allowedPackage.matches(packageName) || windowId < 0 ||
            pauseGeneration < 0 || now < 0 ||
            request.direction !in setOf("up", "down", "left", "right") ||
            NexaTaskScreenObservation.safeLabel(request.label) != request.label)
            return false
        session = Session(request, packageName, windowId, pauseGeneration, now + TASK_MS)
        return true
    }

    fun active(now: Long): Boolean {
        val current = session ?: return false
        if (now >= current.expiresAt) {
            cancel()
            return false
        }
        return true
    }

    fun request(now: Long): Request? = if (active(now)) session?.request else null
    fun attempts(now: Long): Int = if (active(now)) session?.attempts ?: 0 else 0

    private fun sameContext(s: Session, packageName: String, windowId: Int,
                            pauseGeneration: Long): Boolean =
        s.packageName == packageName && s.windowId == windowId &&
            s.pauseGeneration == pauseGeneration

    fun confirmable(packageName: String, windowId: Int,
                    pauseGeneration: Long, now: Long): Boolean {
        if (!active(now)) return false
        val s = session ?: return false
        return sameContext(s, packageName, windowId, pauseGeneration) &&
            s.pendingToken == null && s.attempts < MAX_SCROLLS
    }

    /** Called only after the owner's freshly approved journaled scroll returns
     * Android-accepted; duplicate approval tokens cannot stage a second step.
     */
    fun onApprovedScroll(token: String, packageName: String, windowId: Int,
                         pauseGeneration: Long, now: Long): Boolean {
        if (!confirmable(packageName, windowId, pauseGeneration, now)) return false
        val s = session ?: return false
        if (token.length !in 16..128 || !s.claimedTokens.add(token)) return false
        s.attempts++
        s.pendingToken = token
        s.evidenceExpiresAt = minOf(s.expiresAt, now + EVIDENCE_MS)
        s.stableCount = null
        s.stableBoundsKey = null
        s.stableSince = 0L
        return true
    }

    /**
     * Observe the SAME consumed token and same external window. An accepted
     * Intent/gesture alone is not evidence. Each NEXT_APPROVAL needs a new
     * explicit owner click; FOUND is read-only and never clicks the target.
     */
    fun onEvidence(token: String, status: String?, packageName: String,
                   windowId: Int, pauseGeneration: Long,
                   matchingControls: Int, now: Long,
                   uniqueBoundsKey: String? = null): Progress {
        val s = session
        if (s == null || !active(now)) return Progress.BLOCKED
        if (!sameContext(s, packageName, windowId, pauseGeneration) ||
            token != s.pendingToken || now >= s.evidenceExpiresAt ||
            matchingControls !in 0..1) {
            cancel()
            return Progress.BLOCKED
        }
        if (status == null || status == "claimed" || status == "android_accepted")
            return Progress.WAITING
        if (status == "scroll_event_observed" ||
            (status == "structural_change_observed" && matchingControls == 1)) {
            // Require two post-event, time-separated readings with identical
            // target geometry before reporting FOUND or requesting another
            // owner-approved scroll. This never schedules another action.
            // Without a unique bounds key, "found" is not grounded.
            if (matchingControls == 1 &&
                (uniqueBoundsKey.isNullOrBlank() || uniqueBoundsKey.length > 80)) {
                cancel()
                return Progress.BLOCKED
            }
            if (matchingControls == 0 && uniqueBoundsKey != null) {
                cancel()
                return Progress.BLOCKED
            }
            if (s.stableCount != matchingControls ||
                s.stableBoundsKey != uniqueBoundsKey) {
                s.stableCount = matchingControls
                s.stableBoundsKey = uniqueBoundsKey
                s.stableSince = now
                return Progress.WAITING
            }
            if (now - s.stableSince < MIN_STABLE_MS) return Progress.WAITING
            if (matchingControls == 1) {
                cancel()
                return Progress.FOUND
            }
            if (s.attempts >= MAX_SCROLLS) {
                cancel()
                return Progress.LIMIT
            }
            s.pendingToken = null
            s.evidenceExpiresAt = 0L
            return Progress.NEXT_APPROVAL
        }
        cancel()
        return Progress.BLOCKED
    }
}
