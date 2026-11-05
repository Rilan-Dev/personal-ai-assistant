package dev.rilandev.personalaiassistant

import java.util.Locale

/** Deterministic decomposition of explicit multi-action utterances.
 * It never treats message bodies, contact names or arbitrary prose as commands.
 * Each substep is revalidated by the native tool gate against fresh Android evidence.
 */
internal object NexaVoiceTaskPlan {
    data class Plan(val original:String, val steps:List<String>)
    private val executable=Regex(
        """^(?:(?:can|could|would) you (?:please )?|please )?(?:open|launch|start|read|check|inspect|describe|scroll|swipe|tap|click|press|list|show)\b""",
        RegexOption.IGNORE_CASE)
    private val boundary=Regex(
        """\s+(?:and then|then|and)\s+(?=(?:(?:please|can you|could you)\s+)?(?:open|launch|start|read|check|inspect|describe|scroll|swipe|tap|click|press|list|show)\b)""",
        RegexOption.IGNORE_CASE)
    fun parse(raw:String):Plan? {
        // Speech such as "open WhatsApp and find the contact X and open
        // the conversation chat" is ONE bounded owner task. Previously the
        // generic 'and open' split left a fictitious app name at step 0.
        NexaWhatsAppChatIntent.parse(raw)?.let { chat ->
            return Plan(raw,listOf("open WhatsApp",
                "open chat with "+chat.recipientHint))
        }
        val request=raw.trim().take(450)
            .replace(Regex("""^(?:hey\s+nexa[,\s]*)""",RegexOption.IGNORE_CASE),"")
        val steps=boundary.split(request).map { it.trim().trimEnd('.','?','!') }
        if(steps.size !in 2..4 || steps.any{it.length !in 5..160 || !executable.containsMatchIn(it)})
            return null
        // Never split or automate communications/calls through the generic UI sequence.
        if(steps.any{Regex("""\b(send|message|text|call|dial|pay|buy|delete|confirm|allow|grant)\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(it)})return null
        return Plan(raw,steps)
    }
}

/** Small task cursor; no side-effect can be executed by a planner alone. */
internal class NexaVoiceTaskCursor(private val plan:NexaVoiceTaskPlan.Plan) {
    val taskId="task-"+java.util.UUID.randomUUID().toString()
    val originalRequest:String get()=plan.original
    val steps:List<String> get()=plan.steps
    private var index=0
    val stepIndex:Int get()=index
    fun current():String?=plan.steps.getOrNull(index)
    fun advance(verified:Boolean):String? {
        if(!verified)return null
        index++
        return current()
    }
    val finished:Boolean get()=index>=plan.steps.size
    fun cancel(){index=plan.steps.size}
}

/** Conservative STT fragment handling. Don't open an app called "uh". */
internal object NexaVoiceFragmentGate {
    private val incomplete=Regex(
        """^(?:(?:hey nexa[,\s]*)?(?:can|could|would) you (?:please )?|please )?(?:open|launch|start)\s+(?:the|my|an|a)?\s*(?:uh|um|er|huh)?$""",
        RegexOption.IGNORE_CASE)
    fun isIncomplete(text:String):Boolean=incomplete.matches(text.trim().trimEnd('.','?','!'))
    fun hasPendingCompound(text:String):Boolean {
        val q=text.trim().trimEnd('.','?','!')
        return Regex("""^(?:(?:hey nexa[,\s]*)?(?:can|could|would) you (?:please )?|please )?(?:open|launch|start|read|check|scroll|swipe|tap|click|press)\b.+\b(?:and|then)$""",
            RegexOption.IGNORE_CASE).matches(q)
    }
}
