package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import java.util.Locale

/**
 * Small LOCAL overlay command surface. Not an LLM, not a general automation
 * executor. Every recognized instruction is a fixed, auditable user action.
 * No account data, Accessibility events, screen contents or contact data.
 */
internal object NexaOverlayCommands {
    private fun normalize(text: String): String {
        // One cautious grammar normalization step, not arbitrary LLM commands.
        // Natural politeness such as "Can you please open my Brave app?" should
        // not prevent a previously allowlisted app-opening action.
        var value = text.lowercase(Locale.ROOT)
            .replace(Regex("[?!.,]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        for (prefix in listOf("hey nexa ", "can you please ", "could you please ",
                "would you please ", "can you ", "could you ", "please ")) {
            if (value.startsWith(prefix)) value = value.removePrefix(prefix).trim()
        }
        return value
    }
    private fun installed(context: Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun launch(context: Context, pkg: String, app: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return "$app not installed or hidden by Android"
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "$app launch requested · screen opening not verified"
        } catch (_: Exception) { "Android blocked $app launch" }
    }

    fun status(context: Context): String {
        val manager = if (Build.VERSION.SDK_INT >= 29)
            context.getSystemService(RoleManager::class.java) else null
        val assistant = manager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        val mic = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryPolicy = if (Build.VERSION.SDK_INT >= 23 && power != null)
            if (power.isIgnoringBatteryOptimizations(context.packageName)) "exempted by user"
            else "Android-managed"
        else "unknown"
        val (launcher, swipes) = LauncherNavigationState.status()
        return "Overlay: " + (if (Settings.canDrawOverlays(context)) "allowed" else "disabled") +
            " · Assistant: " + (if (assistant) "selected" else "not selected") +
            "\nMicrophone: " + (if (mic) "allowed" else "off") +
            " · Notifications: " + (if (notifications) "allowed" else "off") +
            "\nHey Nexa model: " + (if (NexaWakeWordModule.modelReady(context)) "ready" else "not installed") +
            " · Wake preference: " +
            (if (NexaWakeWordService.enabled(context)) "enabled" else "disabled") +
            " · Wake listener: " + NexaWakeWordService.state +
            "\nLive voice: " + RealtimeVoiceService.status +
            " · Launcher: $launcher ($swipes swipes)" +
            "\nBattery background policy: $batteryPolicy"
    }

    fun handle(context: Context, raw: String): String {
        val text = normalize(raw)
        return when (text) {
            "help", "commands", "what can you do" ->
                "Try: device status, battery, check github, check whatsapp, open github, " +
                "open whatsapp, open brave, find my github app, launcher status. Messages require review."
            "status", "permissions", "device status", "check device status", "privacy",
            "background status", "background voice status", "hey nexa status" -> status(context)
            "battery", "battery level", "check battery", "check battery level" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val percent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (percent in 0..100) "Battery: $percent% (Android BatteryManager)"
                else "Android battery level unavailable"
            }
            "check github", "is github installed", "search github app" ->
                if (installed(context, "com.github.android")) "GitHub is installed · launcher page unknown"
                else "GitHub not installed or not visible"
            "check whatsapp", "is whatsapp installed" ->
                if (installed(context, "com.whatsapp")) "WhatsApp is installed"
                else "WhatsApp not installed or not visible"
            "check whatsapp business" ->
                if (installed(context, "com.whatsapp.w4b")) "WhatsApp Business is installed"
                else "WhatsApp Business not installed or not visible"
            "open github", "open github app", "open my github app" ->
                launch(context, "com.github.android", "GitHub")
            "open brave", "open brave app", "open brave browser", "open my brave",
            "open my brave app", "open my brave browser" ->
                launch(context, "com.brave.browser", "Brave")
            "check brave", "is brave installed" ->
                if (installed(context, "com.brave.browser")) "Brave is installed"
                else "Brave not installed or not visible"
            "open whatsapp", "open my whatsapp" ->
                launch(context, "com.whatsapp", "WhatsApp")
            "open whatsapp business" ->
                launch(context, "com.whatsapp.w4b", "WhatsApp Business")
            "find github", "find github app", "find my github app", "launcher search" ->
                findGitHubOnHome(context)
            "app settings", "open app settings", "privacy settings" ->
                openSettings(context,Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)))
            "battery settings", "background settings", "battery optimization settings" ->
                openSettings(context,Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            "launcher status", "where is github" -> {
                val (state, swipes) = LauncherNavigationState.status()
                "Launcher search: $state · $swipes swipes. Home-screen page is not verified."
            }
            else -> if (text.contains("send") && text.contains("message")) {
                "Not sent. A recipient, message preview and explicit send approval " +
                    "are required. Open full Nexa Tools to prepare a reviewed WhatsApp draft."
            } else "Unsupported local command. Type help for approved on-device tools. " +
                "AI chat remains available in the full Nexa app."
        }
    }

    private fun openSettings(context: Context, intent: Intent): String =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Android settings opened. Only you can change system permissions."
        } catch (_: Exception) {
            "This phone blocked the requested Android Settings screen"
        }

    private fun findGitHubOnHome(context: Context): String {
        if (!installed(context, "com.github.android")) return "GitHub is not installed"
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = context.packageManager.resolveActivity(home, 0)
            ?.activityInfo?.packageName.orEmpty()
        if (pkg.isBlank() || pkg == "android" || pkg == context.packageName ||
            !LauncherNavigationState.begin(pkg))
            return "Enable Nexa Launcher Navigation in Android Accessibility, then retry"
        return try {
            context.startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Launcher search started (max 8 horizontal swipes). " +
                "Ask launcher status after the search. No app icon will be clicked."
        } catch (_: Exception) {
            LauncherNavigationState.stop("launch_failed")
            "Android blocked navigation to the home launcher"
        }
    }
}
