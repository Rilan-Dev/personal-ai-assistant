package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/**
 * One explicitly approved public search or system navigation step.
 * No generic browser scripting, arbitrary URLs, or notification-content reads.
 * Voice approval remains in RealtimeVoiceService, not in this executor.
 */
internal object NexaBrowserTaskActions {
    private val blocked=Regex("""\b(send|text|message|call|dial|pay|buy|purchase|delete|remove|grant|install|uninstall|password|otp|stop|cancel|without|never|instead)\b""")

    fun safeQuery(raw:String):String? {
        val q=raw.trim().lowercase(Locale.ROOT)
        return q.takeIf {
            it.length in 3..90 && it.matches(Regex("[a-z0-9][a-z0-9 -]{1,89}")) &&
                !blocked.containsMatchIn(it)
        }
    }

    fun openChromeSearchApproved(context:Context,query:String,
                                 origin:String="live-voice"):String {
        if(!NexaNativeActionSafety.permitsNewAction(context))
            return "Nexa device actions are paused. No search launched."
        val q=safeQuery(query) ?:return "Rejected: invalid public Chrome search query."
        val url=Uri.Builder().scheme("https").authority("www.google.com")
            .appendPath("search").appendQueryParameter("q",q).build()
        val intent=Intent(Intent.ACTION_VIEW,url).apply {
            setPackage("com.android.chrome")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return when(NexaApprovedExternalAction.launch(context,intent,
                "com.android.chrome",origin)){
            NexaApprovedExternalAction.Result.ACCEPTED ->
                "Chrome search launch requested for $q. Foreground results are not yet verified."
            NexaApprovedExternalAction.Result.DENIED ->
                "Android refused the reviewed Chrome search. No launch occurred."
            NexaApprovedExternalAction.Result.UNCERTAIN ->
                "Chrome search dispatch is uncertain. Check the phone; no automatic retry."
        }
    }

    /**
     * Open the Android shade only after an owner-visible approval. The device
     * service deliberately excludes System UI and private notification content.
     * Accepted global action is NOT evidence that notifications were read.
     */
    fun notificationsApproved(context:Context, boundService:ScreenAssistService?=ScreenAssistService.active):String {
        if(!NexaNativeActionSafety.permitsNewAction(context))
            return "Nexa device actions are paused. Notification shade not opened."
        val service=boundService
            ?: return "Screen Assist is disabled. Notification shade was not opened."
        val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if(keyguard?.isKeyguardLocked==true)
            return "The phone is locked; notification shade was not opened."
        val ok=try {service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)}
            catch (_:Exception){false}
        return if(ok) "Android accepted the notification shade request. No notifications were read."
            else "Android refused to open the notification shade."
    }

    fun goBackApproved(context:Context):String {
        if(!NexaNativeActionSafety.permitsNewAction(context))
            return "Nexa device actions are paused. No Back navigation occurred."
        val service=ScreenAssistService.active
            ?:return "Screen Assist is disabled. No Back action occurred."
        val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if(keyguard?.isKeyguardLocked==true)
            return "The phone is locked; no Back action occurred."
        val ok=try { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)}
            catch (_:Exception){false}
        return if(ok) "Android accepted one Back navigation request. Verify the resulting screen."
            else "Android rejected the Back navigation request."
    }
}
