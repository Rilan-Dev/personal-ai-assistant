package dev.rilandev.personalaiassistant

/** Strict UI-control vocabulary. Arbitrary screen text never enters icon observations. */
internal object ScreenIconClassifier {
    private val labels: Map<String, Pair<String, String>> = mapOf(
        "call" to ("call" to "Call"), "voice call" to ("call" to "Voice call"),
        "phone" to ("call" to "Phone"),
        "video call" to ("video_call" to "Video call"), "video" to ("video_call" to "Video"),
        "menu" to ("menu" to "Menu"), "navigation menu" to ("menu" to "Navigation menu"),
        "back" to ("back" to "Back"), "navigate back" to ("back" to "Navigate back"),
        "search" to ("search" to "Search"), "search icon" to ("search" to "Search icon"),
        "attach" to ("attach" to "Attach"), "attachment" to ("attach" to "Attachment"),
        "attach file" to ("attach" to "Attach file"),
        "microphone" to ("microphone" to "Microphone"), "mic" to ("microphone" to "Mic"),
        "voice message" to ("microphone" to "Voice message"),
        "camera" to ("camera" to "Camera"), "take photo" to ("camera" to "Take photo"),
        "send" to ("send" to "Send"), "send message" to ("send" to "Send message"),
        "settings" to ("settings" to "Settings"), "share" to ("share" to "Share"),
        "delete" to ("delete" to "Delete"), "trash" to ("delete" to "Trash"),
        "close" to ("close" to "Close"), "more" to ("more_options" to "More"),
        "more options" to ("more_options" to "More options")
    )

    fun classify(raw: CharSequence?): Pair<String, String>? {
        val value = raw?.toString() ?: return null
        if (value.isBlank() || value.length > 80) return null
        val normalized = value.trim().lowercase(java.util.Locale.ROOT)
            .replace(Regex("\\s+"), " ").removeSuffix(" button")
        return labels[normalized]
    }
}
