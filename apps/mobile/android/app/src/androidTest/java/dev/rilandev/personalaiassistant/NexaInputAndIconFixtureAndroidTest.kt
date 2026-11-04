package dev.rilandev.personalaiassistant

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real AccessibilityNodes on a debug-only synthetic screen; no real recipient,
 * provider, app permissions or transmission. */
@RunWith(AndroidJUnit4::class)
class NexaInputAndIconFixtureAndroidTest {
    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while(queue.isNotEmpty() && result.size<160) {
            val n=queue.removeFirst()
            result.add(n)
            for(i in 0 until n.childCount) n.getChild(i)?.let{queue.addLast(it)}
        }
        return result
    }
    private fun foregroundRoot(): AccessibilityNodeInfo {
        val instrument=InstrumentationRegistry.getInstrumentation()
        for(attempt in 0..14) {
            instrument.waitForIdleSync()
            val root=instrument.uiAutomation.rootInActiveWindow
            if(root!=null &&
                root.packageName?.toString()=="dev.rilandev.personalaiassistant") return root
            SystemClock.sleep(180L)
        }
        throw AssertionError("Synthetic Android test window was not accessible")
    }
    private fun safeEditable(node:AccessibilityNodeInfo,label:String):Boolean {
        val hints=listOf(node.hintText?.toString().orEmpty(),
            node.contentDescription?.toString().orEmpty(),
            node.viewIdResourceName.orEmpty())
        return NexaTextInputPolicy.matches(label,hints,
            node.isEditable,node.isPassword,node.isVisibleToUser,node.isEnabled,
            node.text.isNullOrEmpty(),
            node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_SET_TEXT})
    }
    @Test fun identifiesOneRealInputAndFillsHarmlessTextWithoutSending() {
        ActivityScenario.launch(NexaInputQaActivity::class.java).use { scenario ->
            val root=foregroundRoot()
            val input=nodes(root).filter{safeEditable(it,"Search")}
            assertEquals("Exactly one named Search field must be grounded",1,input.size)
            assertFalse(safeEditable(input.single(),"Notes"))
            val args=Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "Project update: meeting at 2 PM")
            }
            assertTrue("Android must accept the test field's ACTION_SET_TEXT",
                input.single().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))
            scenario.onActivity { activity ->
                assertEquals("Project update: meeting at 2 PM",
                    activity.searchField.text.toString())
                assertEquals("",activity.notesField.text.toString())
                assertEquals("",activity.secretField.text.toString())
            }
            assertEquals("No Send button was tapped",0,NexaInputQaActivity.sendClicks)
        }
    }
    @Test fun recognizesSearchIconButRefusesUnknownAndSendActions() {
        ActivityScenario.launch(NexaInputQaActivity::class.java).use { scenario ->
            val root=foregroundRoot()
            val icons=ScreenIconDetector.detect(root)
            val search=icons.filter{it.semantic=="search"&&it.clickable}
            assertEquals("One labeled Search icon",1,search.size)
            assertTrue(NexaScreenActionPolicy.permits(search.single()))
            assertTrue(icons.any{it.semantic=="unknown" && !NexaScreenActionPolicy.permits(it)})
            assertTrue(icons.any{it.semantic=="send" && !NexaScreenActionPolicy.permits(it)})
            val controls=nodes(root).filter{
                it.isClickable && it.className?.toString()?.endsWith("ImageButton")==true &&
                ScreenIconClassifier.classify(it.contentDescription)?.first=="search"
            }
            assertEquals(1,controls.size)
            assertTrue(controls.single().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            scenario.onActivity {
                assertEquals(1,NexaInputQaActivity.searchClicks)
                assertEquals(0,NexaInputQaActivity.sendClicks)
                assertEquals(0,NexaInputQaActivity.unknownClicks)
            }
        }
    }
    @Test fun realPasswordNodeNeverQualifiesForAnyTextInsertion() {
        ActivityScenario.launch(NexaInputQaActivity::class.java).use { scenario ->
            val root=foregroundRoot()
            val pwd=nodes(root).filter{it.isPassword}
            assertEquals(1,pwd.size)
            assertFalse(safeEditable(pwd.single(),"Search"))
            assertFalse(safeEditable(pwd.single(),"Password"))
            scenario.onActivity {
                assertEquals("",it.secretField.text.toString())
                assertEquals(0,NexaInputQaActivity.sendClicks)
            }
        }
    }
}
