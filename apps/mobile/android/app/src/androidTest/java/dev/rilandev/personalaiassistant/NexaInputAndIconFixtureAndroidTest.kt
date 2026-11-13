package dev.rilandev.personalaiassistant

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private fun foregroundRoot(runId:String): AccessibilityNodeInfo =
        NexaQaAccessibilityRoot.ownWindow("QA run: $runId")

    private fun safeEditable(node:AccessibilityNodeInfo,label:String):Boolean {
        val hints=listOf(node.hintText?.toString().orEmpty(),
            node.contentDescription?.toString().orEmpty(),
            node.viewIdResourceName.orEmpty())
        return NexaTextInputPolicy.matches(label,hints,
            node.isEditable,node.isPassword,node.isVisibleToUser,node.isEnabled,
            NexaEditableNode.isEmpty(node),
            node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_SET_TEXT})
    }
    @Test fun identifiesOneRealInputAndFillsHarmlessTextWithoutSending() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val root=foregroundRoot(runId)
            val input=nodes(root).filter{safeEditable(it,"Search")}
            assertEquals("Exactly one named Search field must be grounded",1,input.size)
            assertFalse(safeEditable(input.single(),"Notes"))
            val args=Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "Project update: meeting at 2 PM")
            }
            assertTrue("Android must accept the test field's ACTION_SET_TEXT",
                input.single().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))
            val after=nodes(NexaQaAccessibilityRoot.waitForText(runId,
                "QA run: $runId · Search clicks: 0 · Send clicks: 0 · Unknown clicks: 0 · Search text: Project update: meeting at 2 PM"))
            assertTrue(after.any{it.text?.toString()=="Project update: meeting at 2 PM"})
            assertTrue(after.any{it.text?.toString()?.contains("Send clicks: 0")==true})
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }
    @Test fun recognizesSearchIconButRefusesUnknownAndSendActions() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val root=foregroundRoot(runId)
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
            val after=nodes(NexaQaAccessibilityRoot.waitForText(runId,
                "QA run: $runId · Search clicks: 1 · Send clicks: 0 · Unknown clicks: 0 · Search text: "))
            assertTrue(after.any{it.text?.toString()?.contains("Search clicks: 1")==true})
            assertTrue(after.any{it.text?.toString()?.contains("Send clicks: 0")==true})
            assertTrue(after.any{it.text?.toString()?.contains("Unknown clicks: 0")==true})
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }
    @Test fun realPasswordNodeNeverQualifiesForAnyTextInsertion() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val root=foregroundRoot(runId)
            val pwd=nodes(root).filter{it.isPassword}
            assertEquals(1,pwd.size)
            assertFalse(safeEditable(pwd.single(),"Search"))
            assertFalse(safeEditable(pwd.single(),"Password"))
            assertTrue(nodes(root).any{it.text?.toString()?.contains("Send clicks: 0")==true})
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }
    @Test fun safeIconResolverRejectsStaleClassResourceOrBounds() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val root=foregroundRoot(runId)
            val observed=ScreenIconDetector.detect(root)
                .single { it.semantic=="search" && it.clickable }
            val selected=NexaScreenActions.uniqueCurrentTarget(root,observed)
            assertNotNull("The uniquely identified search icon must be observable",selected)
            assertEquals("Same unchanged source can be reviewed twice",
                selected,NexaScreenActions.uniqueCurrentTarget(root,observed))
            assertNull("A differently located icon is NOT the approved target",
                NexaScreenActions.uniqueCurrentTarget(root,observed.copy(
                    bounds=IconBox(observed.bounds.left+30,observed.bounds.top+30,
                        observed.bounds.right+30,observed.bounds.bottom+30))))
            assertNull("A changed native widget class is NOT the approved target",
                NexaScreenActions.uniqueCurrentTarget(root,observed.copy(
                    className="android.widget.TextView")))
            assertNull("A changed native view identity is NOT the approved target",
                NexaScreenActions.uniqueCurrentTarget(root,observed.copy(
                    resourceId="dev.rilandev.personalaiassistant:id/not-the-search-icon")))
            val send=observed.let {
                ScreenIconDetector.detect(root).single { icon -> icon.semantic=="send" }
            }
            assertFalse("Send remains excluded from all safe navigation actions",
                NexaScreenActionPolicy.permits(send))
        } finally {NexaQaAccessibilityRoot.closeFixture()}
    }

}
