package dev.rilandev.personalaiassistant

import android.content.Context
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

/**
 * Safe Android integration tests: no live recording, no TTS playback.
 * Actual speech engine and offline voices require physical-device UAT.
 */
@RunWith(AndroidJUnit4::class)
class VoiceControlAndroidTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    @Suppress("DEPRECATION")
    private fun module() = VoiceControlModule(BridgeReactContext(app))

    private fun invoke(block: (Promise) -> Unit): Pair<Any?,String?> {
        val latch = CountDownLatch(1)
        val value = AtomicReference<Any?>()
        val error = AtomicReference<String?>()
        val callback = Proxy.newProxyInstance(
            Promise::class.java.classLoader,arrayOf(Promise::class.java)
        ) { _, method, args ->
            when (method.name) {
                "resolve" -> {
                    value.set(args?.firstOrNull())
                    latch.countDown()
                }
                "reject" -> {
                    error.set(args?.firstOrNull()?.toString() ?: "UNKNOWN")
                    latch.countDown()
                }
            }
            null
        } as Promise
        block(callback)
        assertTrue("Voice module callback did not complete",latch.await(10,TimeUnit.SECONDS))
        return Pair(value.get(),error.get())
    }

    @Test fun nativeSpeechCapabilitiesNeverAdvertiseWakeWord() {
        val result=invoke { module().getCapabilities(it) }
        assertNull(result.second)
        val data=result.first as ReadableMap
        assertTrue(data.getBoolean("offlineOnly"))
        assertFalse(data.getBoolean("wakeWord"))
        assertTrue(data.getInt("androidApi") >= 31)
    }

    @Test fun microphoneCannotStartWithoutForegroundActivity() {
        val result=invoke { module().listenOnce("en-IN",it) }
        assertNull("Speech must not start without a foreground Activity",result.first)
        assertEquals("NOT_FOREGROUND",result.second)
    }

    @Test fun spokenOutputCannotStartWithoutForegroundActivity() {
        val result=invoke { module().speakOffline("No private data","en-IN",it) }
        assertNull(result.first)
        assertEquals("NOT_FOREGROUND",result.second)
    }

    @Test fun idleStopControlsDoNotStartBackgroundAudio() {
        val voice=module()
        assertEquals(true,invoke { voice.cancelListening(it) }.first)
        assertEquals(true,invoke { voice.stopSpeaking(it) }.first)
    }
}
