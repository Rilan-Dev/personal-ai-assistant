package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/** Non-destructive policy acceptance. Runs without inspecting or clicking other apps. */
@RunWith(AndroidJUnit4::class)
class NexaScreenInteractionPolicyAndroidTest {
    private fun icon(action:String,clickable:Boolean=true)=ObservedIcon(
        "node-1",action,action,IconBox(10,30,80,100),clickable
    )

    @Test fun onlyNonDestructiveNavigationActionsCanBeSelected() {
        listOf("search","menu","more_options","back","settings","close").forEach {
            assertTrue(it,NexaScreenActionPolicy.permits(icon(it)))
        }
        listOf("call","video_call","send","delete","share","attach","microphone",
            "camera","unknown").forEach {
            assertFalse(it,NexaScreenActionPolicy.permits(icon(it)))
        }
        assertFalse(NexaScreenActionPolicy.permits(icon("search",false)))
    }

    @Test fun malformedHitboxesAndUnlabeledButtonsCannotTriggerClick() {
        assertFalse(NexaScreenActionPolicy.permits(ObservedIcon(
            "bad","Search","search",IconBox(3,3,3,50),true)))
        assertFalse(NexaScreenActionPolicy.permits(ObservedIcon(
            "bad","Unknown icon","unknown",IconBox(0,0,50,50),true)))
    }

    @Test fun spokenIconDescriptionIsProposalNotPermission() {
        assertTrue(NexaPhoneVoiceTools.wantsScreenOverview("Hey Nexa what icons are on my screen?"))
        assertTrue(NexaPhoneVoiceTools.wantsScreenOverview("identify screen icons"))
        assertFalse(NexaPhoneVoiceTools.wantsScreenOverview("tap send on my screen"))
        assertFalse(NexaPhoneVoiceTools.wantsScreenOverview("delete everything"))
    }

    @Test fun voiceIconToolRequiresExplicitApprovalAndEmptyArguments() {
        val tool=NexaLiveToolPolicy.definitions()
        assertTrue((0 until tool.length()).any {tool.getJSONObject(it).optString("name")=="request_visible_icons"})
        val ctx=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val request=NexaLiveToolPolicy.decide(ctx,"request_visible_icons","{}")
        assertTrue(request is NexaLiveToolPolicy.Decision.NeedsApproval)
        val rejected=NexaLiveToolPolicy.decide(ctx,"request_visible_icons",JSONObject().put("tap",true).toString())
        assertTrue(rejected is NexaLiveToolPolicy.Decision.Immediate)
    }
}
