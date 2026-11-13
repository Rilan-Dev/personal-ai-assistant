package dev.rilandev.personalaiassistant

import okhttp3.Response

/**
 * Sarvam HTTPS responses are external, untrusted byte streams. Check their
 * size BEFORE materializing a String; String.take() after body.string() still
 * buffers the entire response and can exhaust an Android process.
 *
 * peekBody reads at most limit+1 decoded bytes. A truncated result is
 * rejected, never silently parsed as a complete model answer or provider
 * error. The caller must still close its original Response.
 */
internal object NexaSarvamResponseBounds {
    const val MAX_CHAT_BYTES=140_000L
    const val MAX_ERROR_BYTES=5_000L

    fun textOrNull(response:Response,limit:Long):String? {
        if(limit !in 1..MAX_CHAT_BYTES)return null
        return try {
            val bounded=response.peekBody(limit+1L)
            if(bounded.contentLength()>limit) null else bounded.string()
        } catch (_:Exception) {
            null
        }
    }
}
