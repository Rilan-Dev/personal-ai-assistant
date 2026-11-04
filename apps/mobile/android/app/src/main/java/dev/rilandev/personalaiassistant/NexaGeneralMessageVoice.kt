package dev.rilandev.personalaiassistant

import android.os.SystemClock

/** Only deterministic owner speech is routed; provider-proposed tool calls
 * never get to invent the recipient, app, message body or authorization. */
internal object NexaGeneralMessageVoice {
    data class Request(val recipientHint:String, val message:String,
        val explicitApp:String?)
    private val grammar=Regex(
        """^(?:hey\s+nexa[,!]?\s+)?(?:(?:can you|could you|please)\s+)?(?:send|prepare)\s+(?:a\s+)?(?:(whatsapp business|whatsapp|sms|imo|botim)\s+)?(?:text\s+)?(?:message|text|sms)\s+to\s+(?:my\s+)?([\p{L}][\p{L}\p{N} .'-]{0,48}?)\s+(?:that|saying)\s+(.{1,500})$""",
        RegexOption.IGNORE_CASE
    )
    private val lock=Any()
    private var requested:Request?=null
    private var expiresAt:Long=0L

    fun parse(raw:String):Request? {
        if(raw.length !in 14..750 || raw.any{it=='\r'||it=='\n'||it=='\u0000'})
            return null
        val parts=grammar.matchEntire(raw.trim())?:return null
        val name=parts.groupValues[2].trim()
        val body=parts.groupValues[3].trim().trim('"','“','”')
        if(name.length !in 2..50 || body.length !in 1..500 || body.isBlank() ||
            body.any{it.code<32})return null
        val app=parts.groupValues[1].trim().lowercase(java.util.Locale.ROOT)
            .replace(" ","-").ifEmpty{null}
        return Request(name,body,app)
    }
    fun queue(intent:Request,now:Long=SystemClock.elapsedRealtime())=synchronized(lock) {
        requested=intent
        expiresAt=now+60_000L
    }
    fun take(now:Long=SystemClock.elapsedRealtime()):Request?=synchronized(lock) {
        val x=requested?.takeIf{now<expiresAt}
        requested=null
        expiresAt=0
        x
    }
    fun clear()=synchronized(lock){requested=null;expiresAt=0}
}
