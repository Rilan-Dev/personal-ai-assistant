package dev.rilandev.personalaiassistant

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.Promise
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No real contact, message, screen, microphone, or gestures involved. */
@RunWith(AndroidJUnit4::class)
class NexaControlledAutomationAndroidTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private fun invoke(action: (Promise)->Unit): Pair<Any?,String?> {
        val ready=CountDownLatch(1)
        val value=AtomicReference<Any?>()
        val error=AtomicReference<String?>()
        val p=Proxy.newProxyInstance(Promise::class.java.classLoader,
            arrayOf(Promise::class.java)) { _, method, args ->
            when(method.name) {
                "resolve" -> {value.set(args?.firstOrNull()); ready.countDown()}
                "reject" -> {error.set(args?.firstOrNull()?.toString()); ready.countDown()}
            }
            null
        } as Promise
        action(p)
        assertTrue("Native bridge promise never finished",ready.await(5,TimeUnit.SECONDS))
        return Pair(value.get(),error.get())
    }
    @Test fun launcherNavigatorNeedsSystemBoundAccessibilityGrant() {
        @Suppress("DEPRECATION")
        val info = app.packageManager.getServiceInfo(
            ComponentName(app,NexaLauncherNavigationService::class.java),
            PackageManager.GET_META_DATA)
        assertTrue(info.exported)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",info.permission)
        assertTrue((info.metaData?.getInt("android.accessibilityservice") ?: 0) != 0)
        assertEquals("NOT_FOREGROUND", invoke {
            NexaLauncherModule(BridgeReactContext(app)).findGitHubIcon(it)
        }.second)
    }
    @Test fun launcherStateRejectsOtherAppsAndCapsSwipes() {
        LauncherNavigationState.stop()
        LauncherNavigationState.connected=true
        try {
            assertTrue(LauncherNavigationState.begin("com.example.home"))
            assertFalse(LauncherNavigationState.allows("com.example.browser"))
            assertEquals("searching",LauncherNavigationState.status().first)
            assertTrue(LauncherNavigationState.allows("com.example.home"))
            for (i in 1..8) {
                assertEquals(i,LauncherNavigationState.beginSwipe())
                LauncherNavigationState.swipeFinished(true)
            }
            assertEquals(-1,LauncherNavigationState.beginSwipe())
            assertEquals("not_found",LauncherNavigationState.status().first)
            assertEquals(8,LauncherNavigationState.status().second)
        } finally {
            LauncherNavigationState.stop()
            LauncherNavigationState.connected=false
        }
    }
    @Test fun launcherStopsWhenUserLeavesConfirmedHome() {
        LauncherNavigationState.connected=true
        try {
            assertTrue(LauncherNavigationState.begin("com.example.home"))
            assertTrue(LauncherNavigationState.allows("com.example.home"))
            LauncherNavigationState.markMissingHome("com.example.email")
            assertEquals("wrong_screen",LauncherNavigationState.status().first)
        } finally {
            LauncherNavigationState.stop()
            LauncherNavigationState.connected=false
        }
    }
    @Test fun messageComposerCannotRunFromBackground() {
        val result=invoke {
            NexaWhatsAppComposeModule(BridgeReactContext(app)).openReviewedDraft(
                "+911234567890","I will call you later",it)
        }
        assertNull(result.first)
        assertEquals("NOT_FOREGROUND",result.second)
    }
    @Test fun rejectedLookupCannotClaimIconFound() {
        LauncherNavigationState.connected=false
        assertFalse(LauncherNavigationState.begin("com.example.home"))
        assertFalse(LauncherNavigationState.allows("com.example.home"))
    }
}
