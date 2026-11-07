package dev.rilandev.personalaiassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONObject

/**
 * Known messaging apps only. This never enumerates an address book or
 * silently treats a remembered app as an authenticated recipient.
 */
internal object NexaMessageChannels {
    private val known = linkedMapOf(
        "whatsapp" to Pair("WhatsApp", "com.whatsapp"),
        "whatsapp-business" to Pair("WhatsApp Business", "com.whatsapp.w4b"),
        "imo" to Pair("imo", "com.imo.android.imoim"),
        "botim" to Pair("BOTIM", "im.thebot.messenger")
    )
    fun installed(context: Context): List<Triple<String, String, String>> {
        val options = mutableListOf<Triple<String,String,String>>()
        for ((id, detail) in known) {
            if (context.packageManager.getLaunchIntentForPackage(detail.second) != null) {
                options.add(Triple(id, detail.first, detail.second))
            }
        }
        val sms = try { Telephony.Sms.getDefaultSmsPackage(context) } catch (_: Exception) { null }
        if (!sms.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:12345678")).setPackage(sms)
            if (intent.resolveActivity(context.packageManager) != null)
                options.add(Triple("sms", "SMS / Messages", sms))
        }
        return options
    }
    fun installedPackage(context: Context, id: String): String? =
        installed(context).firstOrNull { it.first == id }?.third

    fun permitsManualComposer(id: String): Boolean =
        id == "imo" || id == "botim" || id == "whatsapp-business" || id == "whatsapp"
}

/** Ephemeral and single-use; message body never enters SharedPreferences. */
internal object NexaMessageDraftState {
    data class Draft(
        val appId: String, val packageName: String, val recipientHint: String,
        val message: String, val expiresAt: Long
    )
    private val lock = Any()
    private var staged: Draft? = null
    fun clear() = synchronized(lock) { staged = null }
    fun stage(appId: String, pkg: String, hint: String, message: String): Boolean =
        synchronized(lock) {
            clear()
            if (!NexaMessageChannels.permitsManualComposer(appId) ||
                pkg.length !in 3..150 || !pkg.matches(Regex("[a-zA-Z0-9_.]+")) ||
                !hint.matches(Regex("[\\p{L}][\\p{L}\\p{N} '-]{1,49}")) ||
                message.length !in 1..500 || message.isBlank() ||
                message.any { it == '\u0000' || (it.code < 32 && it != '\n' && it != '\t') })
                return@synchronized false
            staged = Draft(appId,pkg,hint,message,
                android.os.SystemClock.elapsedRealtime() + 90_000L)
            true
        }
    fun peek(): Draft? = synchronized(lock) {
        val x=staged
        if(x != null && android.os.SystemClock.elapsedRealtime() >= x.expiresAt) {
            staged=null
            null
        } else x
    }
    fun take(): Draft? = synchronized(lock) {
        val x=peek()
        staged=null
        x
    }
}

class NexaMessagingModule(private val context: ReactApplicationContext):
    ReactContextBaseJavaModule(context) {
    override fun getName(): String = "PAANexaMessaging"

    @ReactMethod fun installedChannels(p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND","Check installed messaging apps from Nexa")
            return
        }
        try {
            val result=Arguments.createArray()
            NexaMessageChannels.installed(context).forEach { (id,label,pkg) ->
                val item=Arguments.createMap()
                item.putString("id",id)
                item.putString("label",label)
                item.putString("packageName",pkg)
                result.pushMap(item)
            }
            p.resolve(result)
        } catch (_: Exception) {
            p.reject("CHANNELS_UNAVAILABLE","Could not check the supported messaging applications")
        }
    }

    /** Only opens the Android SMS composer. No SMS is ever sent by Nexa. */
    @ReactMethod fun openSmsDraft(phone: String, message: String, p: Promise) {
        if (context.currentActivity?.hasWindowFocus()!=true) {
            p.reject("NOT_FOREGROUND","Open SMS draft from visible Nexa review")
            return
        }
        if (!NexaNativeActionSafety.permitsNewAction(context)) {
            p.reject("ACTIONS_PAUSED","Resume Nexa device actions before opening an SMS draft")
            return
        }
        val digits = phone.trim()
        if (!digits.matches(Regex("\\+[1-9][0-9]{7,14}")) ||
            message.length !in 1..500 || message.isBlank() ||
            message.any { it == '\u0000' || (it.code < 32 && it != '\n' && it != '\t') }) {
            p.reject("INVALID_DRAFT","Select a +country-code contact and a valid message")
            return
        }
        val pkg=NexaMessageChannels.installedPackage(context,"sms")
        if(pkg==null) {
            p.reject("SMS_UNAVAILABLE","No supported default SMS application is available")
            return
        }
        try {
            val intent=Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",digits,null))
                .setPackage(pkg)
                .putExtra("sms_body",message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if(intent.resolveActivity(context.packageManager)==null) {
                p.reject("NO_HANDLER","Default Messages app cannot open a draft")
                return
            }
            when(NexaApprovedExternalAction.launch(context,intent,pkg,"chat")){
                NexaApprovedExternalAction.Result.ACCEPTED->{
                    val result=Arguments.createMap()
                    result.putBoolean("intentDispatched",true)
                    result.putBoolean("messageSent",false)
                    result.putString("verification","not_observed")
                    p.resolve(result)
                }
                NexaApprovedExternalAction.Result.DENIED->
                    p.reject("SMS_DRAFT_DENIED",
                        "The selected SMS app changed or device actions were paused. No draft opened.")
                NexaApprovedExternalAction.Result.UNCERTAIN->
                    p.reject("SMS_DRAFT_UNCERTAIN",
                        "SMS composer launch is uncertain. Inspect the phone; do not retry automatically.")
            }
        } catch (_: Exception) {
            p.reject("SMS_DRAFT_FAILED","Android could not open the SMS draft")
        }
    }

    /**
     * IMO/BOTIM/WhatsApp Business have no trusted recipient deep-link guarantee.
     * Open only the selected
     * installed app, then require the owner to navigate to the right chat,
     * inspect its title and confirm exact text insertion in the overlay.
     */
    @ReactMethod fun stageManualComposer(appId: String, recipient: String,
        message: String, p: Promise) {
        if (context.currentActivity?.hasWindowFocus()!=true) {
            p.reject("NOT_FOREGROUND","Review the message in Nexa first")
            return
        }
        if (!NexaNativeActionSafety.permitsNewAction(context)) {
            p.reject("ACTIONS_PAUSED","Resume Nexa device actions before opening a messaging app")
            return
        }
        if (!ScreenAssistState.connected || ScreenAssistService.active==null ||
            !Settings.canDrawOverlays(context)) {
            p.reject("SCREEN_GRANT_REQUIRED","Enable Nexa Screen Assist and floating bubble first")
            return
        }
        val pkg=if(NexaMessageChannels.permitsManualComposer(appId))
            NexaMessageChannels.installedPackage(context,appId) else null
        if(pkg==null || !NexaMessageDraftState.stage(appId,pkg,recipient,message)) {
            p.reject("INVALID_APP_OR_MESSAGE","Choose an installed messaging app and review a valid message")
            return
        }
        val launch=context.packageManager.getLaunchIntentForPackage(pkg)
        if(launch==null) {
            NexaMessageDraftState.clear()
            p.reject("NOT_INSTALLED","Chosen messaging app is not launchable")
            return
        }
        try {
            context.startService(Intent(context,NexaToolOverlayService::class.java).apply {
                action=NexaToolOverlayService.ACTION_SHOW
            })
            when(NexaApprovedExternalAction.launch(context,launch,pkg,"chat")){
                NexaApprovedExternalAction.Result.ACCEPTED->{
                    val result=Arguments.createMap()
                    result.putBoolean("intentDispatched",true)
                    result.putBoolean("messageSent",false)
                    result.putString("verification","not_observed")
                    p.resolve(result)
                }
                NexaApprovedExternalAction.Result.DENIED->{
                    NexaMessageDraftState.clear()
                    p.reject("COMPOSER_DENIED",
                        "The selected messaging app changed or actions were paused. Nothing sent.")
                }
                NexaApprovedExternalAction.Result.UNCERTAIN->{
                    NexaMessageDraftState.clear()
                    p.reject("COMPOSER_UNCERTAIN",
                        "Messaging app launch is uncertain. Check the phone; no automatic retry or Send.")
                }
            }
        } catch (_: Exception) {
            NexaMessageDraftState.clear()
            p.reject("COMPOSER_FAILED","Could not open selected messaging app")
        }
    }

    /** A user-reviewed generic field (search, notes, description).
     * No arbitrary text entry from a model, no automatic IME submit. */
    @ReactMethod fun stageReviewedTextField(label: String, text: String, p: Promise) {
        if(context.currentActivity?.hasWindowFocus()!=true) {
            p.reject("NOT_FOREGROUND","Open the visible Nexa review before typing")
            return
        }
        if(!ScreenAssistState.connected || ScreenAssistService.active==null ||
            !Settings.canDrawOverlays(context)){
            p.reject("TEXT_GRANT_REQUIRED","Enable Screen Assist and overlay permission first")
            return
        }
        if(!NexaTextInputState.stage(label,text)){
            p.reject("INVALID_TEXT_FIELD","Choose a safe named field and up to 500 characters")
            return
        }
        try {
            context.startService(Intent(context,NexaToolOverlayService::class.java).apply{
                action=NexaToolOverlayService.ACTION_SHOW
            })
            p.resolve(true)
        }catch(_:Exception){
            NexaTextInputState.clear()
            p.reject("TEXT_ENTRY_UNAVAILABLE","Unable to present Nexa field-entry approval")
        }
    }

    @ReactMethod fun cancelReviewedTextField(p: Promise) {
        NexaTextInputActions.cancel()
        NexaTextInputState.clear()
        p.resolve(true)
    }

    @ReactMethod fun discardManualComposer(p: Promise) {
        NexaMessageInputActions.cancel()
        NexaMessageDraftState.clear()
        p.resolve(true)
    }
}
