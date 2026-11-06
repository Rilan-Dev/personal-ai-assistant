package dev.rilandev.personalaiassistant

import android.content.Intent
import android.net.Uri
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * User-reviewed WhatsApp composer launch. No WhatsApp UI clicks and NO send.
 * The recipient must originate from the separate Android contact picker
 * (the React UI checks this) and must be reviewed on screen before launch.
 * No raw private contact data, messages or generated URLs are logged.
 */
class NexaWhatsAppComposeModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName() = "PAANexaWhatsAppCompose"

    @ReactMethod fun openReviewedDraft(phone: String, message: String, p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Compose only from the visible Nexa screen")
            return
        }
        if (!NexaNativeActionSafety.permitsNewAction(context)) {
            p.reject("ACTIONS_PAUSED","Resume Nexa device actions before opening the WhatsApp draft")
            return
        }
        // Dialable E.164 digits only. No parsing URLs or accepting encoded query
        // parameters from a model. Country codes are mandatory.
        // Require an international +country-code value. A national-only phone
        // number could silently address the wrong person in another country.
        if (!phone.trim().startsWith("+")) {
            p.reject("COUNTRY_CODE_REQUIRED", "Contact number must begin with +country code")
            return
        }
        val digits = phone.trim().removePrefix("+").replace(Regex("[ ()-]"), "")
        if (digits.length !in 8..15 || digits[0] == '0' || digits.any { !it.isDigit() } ||
            digits.any { it !in '0'..'9' }) {
            p.reject("INVALID_PHONE", "Choose a contact with a valid international number")
            return
        }
        if (message.length !in 1..500 || message.isBlank() ||
            message.any { it == '\u0000' || (it.code < 32 && it != '\n' && it != '\t') }) {
            p.reject("INVALID_MESSAGE", "Message must be 1–500 valid characters")
            return
        }
        if (context.packageManager.getLaunchIntentForPackage("com.whatsapp") == null) {
            p.reject("NOT_INSTALLED", "WhatsApp Messenger is not installed")
            return
        }
        val uri = Uri.Builder()
            .scheme("https").authority("wa.me").appendPath(digits)
            .appendQueryParameter("text", message).build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.whatsapp")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            p.reject("NO_HANDLER", "WhatsApp cannot handle the requested draft link")
            return
        }
        try {
            context.startActivity(intent)
            val result = Arguments.createMap()
            result.putBoolean("intentDispatched", true)
            result.putBoolean("messageSent", false)
            result.putString("verification", "not_observed")
            p.resolve(result)
        } catch (_: Exception) {
            p.reject("DRAFT_FAILED", "Android could not open the WhatsApp draft")
        }
    }
}
