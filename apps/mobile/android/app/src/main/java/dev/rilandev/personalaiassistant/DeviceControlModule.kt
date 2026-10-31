package dev.rilandev.personalaiassistant

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Small, explicitly scoped Android bridge. No accessibility, shell, SMS or
 * background control. Dispatching an Intent is not proof of task completion.
 */
class DeviceControlModule(
    private val context: ReactApplicationContext
) : ReactContextBaseJavaModule(context) {

    override fun getName(): String = "PAADeviceControl"

    @ReactMethod
    fun getStatus(promise: Promise) {
        val status = Arguments.createMap()
        status.putString("platform", "android")
        status.putString("adapter", "intent-v1")
        status.putBoolean("cameraIntentSupported", Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).resolveActivity(context.packageManager) != null)
        status.putBoolean("accessibilityEnabled", false)
        status.putBoolean("voiceWakeWordSupported", false)
        promise.resolve(status)
    }

    @ReactMethod
    fun openSupportedApp(app: String, promise: Promise) {
        val packageId = when (app) {
            "whatsapp" -> "com.whatsapp"
            "whatsapp-business" -> "com.whatsapp.w4b"
            else -> {
                promise.reject("UNSUPPORTED_APP", "App not on native allowlist")
                return
            }
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageId)
        if (intent == null) {
            promise.reject("NOT_INSTALLED", "Supported app not installed or not visible")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            val result = Arguments.createMap()
            result.putBoolean("intentDispatched", true)
            result.putString("packageId", packageId)
            result.putString("verification", "not_observed")
            promise.resolve(result)
        } catch (e: SecurityException) {
            promise.reject("APP_LAUNCH_DENIED", "Android denied launching this app")
        } catch (e: ActivityNotFoundException) {
            promise.reject("APP_UNAVAILABLE", "Application launch activity missing")
        }
    }

    @ReactMethod
    fun openCamera(promise: Promise) {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            promise.reject("NO_CAMERA", "No compatible camera application available")
            return
        }
        try {
            context.startActivity(intent)
            val result = Arguments.createMap()
            result.putBoolean("intentDispatched", true)
            result.putString("action", "camera")
            result.putString("verification", "not_observed")
            promise.resolve(result)
        } catch (e: SecurityException) {
            promise.reject("CAMERA_DENIED", "Android denied launching the camera")
        } catch (e: ActivityNotFoundException) {
            promise.reject("NO_CAMERA", "Camera activity missing")
        }
    }
}
