package dev.rilandev.personalaiassistant

import android.os.SystemClock

/** Spoken task requests are suggestions for the owner to review, never actions. */
internal object NexaOwnerTaskReview {
    private val grammar=Regex(
        """^(?:hey\s+nexa[,!]?\s+)?(?:please\s+)?(?:save|create|add)\s+(?:a\s+)?task\s+(?:to\s+|:\s*)(.+)$""",
        RegexOption.IGNORE_CASE)
    private val lock=Any()
    private var draft:String?=null
    private var expiry=0L

    fun parse(text:String):String? {
        if(text.length !in 10..420 || text.any {it.code<32})return null
        val match=grammar.matchEntire(text.trim()) ?:return null
        val goal=match.groupValues[1].trim().trimEnd('.','?','!').trim()
        if(goal.length !in 5..320)return null
        return goal
    }
    fun queue(goal:String,now:Long=SystemClock.elapsedRealtime()):Boolean=synchronized(lock){
        if(goal.length !in 5..320 || goal.any {it.code<32})return@synchronized false
        if(draft!=null && now<expiry)return@synchronized false
        draft=goal
        expiry=now+60_000L
        true
    }
    fun take(now:Long=SystemClock.elapsedRealtime()):String?=synchronized(lock){
        val value=draft?.takeIf{now<expiry}
        draft=null
        expiry=0L
        value
    }
    fun clear()=synchronized(lock){draft=null;expiry=0L}
}
