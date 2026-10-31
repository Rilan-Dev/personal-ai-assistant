package dev.rilandev.personalaiassistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Limited physical-world tool: explicit foreground app + OS CAMERA grant.
 * Dispatch success is not treated as verified torch state; confirmation must
 * arrive through Android CameraManager.TorchCallback before success resolves.
 * No camera frames are opened or read.
 */
class FlashlightControlModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context), LifecycleEventListener {
    private val main = Handler(Looper.getMainLooper())

    private class Pending(
        val manager: CameraManager,
        val cameraId: String,
        val targetEnabled: Boolean,
        val promise: Promise,
        val callback: CameraManager.TorchCallback,
        val timeout: Runnable
    )

    private var pending: Pending? = null
    init { context.addLifecycleEventListener(this) }
    override fun getName(): String = "PAAFlashlightControl"

    @ReactMethod fun hasFlashlight(promise: Promise) {
        // System feature check does not open camera or request a permission.
        promise.resolve(context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
    }

    private fun complete(ok: Boolean, code: String = "TORCH_UNCONFIRMED") {
        val running = pending ?: return
        pending = null
        main.removeCallbacks(running.timeout)
        try { running.manager.unregisterTorchCallback(running.callback) } catch (_: Exception) {}
        if (ok) {
            val result = Arguments.createMap()
            result.putBoolean("enabled", running.targetEnabled)
            result.putString("verification", "observed")
            result.putString("source", "android-camera2")
            running.promise.resolve(result)
        } else {
            running.promise.reject(code, "Android could not confirm flashlight state")
        }
    }

    @ReactMethod fun setFlashlight(enabled: Boolean, promise: Promise) {
        main.post {
            if (pending != null) {
                promise.reject("TORCH_BUSY", "Flashlight operation already in progress")
                return@post
            }
            if (context.currentActivity?.hasWindowFocus() != true) {
                promise.reject("NOT_FOREGROUND", "Open the app before changing the flashlight")
                return@post
            }
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                promise.reject("CAMERA_PERMISSION_REQUIRED", "Grant camera permission explicitly before using flashlight")
                return@post
            }
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                promise.reject("FLASH_UNAVAILABLE", "This device does not report a flashlight")
                return@post
            }
            try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    ?: throw IllegalStateException("Camera service unavailable")
                val id = manager.cameraIdList.firstOrNull {
                    manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (id == null) {
                    promise.reject("FLASH_UNAVAILABLE", "No usable camera flashlight")
                    return@post
                }
                // Main-thread callback; once started, all completion paths
                // unsubscribe to avoid a lifetime listener or duplicate resolves.
                val callback = object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabledNow: Boolean) {
                        if (cameraId == id && pending?.cameraId == id &&
                            pending?.targetEnabled == enabledNow) {
                            complete(true)
                        }
                    }
                    override fun onTorchModeUnavailable(cameraId: String) {
                        if (cameraId == id && pending?.cameraId == id) complete(false, "TORCH_UNAVAILABLE")
                    }
                }
                val timeout = Runnable { complete(false, "TORCH_TIMEOUT") }
                pending = Pending(manager, id, enabled, promise, callback, timeout)
                manager.registerTorchCallback(callback, main)
                main.postDelayed(timeout, 3000L)
                manager.setTorchMode(id, enabled)
            } catch (_: CameraAccessException) {
                if (pending != null) complete(false, "TORCH_UNAVAILABLE")
                else promise.reject("TORCH_UNAVAILABLE", "Camera hardware is busy or unavailable")
            } catch (_: SecurityException) {
                if (pending != null) complete(false, "CAMERA_PERMISSION_REQUIRED")
                else promise.reject("CAMERA_PERMISSION_REQUIRED", "Android denied flashlight access")
            } catch (_: Exception) {
                if (pending != null) complete(false, "TORCH_UNAVAILABLE")
                else promise.reject("TORCH_UNAVAILABLE", "Could not initialize flashlight")
            }
        }
    }

    override fun onHostResume() {}
    override fun onHostPause() {
        main.post {
            // We cannot reverse an already dispatched hardware command safely.
            // An uncertain operation must remain claimed in the device journal.
            if (pending != null) complete(false, "TORCH_INTERRUPTED")
        }
    }
    override fun onHostDestroy() {
        main.post { if (pending != null) complete(false, "TORCH_INTERRUPTED") }
    }
}
