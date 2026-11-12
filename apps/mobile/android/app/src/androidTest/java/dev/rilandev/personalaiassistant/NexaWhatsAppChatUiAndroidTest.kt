package dev.rilandev.personalaiassistant

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Accessibility trees and clicks on a PRIVATE debug-only Activity.
 * No WhatsApp account, real contact, communications permission or sending. */
@RunWith(AndroidJUnit4::class)
class NexaWhatsAppChatUiAndroidTest {
    @Test fun partiallyClippedTargetNeedsAtLeastEightyPercentVisible() {
        val window=android.graphics.Rect(0,0,1080,2400)
        assertEquals(android.graphics.Rect(0,328,910,445),
            NexaWhatsAppChatActions.visibleTargetBounds(
                android.graphics.Rect(-125,328,910,445),window))
        assertNull(NexaWhatsAppChatActions.visibleTargetBounds(
            android.graphics.Rect(-500,328,100,445),window))
        assertNull(NexaWhatsAppChatActions.visibleTargetBounds(
            android.graphics.Rect(1200,328,1400,445),window))
    }

    private fun root(runId:String):AccessibilityNodeInfo =
        NexaQaAccessibilityRoot.ownWindow("QA run: $runId")

    private fun fixtureTree(root:AccessibilityNodeInfo)=NexaQaAccessibilityRoot.nodes(root)
        .joinToString(" | ") { node ->
            "${node.className}:${node.text}:${node.contentDescription}:${node.isClickable}:${android.graphics.Rect().also{node.getBoundsInScreen(it)}}"
        }

    @Test fun duplicatedLabelNodesResolveToOneRealChatRowAndVerifyHeader() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaChatListQaActivity::class.java,false)
        try {
            val initial=root(runId)
            val scan=NexaWhatsAppChatActions.inspect(initial,"Nova Forever")
            assertEquals("One clickable chat-row target",1,scan.candidates)
            assertEquals(100,scan.bestEvidence)
            assertFalse(scan.alreadyOpen)
            assertNotNull(scan.target)
            assertTrue(scan.target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            // ACTION_CLICK returns before Android has published the destination
            // Accessibility window. Await the fixture's changed navigation state,
            // not the marker shared by both the chat list and conversation.
            val openedStatus=
                "QA run: $runId · Duplicate contacts: false · Chat row clicks: 1 · Send clicks: 0 · Opened: true"
            NexaQaAccessibilityRoot.waitForText(runId,openedStatus).let { current ->
                assertTrue("New chat header + message composer must both be present: "+
                    fixtureTree(current),
                    NexaWhatsAppChatActions.inspect(current,"Nova Forever").alreadyOpen)
                val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
                queue.add(current)
                val composers=mutableListOf<AccessibilityNodeInfo>()
                while(queue.isNotEmpty() && composers.size<3) {
                    val node=queue.removeFirst()
                    if(node.isEditable && node.isVisibleToUser && !node.isPassword) {
                        val desc=listOf(node.hintText?.toString().orEmpty(),
                            node.contentDescription?.toString().orEmpty()).joinToString(" ")
                        if(NexaMessageInputPolicy.isMessageField(desc,true,
                            node.isPassword,node.isEnabled,node.isVisibleToUser,
                            node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_SET_TEXT},
                            !NexaEditableNode.isEmpty(node)))
                            composers.add(node)
                    }
                    for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
                }
                assertEquals("The destination must have exactly one empty composer",1,composers.size)
                val payload=Bundle().apply{
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "Synthetic test: I will call later")
                }
                assertTrue("Android must accept a single composer text insertion",
                    composers.single().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,payload))
            }
            val after=NexaQaAccessibilityRoot.waitForText(runId,
                "QA run: $runId · Duplicate contacts: false · Chat row clicks: 1 · Send clicks: 0 · Opened: true")
            assertTrue(NexaQaAccessibilityRoot.nodes(after).any{
                it.text?.toString()=="Synthetic test: I will call later"
            })
            assertTrue(NexaQaAccessibilityRoot.nodes(after).any{
                it.text?.toString()?.contains("Chat row clicks: 1 · Send clicks: 0 · Opened: true")==true
            })
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }
    @Test fun uncertainSpeechVariantCanBeFoundButNotWrongAlias() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaChatListQaActivity::class.java,false)
        try {
            val current=root(runId)
            val close=NexaWhatsAppChatActions.inspect(current,"Nva Forever")
            assertEquals(fixtureTree(current),1,close.candidates)
            assertEquals(85,close.bestEvidence)
            assertEquals(0,NexaWhatsAppChatActions.inspect(current,"Map Forward").candidates)
            assertTrue(NexaQaAccessibilityRoot.nodes(current).any{
                it.text?.toString()?.contains("Duplicate contacts: false · Chat row clicks: 0 · Send clicks: 0 · Opened: false")==true
            })
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }
    @Test fun twoIdenticalChatRowsRemainAmbiguousAndAreNeverTapped() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaChatListQaActivity::class.java,true)
        try {
            val current=root(runId)
            val scan=NexaWhatsAppChatActions.inspect(current,"Nova Forever")
            assertEquals(fixtureTree(current),2,scan.candidates)
            assertNull("Two different rows cannot become an approved target",scan.target)
            assertTrue(NexaQaAccessibilityRoot.nodes(current).any{
                it.text?.toString()?.contains("Duplicate contacts: true · Chat row clicks: 0 · Send clicks: 0 · Opened: false")==true
            })
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }
}
