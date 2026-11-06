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
