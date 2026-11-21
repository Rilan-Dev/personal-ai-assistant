package dev.rilandev.personalaiassistant

/** Permission status shown by the floating assistant; it always reflects the owner's saved mode. */
internal object NexaToolOverlayPermissionCopy {
    fun note(mode: String): String = when (mode) {
        NexaVoicePermissionPolicy.FULL_ACCESS ->
            "Full Access: routine tools run without Nexa approval; protected sends and Android grants still apply."
        NexaVoicePermissionPolicy.TRUST_NAVIGATION ->
            "Routine navigation can run without repeated approval; other actions still need review."
        NexaVoicePermissionPolicy.TRUST_SAFE ->
            "Previously approved safe actions can skip repeated prompts."
        else -> "On-device actions need your approval."
    }
}
