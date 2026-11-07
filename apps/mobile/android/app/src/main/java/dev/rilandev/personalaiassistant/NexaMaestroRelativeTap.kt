package dev.rilandev.personalaiassistant

import java.util.Locale

/**
 * A literal owner-requested relationship between two short, non-sensitive UI
 * control labels. Neither model text nor a screen's content creates this goal.
 * This class only parses/encodes the requested review; it never taps anything.
 */
internal data class NexaMaestroRelativeTap(
    val target: String,
    val anchor: String,
    val direction: String
) {
    companion object {
        private const val ACTION_PREFIX="tap_relative:"
        private val grammar=Regex(
            """^(?:hey\s+nexa[,!]?\s+)?(?:please\s+)?(?:tap|click|press|select)\s+(?:on\s+)?(?:the\s+)?(.+?)\s+(below|above|left of|right of)\s+(?:the\s+)?(.+)$""",
            RegexOption.IGNORE_CASE
        )
        private val relativeLead=Regex(
            """^(?:hey\s+nexa[,!]?\s+)?(?:(?:can|could|would)\s+you\s+)?(?:please\s+)?(?:tap|click|press|select)\b""",
            RegexOption.IGNORE_CASE
        )
        private val relationWord=Regex(
            """\b(?:below|above|left\s+of|right\s+of)\b""",
            RegexOption.IGNORE_CASE
        )
        /** A malformed relative owner request must NOT be reinterpreted as
         * a generic label tap by the next command parser.
         */
        fun looksRelative(raw:String):Boolean =
            raw.length in 8..150 && relativeLead.containsMatchIn(raw.trim()) &&
                relationWord.containsMatchIn(raw)

        private val unsafeWords=Regex(
            """\b(?:below|above|left|right|then|and|followed|after|before|send|submit|pay|purchase|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|passcode|otp|logout)\b""",
            RegexOption.IGNORE_CASE
        )
        private fun acceptable(value:String):Boolean =
            value==NexaTaskScreenObservation.safeLabel(value) &&
            !unsafeWords.containsMatchIn(value)

        fun parse(raw:String):NexaMaestroRelativeTap? {
            if(raw.length !in 17..125 || raw.any{it.code<32})return null
            val match=grammar.matchEntire(raw.trim().trimEnd('.','?','!')) ?:return null
            val target=match.groupValues[1].trim()
            val anchor=match.groupValues[3].trim()
            if(!acceptable(target) || !acceptable(anchor))return null
            val direction=when(match.groupValues[2].lowercase(Locale.ROOT)) {
                "below" -> "below"
                "above" -> "above"
                "left of" -> "left"
                "right of" -> "right"
                else -> return null
            }
            return NexaMaestroRelativeTap(target,anchor,direction)
        }

        fun fromAction(encoded:String):NexaMaestroRelativeTap? {
            if(!encoded.startsWith(ACTION_PREFIX))return null
            val fields=encoded.removePrefix(ACTION_PREFIX).split('|')
            if(fields.size!=3 || fields[0] !in setOf("below","above","left","right") ||
                !acceptable(fields[1]) || !acceptable(fields[2]))return null
            return NexaMaestroRelativeTap(fields[1],fields[2],fields[0])
        }
    }

    fun toAction():String="tap_relative:$direction|$target|$anchor"
}
