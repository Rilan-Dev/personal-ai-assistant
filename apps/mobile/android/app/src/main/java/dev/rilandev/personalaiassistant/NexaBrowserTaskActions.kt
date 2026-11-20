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

    private enum class NavigationReceipt { ACCEPTED, DENIED, UNCERTAIN }

    /**
     * Atomic owner Pause/Voice Stop gate around ONE Android global action.
     * A consumed no-replay receipt is committed BEFORE dispatch. System
     * navigation does not grant reading notification text or hidden controls.
     */
    private fun dispatchNavigation(context:Context,service:ScreenAssistService,
                                   kind:String,packageName:String,
                                   action:Int,origin:String,
                                   expectedWindow:Int?=null,
                                   structure:String?=null,
                                   claimedToken:(String)->Unit={},
                                   surfaceStillValid:()->Boolean={true}):NavigationReceipt{
        if(origin !in setOf("chat","live-voice","assistant-role","overlay") ||
            !ScreenAssistState.connected || ScreenAssistService.active!==service) {
            if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind precondition=service_or_origin")
            return NavigationReceipt.DENIED
        }
        val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if(keyguard?.isKeyguardLocked==true) {
            if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind precondition=keyguard")
            return NavigationReceipt.DENIED
        }
        val generation=NexaNativeActionSafety.approvalFence(context)
            ?:run {
                if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind precondition=owner_fence")
                return NavigationReceipt.DENIED
            }
        val token=java.util.UUID.randomUUID().toString()
        if(!NexaUiActionJournal.claim(context,token,kind,packageName,origin=origin)) {
            if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind claim=denied")
            return NavigationReceipt.DENIED
        }
        try { claimedToken(token) } catch (_:Exception) {
            if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind claim_callback=failed")
            return NavigationReceipt.DENIED
        }
        return try{
            val accepted=NexaNativeActionSafety.dispatchWithFence(context,generation) {
                if(!ScreenAssistState.connected ||
                    ScreenAssistService.active!==service ||
                    keyguard?.isKeyguardLocked==true) {
                    if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind dispatch_fence=service_or_keyguard")
                    return@dispatchWithFence false
                }
                val surfaceValid=try{surfaceStillValid()}catch(_:Exception){false}
                if(!surfaceValid) {
                    if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind dispatch_fence=surface_changed")
                    return@dispatchWithFence false
                }
                if(expectedWindow!=null){
                    val root=service.externalWindowRoot()
                        ?:run {
                            if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind dispatch_fence=window_missing")
                            return@dispatchWithFence false
                        }
                    val unchanged=try{
                        root.packageName?.toString()==packageName &&
                            root.windowId==expectedWindow &&
                            (structure==null ||
                                NexaUiActionVerifier.fingerprint(root)==structure)
                    }catch(_:Exception){false}
                    finally{try{root.recycle()}catch(_:Exception){}}
                    if(!unchanged) {
                        if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind dispatch_fence=window_changed")
                        return@dispatchWithFence false
                    }
                }
                val result=service.performGlobalAction(action)
                if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind android_returned=${result}")
                result
            }
            when(accepted){
                true-> {
                    val committed=NexaUiActionJournal.finish(context,token,"android_accepted")
                    if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind journal_committed=${committed}")
                    if(committed)NavigationReceipt.ACCEPTED else NavigationReceipt.UNCERTAIN
                }
                false,null->{
                    val committed=NexaUiActionJournal.finish(context,token,"android_rejected")
                    if(BuildConfig.DEBUG)android.util.Log.d("NexaNamedNavigation","$kind dispatch=${if(accepted==null)"owner_fence_blocked" else "rejected"} journal_committed=${committed}")
                    if(accepted==null)NavigationReceipt.UNCERTAIN else NavigationReceipt.DENIED
                }
            }
        }catch(_:Exception){
            NexaUiActionJournal.finish(context,token,"unknown")
            NavigationReceipt.UNCERTAIN
        }
    }

    /** One named system navigation action, bound to the current external window. */
    fun keyActionApproved(context:Context,name:String,
                          boundService:ScreenAssistService?=ScreenAssistService.active,
                          origin:String="chat",
                          claimedToken:(String)->Unit={},
                          ownerWindowFocused:Boolean=false,
                          ownerWindowStillFocused:()->Boolean={ownerWindowFocused}):String {
        if(name !in setOf("back","home","notifications"))
            return "Rejected: unsupported named key action."
        if(!NexaNativeActionSafety.permitsNewAction(context))
            return "Nexa device actions are paused. No key action occurred."
        val service=boundService
            ?:return "Screen Assist is disabled. No key action occurred."
        if(name=="notifications")
            return notificationsApproved(context,service,origin,claimedToken)
        val root=service.externalWindowRoot()
        if(root==null) {
            val shadeVisible=service.isNotificationShadeVisible()
            val fallbackPackage=NexaKeyActionSurfacePolicy.fallbackPackage(
                name,context.packageName,ownerWindowFocused,shadeVisible)
                ?:return "No verified Nexa or notification-shade window is available for the named key action."
            val (fallbackKind,fallbackAction)=if(name=="home")
                "nav_home" to AccessibilityService.GLOBAL_ACTION_HOME
            else "nav_back" to AccessibilityService.GLOBAL_ACTION_BACK
            val stillOnApprovedSurface:()->Boolean=if(fallbackPackage=="com.android.systemui")
                ({service.isNotificationShadeVisible()})
            else ownerWindowStillFocused
            return when(dispatchNavigation(context,service,fallbackKind,fallbackPackage,
                    fallbackAction,origin,claimedToken=claimedToken,
                    surfaceStillValid=stillOnApprovedSurface)) {
                NavigationReceipt.ACCEPTED -> "Android accepted one named $name action. Verify the resulting screen."
                NavigationReceipt.DENIED -> "Android refused the named $name action. No retry was attempted."
                NavigationReceipt.UNCERTAIN -> "The named $name action outcome is uncertain. Inspect the phone; do not retry."
            }
        }
        val packageName:String
        val window:Int
        val structure:String
        try {
            packageName=root.packageName?.toString().orEmpty()
            window=root.windowId
            structure=NexaUiActionVerifier.fingerprint(root)
        } catch (_:Exception) {
            return "Android could not safely inspect the key action target."
        } finally { try { root.recycle() } catch (_:Exception) {} }
        if(packageName==context.packageName || packageName=="android" ||
            packageName=="com.android.systemui" || window<0 ||
            !packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")))
            return "The key action requires one visible external app."
        val (kind,action)=when(name) {
            "back" -> "nav_back" to AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> "nav_home" to AccessibilityService.GLOBAL_ACTION_HOME
            else -> "nav_notifications" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        }
        // Back depends on the exact current page, so keep its full-screen
        // freshness check. Home and Notifications have the same meaning across
        // content changes; bind those global actions to the same app window.
        return when(dispatchNavigation(context,service,kind,packageName,action,
                origin,window,structure.takeIf { name=="back" },claimedToken)) {
            NavigationReceipt.ACCEPTED -> "Android accepted one named $name action. Verify the resulting screen."
            NavigationReceipt.DENIED -> "Android refused the named $name action. No retry was attempted."
            NavigationReceipt.UNCERTAIN -> "The named $name action outcome is uncertain. Inspect the phone; do not retry."
        }
    }

    /** Opens the shade, not its private notification contents. */
    fun notificationsApproved(context:Context,
                              boundService:ScreenAssistService?=ScreenAssistService.active,
                              origin:String="live-voice",
                              claimedToken:(String)->Unit={}):String {
        if(!NexaNativeActionSafety.permitsNewAction(context))
            return "Nexa device actions are paused. Notification shade not opened."
        val service=boundService
            ?:return "Screen Assist is disabled. Notification shade was not opened."
        return when(dispatchNavigation(context,service,"nav_notifications",
                "com.android.systemui",
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,origin,
                claimedToken=claimedToken)){
            NavigationReceipt.ACCEPTED ->
                "Android accepted the notification shade request. No notifications were read."
            NavigationReceipt.DENIED ->
                "Android refused the reviewed notification shade action. No dispatch."
            NavigationReceipt.UNCERTAIN ->
                "Notification shade outcome uncertain. Inspect the phone; no automatic retry."
        }
    }

    /** One Back only from the unchanged, visible third-party app/window. */
    fun goBackApproved(context:Context,origin:String="live-voice"):String {
        if(!NexaNativeActionSafety.permitsNewAction(context))
            return "Nexa device actions are paused. No Back navigation occurred."
        val service=ScreenAssistService.active
            ?:return "Screen Assist is disabled. No Back action occurred."
        val root=service.externalWindowRoot()
            ?:return "No permitted external screen is visible for Back navigation."
        val pkg:String
        val window:Int
        val structure:String
        try {
            pkg=root.packageName?.toString().orEmpty()
            window=root.windowId
            structure=NexaUiActionVerifier.fingerprint(root)
        }catch(_:Exception){
            return "Android could not safely inspect Back target. No action."
        }finally {try{root.recycle()}catch(_:Exception){}}
        if(pkg==context.packageName || pkg=="android" ||
            pkg=="com.android.systemui" || window<0 ||
            !pkg.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")))
            return "Back navigation requires one visible external app."
        return when(dispatchNavigation(context,service,"nav_back",pkg,
                AccessibilityService.GLOBAL_ACTION_BACK,origin,window,structure)){
            NavigationReceipt.ACCEPTED ->
                "Android accepted one Back navigation request. Verify the resulting screen."
            NavigationReceipt.DENIED ->
                "Android rejected Back after the target changed. No dispatch."
            NavigationReceipt.UNCERTAIN ->
                "Back navigation outcome uncertain. Inspect the phone; no automatic retry."
        }
    }
}
