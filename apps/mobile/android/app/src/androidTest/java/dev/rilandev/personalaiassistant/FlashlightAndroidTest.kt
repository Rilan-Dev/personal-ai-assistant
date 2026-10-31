package dev.rilandev.personalaiassistant

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

/**
 * Tests Android flashlight access boundaries without ever changing an LED.
 * Real CameraManager TorchCallback verification requires a physical device.
 */
@RunWith(AndroidJUnit4::class)
class FlashlightAndroidTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    @Suppress("DEPRECATION")
    private fun bridgeContext() = BridgeReactContext(app)

    private fun promise(onResolve: (Any?) -> Unit, onReject: (String) -> Unit): Promise =
        Proxy.newProxyInstance(
            Promise::class.java.classLoader, arrayOf(Promise::class.java)
        ) { _, method, args ->
            when (method.name) {
                "resolve" -> onResolve(args?.firstOrNull())
                "reject" -> onReject(args?.firstOrNull()?.toString() ?: "UNKNOWN")
            }
            null
        } as Promise

    @Test fun nativeHardwareQueryDoesNotChangeTorchOrRequireRuntimeCameraPermission() {
        val module = FlashlightControlModule(bridgeContext())
        val completed = CountDownLatch(1)
        val response = AtomicReference<Any?>()
        val failure = AtomicReference<String>()
        module.hasFlashlight(promise({
            response.set(it); completed.countDown()
        }, {
            failure.set(it); completed.countDown()
        }))
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertNull(failure.get())
        assertTrue(response.get() is Boolean)
    }

    @Test fun flashlightControlMustFailClosedWithoutForegroundActivity() {
        val module = FlashlightControlModule(bridgeContext())
        val completed = CountDownLatch(1)
        val response = AtomicReference<Any?>()
        val failure = AtomicReference<String>()
        // This test context has NO foreground React Native Activity attached.
        // Even if CAMERA happens to be pregranted, this must never change a LED.
        module.setFlashlight(true,promise({
            response.set(it); completed.countDown()
        },{
            failure.set(it); completed.countDown()
        }))
        assertTrue("Native flashlight operation did not finish", completed.await(10, TimeUnit.SECONDS))
        assertNull("No hardware action should run without user foreground", response.get())
        assertEquals("NOT_FOREGROUND", failure.get())
    }

    @Test fun flashlightIsOptionalHardwareAndCameraPermissionIsRuntimeControlled() {
        val pkg = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(pkg.requestedPermissions?.contains("android.permission.CAMERA") == true)
        val required=app.packageManager.getSystemAvailableFeatures()
            .firstOrNull { it.name == PackageManager.FEATURE_CAMERA_FLASH }
        // Flash availability is platform-dependent. No LED command is
        // performed in this test; unsupported devices must not break install.
        if (required == null) {
            assertFalse(app.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
        }
    }
}
