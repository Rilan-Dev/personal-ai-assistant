package dev.rilandev.personalaiassistant

import android.os.SystemClock
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.util.UUID

/**
 * A single owner-review ingress for Chat, Sarvam/OpenAI speech, assistant role
 * and overlay. It is NOT an executor or permission/approval channel.
 *
 * Android mints a short-lived one-shot handoff token. Only the foreground
 * review screen may exchange it for a new encrypted, non-executable task.
 * A model/transcript cannot mint a trusted origin or arbitrary task ID.
 */
internal object NexaOwnerTaskReview {
    private val grammar=Regex(
        """^(?:hey\s+nexa[,!]?\s+)?(?:please\s+)?(?:save|create|add)\s+(?:a\s+)?task\s+(?:to\s+|:\s*)(.+)$""",
        RegexOption.IGNORE_CASE)
    private val lock=Any()
    private const val OFFER_MS=60_000L
    private const val SAVE_MS=5*60_000L
    private val origins=setOf("chat","live-voice","assistant-role","overlay")
    private data class Handoff(
        val goal:String,val origin:String,val token:String,
        val offeredAt:Long,val expiresAt:Long
    )
    private var waiting:Handoff?=null
    private var viewed:Handoff?=null

    fun parse(text:String):String? {
        if(text.length !in 10..420 || text.any {it.code<32})return null
        val match=grammar.matchEntire(text.trim()) ?:return null
        val goal=match.groupValues[1].trim().trimEnd('.','?','!').trim()
        if(goal.length !in 5..320)return null
        return goal
    }
    /** Reject a newer assistant/overlay command while one owner review is live. */
    fun queue(goal:String,origin:String="chat",
              now:Long=SystemClock.elapsedRealtime()):Boolean=synchronized(lock){
        if(goal.length !in 5..320 || goal.any {it.code<32} ||
            origin !in origins)return@synchronized false
        if(waiting?.expiresAt?.let {now<it}==true ||
            viewed?.expiresAt?.let {now<it}==true)return@synchronized false
        waiting=Handoff(goal,origin,UUID.randomUUID().toString(),
            now,now+SAVE_MS)
        viewed=null
        true
    }
    /** Explicit native owner utterance / overlay text submit. Opening the UI
     * never persists a record or grants any native tool capability.
     */
    fun offerInForeground(context:Context,goal:String,origin:String="chat"):Boolean {
        if(!queue(goal,origin))return false
        return try {
            context.startActivity(Intent(context,MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            true
        }catch (_:Exception) {
            clear()
            false
        }
    }
    /** Consume one UI presentation; retain only a short-lived binding until
     * the owner explicitly presses Save. Duplicate UI consumers cannot mint
     * another task ID. No data has been written to custom.db yet.
     */
    fun takeEnvelope(now:Long=SystemClock.elapsedRealtime()):JSONObject?=synchronized(lock){
        val value=waiting?:return@synchronized null
        waiting=null
        if(now-value.offeredAt>OFFER_MS || now>=value.expiresAt)
            return@synchronized null
        viewed=value
        JSONObject().put("goal",value.goal)
            .put("origin",value.origin)
            .put("token",value.token)
    }
    /** Compatibility for older native callers. No secure origin survives
     * this legacy scalar read, so it cannot be used for origin-bound saving.
     */
    fun take(now:Long=SystemClock.elapsedRealtime()):String? =
        takeEnvelope(now)?.optString("goal")

    /**
     * Atomically exchange the presented native token for one encrypted task.
     * In-memory lock prevents double-save, even across racing RN promises.
     * If the DB write fails, it is safe to retry while the token is valid.
     */
    fun saveViewed(context:Context,token:String,goal:String,
                   now:Long=SystemClock.elapsedRealtime()):String?=synchronized(lock){
        val value=viewed?:return@synchronized null
        if(now>=value.expiresAt || token!=value.token ||
            token.length!=36 || goal!=value.goal ||
            value.origin !in origins)return@synchronized null
        val id="task-"+UUID.randomUUID()
        val session="owner-"+UUID.randomUUID()
        val saved=NexaConversationDb.saveProposedAgentTask(
            context,id,session,value.origin,"owner-review-v1",goal)
        if(!saved)return@synchronized null
        viewed=null
        id
    }
    fun clear()=synchronized(lock){waiting=null;viewed=null}
}
