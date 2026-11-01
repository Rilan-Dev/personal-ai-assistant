package dev.rilandev.personalaiassistant

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

/** Instrumented proof that setup can only observe Android permissions. */
@RunWith(AndroidJUnit4::class)
class NexaSetupAndroidTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private fun invoke(action:(Promise)->Unit): Pair<Any?,String?> {
        val done=CountDownLatch(1)
        val data=AtomicReference<Any?>()
        val error=AtomicReference<String?>()
        val promise=Proxy.newProxyInstance(Promise::class.java.classLoader,
            arrayOf(Promise::class.java)){_,method,args->
            when (method.name) {
                "resolve"->{ data.set(args?.firstOrNull());done.countDown() }
                "reject"->{ error.set(args?.firstOrNull()?.toString());done.countDown() }
            }
            null
        } as Promise
        action(promise)
        assertTrue("Bridge did not finish",done.await(8,TimeUnit.SECONDS))
        return Pair(data.get(),error.get())
    }
    @Test fun setupStatusReportsNativeBuildAndRealPermissions() {
        val bridge=NexaSetupModule(BridgeReactContext(app))
        val (value,error)=invoke { bridge.getSetupStatus(it) }
        assertNull(error)
        val details=value as ReadableMap
        assertEquals(BuildConfig.VERSION_NAME,details.getString("versionName"))
        assertEquals(BuildConfig.VERSION_CODE,details.getInt("versionCode"))
        assertEquals(BuildConfig.PAA_SOURCE_REVISION,details.getString("sourceRevision"))
        assertTrue(details.getInt("sdkLevel")>=29)
        assertEquals(app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==
            PackageManager.PERMISSION_GRANTED,details.getBoolean("microphone"))
        assertFalse("No test should silently enable accessibility", details.getBoolean("screenAssist"))
        assertFalse("No test should silently enable launcher gestures",
            details.getBoolean("launcherNavigation"))
    }
    @Test fun setupAcknowledgementRequiresForegroundOwner() {
        val bridge=NexaSetupModule(BridgeReactContext(app))
        assertEquals("NOT_FOREGROUND",invoke {bridge.acknowledgeSetup(it)}.second)
        assertEquals("NOT_FOREGROUND",invoke {bridge.openAppSettings(it)}.second)
    }
}
