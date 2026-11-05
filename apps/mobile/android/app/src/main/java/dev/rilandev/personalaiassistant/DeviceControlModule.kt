package dev.rilandev.personalaiassistant

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.os.BatteryManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import java.util.function.Consumer
import android.provider.MediaStore
import android.net.Uri
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

    /**
     * Android framework BatteryManager is a local read-only API. No phone
     * identifiers, app usage, location, or history are exposed by this tool.
     */
    @ReactMethod
    fun getBatteryStatus(promise: Promise) {
        try {
            val service = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (service == null) {
                promise.reject("BATTERY_UNAVAILABLE", "Battery service unavailable")
                return
            }
            val percent = service.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (percent !in 0..100) {
                promise.reject("BATTERY_UNAVAILABLE", "Battery capacity not reported")
                return
            }
            val state = Arguments.createMap()
            state.putInt("levelPercent", percent)
            state.putBoolean("charging", service.isCharging)
            state.putString("source", "android-battery-manager")
            promise.resolve(state)
        } catch (_: Exception) {
            promise.reject("BATTERY_UNAVAILABLE", "Could not read battery status")
        }
    }

    /** Local read-only storage measurements; no file paths or app data. */
    @ReactMethod
    fun getStorageStatus(promise:Promise) {
        try {
            val stat=android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total=stat.totalBytes
            val free=stat.availableBytes
            if(total<=0 || free<0 || free>total) {
                promise.reject("STORAGE_UNAVAILABLE","Android storage values invalid")
                return
            }
            val result=Arguments.createMap()
            result.putDouble("totalGb",total.toDouble()/1_000_000_000.0)
            result.putDouble("freeGb",free.toDouble()/1_000_000_000.0)
            result.putDouble("usedGb",(total-free).toDouble()/1_000_000_000.0)
            result.putString("source","android-statfs")
            promise.resolve(result)
        }catch (_:Exception){
            promise.reject("STORAGE_UNAVAILABLE","Could not read Android storage")
        }
    }

    @ReactMethod
    fun getStatus(promise: Promise) {
        val status = Arguments.createMap()
        status.putString("platform", "android")
        status.putString("adapter", "intent-v1")
        status.putBoolean("cameraIntentSupported", Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).resolveActivity(context.packageManager) != null)
        // Report the real connected Accessibility service, not a static flag.
        // Installed model and microphone grant indicate wake capability, not
        // that a recording session was started.
        status.putBoolean("accessibilityEnabled",
            ScreenAssistState.connected && ScreenAssistService.active!=null)
        status.putBoolean("voiceWakeWordSupported",
            NexaWakeWordModule.modelReady(context) &&
                context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==
                    android.content.pm.PackageManager.PERMISSION_GRANTED)
        promise.resolve(status)
    }

    /**
     * User-initiated OS prompt. It only adds a launcher tile, not a microphone
     * or privileged assistant service. Android 12 users can add it manually.
     */
    @ReactMethod
    fun requestAssistantTile(promise: Promise) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            promise.resolve("manual_add_required")
            return
        }
        if (context.currentActivity?.hasWindowFocus() != true) {
            promise.reject("NOT_FOREGROUND", "Open the assistant before adding its tile")
            return
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            promise.reject("TILE_UNAVAILABLE", "Quick Settings tile management is unavailable")
            return
        }
        try {
            manager.requestAddTileService(
                ComponentName(context, AssistantTileService::class.java),
                context.getString(R.string.assistant_tile_label),
                Icon.createWithResource(context, R.drawable.ic_assistant_tile),
                context.mainExecutor,
                Consumer { code: Int ->
                    val result = when (code) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "added"
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "already_added"
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "declined"
                        else -> "unavailable"
                    }
                    promise.resolve(result)
                }
            )
        } catch (_: Exception) {
            promise.reject("TILE_UNAVAILABLE", "System could not request Quick Settings tile")
        }
    }

    /** Narrow package check. Android does not expose the launcher icon's home page. */
    @ReactMethod
    fun checkSupportedApp(app: String, promise: Promise) {
        val packageId = when (app) {
            "github" -> "com.github.android"
            "whatsapp" -> "com.whatsapp"
            "whatsapp-business" -> "com.whatsapp.w4b"
            else -> {
                promise.reject("UNSUPPORTED_APP", "This app is not yet allowlisted")
                return
            }
        }
        val result = Arguments.createMap()
        result.putString("app", app)
        result.putString("packageId", packageId)
        result.putBoolean("installed",
            context.packageManager.getLaunchIntentForPackage(packageId) != null)
        result.putString("homeScreenPage", "unknown")
        result.putString("verification", "package_manager_only")
        promise.resolve(result)
    }

    @ReactMethod
    fun openSupportedApp(app: String, promise: Promise) {
        if(context.currentActivity?.hasWindowFocus()!=true){
            promise.reject("NOT_FOREGROUND","Open Nexa and explicitly approve the app launch")
            return
        }
        val packageId = when (app) {
            "whatsapp" -> "com.whatsapp"
            "whatsapp-business" -> "com.whatsapp.w4b"
            "github" -> "com.github.android"
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

    /** User-initiated, explicitly approved public Chrome search. Never accepts URLs,
     * schemes, javascript, redirects, or model-supplied coordinates. Intent delivery
     * is NOT evidence of a successful search-results page.
     */
    @ReactMethod
    fun openChromeSearch(query: String, promise: Promise) {
        if(context.currentActivity?.hasWindowFocus()!=true){
            promise.reject("NOT_FOREGROUND","Open Nexa and explicitly approve Chrome search")
            return
        }
        val clean=query.trim().lowercase(java.util.Locale.ROOT)
        if(clean.length !in 3..90 || clean!=query ||
            !clean.matches(Regex("[a-z0-9][a-z0-9 -]{1,89}")) ||
            Regex("""\b(send|text|message|call|dial|pay|buy|purchase|delete|remove|grant|install|uninstall|password|otp|stop|cancel|without|never|instead)\b""")
                .containsMatchIn(clean)) {
            promise.reject("INVALID_SEARCH", "Search requires a short public text query")
            return
        }
        val url=Uri.Builder().scheme("https").authority("www.google.com")
            .appendPath("search").appendQueryParameter("q",clean).build()
        val intent=Intent(Intent.ACTION_VIEW,url).apply {
            setPackage("com.android.chrome")
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            val result=Arguments.createMap()
            result.putBoolean("intentDispatched",true)
            result.putString("packageId","com.android.chrome")
            result.putString("action","chrome_search")
            result.putString("verification","not_observed")
            promise.resolve(result)
        } catch (_:ActivityNotFoundException) {
            promise.reject("CHROME_UNAVAILABLE","Chrome is not installed or cannot handle a search")
        } catch (_:SecurityException) {
            promise.reject("CHROME_DENIED","Android denied the Chrome search")
        } catch (_:Exception) {
            promise.reject("CHROME_UNAVAILABLE","Android could not open Chrome")
        }
    }

    @ReactMethod
    fun openCamera(promise: Promise) {
        if(context.currentActivity?.hasWindowFocus()!=true){
            promise.reject("NOT_FOREGROUND","Open Nexa and explicitly approve camera launch")
            return
        }
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
