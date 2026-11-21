package dev.rilandev.personalaiassistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end one-shot Screen Assist check without UiAutomation/Accessibility takeover. */
@RunWith(AndroidJUnit4::class)
class NexaScreenAssistLiveAndroidTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun matches(view: View, labels: Set<String>): Boolean =
        view.contentDescription?.toString() in labels ||
            (view is TextView && view.text?.toString() in labels)

    private fun findLabel(view: View, labels: Set<String>): View? {
        if (matches(view, labels)) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findLabel(view.getChildAt(index), labels)?.let { return it }
        }
        return null
    }

    private fun clickableAncestor(view: View?): View? {
        var current = view
        while (current != null) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent as? View
        }
        return null
    }

    private fun scrollForward(activity: MainActivity): Boolean {
        fun findScrollable(view: View): View? {
            if (view.canScrollVertically(1) && view is ViewGroup &&
                view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null))
                return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    findScrollable(view.getChildAt(index))?.let { return it }
                }
            }
            return null
        }
        return findScrollable(activity.window.decorView) != null
    }

    private fun clickLabel(
        scenario: ActivityScenario<MainActivity>,
        labels: Set<String>,
        timeoutMs: Long = 12_000
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var clicked = false
            scenario.onActivity { activity ->
                val target = clickableAncestor(findLabel(activity.window.decorView, labels))
                if (target != null && target.isShown && target.width > 0 && target.height > 0)
                    clicked = target.performClick()
            }
            if (clicked) return true
            scenario.onActivity(::scrollForward)
            SystemClock.sleep(250)
        }
        return false
    }

    private fun waitForLabel(
        scenario: ActivityScenario<MainActivity>,
        labels: Set<String>,
        timeoutMs: Long = 12_000
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var found = false
            scenario.onActivity { activity ->
                found = findLabel(activity.window.decorView, labels) != null
            }
            if (found) return true
            SystemClock.sleep(150)
        }
        return false
    }

    private fun hasVisibleLabel(
        scenario: ActivityScenario<MainActivity>,
        labels: Set<String>
    ): Boolean {
        var found = false
        scenario.onActivity { activity ->
            val target = findLabel(activity.window.decorView, labels)
            found = target?.isShown == true && target.width > 0 && target.height > 0
        }
        return found
    }

    private fun waitForSettingsRoot(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = try { ScreenAssistService.active?.externalWindowRoot() }
                catch (_: Exception) { null }
            if (root != null) {
                val isSettings = root.packageName?.toString() == "com.android.settings"
                try { root.recycle() } catch (_: Exception) {}
                if (isSettings) return true
            }
            SystemClock.sleep(100)
        }
        return false
    }

    @Test fun armCaptureReviewAndDiscardOnLiveSettingsScreen() {
        assertNotNull("Screen Assist service must remain bound while the test drives app views",
            ScreenAssistService.active)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            assertTrue("Nexa Tools tab did not render", waitForLabel(scenario, setOf("Tools tab", "Tools")))
            assertTrue("Nexa Tools tab could not be opened",
                clickLabel(scenario, setOf("Tools tab", "Tools")))
            assertTrue("Arm control did not become visible",
                clickLabel(scenario, setOf("Arm one screen read")))
            assertTrue("visible Arm control did not arm the native one-shot", ScreenAssistState.isArmed())

            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("Android Settings did not become the observed external window",
                waitForSettingsRoot(5_000))
            val captureDeadline = SystemClock.elapsedRealtime() + 6_000
            while (ScreenAssistState.isArmed() && SystemClock.elapsedRealtime() < captureDeadline)
                SystemClock.sleep(100)
            assertFalse("Screen Assist never completed or expired the one-shot window",
                ScreenAssistState.isArmed())

            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            assertNotNull("Nexa launcher activity is unavailable", launch)
            context.startActivity(launch!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            assertTrue("Review control did not return to the visible Nexa Tools screen",
                waitForLabel(scenario, setOf("Review captured text")))
            assertTrue("Review control did not consume a Settings snapshot",
                clickLabel(scenario, setOf("Review captured text")))
            assertTrue("Review did not report a local Settings capture",
                waitForLabel(scenario, setOf("Captured from com.android.settings")))
            assertTrue("Discard control was unavailable after review",
                clickLabel(scenario, setOf("Discard")))
            assertTrue("Discard did not clear the visible source label",
                waitForLabel(scenario, setOf("Screen text discarded from memory.")))
            assertFalse("Discard left captured content in the UI",
                hasVisibleLabel(scenario, setOf("Captured from com.android.settings")))
        } finally {
            ScreenAssistState.clear()
            scenario.close()
        }
    }
}
