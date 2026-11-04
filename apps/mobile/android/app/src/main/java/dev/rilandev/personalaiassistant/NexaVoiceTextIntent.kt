package dev.rilandev.personalaiassistant

import android.os.SystemClock

/** Voice transcription does not bypass the native field or owner-approval gates. */
internal object NexaVoiceTextIntent {
    data class Request(val targetLabel:String,val exactText:String)
    private val typeGrammar=Regex(
        """^(?:hey\s+nexa[,!]?\s+)?(?:please\s+)?(?:type|enter|write|insert)\s+(.+?)\s+(?:into|in)\s+(?:the\s+)?(.+)$""",
        RegexOption.IGNORE_CASE)
    private val fillGrammar=Regex(
        """^(?:hey\s+nexa[,!]?\s+)?(?:please\s+)?fill\s+(?:the\s+)?(.+?)\s+with\s+(.+)$""",
        RegexOption.IGNORE_CASE)
    private val lock=Any()
    private var request:Request?=null
    private var expiry:Long=0L

    fun parse(raw:String):Request?{
        if(raw.length !in 14..820 || raw.any{it=='\u0000'||it=='\r'||it=='\n'})
            return null
        val a=typeGrammar.matchEntire(raw.trim())
        val b=if(a==null)fillGrammar.matchEntire(raw.trim()) else null
        val text=(a?.groupValues?.get(1) ?: b?.groupValues?.get(2))?.trim()
            ?.trim('"','“','”','\'') ?:return null
        val label=(a?.groupValues?.get(2) ?: b?.groupValues?.get(1))?.trim()
            ?.replace(Regex("""\s+(?:input\s+)?(?:field|box)$""",RegexOption.IGNORE_CASE),"")
            ?.trim() ?:return null
        if(!NexaTextInputPolicy.safeTarget(label) || text.length !in 1..500 ||
            text.isBlank() || text.any{it.code<32} ||
            Regex("""\b(and then|also open|and send|and submit|without approval)\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(raw))return null
        return Request(label,text)
    }
    fun queue(value:Request,now:Long=SystemClock.elapsedRealtime())=synchronized(lock){
        request=value
        expiry=now+60_000L
    }
    fun take(now:Long=SystemClock.elapsedRealtime()):Request?=synchronized(lock){
        val value=request?.takeIf{now<expiry}
        request=null
        expiry=0
        value
    }
    fun clear()=synchronized(lock){request=null;expiry=0}
}
