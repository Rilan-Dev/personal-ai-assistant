package dev.rilandev.personalaiassistant

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaOverlayTranscriptAndroidTest {
    @Test fun sharedChatReplyReopensCollapsedOverlay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Overlay access is needed for this device integration test",
            Settings.canDrawOverlays(app))
        val overlayWasVisible = NexaToolOverlayService.visible
        var service: NexaToolOverlayService? = null
        var expandedField: java.lang.reflect.Field? = null
        var previousExpanded: Boolean? = null
        var sectionField: java.lang.reflect.Field? = null
        var previousSection: String? = null
        var history: MutableCollection<String>? = null
        var previousHistory: List<String>? = null
        try {
            if (!overlayWasVisible) {
                app.startService(Intent(app, NexaToolOverlayService::class.java).apply {
                    action = NexaToolOverlayService.ACTION_SHOW
                })
                assertTrue("The test must start the floating overlay", waitForOverlay(true))
            }
            val serviceField = NexaToolOverlayService::class.java
                .getDeclaredField("activeOverlay").apply { isAccessible = true }
            service = serviceField.get(NexaToolOverlayService.Companion)
                as? NexaToolOverlayService
            assertNotNull("Show Nexa's floating overlay before this test", service)
            expandedField = service!!.javaClass.getDeclaredField("expanded")
                .apply { isAccessible = true }
            sectionField = service!!.javaClass.getDeclaredField("section")
                .apply { isAccessible = true }
            val historyField = service!!.javaClass.getDeclaredField("history")
                .apply { isAccessible = true }
            history = historyField.get(service) as MutableCollection<String>
            instrumentation.runOnMainSync {
                previousExpanded = expandedField!!.getBoolean(service)
                previousSection = sectionField!!.get(service) as String
                previousHistory = history!!.toList()
                expandedField!!.setBoolean(service, false)
                sectionField!!.set(service, "chat")
                service!!.javaClass.getDeclaredMethod("render")
                    .apply { isAccessible = true }
                    .invoke(service)
            }

            NexaToolOverlayService.showSharedChatReply(
                "qa-reopen-collapsed-chat", "QA scroll result received"
            )

            assertTrue(
                "A shared Chat result must reopen a collapsed overlay so the owner can read it",
                waitForExpanded(instrumentation, service!!, expandedField!!, true)
            )
        } finally {
            val currentService = service
            if (overlayWasVisible && currentService != null) {
                instrumentation.runOnMainSync {
                    if (history != null && previousHistory != null) {
                        history!!.clear()
                        history!!.addAll(previousHistory!!)
                    }
                    if (sectionField != null && previousSection != null) {
                        sectionField!!.set(currentService, previousSection)
                    }
                    if (expandedField != null && previousExpanded != null) {
                        expandedField!!.setBoolean(currentService, previousExpanded!!)
                    }
                    currentService.javaClass.getDeclaredMethod("render")
                        .apply { isAccessible = true }
                        .invoke(currentService)
                }
            } else if (!overlayWasVisible) {
                app.stopService(Intent(app, NexaToolOverlayService::class.java))
                waitForOverlay(false)
            }
        }
    }

    @Test fun latestSharedChatReplyIsVisibleWithoutManualScroll() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Overlay access is needed for this device integration test",
            Settings.canDrawOverlays(app))
        val overlayWasVisible = NexaToolOverlayService.visible
        var service: NexaToolOverlayService? = null
        var history: MutableCollection<String>? = null
        var previousHistory: List<String>? = null
        var sectionField: java.lang.reflect.Field? = null
        var previousSection: String? = null
        var expandedField: java.lang.reflect.Field? = null
        var previousExpanded: Boolean? = null
        try {
            if (!overlayWasVisible) {
                app.startService(Intent(app, NexaToolOverlayService::class.java).apply {
                    action = NexaToolOverlayService.ACTION_SHOW
                })
                assertTrue("The test must start the floating overlay", waitForOverlay(true))
            }
            val serviceField = NexaToolOverlayService::class.java
                .getDeclaredField("activeOverlay").apply { isAccessible = true }
            service = serviceField.get(NexaToolOverlayService.Companion)
                as? NexaToolOverlayService
            assertNotNull("Show Nexa's floating overlay before this test", service)
            assertTrue("The floating panel must be visible", NexaToolOverlayService.visible)

            sectionField = service!!.javaClass.getDeclaredField("section")
                .apply { isAccessible = true }
            previousSection = sectionField!!.get(service) as String
            expandedField = service!!.javaClass.getDeclaredField("expanded")
                .apply { isAccessible = true }
            previousExpanded = expandedField!!.getBoolean(service)
            // The overlay may be collapsed or left on Tools by an earlier integration
            // flow. Expand it and select Chat explicitly, then restore both prior states.
            instrumentation.runOnMainSync {
                expandedField!!.setBoolean(service, true)
                sectionField!!.set(service, "chat")
                service!!.javaClass.getDeclaredMethod("render")
                    .apply { isAccessible = true }
                    .invoke(service)
            }

            val historyField = service!!.javaClass.getDeclaredField("history")
                .apply { isAccessible = true }
            history = historyField.get(service) as MutableCollection<String>
            previousHistory = history!!.toList()

            repeat(3) { index ->
                NexaToolOverlayService.showSharedChatReply(
                    "qa-transcript-$index",
                    ("earlier safe result $index ").repeat(35)
                )
            }
            val latest = ("latest safe controls result ").repeat(35) + "LATEST_RESULT"
            NexaToolOverlayService.showSharedChatReply("qa-transcript-latest", latest)
            instrumentation.waitForIdleSync()

            var transcript: ScrollView? = null
            var renderedText = ""
            instrumentation.runOnMainSync {
                val rootField = service!!.javaClass.getDeclaredField("view")
                    .apply { isAccessible = true }
                val root = rootField.get(service) as View
                transcript = findTranscript(root)
                renderedText = transcript?.let { (it.getChildAt(0) as TextView).text.toString() } ?: ""
            }

            val scrollView = transcript
            assertNotNull("The Chat transcript must use a scrollable viewport", scrollView)
            assertTrue("The latest reply must be present", renderedText.endsWith("LATEST_RESULT"))
            assertTrue(
                "The transcript must scroll to the latest reply after its overlay layout completes",
                waitForTranscriptAtBottom(instrumentation, scrollView!!)
            )
            assertTrue(
                "The transcript must start at its latest reply instead of hiding it below the viewport",
                scrollView!!.scrollY > 0
            )
            assertEquals(
                "The scroll viewport should be at the bottom after a new Chat reply",
                scrollView.getChildAt(0).height - scrollView.height,
                scrollView.scrollY
            )
        } finally {
            val currentService = service
            if (overlayWasVisible && currentService != null) {
                instrumentation.runOnMainSync {
                    if (history != null && previousHistory != null) {
                        history!!.clear()
                        history!!.addAll(previousHistory!!)
                    }
                    if (sectionField != null && previousSection != null) {
                        sectionField!!.set(currentService, previousSection)
                    }
                    if (expandedField != null && previousExpanded != null) {
                        expandedField!!.setBoolean(currentService, previousExpanded!!)
                    }
                    currentService.javaClass.getDeclaredMethod("render")
                        .apply { isAccessible = true }
                        .invoke(currentService)
                }
            } else if (!overlayWasVisible) {
                app.stopService(Intent(app, NexaToolOverlayService::class.java))
                waitForOverlay(false)
            }
        }
    }

    private fun waitForTranscriptAtBottom(
        instrumentation: android.app.Instrumentation,
        scrollView: ScrollView
    ): Boolean {
        repeat(40) {
            var atBottom = false
            instrumentation.runOnMainSync {
                val content = scrollView.getChildAt(0)
                val maxScroll = ((content?.bottom ?: 0) - scrollView.height).coerceAtLeast(0)
                atBottom = maxScroll > 0 && scrollView.scrollY == maxScroll
            }
            if (atBottom) return true
            SystemClock.sleep(50)
        }
        return false
    }

    private fun waitForOverlay(expected: Boolean): Boolean {
        repeat(80) {
            if (NexaToolOverlayService.visible == expected) return true
            SystemClock.sleep(50)
        }
        return false
    }

    private fun waitForExpanded(
        instrumentation: android.app.Instrumentation,
        service: NexaToolOverlayService,
        expandedField: java.lang.reflect.Field,
        expected: Boolean,
    ): Boolean {
        repeat(80) {
            var expanded = !expected
            instrumentation.runOnMainSync {
                expanded = expandedField.getBoolean(service)
            }
            if (expanded == expected) return true
            SystemClock.sleep(50)
        }
        return false
    }

    private fun findTranscript(view: View): ScrollView? {
        if (view is ScrollView && view.childCount == 1 && view.getChildAt(0) is TextView) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTranscript(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
