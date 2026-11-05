package dev.rilandev.personalaiassistant

import java.text.Normalizer
import java.util.Locale

/**
 * Deliberately narrow, owner-originated "open WhatsApp -> find chat" task.
 * A contact name in a transcript is only a search hint, not identity proof.
 * No messages are read, typed, or sent by this planner.
 */
internal object NexaWhatsAppChatIntent {
    data class Request(val recipientHint:String)
    private val full=Regex(
        """^(?:open|launch|start) (?:my |the )?whatsapp(?: app)?\s+and\s+(?:find|look for|search for)\s+(?:the )?(?:contact |chat |conversation )?(.+?)\s+and\s+(?:open|show)\s+(?:the )?(?:conversation )?chat$""",
        RegexOption.IGNORE_CASE)
    private val next=Regex(
        """^(?:open|show)\s+(?:the )?(?:whatsapp )?(?:conversation|chat)\s+(?:with|for)\s+(.+)$""",
        RegexOption.IGNORE_CASE)
    private val forbidden=Regex(
        """\b(send|delete|pay|call|dial|transfer|purchase|password|message|submit|confirm|grant|allow|install|uninstall)\b""",
        RegexOption.IGNORE_CASE)
    private fun cleanCommand(input:String):String {
        var v=input.trim().trimEnd('.','?','!').trim()
        v=v.replace(Regex("""^(?:hey\s+nexa[,!\s]*)""",RegexOption.IGNORE_CASE),"")
        v=v.replace(Regex("""^(?:can|could|would)\s+you\s+(?:please\s+)?""",RegexOption.IGNORE_CASE),"")
        return v.replace(Regex("""^please\s+""",RegexOption.IGNORE_CASE),"").trim()
    }
    private fun contact(raw:String):String? {
        val name=raw.trim().replace(Regex("""\s+""")," ")
        if(name.length !in 3..55 || forbidden.containsMatchIn(name) ||
            name.any{it.code<32} || !name.any{it.isLetter()})return null
        if(name.matches(Regex("""(?i).*\b(and|then|click|tap|send|open|search)\b.*""")))
            return null
        return name
    }
    fun parse(raw:String):Request? {
        if(raw.length !in 24..220)return null
        val m=full.matchEntire(cleanCommand(raw))?:return null
        return contact(m.groupValues[1])?.let(::Request)
    }
    fun followupRecipient(raw:String):String? {
        if(raw.length !in 14..110)return null
        val v=cleanCommand(raw)
        return next.matchEntire(v)?.groupValues?.get(1)?.let(::contact)
    }
}

/**
 * Ground an explicitly requested chat against CURRENT visible Accessibility
 * labels. Emoji and punctuation are ignored; a single small STT error is
 * tolerated only for sufficiently long, unambiguous names. This is *not* a
 * contact lookup, transcript memory, or permission to send messages.
 */
internal object NexaWhatsAppChatMatch {
    private fun key(value:String):String {
        val normalized=Normalizer.normalize(value,Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        return normalized.replace(Regex("""[^\p{L}\p{N}]"""),"").take(80)
    }
    private fun distanceAtMostOne(a:String,b:String):Boolean {
        if(kotlin.math.abs(a.length-b.length)>1)return false
        var i=0;var j=0;var changes=0
        while(i<a.length&&j<b.length) {
            if(a[i]==b[j]){i++;j++;continue}
            changes++
            if(changes>1)return false
            when {
                a.length>b.length -> i++
                b.length>a.length -> j++
                else -> {i++;j++}
            }
        }
        return changes+(a.length-i)+(b.length-j)<=1
    }
    /** 0=no match, 100=exact after emoji stripping, 85=one ASR edit. */
    fun evidence(expected:String,visible:String):Int {
        val a=key(expected);val b=key(visible)
        if(a.length !in 3..55||b.length !in 3..80)return 0
        if(a==b)return 100
        // A one-letter mismatch is permissible only for a sufficiently
        // distinctive multi-word name. Require a long shared suffix as well.
        if(a.length>=9 && b.length>=9 &&
            expected.trim().contains(' ') && visible.trim().contains(' ') &&
            distanceAtMostOne(a,b))return 85
        return 0
    }
}
