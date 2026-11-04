package dev.rilandev.personalaiassistant

import android.os.SystemClock
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
    private fun root():AccessibilityNodeInfo {
        val instrument=InstrumentationRegistry.getInstrumentation()
        repeat(16) {
            instrument.waitForIdleSync()
            instrument.uiAutomation.rootInActiveWindow?.let{return it}
            SystemClock.sleep(120)
        }
        throw AssertionError("Synthetic chat UI Accessibility root unavailable")
    }
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
            }
            scenario.onActivity {
                assertEquals(1,NexaChatListQaActivity.chatRowClicks)
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
