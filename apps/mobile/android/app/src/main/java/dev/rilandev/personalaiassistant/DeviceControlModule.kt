package dev.rilandev.personalaiassistant

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.os.BatteryManager
import android.app.StatusBarManager
import android.app.KeyguardManager
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
import org.json.JSONObject

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

    /** This control is callable only from the unlocked foreground Nexa UI.
     * Neither an AI tool nor a Voice provider receives this setter.
     */
    @ReactMethod
    fun setNativeDeviceActionsPaused(paused:Boolean,promise:Promise) {
        if(context.currentActivity?.hasWindowFocus()!=true){
            promise.reject("NOT_FOREGROUND",
                "Open Nexa to pause or resume native device actions")
            return
        }
        val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE)
            as? android.app.KeyguardManager
        if(keyguard?.isKeyguardLocked==true){
            promise.reject("PHONE_LOCKED","Unlock your phone to change this control")
            return
        }
        if(!NexaNativeActionSafety.setPaused(context,paused)){
            promise.reject("SAFETY_STORAGE_FAILED",
                "Could not persist owner device-action safety preference")
            return
        }
        if(paused){
            // The owner may have prepared a target before stopping actions.
            // Never leave a previously reviewed native screen/text selection
            // available for a later Resume toggle to silently reuse.
            NexaScreenActions.reset()
            NexaScreenScroll.cancel()
            NexaMessageInputActions.cancel()
            NexaTextInputActions.cancel()
        }
        promise.resolve(NexaNativeActionSafety.isPaused(context))
    }

    @ReactMethod
    fun nativeDeviceActionsPaused(promise:Promise){
        promise.resolve(NexaNativeActionSafety.isPaused(context))
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

    /** Explicit Chat owner-approved, redacted visible-control observation.
     * No screen contents, editable values, coordinates, tap or submission.
     * The one-shot native task claim and evidence are coordinator-owned.
     */
    @ReactMethod
    fun readVisibleControls(promise:Promise) {
        if(!NexaNativeActionSafety.permitsNewAction(context)){
            promise.reject("OWNER_ACTIONS_PAUSED","Nexa device actions are paused")
            return
        }
        if(context.currentActivity?.hasWindowFocus()!=true) {
            promise.reject("NOT_FOREGROUND","Open Nexa to approve screen inspection")
            return
        }
        if(!ScreenAssistState.connected || ScreenAssistService.active==null) {
            promise.reject("ACCESSIBILITY_DISABLED",
                "Enable Screen Assist in Android Accessibility Settings")
            return
        }
        val result=NexaTaskCoordinator.executeOwnerDeviceRead(
            context,"visible screen controls","chat")
        if(!result.startsWith("Visible non-sensitive controls in ")) {
            promise.reject("SCREEN_OBSERVATION_UNAVAILABLE",
                "No verified external-app control observation was available")
            return
        }
        promise.resolve(result)
    }

    @ReactMethod
    fun openSupportedApp(app: String, promise: Promise) {
        if(!NexaNativeActionSafety.permitsNewAction(context)){
            promise.reject("OWNER_ACTIONS_PAUSED","Nexa device actions are paused")
            return
        }
        if(context.currentActivity?.hasWindowFocus()!=true){
            promise.reject("NOT_FOREGROUND","Open Nexa and explicitly approve the app launch")
            return
        }
        // Resolve one exact launcher label; no guessed app or model-generated
        // package name may reach the shared native dispatch coordinator.
        val choice=NexaPhoneVoiceTools.findExactLaunchableApp(context,app)
        if(choice==null){
            promise.reject("APP_NOT_UNIQUE_OR_INSTALLED",
                "No unique installed app has that visible name")
            return
        }
        when(NexaTaskCoordinator.dispatchOwnerApprovedAppLaunch(
            context,choice.label,choice.packageName,"chat")){
            "accepted" -> {
                val result=Arguments.createMap()
                result.putBoolean("intentDispatched",true)
                result.putString("packageId",choice.packageName)
                result.putString("verification","not_observed")
                promise.resolve(result)
            }
            "uncertain" -> promise.reject("APP_LAUNCH_OUTCOME_UNKNOWN",
                "Android launch outcome is uncertain. Inspect the phone; do not retry automatically.")
            else -> promise.reject("APP_LAUNCH_REFUSED",
                "Native task journal or exact installed-app binding refused the launch")
        }
    }

    /** User-initiated, explicitly approved public Chrome search. Never accepts URLs,
     * schemes, javascript, redirects, or model-supplied coordinates. Intent delivery
     * is NOT evidence of a successful search-results page.
     */
    @ReactMethod
    fun openChromeSearch(query: String, promise: Promise) {
        if(!NexaNativeActionSafety.permitsNewAction(context)){
            promise.reject("OWNER_ACTIONS_PAUSED","Nexa device actions are paused")
            return
        }
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

    /** One owner-approved system navigation action; no notification text is read. */
    @ReactMethod
    fun openNotificationShade(promise: Promise) {
        if(!NexaNativeActionSafety.permitsNewAction(context)){
            promise.reject("OWNER_ACTIONS_PAUSED","Nexa device actions are paused")
            return
        }
        if (context.currentActivity?.hasWindowFocus() != true) {
            promise.reject("NOT_FOREGROUND", "Open Nexa and approve the notification shade action")
            return
        }
        if (!ScreenAssistState.connected || ScreenAssistService.active == null) {
            promise.reject("ACCESSIBILITY_DISABLED", "Enable Screen Assist in Android Accessibility Settings")
            return
        }
        val result = NexaBrowserTaskActions.notificationsApproved(context)
        if (!result.startsWith("Android accepted")) {
            promise.reject("NOTIFICATION_SHADE_FAILED", result)
            return
        }
        val value = Arguments.createMap()
        value.putBoolean("notificationShadeAccepted", true)
        promise.resolve(value)
    }

    @ReactMethod
    fun openCamera(promise: Promise) {
        if(!NexaNativeActionSafety.permitsNewAction(context)){
            promise.reject("OWNER_ACTIONS_PAUSED","Nexa device actions are paused")
            return
        }
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
    /**
     * AgentHarness has already consumed ONE owner-visible action approval.
     * The native controller still independently checks the live Accessibility
     * window, unique target, Pause generation and permanent SQLite claim.
     * We only return evidence for the exact receipt created by this call.
     * Neither accepted gestures nor an unchanged screen mean task success.
     */
    private fun sequencedScreenService(p:Promise):ScreenAssistService? {
        if(!NexaNativeActionSafety.permitsNewAction(context)){
            p.reject("OWNER_ACTIONS_PAUSED","Device actions are paused")
            return null
        }
        if(context.currentActivity?.hasWindowFocus()!=true){
            p.reject("NOT_FOREGROUND","Review this step in the unlocked Nexa Chat")
            return null
        }
        val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if(keyguard?.isKeyguardLocked==true){
            p.reject("PHONE_LOCKED","Unlock your phone before executing a screen step")
            return null
        }
        val service=ScreenAssistService.active
        if(!ScreenAssistState.connected || service==null){
            p.reject("ACCESSIBILITY_DISABLED","Screen Assist is disconnected; no action performed")
            return null
        }
        return service
    }

    private fun finishUiStepAfterObservation(token:String,kind:String,
                                              session:String,call:String,
                                              fingerprint:String,toolId:String,
                                              p:Promise) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val status=NexaUiActionJournal.outcomeFor(context,token,kind)
                val observed=status in setOf("observed_change",
                    "structural_change_observed","window_change_observed",
                    "scroll_event_observed")
                if(observed && !NexaAgentActionAudit.recordReviewedUiEvidence(
                        context,session,call,fingerprint,toolId,
                        "observed_change",token)){
                    p.reject("UI_EVIDENCE_NOT_COMMITTED",
                        "Native UI change was observed but the parent task record is uncertain")
                    return@postDelayed
                }
                val result=Arguments.createMap()
                result.putBoolean("actionAccepted",true)
                result.putString("verification",
                    if(observed)"observed_change" else "not_observed")
                // The observation describes a UI change, NOT successful
                // recipient navigation, business outcome or submitted message.
                result.putBoolean("businessOutcomeVerified",false)
                p.resolve(result)
            }catch(_:Exception){
                p.reject("UI_OUTCOME_UNCERTAIN","Could not verify the claimed step; do not replay it")
            }
        },1250L)
    }

    @ReactMethod
    fun performReviewedScreenTap(label:String,sessionId:String,callId:String,
                                 fingerprint:String,promise:Promise) {
        val service=sequencedScreenService(promise)?:return
        if(!NexaAgentActionAudit.reserveReviewedUiStep(context,
                sessionId,callId,fingerprint,"screen.tap_label",
                JSONObject().put("label",label))){
            promise.reject("REVIEWED_STEP_NOT_CLAIMED",
                "This tap has no consumed owner-approved step mandate")
            return
        }
        try {
            var receipt:String?=null
            val result=NexaScreenLabelTap.approved(service,label,origin="chat",
                claimedToken={receipt=it})
            val token=receipt
            if(token==null || !result.startsWith("Android accepted one tap")){
                promise.reject("UI_TAP_DENIED",
                    "Android rejected or could not uniquely ground the requested tap")
                return
            }
            finishUiStepAfterObservation(token,"tap",sessionId,callId,
                fingerprint,"screen.tap_label",promise)
        }catch(_:Exception){
            promise.reject("UI_TAP_UNCERTAIN","Tap outcome uncertain. No automatic replay.")
        }
    }

    @ReactMethod
    fun performReviewedScreenScroll(direction:String,sessionId:String,callId:String,
                                    fingerprint:String,promise:Promise) {
        if(direction !in setOf("up","down","left","right")){
            promise.reject("INVALID_SCROLL","Unsupported scroll direction")
            return
        }
        val service=sequencedScreenService(promise)?:return
        if(!NexaAgentActionAudit.reserveReviewedUiStep(context,
                sessionId,callId,fingerprint,"screen.scroll",
                JSONObject().put("direction",direction))){
            promise.reject("REVIEWED_STEP_NOT_CLAIMED",
                "This scroll has no consumed owner-approved step mandate")
            return
        }
        try{
            val preflight=NexaScreenScroll.prepare(service,direction)
            if(!preflight.startsWith("Confirm scroll ") &&
                !preflight.startsWith("Confirm one ")){
                promise.reject("SCROLL_TARGET_UNAVAILABLE",
                    "No unique safe scroll target is currently visible")
                return
            }
            var receipt:String?=null
            val outcome=NexaScreenScroll.confirm(service,origin="chat",
                claimedToken={receipt=it})
            val token=receipt
            if(token==null ||
                (!outcome.startsWith("Android accepted one scroll") &&
                 !outcome.startsWith("Android queued one"))){
                promise.reject("SCROLL_OUTCOME_UNCERTAIN",
                    "Android did not verify scroll dispatch. No automatic retry.")
                return
            }
            finishUiStepAfterObservation(token,"scroll_"+direction,
                sessionId,callId,fingerprint,"screen.scroll",promise)
        }catch(_:Exception){
            NexaScreenScroll.cancel()
            promise.reject("SCROLL_OUTCOME_UNCERTAIN",
                "Screen context changed. No automatic retry.")
        }
    }

    /**
     * The owner reviewed the exact label AND text in the harness approval.
     * Reuse the existing two-check native field inspector and one-time
     * text/field/target digest; never press Enter, Submit or Send.
     */
    @ReactMethod
    fun performReviewedScreenText(targetLabel:String,exactText:String,
                                  sessionId:String,callId:String,fingerprint:String,
                                  promise:Promise) {
        val service=sequencedScreenService(promise)?:return
        if(!NexaAgentActionAudit.reserveReviewedUiStep(context,
                sessionId,callId,fingerprint,"screen.fill_text",
                JSONObject().put("targetLabel",targetLabel).put("exactText",exactText))){
            promise.reject("REVIEWED_STEP_NOT_CLAIMED",
                "Text entry has no consumed, exact owner-approved step mandate")
            return
        }
        try {
            if(!NexaTextInputState.stage(targetLabel,exactText)){
                promise.reject("TEXT_REVIEW_INVALID","Unsafe or invalid reviewed text field")
                return
            }
            val preflight=NexaTextInputActions.prepare(service)
            if(!preflight.startsWith("One empty '")){
                NexaTextInputState.clear()
                NexaTextInputActions.cancel()
                promise.reject("TEXT_FIELD_UNVERIFIED",
                    "The reviewed named empty field was not uniquely visible")
                return
            }
            val outcome=NexaTextInputActions.confirm(service)
            val visible=outcome.startsWith("Requested text is visible in the approved field.")
            if(!visible){
                promise.reject("TEXT_OUTCOME_UNCERTAIN",
                    "Reviewed text insertion was not verified. Nothing was submitted.")
                return
            }
            if(!NexaAgentActionAudit.recordReviewedUiEvidence(context,
                    sessionId,callId,fingerprint,"screen.fill_text",
                    "exact_text_visible")){
                promise.reject("TEXT_PARENT_EVIDENCE_FAILED",
                    "Native field verified the text, but could not commit the parent task evidence")
                return
            }
            val result=Arguments.createMap()
            result.putBoolean("submitted",false)
            result.putString("verification","exact_text_visible")
            promise.resolve(result)
        }catch(_:Exception) {
            NexaTextInputState.clear()
            NexaTextInputActions.cancel()
            promise.reject("TEXT_OUTCOME_UNCERTAIN",
                "Text-entry result uncertain. Do not retry or submit automatically.")
        }
    }


    /**
     * Native WhatsApp-only, owner-reviewed contact navigation. One exact
     * recipient hint is sealed in the AgentHarness fingerprint and independently
     * compared to the actual visible WhatsApp header + composer AFTER tap.
     * No contact DB lookup, message entry, Send or automatic retry.
     */
    @ReactMethod
    fun performReviewedWhatsAppChat(recipientHint:String,sessionId:String,
                                    callId:String,fingerprint:String,promise:Promise) {
        val service=sequencedScreenService(promise)?:return
        if(!NexaAgentActionAudit.reserveReviewedUiStep(context,sessionId,callId,
                fingerprint,"screen.open_whatsapp_chat",
                JSONObject().put("recipientHint",recipientHint))){
            promise.reject("CHAT_STEP_NOT_CLAIMED",
                "The exact WhatsApp recipient was not approved for this step")
            return
        }
        try {
            var receipt:String?=null
            val outcome=NexaWhatsAppChatActions.openApproved(service,recipientHint,
                origin="chat",claimedToken={receipt=it})
            val dispatched=outcome.startsWith("Android accepted a uniquely grounded")
            val alreadyOpen=outcome.startsWith("Matching chat header and composer")
            if(!dispatched && !alreadyOpen){
                promise.reject("CHAT_ROW_UNVERIFIED",
                    "No unique, approved WhatsApp chat row was identified")
                return
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val verified=NexaWhatsAppChatActions.verifiedOpen(service,recipientHint)
                    if(!verified){
                        promise.reject("CHAT_HEADER_NOT_VERIFIED",
                            "The intended contact's exact visible header and composer were not verified")
                        return@postDelayed
                    }
                    if(!NexaAgentActionAudit.recordReviewedUiEvidence(context,
                            sessionId,callId,fingerprint,
                            "screen.open_whatsapp_chat","verified_chat_header",receipt)){
                        promise.reject("CHAT_EVIDENCE_NOT_COMMITTED",
                            "WhatsApp header verified but parent step evidence was not committed")
                        return@postDelayed
                    }
                    val result=Arguments.createMap()
                    result.putString("verification","verified_chat_header")
                    result.putBoolean("recipientMatched",true)
                    result.putBoolean("messageComposerVisible",true)
                    result.putBoolean("submitted",false)
                    result.putBoolean("sent",false)
                    promise.resolve(result)
                }catch(_:Exception){
                    promise.reject("CHAT_VERIFICATION_UNCERTAIN",
                        "WhatsApp navigation uncertain; no automatic retry")
                }
            },if(dispatched)1400L else 0L)
        }catch(_:Exception){
            promise.reject("CHAT_NAVIGATION_UNCERTAIN",
                "The WhatsApp chat action was not verified. No automatic retry.")
        }
    }

    /**
     * Owner-reviewed composition ONLY: match actual WhatsApp package,
     * identical recipient header, one empty composer, exact text and durable
     * no-replay claim. Pressing Send is NEVER exposed to the agent.
     */
    @ReactMethod
    fun performReviewedWhatsAppDraft(recipientHint:String,exactText:String,
                                     sessionId:String,callId:String,fingerprint:String,
                                     promise:Promise) {
        val service=sequencedScreenService(promise)?:return
        if(!NexaAgentActionAudit.reserveReviewedUiStep(context,sessionId,callId,
                fingerprint,"screen.compose_whatsapp",
                JSONObject().put("recipientHint",recipientHint)
                    .put("exactText",exactText))){
            promise.reject("MESSAGE_STEP_NOT_CLAIMED",
                "The recipient and exact draft text were not approved for this step")
            return
        }
        try {
            if(!NexaWhatsAppChatActions.verifiedOpen(service,recipientHint) ||
                !NexaMessageDraftState.stage(
                    "whatsapp","com.whatsapp",recipientHint,exactText)){
                promise.reject("MESSAGE_RECIPIENT_UNVERIFIED",
                    "The exact selected chat is not open or draft data is invalid")
                return
            }
            val preflight=NexaMessageInputActions.prepare(service)
            if(!preflight.startsWith("One empty input box is visible")) {
                NexaMessageDraftState.clear()
                NexaMessageInputActions.cancel()
                promise.reject("MESSAGE_COMPOSER_UNVERIFIED",
                    "One safe, empty message input was not uniquely verified")
                return
            }
            val outcome=NexaMessageInputActions.confirm(service,origin="chat")
            if(!outcome.startsWith("Exact prepared text is visible") ||
                !NexaWhatsAppChatActions.verifiedOpen(service,recipientHint)){
                promise.reject("MESSAGE_DRAFT_UNCERTAIN",
                    "The recipient or exact draft text was not verified; no automatic retry or send")
                return
            }
            if(!NexaAgentActionAudit.recordReviewedUiEvidence(context,
                    sessionId,callId,fingerprint,"screen.compose_whatsapp",
                    "exact_text_visible")){
                promise.reject("MESSAGE_EVIDENCE_NOT_COMMITTED",
                    "Exact draft was visible but parent task evidence could not be committed")
                return
            }
            val result=Arguments.createMap()
            result.putString("verification","exact_text_visible")
            result.putBoolean("recipientMatched",true)
            result.putBoolean("submitted",false)
            result.putBoolean("sent",false)
            promise.resolve(result)
        }catch(_:Exception){
            NexaMessageDraftState.clear()
            NexaMessageInputActions.cancel()
            promise.reject("MESSAGE_DRAFT_UNCERTAIN",
                "Reviewed message preparation was not verified. No Send attempted.")
        }
    }

    /** Package-only evidence after an approved launcher Intent. */
    @ReactMethod
    fun observeLaunchedAppPackage(expectedPackage:String,promise:Promise) {
        if(!expectedPackage.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")) ||
            NexaPhoneVoiceTools.uniqueInstalledPackageChoice(context,expectedPackage)==null){
            promise.resolve(false)
            return
        }
        val service=ScreenAssistService.active
        if(!ScreenAssistState.connected || service==null){
            promise.resolve(false)
            return
        }
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
        try{
            promise.resolve(root?.isVisibleToUser==true &&
                root.packageName?.toString()==expectedPackage)
        }catch(_:Exception){promise.resolve(false)}
        finally{try{root?.recycle()}catch(_:Exception){}}
    }

}
