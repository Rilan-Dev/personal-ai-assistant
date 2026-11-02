package dev.rilandev.personalaiassistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.util.Locale

/**
 * A small, explicitly spoken, on-device tool gate. No Accessibility scan,
 * contacts, messages, screenshots, or generic command execution.
 *
 * The user's own direct transcript is matched locally. A model is never
 * authorized to execute these tools, and app launch still requires the
 * on-screen one-time approval in the assistant overlay.
 */
internal object NexaPhoneVoiceTools {
    data class AppChoice(val label: String, val packageName: String)
    sealed class Decision {
        data class Immediate(val result: String): Decision()
        data class NeedsApproval(val app: AppChoice): Decision()
    }

    fun deviceFacts(context: Context, utterance: String): String? {
        val q=utterance.lowercase(Locale.ROOT).trim()
            .replace(Regex("[?.!,]+$"),"").trim()
        if(q in setOf("battery","battery status","battery level","battery percentage",
                "check battery","check my battery","what is my battery",
                "what is my battery percentage","how much battery do i have",
                "how much battery is left","what's my battery percentage")) {
            val bm=context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct=bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            return if(pct in 0..100) "Your battery is at $pct percent."
                else "Android did not provide a battery percentage."
        }
        if(q in setOf("storage","phone storage","storage status","device storage",
                "check storage","check my storage","how much storage do i have",
                "how much storage is free","how much storage is left",
                "what is my storage","what is my phone storage")) {
            val stats=StatFs(Environment.getDataDirectory().path)
            val total=stats.totalBytes.coerceAtLeast(1L)
            val free=stats.availableBytes.coerceAtLeast(0L)
            fun gb(bytes:Long)=String.format(Locale.US,"%.1f",bytes/1_000_000_000.0)
            return "Internal storage: ${gb(free)} GB free of ${gb(total)} GB. " +
                "${gb((total-free).coerceAtLeast(0L))} GB used."
        }
        if(q in setOf("device status","check device status","phone status",
                "check my phone status")) {
            return NexaOverlayCommands.status(context).take(320)
        }
        return null
    }

    /**
     * Resolve a single exact app label from Android's visible launcher apps.
     * No QUERY_ALL_PACKAGES, no cloud upload, no automatic launch.
     * Ambiguous labels are denied rather than guessing the wrong package.
     */
    fun findLaunchableApp(context: Context, utterance: String): Decision? {
        val q=utterance.lowercase(Locale.ROOT).trim()
            .replace(Regex("[.?!]+$"),"").trim()
        if(q.length !in 5..150 || Regex("""[;\\n"“”]|\\b(and|then|send|text|message|call|dial|delete|remove|install|settings|without|don't|not)\\b""")
                .containsMatchIn(q)) return null
        val match=Regex("^(?:(?:can|could|would) you (?:please )?|please |i (?:want|need) you to )?(?:open|launch|start|find) (?:my |the )?([a-z0-9][a-z0-9 ._+-]{1,55})(?: app)?$")
            .matchEntire(q) ?: return null
        val target=match.groupValues[1].trim()
        if(target in setOf("app","an app","settings","launcher","the app") || target.length<2)
            return null
        val pm=context.packageManager
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries=try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)
        }catch (_:Exception){emptyList()}
        val candidates=entries.asSequence().mapNotNull { resolved ->
            val pkg=resolved.activityInfo?.packageName.orEmpty()
            if(pkg.isBlank() || pkg==context.packageName)return@mapNotNull null
            val label=resolved.loadLabel(pm)?.toString()?.trim().orEmpty()
            if(label.isBlank() || label.length>80)return@mapNotNull null
            val normalized=label.lowercase(Locale.ROOT)
            if(normalized==target || normalized.removeSuffix(" app")==target)
                AppChoice(label,pkg) else null
        }.distinctBy{it.packageName}.take(3).toList()
        return when(candidates.size) {
            1 -> Decision.NeedsApproval(candidates[0])
            0 -> Decision.Immediate("I could not find an installed app named $target. " +
                "Please tell me its exact visible app name or open your app drawer.")
            else -> Decision.Immediate("More than one app matches $target. " +
                "Please choose the exact app name in Nexa before opening.")
        }
    }

    /** Called ONLY after explicit approval in the visible native overlay. */
    fun launchApproved(context:Context, app:AppChoice):String {
        if(!app.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,180}")))
            return "Invalid Android application identifier."
        val intent=context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?:return "${app.label} is not available to launch on this phone."
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "${app.label} launch requested. Android has not confirmed the screen opened."
        }catch (_:Exception){
            "Android could not open ${app.label}."
        }
    }
}
