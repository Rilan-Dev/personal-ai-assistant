package dev.rilandev.personalaiassistant

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
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

@RunWith(AndroidJUnit4::class)
class NexaWakeWordAndroidTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun invoke(block:(Promise)->Unit): Pair<Any?,String?> {
        val done=CountDownLatch(1)
        val value=AtomicReference<Any?>()
        val error=AtomicReference<String?>()
        val p=Proxy.newProxyInstance(Promise::class.java.classLoader,
            arrayOf(Promise::class.java)){_,method,args->
            when(method.name) {
                "resolve" -> {value.set(args?.firstOrNull());done.countDown()}
                "reject" -> {error.set(args?.firstOrNull()?.toString());done.countDown()}
            }
            null
        } as Promise
        block(p)
        assertTrue(done.await(6,TimeUnit.SECONDS))
        return Pair(value.get(),error.get())
    }
    @Test fun twoConsistentWakeFramesNeededWithTimeoutReset() {
        val detector = NexaWakePhrase()
        assertFalse(detector.observe("hey", 1000))
        assertFalse(detector.observe("nexa", 1200))
        assertFalse(detector.observe("hey nexa", 1300))
        assertTrue(detector.observe("hey nexa", 1700, complete = true))
        assertFalse(detector.observe("hey nexa", 5000))
        assertFalse(detector.observe("noise", 5100))
        assertFalse(detector.observe("hey nexa", 5500))
        assertTrue(detector.observe("hey nexa", 5700, complete = true))
        assertFalse(detector.observe("hello alexa", 6200))
        assertFalse(detector.observe("hey next", 6400))
        // ASR may emit a misleading partial for "Hey next", but the final
        // transcript rejects that false wake candidate.
        val ambiguous = NexaWakePhrase()
        assertFalse(ambiguous.observe("hey next a", 7000))
        assertFalse(ambiguous.observe("hey next a", 7250))
        assertFalse(ambiguous.observe("hey next", 7400, complete = true))
    }
    @Test fun serviceIsNotStartedWithoutVisibleOwner() {
        val module = NexaWakeWordModule(BridgeReactContext(ctx))
        val (data,error)=invoke {module.getStatus(it)}
        assertNull(error)
        val status=data as ReadableMap
        assertTrue(status.hasKey("modelReady"))
        assertTrue(status.hasKey("listening"))
        assertTrue(status.hasKey("permissionGranted"))
        assertEquals("NOT_FOREGROUND",invoke {module.startListening(it)}.second)
        assertEquals("NOT_FOREGROUND",invoke {module.installModel(it)}.second)
        assertFalse(NexaWakeWordService.listening)
    }
}
