package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No third-party service, account, contact or app is touched. */
@RunWith(AndroidJUnit4::class)
class NexaMessageInputPolicyAndroidTest {
    @Test fun recognizesOnlyVisibleSinglePurposeEmptyMessageBoxes() {
        assertTrue(NexaMessageInputPolicy.isMessageField(
            "Type a message",true,false,true,true,true,false))
        assertTrue(NexaMessageInputPolicy.isMessageField(
            "com.imo.android.imoim:id/chat_input",true,false,true,true,true,false))
        assertFalse(NexaMessageInputPolicy.isMessageField(
            "Search conversations",true,false,true,true,true,false))
        assertFalse(NexaMessageInputPolicy.isMessageField(
            "Enter recipient",true,false,true,true,true,false))
        assertFalse(NexaMessageInputPolicy.isMessageField(
            "Message",true,true,true,true,true,false))
        assertFalse(NexaMessageInputPolicy.isMessageField(
            "Message",true,false,true,true,true,true))
        assertFalse(NexaMessageInputPolicy.isMessageField(
            "Message",true,false,false,true,true,false))
        assertFalse(NexaMessageInputPolicy.isMessageField(
            "Message",true,false,true,true,false,false))
        assertFalse(NexaMessageInputPolicy.isMessageField(
            "Unknown field",true,false,true,true,true,false))
    }

    @Test fun recipientTitleRequiresExactIdentityNotSubstringOrProviderGuess() {
        assertTrue(NexaMessageInputPolicy.recipientMatches("Mom","Mom"))
        assertFalse(NexaMessageInputPolicy.recipientMatches("Mom","Mom Group"))
        assertFalse(NexaMessageInputPolicy.recipientMatches("Mom","Mom's Friend"))
        assertFalse(NexaMessageInputPolicy.recipientMatches("Mom","Dad"))
        assertFalse(NexaMessageInputPolicy.recipientMatches("Mom",""))
    }

    @Test fun disconnectedServiceCannotPrepareOrPerformTextEntry() {
        val service=ScreenAssistService()
        val initial=ScreenAssistState.connected
        try {
            ScreenAssistState.connected=false
            NexaMessageDraftState.clear()
            assertTrue(NexaMessageInputActions.prepare(service).contains("No active reviewed draft"))
            assertTrue(NexaMessageInputActions.confirm(service).contains("not requested"))
        } finally {
            NexaMessageInputActions.cancel()
            NexaMessageDraftState.clear()
            ScreenAssistState.connected=initial
        }
    }

    @Test fun onlySupportedManualChannelsAndNonemptyValidTextCanStage() {
        assertFalse(NexaMessageDraftState.stage("whatsapp","com.whatsapp","Mom","Hello"))
        assertFalse(NexaMessageDraftState.stage("imo","com.imo.android.imoim","Mom",""))
        assertFalse(NexaMessageDraftState.stage("imo","com.imo.android.imoim","Mom","x".repeat(501)))
        assertTrue(NexaMessageDraftState.stage("imo","com.imo.android.imoim","Mom","I will call you later"))
        assertNotNull(NexaMessageDraftState.peek())
        assertNotNull(NexaMessageDraftState.take())
        assertNull(NexaMessageDraftState.take())
    }
}
