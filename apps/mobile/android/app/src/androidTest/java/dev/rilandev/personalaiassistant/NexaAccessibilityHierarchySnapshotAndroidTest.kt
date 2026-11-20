package dev.rilandev.personalaiassistant

import android.os.Bundle
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the same AccessibilityNodeInfo tree serializer used by the QA tool. */
@RunWith(AndroidJUnit4::class)
class NexaAccessibilityHierarchySnapshotAndroidTest {
    @Test fun hierarchyIncludesParentsChildrenWidgetFlagsAndRedactsEditableValues() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val rootBefore=NexaQaAccessibilityRoot.ownWindow("QA run: $runId")
            val beforeNodes=NexaQaAccessibilityRoot.nodes(rootBefore)
            try {
                val search=beforeNodes.firstOrNull {
                    it.isEditable && it.hintText?.toString()=="Search"
                }
                assertNotNull("Search is available as an editable accessibility node",search)
                val args=Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "QA_TREE_PRIVATE_FIELD_VALUE")
                }
                assertTrue(search!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))
            } finally {
                beforeNodes.filter{it!==rootBefore}.forEach{try{it.recycle()}catch(_:Exception){}}
                rootBefore.recycle()
            }

            val root=NexaQaAccessibilityRoot.ownWindow("QA run: $runId")
            val rootBounds=Rect().also { root.getBoundsInScreen(it) }
            val rootDiagnostics="package=${root.packageName}, expected=${BuildConfig.APPLICATION_ID}, " +
                "visible=${root.isVisibleToUser}, windowId=${root.windowId}, bounds=$rootBounds"
            val encoded=try {
                NexaAccessibilityHierarchySnapshot.captureFixtureForTest(root)
            } finally {root.recycle()}
            assertNotNull(
                "A bounded hierarchy snapshot is available " +
                    "($rootDiagnostics)",
                encoded
            )
            val snapshot=JSONObject(encoded!!)
            assertEquals("top-left",snapshot.getJSONObject("coordinateSystem").getString("origin"))
            assertEquals("right",snapshot.getJSONObject("coordinateSystem").getString("xDirection"))
            assertEquals("down",snapshot.getJSONObject("coordinateSystem").getString("yDirection"))
            assertFalse("The fixture is within the node and response limits",
                snapshot.getBoolean("truncated"))

            val entries=snapshot.getJSONArray("nodes")
            val nodes=(0 until entries.length()).map { entries.getJSONObject(it) }
            val byId=nodes.associateBy { it.getString("id") }
            val rootNode=byId.getValue("0")
            assertTrue("Root ID is a snapshot-local path",rootNode.isNull("parentId"))
            assertTrue("Parent and child paths resolve to the same hierarchy",
                nodes.all { parent ->
                    val children=parent.getJSONArray("childIds")
                    (0 until children.length()).all { childIndex ->
                        val child=byId[children.getString(childIndex)]
                        child!=null && child.getString("parentId")==parent.getString("id")
                    }
                })

            val search=nodes.single {
                it.optBoolean("editable") && it.optString("hintText")=="Search"
            }
            assertTrue("Editable node is marked redacted",search.getBoolean("valueRedacted"))
            assertTrue("Entered field content is omitted",search.isNull("text"))
            assertTrue("Field accessibility descriptions are redacted too",
                search.isNull("contentDescription"))
            assertFalse("Password or editable values are not copied from their input nodes",
                search.optString("text").contains("QA_TREE_PRIVATE_FIELD_VALUE"))

            assertTrue("A non-clickable text widget is present",nodes.any {
                it.getString("role")=="TextView" && !it.getBoolean("clickable") &&
                    it.optString("text").contains("Nexa synthetic input")
            })
            assertTrue("Clickable image widgets retain their actual click flag",nodes.any {
                it.getString("role")=="ImageButton" && it.getBoolean("clickable") &&
                    it.getBoolean("imageLike")
            })
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }
}
