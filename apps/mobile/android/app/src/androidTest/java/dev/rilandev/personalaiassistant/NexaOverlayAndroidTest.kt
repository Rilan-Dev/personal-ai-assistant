package dev.rilandev.personalaiassistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
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

@RunWith(AndroidJUnit4::class)
class NexaOverlayAndroidTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private fun invoke(block:(Promise)->Unit):Pair<Any?,String?> {
        val done=CountDownLatch(1)
        val result=AtomicReference<Any?>()
        val error=AtomicReference<String?>()
        val p=Proxy.newProxyInstance(Promise::class.java.classLoader,
            arrayOf(Promise::class.java)){_,method,args->
            when(method.name) {
                "resolve"->{result.set(args?.firstOrNull());done.countDown()}
                "reject"->{error.set(args?.firstOrNull()?.toString());done.countDown()}
            }
            null
        } as Promise
        block(p)
        assertTrue(done.await(8,TimeUnit.SECONDS))
        return Pair(result.get(),error.get())
    }
    @Test fun overlayServiceIsPrivateAndPermissionDeclared() {
        val pm=app.packageManager
        @Suppress("DEPRECATION")
        val svc=pm.getServiceInfo(ComponentName(app,NexaToolOverlayService::class.java),0)
        assertFalse(svc.exported)
        assertTrue(pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(Manifest.permission.SYSTEM_ALERT_WINDOW) == true)
        assertFalse("No test starts the overlay",NexaToolOverlayService.visible)
    }
    @Test fun overlayReportsSystemPermissionButRefusesBackgroundStart() {
        val bridge=NexaToolOverlayModule(BridgeReactContext(app))
        val (value,error)=invoke {bridge.getStatus(it)}
        assertNull(error)
        val state=value as ReadableMap
        assertEquals(Settings.canDrawOverlays(app),state.getBoolean("granted"))
        assertFalse(state.getBoolean("visible"))
        assertEquals("NOT_FOREGROUND",invoke{bridge.show(it)}.second)
        assertEquals("NOT_FOREGROUND",invoke{bridge.requestPermission(it)}.second)
    }
    @Test fun assistantSettingsRequiresVisibleApp() {
        val bridge=NexaAssistantRoleModule(BridgeReactContext(app))
        assertEquals("NOT_FOREGROUND",invoke{bridge.openAssistantSettings(it)}.second)
        assertEquals("NOT_FOREGROUND",invoke{bridge.requestAssistantRole(it)}.second)
    }
}
