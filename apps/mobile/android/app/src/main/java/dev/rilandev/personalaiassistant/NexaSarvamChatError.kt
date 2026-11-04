package dev.rilandev.personalaiassistant

import org.json.JSONObject
import java.util.Locale

/**
 * Privacy-safe provider failure classification. Never includes raw error
 * messages, prompts, API keys, recipient labels or request bodies in logs.
 */
internal object NexaSarvamChatError {
    data class Diagnosis(val code:String,val explanation:String,
        val safeTextOnlyRetry:Boolean)

    fun classify(status:Int,raw:String):Diagnosis {
        if(status==401 || status==403)
            return Diagnosis("authentication",
                "Sarvam rejected this API credential. Verify your key and access.",false)
        if(status==429)
            return Diagnosis("rate_limit","Sarvam temporarily rate-limited the conversation.",false)
        if(status>=500)
            return Diagnosis("provider_unavailable",
                "Sarvam's conversation service is temporarily unavailable.",false)
        if(status!=400)
            return Diagnosis("http_"+status,"Sarvam chat request failed (HTTP "+status+").",false)
        val error=try{
            val json=JSONObject(raw.take(5000))
            json.optJSONObject("error")
        }catch(_:Exception){null}
        val type=error?.optString("code").orEmpty().lowercase(Locale.ROOT)
        val message=error?.optString("message").orEmpty().lowercase(Locale.ROOT)
        val schemaIssue=(type.contains("tool") ||
            message.contains("tool schema") ||
            message.contains("unsupported tools") ||
            message.contains("tools not supported") ||
            message.contains("invalid tool") ||
            message.contains("tool_choice") ||
            message.contains("function parameters"))
        val contextIssue=type.contains("context") || message.contains("context length") ||
            message.contains("maximum context") || message.contains("too many tokens")
        val code=when {
            schemaIssue -> "unsupported_tool_schema"
            contextIssue -> "context_limit"
            type=="invalid_request_error" -> "invalid_request"
            else -> "bad_request_unclassified"
        }
        // A single no-tool fallback is permitted ONLY when the provider itself
        // specifically names the tool schema. An arbitrary HTTP 400 is not a
        // license to alter privacy settings or retry indefinitely.
        val description=when(code) {
            "unsupported_tool_schema" -> "Sarvam rejected the conversation tool schema."
            "context_limit" -> "Sarvam rejected the conversation context length."
            "invalid_request" -> "Sarvam rejected a conversation request parameter."
            else -> "Sarvam returned HTTP 400 without a recognized safe error category."
        }
        return Diagnosis(code,description,schemaIssue)
    }
}
