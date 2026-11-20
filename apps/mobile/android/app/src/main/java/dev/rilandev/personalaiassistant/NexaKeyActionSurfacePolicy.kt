package dev.rilandev.personalaiassistant

/** Narrow fallback for named global navigation when no third-party window is exposed. */
internal object NexaKeyActionSurfacePolicy {
    private const val SYSTEM_UI = "com.android.systemui"
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")
    private val notificationShadeMarkers = setOf(
        "$SYSTEM_UI:id/notification_panel",
        "$SYSTEM_UI:id/quick_settings_container",
        "$SYSTEM_UI:id/notification_stack_scroller"
    )

    fun fallbackPackage(name: String, ownerPackage: String,
                        ownerWindowFocused: Boolean,
                        notificationShadeVisible: Boolean): String? {
        if (name !in setOf("back", "home", "notifications") ||
            !packagePattern.matches(ownerPackage)) return null
        if (name == "notifications")
            return SYSTEM_UI.takeIf { ownerWindowFocused || notificationShadeVisible }
        if (notificationShadeVisible) return SYSTEM_UI
        if (ownerWindowFocused) return ownerPackage
        return null
    }

    fun isNotificationShade(packageName: String, windowTitle: String,
                            rootResourceIds: Set<String> = emptySet()): Boolean {
        if (packageName != SYSTEM_UI) return false
        val normalized = windowTitle.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
        return normalized in setOf("notificationshade", "quicksettings",
            "notificationpanel", "notifications") ||
            rootResourceIds.any { it in notificationShadeMarkers }
    }

    /** Notification surfaces expose only this bounded set of safe icon metadata. */
    fun allowsNotificationShadeIcon(semantic: String): Boolean =
        semantic in setOf("unknown", "settings", "close", "back", "menu", "more_options", "search")
}
