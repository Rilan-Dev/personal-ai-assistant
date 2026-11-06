package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Synthetic native mandate checks. No Accessibility action, real app,
 * recipient, text field or Android permission is exercised.
 */
@RunWith(AndroidJUnit4::class)
class NexaOwnerSequentialUiMandateAndroidTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun fingerprint(session:String,call:String,tool:String,args:JSONObject)=
        JSONArray().put(session).put(call).put(tool).put(args).toString()

    @Test fun onlyExactOneTimeConsumedStepMayEnterNativeScreenController(){
        val previouslyPaused=NexaNativeActionSafety.isPaused(context)
        try{
            assertTrue(NexaNativeActionSafety.setPaused(context,false))
            val session="native-sequence-"+UUID.randomUUID()
            val call="owner-seq-1"
            val args=JSONObject().put("label","Search")
            val fp=fingerprint(session,call,"screen.tap_label",args)
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.tap_label",args))
            assertTrue(NexaAgentActionAudit.claim(context,session,call,fp,true))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.tap_label",JSONObject().put("label","Send")))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.scroll",args))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,"owner-seq-2",fp,"screen.tap_label",args))
            assertTrue(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.tap_label",args))
            assertFalse("Duplicate native dispatch must be blocked before ACTION_CLICK",
                NexaAgentActionAudit.reserveReviewedUiStep(
                    context,session,call,fp,"screen.tap_label",args))
            assertTrue(NexaAgentActionAudit.recordSettlement(
                context,session,call,fp,false))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.tap_label",args))
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(context,previouslyPaused))
        }
    }

    @Test fun parentWillNotAdmitNextStepUntilNativeGestureObservationIsCommitted(){
        val previous=NexaNativeActionSafety.isPaused(context)
        try{
            assertTrue(NexaNativeActionSafety.setPaused(context,false))
            val session="native-verify-"+UUID.randomUUID()
            val label=JSONObject().put("label","Search")
            val call="owner-seq-1"
            val fp=fingerprint(session,call,"screen.tap_label",label)
            assertTrue(NexaAgentActionAudit.claim(context,session,call,fp,true))
            assertTrue(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.tap_label",label))
            assertFalse(NexaAgentActionAudit.recordReviewedUiEvidence(
                context,session,call,fp,"screen.tap_label","observed_change"))
            assertTrue(NexaAgentActionAudit.recordSettlement(
                context,session,call,fp,false))
            val second="owner-seq-2"
            val scroll=JSONObject().put("direction","down")
            val fp2=fingerprint(session,second,"screen.scroll",scroll)
            assertFalse("An accepted tap does not grant the next action",
                NexaAgentActionAudit.claim(context,session,second,fp2,true))
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(context,previous))
        }
    }

    @Test fun ownerPauseAndResumeRevokesAnAlreadyClaimedChatEffect(){
        val previouslyPaused=NexaNativeActionSafety.isPaused(context)
        try{
            assertTrue(NexaNativeActionSafety.setPaused(context,false))
            val session="native-pause-"+UUID.randomUUID()
            val call="owner-seq-2"
            val args=JSONObject().put("targetLabel","Notes").put("exactText","local review")
            val fp=fingerprint(session,call,"screen.fill_text",args)
            assertTrue(NexaAgentActionAudit.claim(context,session,call,fp,true))
            assertTrue(NexaNativeActionSafety.setPaused(context,true))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.fill_text",args))
            assertTrue(NexaNativeActionSafety.setPaused(context,false))
            assertFalse("Resuming cannot revive stale owner approval",
                NexaAgentActionAudit.reserveReviewedUiStep(
                    context,session,call,fp,"screen.fill_text",args))
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(context,previouslyPaused))
        }
    }


    @Test fun whatsappRecipientAndExactDraftCannotInheritOtherApproval(){
        val wasPaused=NexaNativeActionSafety.isPaused(context)
        try{
            assertTrue(NexaNativeActionSafety.setPaused(context,false))
            val chatSession="native-whatsapp-"+UUID.randomUUID()
            val target=JSONObject().put("recipientHint","Mom")
            val first="owner-seq-2"
            val approveChat=fingerprint(chatSession,first,
                "screen.open_whatsapp_chat",target)
            assertTrue(NexaAgentActionAudit.claim(context,
                chatSession,first,approveChat,true))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,chatSession,first,approveChat,
                "screen.open_whatsapp_chat",
                JSONObject().put("recipientHint","Dad")))
            assertTrue(NexaAgentActionAudit.reserveReviewedUiStep(
                context,chatSession,first,approveChat,
                "screen.open_whatsapp_chat",target))
            assertFalse("No real WhatsApp header was observed",
                NexaAgentActionAudit.recordReviewedUiEvidence(
                    context,chatSession,first,approveChat,
                    "screen.open_whatsapp_chat","verified_chat_header"))
            assertTrue(NexaAgentActionAudit.recordSettlement(
                context,chatSession,first,approveChat,false))
            val next="owner-seq-3"
            val draft=JSONObject().put("recipientHint","Mom")
                .put("exactText","I am in a meeting")
            val approveDraft=fingerprint(chatSession,next,
                "screen.compose_whatsapp",draft)
            assertFalse("An unverified chat cannot authorize a message draft",
                NexaAgentActionAudit.claim(context,
                    chatSession,next,approveDraft,true))

            // Independently test argument binding using a fresh parent, not
            // an actual message draft or an Accessibility input field.
            val separate="native-whatsapp-draft-"+UUID.randomUUID()
            val direct="owner-seq-1"
            val fp=fingerprint(separate,direct,"screen.compose_whatsapp",draft)
            assertTrue(NexaAgentActionAudit.claim(
                context,separate,direct,fp,true))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,separate,direct,fp,"screen.compose_whatsapp",
                JSONObject().put("recipientHint","Mom")
                    .put("exactText","Changed without approval")))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,separate,direct,fp,"screen.compose_whatsapp",
                JSONObject().put("recipientHint","Dad")
                    .put("exactText","I am in a meeting")))
            assertTrue(NexaAgentActionAudit.reserveReviewedUiStep(
                context,separate,direct,fp,"screen.compose_whatsapp",draft))
            assertFalse("Never claim a draft was typed without native UI proof",
                NexaAgentActionAudit.recordReviewedUiEvidence(
                    context,separate,direct,fp,
                    "screen.compose_whatsapp","exact_text_visible"))
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(context,wasPaused))
        }
    }

    @Test fun aReadOnlyOrForgedModelClaimCannotAuthorizeUiEffects(){
        val previouslyPaused=NexaNativeActionSafety.isPaused(context)
        try{
            assertTrue(NexaNativeActionSafety.setPaused(context,false))
            val session="native-read-"+UUID.randomUUID()
            val call="owner-seq-3"
            val args=JSONObject().put("direction","down")
            val fp=fingerprint(session,call,"screen.scroll",args)
            assertTrue(NexaAgentActionAudit.claim(context,session,call,fp,false))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.scroll",args))
            assertFalse(NexaAgentActionAudit.reserveReviewedUiStep(
                context,session,call,fp,"screen.tap_label",args))
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(context,previouslyPaused))
        }
    }
}
