package dev.rilandev.personalaiassistant

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Integration safety assertions: never turns microphone or screen access on. */
@RunWith(AndroidJUnit4::class)
class BackgroundAssistSafetyAndroidTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Suppress("DEPRECATION")
    private fun reactContext() = BridgeReactContext(context)

    private fun invoke(block: (Promise) -> Unit): Pair<Any?,String?> {
        val ready=CountDownLatch(1)
        val value=AtomicReference<Any?>()
        val error=AtomicReference<String?>()
        val promise=Proxy.newProxyInstance(
            Promise::class.java.classLoader,arrayOf(Promise::class.java)
        ) { _, method, args ->
            when (method.name) {
                "resolve" -> { value.set(args?.firstOrNull());ready.countDown() }
                "reject" -> { error.set(args?.firstOrNull()?.toString());ready.countDown() }
            }
            null
        } as Promise
        block(promise)
        assertTrue("Native promise did not finish",ready.await(8,TimeUnit.SECONDS))
        return Pair(value.get(),error.get())
    }

    @Test fun microphoneForegroundServiceIsNotPublicOrAutoStarted() {
        val info=context.packageManager.getServiceInfo(
            ComponentName(context,RealtimeVoiceService::class.java),0)
        assertFalse(info.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        val result=invoke { RealtimeVoiceModule(reactContext()).startVoiceSession(it) }
        assertNull("No audio service can be started from an invisible Activity",result.first)
        assertEquals("NOT_FOREGROUND",result.second)
    }

    @Test fun backgroundMicStatusIsPureReadWithoutAudioCapture() {
        val result=invoke { RealtimeVoiceModule(reactContext()).getVoiceSessionStatus(it) }
        assertNull(result.second)
        val state=result.first as ReadableMap
        assertFalse(state.getBoolean("microphoneActive"))
    }

    @Test fun accessibilityServiceRequiresUserEnablementInAndroidSettings() {
        val info=context.packageManager.getServiceInfo(
            ComponentName(context,ScreenAssistService::class.java),
            PackageManager.GET_META_DATA)
        assertTrue(info.exported)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",info.permission)
        assertNotNull(info.metaData?.get("android.accessibilityservice"))
        val denied=invoke { ScreenAssistModule(reactContext()).armOneScreen(it) }
        assertEquals("NOT_FOREGROUND",denied.second)
    }

    @Test fun ownerArmedVisibleTextSnapshotIsOneTimeAndOnlyInMemory() {
        // Simulated owner enabling the service. No Android screen or outside
        // application is read during this test.
        try {
            ScreenAssistState.clear()
            ScreenAssistState.connected = true
            assertTrue(ScreenAssistState.arm())
            ScreenAssistState.capture("com.example.visible", "Visible text from synthetic screen")
            ScreenAssistState.capture("com.example.second", "Never collect a second screen")
            val first=ScreenAssistState.consume()
            assertNotNull(first)
            assertEquals("com.example.visible", first!!.second)
            assertTrue(first.first.contains("Visible text"))
            assertFalse(first.first.contains("Never collect"))
            assertNull("Repeated snapshot read must be empty", ScreenAssistState.consume())
        } finally {
            ScreenAssistState.connected = false
            ScreenAssistState.clear()
        }
    }

    @Test fun screenSnapshotIsBoundedAndOwnerCanDiscardWithoutReading() {
        try {
            ScreenAssistState.clear()
            ScreenAssistState.connected = true
            assertTrue(ScreenAssistState.arm())
            ScreenAssistState.capture("com.example.public", "A".repeat(3000))
            val result=ScreenAssistState.consume()
            assertNotNull(result)
            assertEquals(2000,result!!.first.length)
            assertTrue(ScreenAssistState.arm())
            ScreenAssistState.capture("com.example.public", "Synthetic private text")
            ScreenAssistState.clear()
            assertNull("Owner discard must purge memory",ScreenAssistState.consume())
            assertFalse(ScreenAssistState.isArmed())
        } finally {
            ScreenAssistState.connected = false
            ScreenAssistState.clear()
        }
    }

    @Test fun screenTextCannotBeReadOrArmedWithoutForegroundConsent() {
        val module=ScreenAssistModule(reactContext())
        val status=invoke { module.getStatus(it) }
        assertNull(status.second)
        val data=status.first as ReadableMap
        assertFalse(data.getBoolean("armed"))
        assertFalse(data.getBoolean("enabled"))
        val setting=invoke { module.openAccessibilitySettings(it) }
        assertEquals("NOT_FOREGROUND",setting.second)
        assertNull(invoke { module.takeCapturedText(it) }.first)
    }
}
