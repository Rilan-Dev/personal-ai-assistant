package dev.rilandev.personalaiassistant

import java.util.Locale

/**
 * Sarvam sends transcribed USER utterances as data, not trusted instructions.
 * Only a bounded, unambiguous single owner request may enter the native
 * execution path. The managed Sarvam voice model continues dialogue, while
 * the device independently enforces consent on app launches.
 *
 * This is not an undocumented Sarvam tool-calling protocol. The server-side
 * Sarvam API-tool/webhook configuration is a separate integration.
 */
internal object NexaVoiceIntentGate {
    data class AppRequest(val app: String, val command: String)

    fun appRequest(raw: String): AppRequest? {
        if (raw.length !in 1..250) return null
        var text = raw.lowercase(Locale.ROOT).trim()
            .replace(Regex("[.!?]+$"), "").trim()
        text = text.replace(Regex("^hey\\s+nexa[,!]?\\s+"), "")
        // Do not turn quoted speech, negation, multi-step or delivery requests
        // into a partial app-opening side effect.
        if (Regex("[;\\n\\\"'“”‘’]|\\b(and|then|or|also|after|before|send|text|message|call|dial|delete|don't|not|never|without|instead)\\b")
                .containsMatchIn(text)) return null
        text = text.replace(Regex("^would you mind (please )?"), "")
            .replace(Regex("^(can|could|would|will) you (please )?"), "")
            .replace(Regex("^please "), "")
            .replace(Regex(" please$"), "")
            .replace(Regex(" for me$"), "").trim()
        text = text.replace(Regex("^opening "), "open ")
        text = text.replace(Regex("^launching "), "launch ")
        val match = Regex("^(open|launch|start) (my |the )?(whatsapp business|whatsapp|github|brave)( app)?$")
            .matchEntire(text) ?: return null
        val app = match.groupValues[3]
        return AppRequest(app, "open $app")
    }
}
