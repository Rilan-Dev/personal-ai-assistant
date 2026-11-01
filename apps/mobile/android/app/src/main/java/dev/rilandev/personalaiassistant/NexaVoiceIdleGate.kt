package dev.rilandev.personalaiassistant

/**
 * Best-effort local speech-activity timeout for user-started live voice.
 * This is NOT speech recognition, a hardware hotword, or voice identity.
 * Persistent noise can postpone idle detection. Android always retains
 * its microphone privacy indicator and owner Stop notification.
 */
internal class NexaVoiceIdleGate(
    private val limitMs: Long = 45_000L,
    private val sampleThreshold: Int = 650
) {
    @Volatile private var lastSpeechMs = 0L
    private var activeFrames = 0

    @Synchronized fun reset(now: Long) {
        lastSpeechMs = now
        activeFrames = 0
    }

    @Synchronized fun observePcm(pcm: ByteArray, size: Int, now: Long) {
        if (size <= 0 || size > pcm.size) return
        val samples = size / 2
        if (samples < 100) return
        var amplitude = 0L
        var i = 0
        while (i + 1 < size) {
            val sample = (pcm[i].toInt() and 0xff) or (pcm[i+1].toInt() shl 8)
            amplitude += kotlin.math.abs(sample.toShort().toInt())
            i += 2
        }
        if (amplitude / samples >= sampleThreshold) activeFrames++
        else activeFrames = 0
        if (activeFrames >= 4) {
            lastSpeechMs = now
            if (activeFrames > 8) activeFrames = 8
        }
    }

    fun inactive(now: Long): Boolean =
        lastSpeechMs != 0L && now - lastSpeechMs >= limitMs
}
