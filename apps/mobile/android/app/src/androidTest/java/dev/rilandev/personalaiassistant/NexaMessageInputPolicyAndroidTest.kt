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

    @Test fun matchingNameInChatHistoryIsNotTheConversationHeader() {
        val window=android.graphics.Rect(0,80,1000,2080)
        assertTrue(NexaMessageInputPolicy.isHeaderLabel(
            android.graphics.Rect(60,160,310,235),window))
        assertFalse(NexaMessageInputPolicy.isHeaderLabel(
            android.graphics.Rect(60,1200,310,1280),window))
        assertFalse(NexaMessageInputPolicy.isHeaderLabel(
            android.graphics.Rect(60,0,310,75),window))
        assertFalse(NexaMessageInputPolicy.isHeaderLabel(
            android.graphics.Rect(0,160,1000,900),window))
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
        try {
            assertEquals("com.whatsapp",NexaMessageChannels.expectedManualPackage("whatsapp"))
            assertEquals("com.whatsapp.w4b",NexaMessageChannels.expectedManualPackage("whatsapp-business"))
            assertEquals("com.imo.android.imoim",NexaMessageChannels.expectedManualPackage("imo"))
            assertEquals("im.thebot.messenger",NexaMessageChannels.expectedManualPackage("botim"))
            assertNull(NexaMessageChannels.expectedManualPackage("sms"))
            assertNull(NexaMessageChannels.expectedManualPackage("unknown"))
            // WhatsApp manual drafts are now supported, but are never sent.
            assertTrue(NexaMessageDraftState.stage("whatsapp","com.whatsapp","Mom","Hello"))
            assertNotNull(NexaMessageDraftState.take())
            assertNull(NexaMessageDraftState.take())
            // A channel must not be used to stage an arbitrary other package.
            assertFalse(NexaMessageDraftState.stage("whatsapp","com.imo.android.imoim","Mom","Hello"))
            assertFalse(NexaMessageDraftState.stage("imo","com.whatsapp","Mom","Hello"))
            assertFalse(NexaMessageDraftState.stage("unknown","com.whatsapp","Mom","Hello"))
            assertFalse(NexaMessageDraftState.stage("imo","com.imo.android.imoim","Mom",""))
            assertFalse(NexaMessageDraftState.stage("imo","com.imo.android.imoim","Mom","x".repeat(501)))
            assertTrue(NexaMessageDraftState.stage("imo","com.imo.android.imoim","Mom","I will call you later"))
            assertNotNull(NexaMessageDraftState.peek())
            assertNotNull(NexaMessageDraftState.take())
            assertNull(NexaMessageDraftState.take())
        } finally {
            NexaMessageDraftState.clear()
        }
    }

    @Test fun agentSendAllowlistMatchesExactWhatsAppImoAndBotimPackages() {
        assertEquals("com.whatsapp",NexaMessageChannels.agentSendPackage("whatsapp"))
        assertEquals("com.imo.android.imoim",NexaMessageChannels.agentSendPackage("imo"))
        assertEquals("im.thebot.messenger",NexaMessageChannels.agentSendPackage("botim"))
        assertNull(NexaMessageChannels.agentSendPackage("whatsapp-business"))
        assertNull(NexaMessageChannels.agentSendPackage("sms"))
        assertNull(NexaMessageChannels.agentSendPackage("unknown"))
        assertFalse(NexaMessageChannels.isAgentSendTarget("imo","com.whatsapp"))
        assertFalse(NexaMessageChannels.isAgentSendTarget("botim","com.imo.android.imoim"))
        assertTrue(NexaMessageChannels.isSafeAgentRecipient("Amma"))
        assertFalse(NexaMessageChannels.isSafeAgentRecipient("send"))
        assertFalse(NexaMessageChannels.isSafeAgentRecipient("Amma then send"))
        assertFalse(NexaMessageChannels.isSafeAgentRecipient(""))
    }

    @Test fun approvalBindingRejectsAReplacedRecipientChannelOrMessage() {
        val exact=NexaMessageInputPolicy.approvedDraftDigest(
            "imo","com.imo.android.imoim","Mom","I am in a meeting")
        assertEquals(exact,NexaMessageInputPolicy.approvedDraftDigest(
            "imo","com.imo.android.imoim","Mom","I am in a meeting"))
        assertNotEquals(exact,NexaMessageInputPolicy.approvedDraftDigest(
            "imo","com.imo.android.imoim","Mom","Please transfer money"))
        assertNotEquals(exact,NexaMessageInputPolicy.approvedDraftDigest(
            "imo","com.imo.android.imoim","Dad","I am in a meeting"))
        assertNotEquals(exact,NexaMessageInputPolicy.approvedDraftDigest(
            "botim","com.imo.android.imoim","Mom","I am in a meeting"))
        assertNotEquals(exact,NexaMessageInputPolicy.approvedDraftDigest(
            "imo","com.other.messaging","Mom","I am in a meeting"))
        assertTrue(exact.matches(Regex("[0-9a-f]{64}")))
        assertFalse("The digest must never expose the raw content",
            exact.contains("meeting",true))
        assertNotEquals(
            NexaMessageInputPolicy.approvedDraftDigest("a","bc","d","e"),
            NexaMessageInputPolicy.approvedDraftDigest("ab","c","d","e"))
    }

    @Test fun sendRequiresOneAccessibleSendButtonBesideTheExactComposer() {
        assertTrue(NexaWhatsAppSendPolicy.isSendButton(
            "Send","android.widget.ImageButton",true,true,true,true))
        assertTrue(NexaWhatsAppSendPolicy.isSendButton(
            "Send message","android.widget.Button",true,true,true,true))
        assertFalse(NexaWhatsAppSendPolicy.isSendButton(
            "Send to group","android.widget.Button",true,true,true,true))
        assertFalse(NexaWhatsAppSendPolicy.isSendButton(
            "Send","android.widget.TextView",true,true,true,true))
        assertFalse(NexaWhatsAppSendPolicy.isSendButton(
            "Send","android.widget.Button",false,true,true,true))
        assertFalse(NexaWhatsAppSendPolicy.isSendButton(
            "Send","android.widget.Button",true,false,true,true))
        assertFalse(NexaWhatsAppSendPolicy.isSendButton(
            "Send","android.widget.Button",true,true,false,true))
        assertFalse(NexaWhatsAppSendPolicy.isSendButton(
            "Send","android.widget.Button",true,true,true,false))

        val window=android.graphics.Rect(0,0,1080,2400)
        val composer=android.graphics.Rect(70,2100,900,2220)
        val send=android.graphics.Rect(920,2110,1040,2210)
        val otherRow=android.graphics.Rect(920,1850,1040,1950)
        val leftSide=android.graphics.Rect(10,2110,60,2210)
        assertTrue(NexaWhatsAppSendPolicy.isBesideComposer(window,composer,send))
        assertFalse(NexaWhatsAppSendPolicy.isBesideComposer(window,composer,otherRow))
        assertFalse(NexaWhatsAppSendPolicy.isBesideComposer(window,composer,leftSide))
        assertFalse(NexaWhatsAppSendPolicy.isBesideComposer(
            window,composer,android.graphics.Rect(1100,2110,1180,2210)))
    }

    @Test fun sentEvidenceRequiresExactWhatsAppChatTextAndClearedComposer() {
        assertTrue(NexaWhatsAppSendPolicy.hasVerifiedSentEvidence(
            "com.whatsapp",true,true,true,true))
        assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentEvidence(
            "com.whatsapp.w4b",true,true,true,true))
        assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentEvidence(
            "com.whatsapp",false,true,true,true))
        assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentEvidence(
            "com.whatsapp",true,false,true,true))
        assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentEvidence(
            "com.whatsapp",true,true,false,true))
        assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentEvidence(
            "com.whatsapp",true,true,true,false))
    }

    @Test fun sentEvidenceIsBoundToTheExactAllowlistedMessengerPackage() {
        for ((app,pkg) in listOf(
            "whatsapp" to "com.whatsapp",
            "imo" to "com.imo.android.imoim",
            "botim" to "im.thebot.messenger"
        )) {
            assertTrue(NexaWhatsAppSendPolicy.hasVerifiedSentMessageEvidence(
                app,pkg,true,true,true,true))
            assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentMessageEvidence(
                app,pkg,false,true,true,true))
            assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentMessageEvidence(
                app,pkg,true,false,true,true))
            assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentMessageEvidence(
                app,pkg,true,true,false,true))
            assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentMessageEvidence(
                app,pkg,true,true,true,false))
        }
        assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentMessageEvidence(
            "imo","com.whatsapp",true,true,true,true))
        assertFalse(NexaWhatsAppSendPolicy.hasVerifiedSentMessageEvidence(
            "whatsapp-business","com.whatsapp.w4b",true,true,true,true))
    }

}
