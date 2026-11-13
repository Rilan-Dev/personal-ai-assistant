package dev.rilandev.personalaiassistant

import org.json.JSONObject

/**
 * A successful HTTP status does NOT prove the conversation model accepted
 * Nexa's chat/tool schema. Readiness must observe one non-executing assistant
 * completion, not an empty 200, malformed JSON, provider error object, a
 * truncated response or a model-generated function call.
 *
 * This never executes tools and does not parse private speech or phone data:
 * the readiness probe sends only the literal synthetic "Reply OK only".
 */
internal object NexaSarvamProbeResponse {
    fun hasNonExecutingAssistantCompletion(raw:String?):Boolean {
        if(raw.isNullOrBlank() || raw.length>140_000)return false
        return try {
            val obj=JSONObject(raw)
            val choices=obj.optJSONArray("choices") ?:return false
            if(choices.length()!=1)return false
            val choice=choices.optJSONObject(0) ?:return false
            if(choice.optString("finish_reason")=="length")return false
            val message=choice.optJSONObject("message") ?:return false
            if(message.optString("role")!="assistant")return false
            val toolCalls=message.optJSONArray("tool_calls")
            if(toolCalls!=null && toolCalls.length()!=0)return false
            val content=message.opt("content") as? String ?:return false
            content.trim().length in 1..3_000
        }catch(_:Exception){false}
    }
}
