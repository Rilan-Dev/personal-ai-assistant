package dev.rilandev.personalaiassistant

import java.util.Locale

/** Strict, local two-frame phrase gate; never matches just "hey" or "nexa". */
internal class NexaWakePhrase {
    private var matches = 0
    private var lastAt = 0L

    fun observe(raw: String, now: Long, complete: Boolean = false): Boolean {
        val normalized = raw.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val phrase = listOf("hey nexa", "hey nexus", "hey next a", "hey nexa ai")
            .any { normalized == it || normalized.startsWith("$it ") }
        if (!phrase) {
            matches = 0
            return false
        }
        matches = if (now - lastAt < 2000 && now >= lastAt) matches + 1 else 1
        lastAt = now
        return if (complete && matches >= 2) { matches = 0; true } else false
    }
}
