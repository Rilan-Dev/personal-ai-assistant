package dev.rilandev.personalaiassistant

/**
 * Monotonic generation marker for one-shot visual-capture callbacks.
 * Every cancellation or re-arm invalidates all older callbacks. Callers
 * synchronize access with their own state lock.
 */
internal class NexaCaptureEpoch {
    private var generation=0L
    fun begin(): Long { generation++; return generation }
    fun invalidate() { generation++ }
    fun current(token: Long): Boolean = token == generation
}
