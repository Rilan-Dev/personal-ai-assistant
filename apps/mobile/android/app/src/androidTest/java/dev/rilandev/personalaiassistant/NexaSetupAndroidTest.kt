package dev.rilandev.personalaiassistant

import android.content.Context
import android.provider.Settings
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
        val enabledServicesBefore=Settings.Secure.getString(app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val accessibilityBefore=Settings.Secure.getInt(app.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,0)
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
        assertEquals("Setup status must reflect Android's actual Screen Assist setting",
            NexaAccessibilityServiceStatus.isEnabled(enabledServicesBefore,
                android.content.ComponentName(app,ScreenAssistService::class.java)),
            details.getBoolean("screenAssist"))
        assertEquals("Setup status must reflect Android's actual launcher setting",
            NexaAccessibilityServiceStatus.isEnabled(enabledServicesBefore,
                android.content.ComponentName(app,NexaLauncherNavigationService::class.java)),
            details.getBoolean("launcherNavigation"))
        assertEquals("Reading setup status must not change enabled services",enabledServicesBefore,
            Settings.Secure.getString(app.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        assertEquals("Reading setup status must not enable global Accessibility",accessibilityBefore,
            Settings.Secure.getInt(app.contentResolver,Settings.Secure.ACCESSIBILITY_ENABLED,0))
    }
    @Test fun setupAcknowledgementRequiresForegroundOwner() {
        val bridge=NexaSetupModule(BridgeReactContext(app))
        assertEquals("NOT_FOREGROUND",invoke {bridge.acknowledgeSetup(it)}.second)
        assertEquals("NOT_FOREGROUND",invoke {bridge.openAppSettings(it)}.second)
    }
}
