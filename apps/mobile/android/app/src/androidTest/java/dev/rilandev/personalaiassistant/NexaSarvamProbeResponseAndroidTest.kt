package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic JSON only. No user key, provider request, audio or tool dispatch. */
@RunWith(AndroidJUnit4::class)
class NexaSarvamProbeResponseAndroidTest {
    private fun reply(content:Any= "OK",role:String="assistant",
                      finish:String="stop",call:Boolean=false):String {
        val message=JSONObject().put("role",role).put("content",content)
        if(call)message.put("tool_calls",JSONArray().put(JSONObject()
            .put("id","call_x").put("type","function")))
        return JSONObject().put("choices",JSONArray().put(JSONObject()
            .put("finish_reason",finish).put("message",message))).toString()
    }

    @Test fun properTextOnlyCompletionIsAccepted() {
        assertTrue(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(reply()))
        assertTrue(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(
            reply(content="  Confirmed.  ")))
    }

    @Test fun http200WithMissingOrMisleadingChoicesIsNotReady() {
        listOf("{}", "{\"error\":{\"message\":\"unsupported tools\"}}",
            "{\"choices\":[]}", "not json", "", "null").forEach {
            assertFalse(it,NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(it))
        }
    }

    @Test fun toolCallsTruncationAndNonAssistantReplyAreRejected() {
        assertFalse(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(reply(call=true)))
        assertFalse(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(
            reply(finish="length")))
        assertFalse(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(
            reply(role="user")))
        assertFalse(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(
            reply(content=JSONObject.NULL)))
    }

    @Test fun blankAndOversizedResponsesDoNotPass() {
        assertFalse(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(
            reply(content="  ")))
        assertFalse(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(
            reply(content="x".repeat(3_001))))
        assertFalse(NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion(
            "x".repeat(140_001)))
    }
}
