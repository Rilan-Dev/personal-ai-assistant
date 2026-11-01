package dev.rilandev.personalaiassistant

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/** Explicit owner actions, not part of the AI provider tool registry. */
class NexaToolOverlayModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName() = "PAANexaToolOverlay"

    @ReactMethod fun getStatus(p: Promise) {
        val result = Arguments.createMap()
        result.putBoolean("granted", Settings.canDrawOverlays(context))
        result.putBoolean("visible", NexaToolOverlayService.visible)
        p.resolve(result)
    }

    @ReactMethod fun requestPermission(p: Promise) {
        val activity = context.currentActivity
        if (activity == null || activity.isFinishing ||
            context.lifecycleState != com.facebook.react.common.LifecycleState.RESUMED) {
            p.reject("NOT_FOREGROUND", "Open Nexa to enable display-over-other-apps access")
            return
        }
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            // On Android 11+, the OS may ignore the package URI.
            if (android.os.Build.VERSION.SDK_INT < 30) {
                intent.data = Uri.parse("package:" + context.packageName)
            }
            activity.startActivity(intent)
            p.resolve("settings_opened")
        } catch (_: Exception) {
            p.reject("OVERLAY_SETTINGS_UNAVAILABLE",
                "Your Android device has no overlay permission settings activity")
        }
    }

    @ReactMethod fun show(p: Promise) {
        val activity = context.currentActivity
        if (activity == null || activity.isFinishing ||
            context.lifecycleState != com.facebook.react.common.LifecycleState.RESUMED) {
            p.reject("NOT_FOREGROUND", "Show the floating tool bubble only from visible Nexa")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            p.reject("OVERLAY_PERMISSION_MISSING",
                "Enable Display over other apps in Android Settings first")
            return
        }
        try {
            context.startService(Intent(context, NexaToolOverlayService::class.java).apply {
                action = NexaToolOverlayService.ACTION_SHOW
            })
            p.resolve(true) // Intent dispatched; service reports actual visibility later
        } catch (_: Exception) {
            p.reject("OVERLAY_START_DENIED", "Android could not show Nexa's tool bubble")
        }
    }

    @ReactMethod fun hide(p: Promise) {
        try {
            context.stopService(Intent(context, NexaToolOverlayService::class.java))
            p.resolve(true)
        } catch (_: Exception) {
            p.reject("OVERLAY_STOP_DENIED", "Could not hide the tool bubble")
        }
    }

    @ReactMethod fun consumePendingTab(p: Promise) {
        val prefs = context.getSharedPreferences("nexa_overlay", android.content.Context.MODE_PRIVATE)
        val tab = prefs.getString("pending_tab", "") ?: ""
        prefs.edit().remove("pending_tab").apply()
        p.resolve(if (tab in setOf("chat","voice","tools")) tab else "")
    }
}
