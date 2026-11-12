package dev.rilandev.personalaiassistant

/**
 * Trusted, thread-local provenance for one already-authorized compound Voice
 * action. Only the native Voice worker enters this scope after the existing
 * visible/persistent-owner policy and one-shot step receipt have been checked.
 *
 * The scope is metadata, NEVER authorization. A canonical UI claim must
 * independently verify the saved parent task, active step and consumed receipt
 * in its own SQLite transaction before linking an observed Android action.
 * Unscoped actions retain their existing separate audit behavior.
 */
internal object NexaVoiceActionScope {
    data class Parent(val taskId:String,val sessionId:String,
                      val ordinal:Int,val command:String,
                      val packageName:String)
    private val local=ThreadLocal<Parent?>()

    fun active():Parent?=local.get()

    fun <T> during(eligible:Boolean,taskId:String,sessionId:String,
                   ordinal:Int,command:String,packageName:String,
                   block:()->T):T {
        if(!eligible)return block()
        check(local.get()==null){"Voice action context already active"}
        require(Regex("task-[A-Za-z0-9_-]{8,96}").matches(taskId))
        require(sessionId.length in 1..100 && ordinal in 0..7 &&
            command.length in 1..500 &&
            (packageName.isEmpty() ||
                packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}"))))
        local.set(Parent(taskId,sessionId,ordinal,command,packageName))
        return try{block()}finally{local.remove()}
    }

    /** This is a narrow classification of existing actions, not a tool grant. */
    fun expectedUiKind(command:String):String? = when {
        command.startsWith("open_whatsapp_chat:") ||
            command.startsWith("tap_visible_label:") ||
            command.startsWith("tap_safe_icon:") -> "tap"
        command.startsWith("scroll_screen:") -> {
            val direction=command.removePrefix("scroll_screen:")
            if(direction in setOf("up","down","left","right"))
                "scroll_"+direction else null
        }
        command=="go_back" -> "nav_back"
        command=="open_notification_shade" -> "nav_notifications"
        command=="open" || command.startsWith("open ") ||
            command.startsWith("chrome_search:") -> "app_launch"
        else -> null
    }
}
