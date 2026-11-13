package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.util.UUID

/** Strict, test-only Accessibility root selection for owner-isolated emulator.
 * ActiveWindow can point at a lingering IME or system permission dialog.
 * Never inspect any non-app window's text or return its nodes to the tests.
 */
internal object NexaQaAccessibilityRoot {
    // All tests in one instrumentation process share a single Accessibility
    // UiAutomation client. Preserve the owner's enabled services explicitly;
    // never silently disable them or alter Android security settings.
    private val testAutomation:UiAutomation by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val instrument=InstrumentationRegistry.getInstrumentation()
        instrument.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            ?: error("QA automation unavailable; no fixture interaction allowed")
    }
    private fun qaAutomation():UiAutomation=testAutomation
    fun nodes(root:AccessibilityNodeInfo):List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while(queue.isNotEmpty()&&result.size<160) {
            val node=queue.removeFirst()
            result.add(node)
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        return result
    }

    private fun matchesFixture(root:AccessibilityNodeInfo,marker:String):Boolean {
        val bounds=android.graphics.Rect().also{root.getBoundsInScreen(it)}
        if(bounds.width()<=0 || bounds.height()<=0 || root.childCount<=0)return false
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited=0
        while(queue.isNotEmpty()&&visited++<160) {
            val node=queue.removeFirst()
            if(node.text?.toString()?.startsWith(marker)==true ||
                node.contentDescription?.toString()?.startsWith(marker)==true)return true
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        return false
    }
    fun ownWindow(marker:String):AccessibilityNodeInfo {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val automation=qaAutomation()
        val fixturePackage=instrument.context.packageName
        try {
            val info=automation.serviceInfo
            info.flags=info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            automation.serviceInfo=info
        }catch(_:Exception){ /* rootInActiveWindow remains the fallback */ }
        var observedPackage=""
        // ActivityScenario.launch waits for RESUMED, which can precede the
        // first accessibility window snapshot on a cold emulator. Poll the
        // condition without blocking the main thread on repeated idle checks.
        instrument.waitForIdleSync()
        repeat(120) {
            val active=try{automation.rootInActiveWindow}catch(_:Exception){null}
            if(active!=null) {
                val pkg=active.packageName?.toString().orEmpty()
                observedPackage=pkg
                if(pkg==fixturePackage && matchesFixture(active,marker))return active
            }
            if(it%4==0) {
                val windows=try{automation.windows}catch(_:Exception){emptyList()}
                for(window in windows) {
                    val root=try{window.root}catch(_:Exception){null}?:continue
                    if(root.packageName?.toString()==fixturePackage &&
                        matchesFixture(root,marker))return root
                }
            }
            SystemClock.sleep(100L)
        }
        // Report package identity only. Never print screen text, screenshots
        // or any credential or personal contact.
        throw AssertionError("Synthetic target window not accessible/ready; active package="+
            observedPackage.take(75))
    }

    fun launchFixture(activity:Class<*>,duplicate:Boolean?=null):String {
        val packageName=InstrumentationRegistry.getInstrumentation().context.packageName
        val component="$packageName/${activity.name}"
        val extra=duplicate?.let { " --ez duplicate $it" }.orEmpty()
        val runId=UUID.randomUUID().toString().replace("-","")
        val descriptor=qaAutomation().executeShellCommand("am start -n $component -f 0x10008000$extra --es qa_run $runId")
        val output=FileInputStream(descriptor.fileDescriptor).bufferedReader().use{it.readText()}
        check(!output.contains("Error",ignoreCase=true)) { "Could not launch synthetic test fixture" }
        return runId
    }

    fun waitForText(runId:String,expected:String):AccessibilityNodeInfo {
        repeat(50) {
            val root=ownWindow("QA run: $runId")
            if(nodes(root).any{it.text?.toString()==expected})return root
            SystemClock.sleep(100L)
        }
        throw AssertionError("Synthetic fixture did not expose the reviewed text outcome")
    }

    fun closeFixture() {
        val descriptor=qaAutomation().executeShellCommand("input keyevent 4")
        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
    }
}
