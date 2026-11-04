package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/** Strict, test-only Accessibility root selection for owner-isolated emulator.
 * ActiveWindow can point at a lingering IME or system permission dialog.
 * Never inspect any non-app window's text or return its nodes to the tests.
 */
internal object NexaQaAccessibilityRoot {
    fun ownWindow():AccessibilityNodeInfo {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val automation=instrument.uiAutomation
        val app=instrument.targetContext.packageName
        try {
            val info=automation.serviceInfo
            info.flags=info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            automation.serviceInfo=info
        }catch(_:Exception){ /* rootInActiveWindow remains the fallback */ }
        var observedPackage=""
        repeat(24) {
            instrument.waitForIdleSync()
            val active=try{automation.rootInActiveWindow}catch(_:Exception){null}
            if(active!=null) {
                val pkg=active.packageName?.toString().orEmpty()
                observedPackage=pkg
                if(pkg==app)return active
            }
            val windows=try{automation.windows}catch(_:Exception){emptyList()}
            for(window in windows) {
                val root=try{window.root}catch(_:Exception){null}?:continue
                if(root.packageName?.toString()==app)return root
            }
            SystemClock.sleep(150L)
        }
        // Report package identity only. Never print screen text, screenshots
        // or any credential or personal contact.
        throw AssertionError("Synthetic target window not accessible; active package="+
            observedPackage.take(75))
    }
}
