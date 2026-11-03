package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/** Bounded horizontal gesture only on the owner's current HOME launcher.
 * No arbitrary model coordinates, no gesture on messaging/payment/settings UI.
 */
internal object NexaLauncherSwipe {
    fun isHome(service:ScreenAssistService,root:AccessibilityNodeInfo):Boolean {
        val pkg=root.packageName?.toString().orEmpty()
        if(pkg.isBlank() || pkg==service.packageName) return false
        val home=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolved=try { service.packageManager.resolveActivity(home,0)
            ?.activityInfo?.packageName.orEmpty() }catch(_:Exception){""}
        return pkg==resolved || (pkg=="com.transsion.XOSLauncher" &&
            (resolved.isBlank() || resolved=="android"))
    }

    fun gesture(service:ScreenAssistService,root:AccessibilityNodeInfo,
                direction:String,token:String):String {
        if(direction !in setOf("left","right") || !isHome(service,root))
            return "Horizontal gesture is only available on the verified launcher."
        val bounds=Rect().also {root.getBoundsInScreen(it)}
        if(bounds.width()<300 || bounds.height()<380 || bounds.left<0 || bounds.top<0)
            return "Launcher bounds are unavailable. No gesture attempted."
        val startFraction=if(direction=="left")0.78f else 0.22f
        val endFraction=if(direction=="left")0.22f else 0.78f
        val y=bounds.top+bounds.height()*0.52f
        val path=Path().apply {
            moveTo(bounds.left+bounds.width()*startFraction,y)
            lineTo(bounds.left+bounds.width()*endFraction,y)
        }
        val stroke=GestureDescription.StrokeDescription(path,0,380L)
        val description=GestureDescription.Builder().addStroke(stroke).build()
        val pkg=root.packageName.toString()
        val before=NexaUiActionVerifier.fingerprint(root)
        if(!NexaUiActionJournal.claim(service,token,"scroll_$direction",pkg))
            return "Action journal denied repeat/unapproved gesture."
        NexaUiActionVerifier.begin(token,"scroll",pkg,root.windowId,before)
        val queued=try {
            service.dispatchGesture(description,
                object:AccessibilityService.GestureResultCallback(){
                    override fun onCancelled(gestureDescription:GestureDescription?) {
                        NexaUiActionVerifier.cancel(token)
                        NexaUiActionJournal.markObservation(service,token,"unverified")
                    }
                },Handler(Looper.getMainLooper()))
        }catch(_:Exception){false}
        if(!queued) {
            NexaUiActionVerifier.cancel(token)
            NexaUiActionJournal.finish(service,token,"android_rejected")
            return "Android refused the launcher gesture. No retry."
        }
        NexaUiActionVerifier.observeAfter(service,token,before)
        NexaUiActionJournal.finish(service,token,"android_accepted")
        return "Android queued one $direction launcher swipe; check visible page-change evidence."
    }
}
