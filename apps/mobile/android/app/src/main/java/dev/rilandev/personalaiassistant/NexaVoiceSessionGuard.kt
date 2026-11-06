package dev.rilandev.personalaiassistant

/**
 * Marks the thread that is performing ONE already-approved Voice tool.
 *
 * This scope is only provenance; it DOES NOT authorize dispatch. Android's
 * owner Pause/Resume and the existing durable task/tool receipts still
 * control each operation. The native safety lock verifies that the scope's
 * session remains armed at the exact dispatch boundary. Owner Stop,
 * interruption, timeout, failed handshake and service destruction revoke
 * the session; a stale worker cannot execute after a later session starts.
 */
internal object NexaVoiceSessionGuard {
    private val scope=ThreadLocal<String?>()

    fun current():String?=scope.get()

    fun <T> during(session:String,block:()->T):T{
        require(session.matches(Regex("voice-[0-9a-f-]{36}"))) {
            "Invalid native Voice session scope"
        }
        check(scope.get()==null) { "Another Voice scope already active" }
        scope.set(session)
        return try{block()}finally{scope.remove()}
    }
}
