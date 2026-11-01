package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Read-only Android setup diagnostics and explicit links to system Settings.
 * No permissions are granted, services enabled, or microphones started here.
 */
class NexaSetupModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName() = "PAANexaSetup"

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun serviceEnabled(target: Class<*>): Boolean = try {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as AccessibilityManager
        manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.resolveInfo?.serviceInfo?.let { info ->
            info.packageName == context.packageName &&
                info.name == target.name
        } == true }
    } catch (_: Exception) { false }

    @ReactMethod fun getSetupStatus(p: Promise) {
        try {
            val manager = if (Build.VERSION.SDK_INT >= 29)
                context.getSystemService(RoleManager::class.java) else null
            val prefs = context.getSharedPreferences("nexa_setup", Context.MODE_PRIVATE)
            val data = Arguments.createMap()
            data.putBoolean("microphone", granted(Manifest.permission.RECORD_AUDIO))
            data.putBoolean("notifications", Build.VERSION.SDK_INT < 33 ||
                granted(Manifest.permission.POST_NOTIFICATIONS))
            data.putBoolean("camera", granted(Manifest.permission.CAMERA))
            data.putBoolean("approximateLocation", granted(Manifest.permission.ACCESS_COARSE_LOCATION))
            data.putBoolean("screenAssist", serviceEnabled(ScreenAssistService::class.java))
            data.putBoolean("launcherNavigation",
                serviceEnabled(NexaLauncherNavigationService::class.java))
            data.putBoolean("assistantRoleAvailable",
                manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true)
            data.putBoolean("overlayGranted", Settings.canDrawOverlays(context))
            data.putBoolean("overlayVisible", NexaToolOverlayService.visible)
            data.putBoolean("assistantSelected",
                manager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true)
            data.putBoolean("setupAcknowledged", prefs.getBoolean("acknowledged_v2", false))
            data.putBoolean("autoStartVoiceOnInvocation", prefs.getBoolean("auto_voice_invocation_v1", false))
            data.putString("versionName", BuildConfig.VERSION_NAME)
            data.putInt("versionCode", BuildConfig.VERSION_CODE)
            data.putInt("sdkLevel", Build.VERSION.SDK_INT)
            data.putString("sourceRevision", BuildConfig.PAA_SOURCE_REVISION)
            p.resolve(data)
        } catch (_: Exception) {
            p.reject("SETUP_STATUS_FAILED", "Could not read Android permission status")
        }
    }

    @ReactMethod fun acknowledgeSetup(p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Review setup in the visible Nexa application")
            return
        }
        val success = context.getSharedPreferences("nexa_setup", Context.MODE_PRIVATE)
            .edit().putBoolean("acknowledged_v2", true).commit()
        if (success) p.resolve(true) else
            p.reject("SETUP_WRITE_FAILED", "Could not save local setup preference")
    }

    /**
     * Starting cloud speech after a system assistant gesture is optional.
     * Save only from the visible Setup UI. No microphone is started here.
     */
    @ReactMethod fun setAutoStartVoiceOnInvocation(enabled: Boolean, p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Change voice activation only from visible Nexa Setup")
            return
        }
        if (enabled && (!granted(Manifest.permission.RECORD_AUDIO) ||
            (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)))) {
            p.reject("VOICE_PERMISSION_MISSING", "Grant voice permissions before enabling automatic conversation")
            return
        }
        if (enabled && AgentJournalModule.openVoiceCredentials(context) == null) {
            p.reject("VOICE_PROVIDER_MISSING", "Configure OpenAI realtime or Sarvam Voice Agent before enabling this")
            return
        }
        val saved = context.getSharedPreferences("nexa_setup", Context.MODE_PRIVATE)
            .edit().putBoolean("auto_voice_invocation_v1", enabled).commit()
        if (saved) p.resolve(true)
        else p.reject("SETUP_WRITE_FAILED", "Could not save voice activation preference")
    }

    @ReactMethod fun openAppSettings(p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Open permissions from the visible Nexa app")
            return
        }
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            p.resolve(true)
        } catch (_: Exception) {
            p.reject("SETTINGS_UNAVAILABLE", "Android App Settings could not be opened")
        }
    }
}
