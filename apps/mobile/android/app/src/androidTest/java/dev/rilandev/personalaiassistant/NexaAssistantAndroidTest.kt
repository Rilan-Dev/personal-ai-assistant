package dev.rilandev.personalaiassistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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

/** Does not activate the default assistant, launch external apps or access a microphone. */
@RunWith(AndroidJUnit4::class)
class NexaAssistantAndroidTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun invoke(block: (Promise) -> Unit): Pair<Any?, String?> {
        val latch = CountDownLatch(1)
        val value = AtomicReference<Any?>()
        val error = AtomicReference<String?>()
        val callback = Proxy.newProxyInstance(Promise::class.java.classLoader,
            arrayOf(Promise::class.java)) { _, method, args ->
            when (method.name) {
                "resolve" -> { value.set(args?.firstOrNull()); latch.countDown() }
                "reject" -> { error.set(args?.firstOrNull()?.toString()); latch.countDown() }
            }
            null
        } as Promise
        block(callback)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return Pair(value.get(), error.get())
    }

    @Test fun assistantServiceIsSystemBoundWithVoiceMetadata() {
        val pm = app.packageManager
        val component = ComponentName(app, NexaAssistantService::class.java)
        @Suppress("DEPRECATION")
        val info = pm.getServiceInfo(component, PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_VOICE_INTERACTION, info.permission)
        assertTrue(info.exported)
        assertTrue((info.metaData?.getInt("android.voice_interaction") ?: 0) != 0)
        @Suppress("DEPRECATION")
        val session = pm.getServiceInfo(
            ComponentName(app, NexaVoiceSessionService::class.java), 0)
        assertEquals(Manifest.permission.BIND_VOICE_INTERACTION, session.permission)
        assertTrue(session.exported)
        @Suppress("DEPRECATION")
        val speech = pm.getServiceInfo(ComponentName(app, NexaRecognitionService::class.java), 0)
        assertTrue(speech.exported)
    }

    @Test fun roleStatusCannotClaimHotwordAvailable() {
        val result = invoke {
            NexaAssistantRoleModule(BridgeReactContext(app)).getAssistantRoleStatus(it)
        }
        assertNull(result.second)
        val status = result.first as ReadableMap
        assertFalse(status.getBoolean("wakeWordReady"))
        assertTrue(status.hasKey("selected"))
        assertTrue(status.hasKey("available"))
    }

    @Test fun packageAvailabilityNeverClaimsHomeScreenLocation() {
        val result = invoke {
            DeviceControlModule(BridgeReactContext(app)).checkSupportedApp("github", it)
        }
        assertNull(result.second)
        val data = result.first as ReadableMap
        assertEquals("com.github.android", data.getString("packageId"))
        assertEquals("unknown", data.getString("homeScreenPage"))
        assertEquals("package_manager_only", data.getString("verification"))
    }

    @Test fun unknownAppCannotBeQueried() {
        val result = invoke {
            DeviceControlModule(BridgeReactContext(app)).checkSupportedApp("random-app", it)
        }
        assertNull(result.first)
        assertEquals("UNSUPPORTED_APP", result.second)
    }
}
