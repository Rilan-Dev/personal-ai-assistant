package dev.rilandev.personalaiassistant

import android.os.SystemClock
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Accessibility trees and clicks on a PRIVATE debug-only Activity.
 * No WhatsApp account, real contact, communications permission or sending. */
@RunWith(AndroidJUnit4::class)
class NexaWhatsAppChatUiAndroidTest {
    private fun root():AccessibilityNodeInfo =
        NexaQaAccessibilityRoot.ownWindow("WhatsApp - Synthetic QA only")

    @Test fun duplicatedLabelNodesResolveToOneRealChatRowAndVerifyHeader() {
        NexaChatListQaActivity.duplicateContact=false
        ActivityScenario.launch(NexaChatListQaActivity::class.java).use{ scenario ->
            val initial=root()
            val scan=NexaWhatsAppChatActions.inspect(initial,"Nova Forever")
            assertEquals("One clickable chat-row target",1,scan.candidates)
            assertEquals(100,scan.bestEvidence)
            assertFalse(scan.alreadyOpen)
            assertNotNull(scan.target)
            assertTrue(scan.target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            root().let { current ->
                assertTrue("New chat header + message composer must both be present",
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
            scenario.onActivity {
                assertEquals(1,NexaChatListQaActivity.chatRowClicks)
                assertEquals("Synthetic test: I will call later",
                    it.messageInput.text.toString())
                assertEquals(0,NexaChatListQaActivity.sendClicks)
                assertTrue(NexaChatListQaActivity.opened)
            }
        }
    }
    @Test fun uncertainSpeechVariantCanBeFoundButNotWrongAlias() {
        NexaChatListQaActivity.duplicateContact=false
        ActivityScenario.launch(NexaChatListQaActivity::class.java).use{
            val current=root()
            val close=NexaWhatsAppChatActions.inspect(current,"Nva Forever")
            assertEquals(1,close.candidates)
            assertEquals(85,close.bestEvidence)
            assertEquals(0,NexaWhatsAppChatActions.inspect(current,"Map Forward").candidates)
            assertEquals(0,NexaChatListQaActivity.sendClicks)
        }
    }
    @Test fun twoIdenticalChatRowsRemainAmbiguousAndAreNeverTapped() {
        NexaChatListQaActivity.duplicateContact=true
        try {
            ActivityScenario.launch(NexaChatListQaActivity::class.java).use{
                val scan=NexaWhatsAppChatActions.inspect(root(),"Nova Forever")
                assertEquals(2,scan.candidates)
                assertNull("Two different rows cannot become an approved target",scan.target)
                assertEquals(0,NexaChatListQaActivity.chatRowClicks)
                assertEquals(0,NexaChatListQaActivity.sendClicks)
            }
        }finally {NexaChatListQaActivity.duplicateContact=false}
    }
}
