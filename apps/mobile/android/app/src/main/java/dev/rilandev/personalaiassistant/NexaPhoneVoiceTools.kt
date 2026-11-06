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

    private fun normalizedVoice(utterance:String):String {
        var q=utterance.lowercase(Locale.ROOT).trim().trimEnd('?','.','!').trim()
        q=q.removePrefix("hey nexa,").removePrefix("hey nexa ").trim()
        val polite=listOf("can you please ","could you please ","would you please ",
            "can you ","could you ","would you ","please ",
            "i want you to ","i need you to ")
        for(prefix in polite) {
            if(q.startsWith(prefix)){q=q.removePrefix(prefix).trim();break}
        }
        return q
    }

    /** A bounded two-part browser goal; not a general natural-language web agent. */
    fun chromeSearchQuery(utterance:String):String? {
        val q=normalizedVoice(utterance)
        val match=Regex("""^(?:open|launch|start) (?:my |the )?chrome(?: browser)? and (?:search(?: for)?|look up) ([a-z0-9][a-z0-9 -]{1,89})$""")
            .matchEntire(q) ?:return null
        return NexaBrowserTaskActions.safeQuery(match.groupValues[1])
    }
    fun wantsNotificationShade(utterance:String):Boolean {
        val q=normalizedVoice(utterance)
        return q in setOf("open notification bar","open the notification bar",
            "open notifications","show notifications","show my notifications",
            "open notification shade","open the notification shade",
            "pull down notifications","show notification bar")
    }
    fun wantsGoBack(utterance:String):Boolean {
        val q=normalizedVoice(utterance)
        return q in setOf("go back","navigate back","back","press back","tap back",
            "return to previous screen")
    }

    /** Screen content is accessed only after a visible one-time approval. */
    fun wantsScreenOverview(utterance:String):Boolean {
        val q=normalizedVoice(utterance)
        return q in setOf("what icons are on my screen","what icons are on screen",
            "describe the icons on my screen","describe icons on screen",
            "identify my screen icons","identify screen icons",
            "what buttons are on my screen","inspect screen icons",
            "show screen controls","list visible buttons") ||
            (q.contains("screen") && Regex("""\b(icon|icons|buttons|controls)\b""").containsMatchIn(q))
    }
    fun locateIconIntent(utterance:String):String? {
        val q=normalizedVoice(utterance)
        val match=Regex("^(?:where is|find|locate|show me) (?:the |my )?(search|menu|settings|back|close|call|video call|send|attach|camera|microphone) (?:icon|button|control)$")
            .matchEntire(q) ?:return null
        return match.groupValues[1].replace(' ','_')
    }
    fun wantsVisibleText(utterance:String):Boolean {
        val q=normalizedVoice(utterance)
        return q in setOf("read my screen","read the screen","read screen",
            "read screen contents","read my screen contents","what is on my screen",
            "what's on my screen","what does my screen say",
            "tell me what is on my screen","describe what is on my screen",
            "describe the current screen","check my screen","check the screen",
            "inspect my screen","inspect the current app","read the current app",
            "check app","check the app","check current app","check the current app",
            "read current screen","tell me about the current app",
            "what can you see on my screen") ||
            (Regex("""\b(screen|current app|display)\b""").containsMatchIn(q) &&
             Regex("""\b(read|describe|check|inspect|see|show|look at|understand|analy[sz]e)\b""")
                .containsMatchIn(q) &&
             !Regex("""\b(icon|icons|buttons|controls)\b""").containsMatchIn(q))
    }
    fun scrollDirection(utterance:String):String? {
        val q=normalizedVoice(utterance)
        val match=Regex("""^(?:scroll|swipe|slide|move) (?:the |my |this )?(?:screen |page |view )?(up|down|left|right)(?: (?:on |in |of )?(?:my |the |this )?(?:screen|page|view|app))?$""")
            .matchEntire(q)
        if(match!=null)return match.groupValues[1]
        val original=Regex("""^(?:scroll|swipe|slide|move) (up|down|left|right)(?: (?:the |this )?page)?$""")
            .matchEntire(q)
        return original?.groupValues?.get(1)
    }
    fun clickSafeIcon(utterance:String):String? {
        val q=normalizedVoice(utterance)
        val match=Regex("^(?:tap|click|press) (?:on )?(?:the )?(search|menu|settings|back|close)(?: (?:icon|button|control))?$")
            .matchEntire(q) ?:return null
        return match.groupValues[1]
    }
    /** A grounded text-label request, never a free-form XY action. */
    fun tapVisibleLabelIntent(utterance:String):String? {
        val q=normalizedVoice(utterance)
        val match=Regex("""^(?:tap|click|press|select|pick) (?:on )?(?:the )?(?:visible )?([a-z0-9][a-z0-9 _-]{1,45})(?: (?:button|control))?$""")
            .matchEntire(q) ?:return null
        val name=match.groupValues[1].trim()
            .removePrefix("label ").removePrefix("icon ").removePrefix("button ")
            .removeSuffix(" icon").removeSuffix(" label")
            .removeSuffix(" input box").removeSuffix(" search field")
            .removeSuffix(" text field").removeSuffix(" field")
            .removeSuffix(" button").trim()
        if(name in setOf("screen","here","there","anything","somewhere","the screen") ||
            Regex("""\b(send|submit|pay|purchase|buy|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|otp|sign out|logout)\b""")
                .containsMatchIn(name)) return null
        return name.takeIf { it.length in 2..45 }
    }
    fun wantsInstalledApps(utterance:String):Boolean {
        val q=normalizedVoice(utterance)
        return q in setOf("list installed apps","show installed apps",
            "what apps are installed","what apps are on my phone",
            "check my installed apps","list my apps","show my apps",
            "show my installed applications")
    }
    fun wantsScreenHelp(utterance:String):Boolean {
        val q=normalizedVoice(utterance)
        return Regex("""\b(screen|scroll|swipe|tap|click|installed apps|navigate|accessibility)\b""")
            .containsMatchIn(q) &&
            Regex("""\b(can|could|why|how|access|interact|navigate|control|permissions|permission|help)\b""")
            .containsMatchIn(q)
    }
    /** Bounded launcher labels, shared with the provider only after approval. */
    fun installedAppsApproved(context:Context):String {
        return try {
            val query=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val apps=context.packageManager.queryIntentActivities(query,0)
                .mapNotNull { it.loadLabel(context.packageManager)?.toString()?.trim() }
                .filter { it.isNotBlank() && it.length<=60 }
                .distinct().sorted().take(24)
            if(apps.isEmpty()) "No launcher app labels are visible to Nexa on this device."
            else ("Installed launcher apps visible to Nexa: "+apps.joinToString(", ")+
                ". This is a limited launcher list, not all installed packages.").take(950)
        }catch (_:Exception){ "Android did not allow reading installed launcher labels." }
    }

    fun deviceFacts(context: Context, utterance: String): String? {
        // Device vendors can throw from BatteryManager/StatFs and local
        // status APIs. No exception may escape into the Sarvam WS listener.
        return try { deviceFactsSafe(context,utterance) }
        catch (_: Exception) { "Android device information is temporarily unavailable." }
    }

    private fun deviceFactsSafe(context: Context, utterance: String): String? {
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
        if(q.length !in 5..150 || Regex("""[;\n"“”]|\b(and|then|send|text|message|call|dial|delete|remove|install|settings|without|don't|not)\b""")
                .containsMatchIn(q)) return null
        val match=Regex("^(?:(?:can|could|would) you (?:please )?|please |i (?:want|need) you to )?(?:open|launch|start|find) (?:my |the )?([a-z0-9][a-z0-9 ._+-]{1,55})(?: app)?$")
            .matchEntire(q) ?: return null
        val target=match.groupValues[1].trim().removeSuffix(" app").trim()
        if(target in setOf("app","an app","settings","launcher","the app","uh","um","er","huh") || target.length<2)
            return null
        val available=launchableApps(context)
        val exact=available.filter { candidate ->
            val label=candidate.label.lowercase(Locale.ROOT)
            label==target || label.removeSuffix(" app")==target
        }
        // Speech transcription occasionally swaps syllables ("Halite Motion"
        // for "Alight Motion"). Candidate is never launched without approval.
        // Only suggest a unique near-match for a sufficiently long app name.
        val fuzzy=if(exact.isEmpty() && target.length>=8) {
            available.map { candidate ->
                candidate to editDistanceAtMost(candidate.label.lowercase(Locale.ROOT),target,if(target.length>=10)4 else 3)
            }.filter { it.second in 1..(if(target.length>=10)4 else 3) }
                .sortedBy { it.second }
                .let { matches ->
                    if(matches.size==1 || matches.size>1 && matches[0].second<matches[1].second)
                        matches.take(1).map {it.first} else emptyList()
                }
        }else emptyList()
        val candidates=if(exact.isNotEmpty())exact else fuzzy
        return when(candidates.size) {
            1 -> Decision.NeedsApproval(candidates[0])
            0 -> Decision.Immediate("I could not find an installed app named $target. " +
                "Please tell me its exact visible app name or open your app drawer.")
            else -> Decision.Immediate("More than one app matches $target. " +
                "Please choose the exact app name in Nexa before opening.")
        }
    }

    /** Chat launch resolves only one exact, currently installed launcher label. */
    fun findExactLaunchableApp(context: Context, name: String): AppChoice? {
        val target=name.trim().lowercase(Locale.ROOT)
            .let { if(it=="whatsapp-business") "whatsapp business" else it }
        if(target.length !in 2..56 ||
            !target.matches(Regex("[a-z0-9][a-z0-9 ._+-]*"))) return null
        val matches=launchableApps(context).filter { candidate ->
            val label=candidate.label.lowercase(Locale.ROOT)
            label==target || label.removeSuffix(" app")==target
        }
        return matches.singleOrNull()
    }

    /** Revalidate an EXACT native owner-reviewed launcher choice immediately
     * before dispatch. App labels and package bindings are untrusted after
     * the owner looked at them: two duplicate labels are never guessed.
     */
    fun sameInstalledLaunchChoice(context:Context,label:String,pkg:String):Boolean {
        if(label.isBlank() || label.length>80 ||
            !pkg.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,180}")))return false
        val apps=launchableApps(context)
        return apps.count{it.label.equals(label,ignoreCase=true)}==1 &&
            apps.any{it.label==label && it.packageName==pkg}
    }

    private fun launchableApps(context: Context): List<AppChoice> {
        val pm=context.packageManager
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries=try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent,0)
        }catch (_:Exception){emptyList()}
        return entries.asSequence().mapNotNull { resolved ->
            val pkg=resolved.activityInfo?.packageName.orEmpty()
            if(pkg.isBlank() || pkg==context.packageName)return@mapNotNull null
            val label=resolved.loadLabel(pm)?.toString()?.trim().orEmpty()
            if(label.isBlank() || label.length>80)return@mapNotNull null
            AppChoice(label,pkg)
        }.distinctBy{it.packageName to it.label}.toList()
    }

    /** Bounded edit distance, no uncontrolled fuzzy match on short app names. */
    private fun editDistanceAtMost(left:String,right:String,limit:Int):Int {
        if(kotlin.math.abs(left.length-right.length)>limit)return limit+1
        var previous=IntArray(right.length+1){it}
        for(i in left.indices){
            val next=IntArray(right.length+1)
            next[0]=i+1
            for(j in right.indices){
                next[j+1]=minOf(next[j]+1,previous[j+1]+1,
                    previous[j]+if(left[i]==right[j])0 else 1)
            }
            if(next.minOrNull()!!>limit)return limit+1
            previous=next
        }
        return previous[right.length]
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
