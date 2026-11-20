package dev.rilandev.personalaiassistant

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
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

    /** Resolve each reviewed action against a fresh live window, as production
     * does before both owner review and final dispatch. Accessibility nodes are
     * snapshots and must not be reused as if they were live controls.
     */
    private fun resolvedBounds(runId:String,icon:ObservedIcon):IconBox? {
        val root=foregroundRoot(runId)
        try {
            val target=NexaScreenActions.uniqueCurrentTarget(root,icon) ?: return null
            return try {
                val bounds=android.graphics.Rect().also{target.getBoundsInScreen(it)}
                if(bounds.isEmpty)null else IconBox(bounds.left,bounds.top,bounds.right,bounds.bottom)
            } finally {target.recycle()}
        } finally {root.recycle()}
    }

    private fun fixtureGeometry(runId:String):String {
        val root=foregroundRoot(runId)
        try {
            val window=android.graphics.Rect().also{root.getBoundsInScreen(it)}
            val icons=ScreenIconDetector.detect(root).joinToString {
                "${it.semantic}/${it.label}/${it.className}/${it.resourceId}/${it.bounds}"
            }
            val controls=nodes(root).filter{it.isClickable}.mapNotNull{node->
                val label=ScreenIconClassifier.classify(node.contentDescription)
                    ?:ScreenIconClassifier.classify(node.text)
                if(label==null)return@mapNotNull null
                val bounds=android.graphics.Rect().also{node.getBoundsInScreen(it)}
                "${label.second}/${node.className}/${node.viewIdResourceName}/$bounds"
            }.joinToString()
            return "window=$window; icons=[$icons]; controls=[$controls]"
        } finally {root.recycle()}
    }

    private fun settledSearchIcon(runId:String):ObservedIcon {
        var previous:ObservedIcon? = null
        var previousWindowId:Int? = null
        var previousViewport:IconBox? = null
        repeat(24) {
            val root=foregroundRoot(runId)
            val viewport=android.graphics.Rect().also{root.getBoundsInScreen(it)}
            val windowId=root.windowId
            val current=try {
                ScreenIconDetector.detect(root).singleOrNull {
                    it.semantic=="search" && it.clickable
                }
            } finally {root.recycle()}
            val last=previous
            val liveBounds=current?.let{resolvedBounds(runId,it)}
            val liveViewport=IconBox(viewport.left,viewport.top,viewport.right,viewport.bottom)
            if (current!=null && last!=null &&
                liveBounds==current.bounds &&
                current.bounds==last.bounds && current.label==last.label &&
                current.semantic==last.semantic && current.className==last.className &&
                current.resourceId==last.resourceId &&
                previousWindowId==windowId && previousViewport==liveViewport) return current
            previous=current
            previousWindowId=windowId
            previousViewport=liveViewport
            SystemClock.sleep(125L)
        }
        throw AssertionError("Synthetic Search icon layout did not settle across fresh windows")
    }

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

    @Test fun assistantSnapshotReturnsFreshTargetsAndIconsWithoutEditableText() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val root=foregroundRoot(runId)
            val encoded=try {
                NexaPowerShellScreenSnapshot.captureAssistantFixtureForTest(root)
            } finally {root.recycle()}
            assertNotNull("A safe bounded snapshot is available",encoded)
            val snapshot=JSONObject(encoded!!)
            assertTrue(snapshot.getString("snapshotId").isNotBlank())
            assertTrue(snapshot.getString("packageId").startsWith(
                "dev.rilandev.personalaiassistant"))
            assertFalse("The assistant snapshot never returns raw Accessibility text",
                snapshot.has("text"))
            assertFalse(snapshot.getBoolean("truncated"))

            val targets=snapshot.getJSONArray("targets")
            val searchTargets=(0 until targets.length()).map{targets.getJSONObject(it)}
                .filter{it.getString("label")=="Search"}
            assertEquals("The actionable Search icon is uniquely indexed; safe snapshot=$snapshot; ${fixtureGeometry(runId)}",1,searchTargets.size)
            assertEquals("icon",searchTargets.single().getString("kind"))
            assertEquals("ImageButton",searchTargets.single().getString("role"))
            assertTrue(searchTargets.single().getJSONObject("bounds").getInt("right")<=1000)
            val newChatTargets=(0 until targets.length()).map{targets.getJSONObject(it)}
                .filter{it.getString("label")=="New chat"}
            assertEquals("A safely labeled unknown icon is available by its exact label",1,
                newChatTargets.size)
            assertEquals("icon",newChatTargets.single().getString("kind"))
            assertEquals("ImageButton",newChatTargets.single().getString("role"))
            val digitTargets=(0 until targets.length()).map{targets.getJSONObject(it)}
                .filter{it.getString("label")=="1"}
            assertEquals("A unique one-digit button is an actionable visible target",1,
                digitTargets.size)
            assertEquals("control",digitTargets.single().getString("kind"))
            assertEquals("Button",digitTargets.single().getString("role"))
            assertFalse("Long numeric values stay excluded from generic tap targets",
                (0 until targets.length()).map{targets.getJSONObject(it).getString("label")}
                    .any{it=="123456"})
            assertFalse("Sensitive or submit controls are never tap targets",
                (0 until targets.length()).map{targets.getJSONObject(it).getString("label")}
                    .any{it.equals("Send",ignoreCase=true)||it.contains("Password",ignoreCase=true)})

            val icons=snapshot.getJSONArray("icons")
            assertTrue("Known and unknown visible icon metadata are surfaced",icons.length()>=2)
            val searchIcon=(0 until icons.length()).map{icons.getJSONObject(it)}
                .single{it.getString("semantic")=="search"}
            assertEquals("Search icon target linkage; ${fixtureGeometry(runId)}",
                searchTargets.single().getInt("index"),searchIcon.optInt("targetIndex",-1))
            val newChatIcon=(0 until icons.length()).map{icons.getJSONObject(it)}
                .single{it.getString("semantic")=="unknown"&&
                    it.optInt("targetIndex",-1)==newChatTargets.single().getInt("index")}
            assertEquals("The icon remains visually unknown but links to its safe exact target",
                "Unknown icon",newChatIcon.getString("label"))
            assertTrue("An unlabeled icon is visible but cannot become a generic tap target",
                (0 until icons.length()).map{icons.getJSONObject(it)}.any{
                    it.getString("semantic")=="unknown"&&it.isNull("targetIndex")})
            assertFalse("Editable search values never appear in the model snapshot",
                encoded.contains("Search text"))
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }

    @Test fun safeContentDescriptionSurvivesDecorativeVisibleText() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val root=foregroundRoot(runId)
            val encoded=try {
                NexaPowerShellScreenSnapshot.captureAssistantFixtureForTest(root)
            } finally {root.recycle()}
            assertNotNull("The fixture provides a bounded safe snapshot",encoded)
            val snapshot=JSONObject(encoded!!)
            assertFalse("The fixture observation is complete",snapshot.getBoolean("truncated"))
            val matches=(0 until snapshot.getJSONArray("targets").length())
                .map{snapshot.getJSONArray("targets").getJSONObject(it)}
                .filter{it.getString("label")=="Nexa AI"}
            assertEquals("The clean accessibility label remains an exact target even when visible text has decoration; snapshot=$snapshot; ${fixtureGeometry(runId)}",
                1,matches.size)
            assertEquals("TextView",matches.single().getString("role"))
            assertFalse("An unsafe visible action cannot be relabeled through a harmless description",
                (0 until snapshot.getJSONArray("targets").length())
                    .map{snapshot.getJSONArray("targets").getJSONObject(it).getString("label")}
                    .contains("Archive"))
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }

    @Test fun notificationShadeModeReturnsOnlySafeIconMetadataAndNoTextOrTapTargets() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val root=foregroundRoot(runId)
            val encoded=try {
                NexaPowerShellScreenSnapshot.captureAssistantSystemUiFixtureForTest(root)
            } finally {root.recycle()}
            assertNotNull("A bounded synthetic shade observation is available",encoded)
            val snapshot=JSONObject(encoded!!)
            assertFalse("System UI mode never returns raw text",snapshot.has("text"))
            assertFalse("Notification-style mode exposes no generic tap targets",
                snapshot.getJSONArray("targets").length()>0)
            assertFalse("Sensitive and message-like controls are excluded",
                encoded.contains("Send",ignoreCase=true) || encoded.contains("Password",ignoreCase=true))
            assertFalse("Editable values are excluded",encoded.contains("Search text"))
            val icons=snapshot.getJSONArray("icons")
            assertTrue("Visible icon geometry remains available",icons.length()>0)
            for(index in 0 until icons.length()) {
                val icon=icons.getJSONObject(index)
                assertTrue("Icon coordinates remain observable",icon.has("bounds"))
                assertTrue("No System UI icon is actionable from this read-only snapshot",
                    icon.isNull("targetIndex"))
                assertTrue("Risky operations are filtered from shade metadata",
                    NexaKeyActionSurfacePolicy.allowsNotificationShadeIcon(
                        icon.getString("semantic")))
            }
        } finally {
            NexaQaAccessibilityRoot.closeFixture()
        }
    }

    @Test fun assistantObservationRedactsValuesAfterAnEditableFieldChanges() {
        val runId=NexaQaAccessibilityRoot.launchFixture(NexaInputQaActivity::class.java)
        try {
            val before=NexaQaAccessibilityRoot.ownWindow("QA run: $runId")
            val beforeNodes=nodes(before)
            try {
                val search=beforeNodes.firstOrNull {
                    it.isEditable && it.hintText?.toString()=="Search"
                }
                assertNotNull("The fixture exposes a labeled editable Search node",search)
                val args=android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "QA_TREE_SECRET_VALUE")
                }
                assertTrue(search!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))
            } finally {
                beforeNodes.filter{it!==before}.forEach{try{it.recycle()}catch(_:Exception){}}
                before.recycle()
            }

            val root=NexaQaAccessibilityRoot.ownWindow("QA run: $runId")
            val encoded=try {
                NexaPowerShellScreenSnapshot.captureAssistantFixtureForTest(root)
            } finally {root.recycle()}
            assertNotNull("The safe assistant snapshot remains available",encoded)
            val snapshot=JSONObject(encoded!!)
            assertTrue("The bounded fixture is complete",!snapshot.getBoolean("truncated"))
            assertFalse("Assistant observations never contain raw text nodes",snapshot.has("text"))
            assertFalse("Entered text never appears in the tree payload",
                encoded.contains("QA_TREE_SECRET_VALUE"))
            assertTrue("Safe clickable targets remain visible after text entry; safe snapshot=$snapshot; ${fixtureGeometry(runId)}",
                snapshot.getJSONArray("targets").length()>0)
            assertTrue("Icon metadata remains available after text entry",
                snapshot.getJSONArray("icons").length()>0)
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
            // Fixture text can become accessible just before Android completes
            // the first layout pass. Ground the approved bounds only after two
            // consecutive fresh windows report the same native icon identity.
            val observed=settledSearchIcon(runId)
            assertEquals("The uniquely identified search icon resolves on a fresh live window; observed=$observed; ${fixtureGeometry(runId)}",
                observed.bounds,resolvedBounds(runId,observed))
            assertEquals("The same unchanged source remains reviewable from a second fresh snapshot",
                observed.bounds,resolvedBounds(runId,observed))
            assertNull("A differently located icon is NOT the approved target",
                resolvedBounds(runId,observed.copy(
                    bounds=IconBox(observed.bounds.left+30,observed.bounds.top+30,
                        observed.bounds.right+30,observed.bounds.bottom+30))))
            assertNull("A changed native widget class is NOT the approved target",
                resolvedBounds(runId,observed.copy(
                    className="android.widget.TextView")))
            assertNull("A changed native view identity is NOT the approved target",
                resolvedBounds(runId,observed.copy(
                    resourceId="dev.rilandev.personalaiassistant:id/not-the-search-icon")))
            val freshRoot=foregroundRoot(runId)
            val send=try {ScreenIconDetector.detect(freshRoot)
                .single { icon -> icon.semantic=="send" }
            } finally {freshRoot.recycle()}
            assertFalse("Send remains excluded from all safe navigation actions",
                NexaScreenActionPolicy.permits(send))
        } finally {NexaQaAccessibilityRoot.closeFixture()}
    }

}
