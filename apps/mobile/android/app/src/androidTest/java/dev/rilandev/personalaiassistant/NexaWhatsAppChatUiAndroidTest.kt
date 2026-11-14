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

    /**
     * Android may report a fixture marker while the window is still moving.
     * Require four consecutive snapshots containing the expected *actual*
     * chat-row candidates, not merely the expected number of clickable nodes.
     * Each returned scan is computed on the same window as its row geometry.
     * Production recipient checks are never relaxed to make a test pass.
     */
    private data class Settled(val root:AccessibilityNodeInfo,
                               val scan:NexaWhatsAppChatActions.Scan)

    private fun settledScan(runId:String,expectedRows:Int,recipient:String,
                            evidence:Int):Settled {
        var consecutive=0
        var previousSignature:String?=null
        repeat(120) {
            val current=root(runId)
            val window=android.graphics.Rect().also{current.getBoundsInScreen(it)}
            val rows=NexaQaAccessibilityRoot.nodes(current).filter { node ->
                node.isClickable &&
                    node.contentDescription?.toString()?.startsWith("Mah Forever")==true
            }
            val geometry=rows.size==expectedRows && !window.isEmpty &&
                rows.all { node ->
                    val rect=android.graphics.Rect().also{node.getBoundsInScreen(it)}
                    node.isVisibleToUser && rect.width()>25 && rect.height()>20 &&
                        window.contains(rect)
                }
            val scan=NexaWhatsAppChatActions.inspect(current,recipient)
            val candidateReady=geometry && scan.candidates==expectedRows &&
                scan.bestEvidence==evidence && !scan.alreadyOpen
            val signature=if(candidateReady) {
                window.toShortString()+"|"+rows.joinToString("|") { node ->
                    android.graphics.Rect().also{node.getBoundsInScreen(it)}.toShortString()
                }
            } else null
            consecutive=if(signature!=null && signature==previousSignature)
                consecutive+1 else 0
            previousSignature=signature
            if(consecutive>=3)return Settled(current,scan)
            android.os.SystemClock.sleep(100L)
        }
        throw AssertionError("Synthetic chat list never stabilized with expected candidate count")
    }

    private fun fixtureTree(root:AccessibilityNodeInfo)=NexaQaAccessibilityRoot.nodes(root)
        .joinToString(" | ") { node ->
            "${node.className}:${node.text}:${node.contentDescription}:${node.isClickable}:${android.graphics.Rect().also{node.getBoundsInScreen(it)}}"
        }

    @Test fun duplicatedLabelNodesResolveToOneRealChatRowAndVerifyHeader() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaChatListQaActivity::class.java,false)
        try {
            val opened=settledScan(runId,1,"Mah Forever",100)
            val scan=opened.scan
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
                    NexaWhatsAppChatActions.inspect(current,"Mah Forever").alreadyOpen)
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
            val matched=settledScan(runId,1,"Nva Forever",85)
            val current=matched.root
            val close=matched.scan
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
            val matched=settledScan(runId,2,"Mah Forever",100)
            val current=matched.root
            val scan=matched.scan
            assertEquals(fixtureTree(current),2,scan.candidates)
            assertNull("Two different rows cannot become an approved target",scan.target)
            assertTrue(NexaQaAccessibilityRoot.nodes(current).any{
                it.text?.toString()?.contains("Duplicate contacts: true · Chat row clicks: 0 · Send clicks: 0 · Opened: false")==true
            })
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }

    @Test fun overlappingDistinctRowsNeverBecomeOneApprovedRecipient() {
        val runId=NexaQaAccessibilityRoot.launchFixture(
            NexaChatListQaActivity::class.java,true,overlap=true)
        try {
            val observed=settledScan(runId,2,"Mah Forever",100)
            val rows=NexaQaAccessibilityRoot.nodes(observed.root).filter {
                it.isClickable &&
                    it.contentDescription?.toString()?.startsWith("Mah Forever")==true
            }
            assertEquals("Two native source identities must be visible",2,rows.size)
            val a=android.graphics.Rect().also{rows[0].getBoundsInScreen(it)}
            val b=android.graphics.Rect().also{rows[1].getBoundsInScreen(it)}
            assertTrue("Test fixture must truly overlap",android.graphics.Rect(a).intersect(b))
            assertEquals("Overlapping sibling contacts remain ambiguous",2,observed.scan.candidates)
            assertNull("No unsafe chat-row target may be chosen",observed.scan.target)
        } finally {NexaQaAccessibilityRoot.closeFixture()}
    }

}
