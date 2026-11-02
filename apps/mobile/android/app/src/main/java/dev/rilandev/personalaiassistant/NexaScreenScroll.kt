package dev.rilandev.personalaiassistant

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/** User-confirmed accessibility scrolling; no global coordinates or gesture injection. */
internal object NexaScreenScroll {
    private data class Target(
        val token: String, val pkg: String, val windowId: Int,
        val classname: String, val resourceId: String, val bounds: IconBox,
        val forward: Boolean, val expiry: Long
    )
    private val lock=Any()
    private var pending: Target? = null
    fun cancel() = synchronized(lock) { pending=null }

    private fun safeRoot(service: ScreenAssistService): AccessibilityNodeInfo? {
        if (!ScreenAssistState.connected) return null
        val guard=service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (guard?.isKeyguardLocked==true) return null
        val root=service.externalWindowRoot() ?: return null
        val pkg=root.packageName?.toString().orEmpty()
        if (pkg.isBlank() || pkg==service.packageName || pkg=="com.android.systemui" ||
            pkg=="android" || pkg.startsWith("com.google.android.inputmethod")) {
            root.recycle()
            return null
        }
        return root
    }
    fun prepare(service: ScreenAssistService, direction: String): String {
        cancel()
        if(direction!="up" && direction!="down") return "Unsupported scroll direction."
        val root=safeRoot(service) ?: return "No permitted app window to scroll."
        try{
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited=0
            var best: AccessibilityNodeInfo?=null
            var bestArea=0L
            while(queue.isNotEmpty() && visited<200){
                val node=queue.removeFirst()
                visited++
                if (!node.isPassword && !node.isEditable && node.isVisibleToUser){
                    if(node.isScrollable){
                        val rect=Rect()
                        node.getBoundsInScreen(rect)
                        val action=if(direction=="down") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        val actions=node.actionList.any { it.id==action }
                        val area=rect.width().toLong()*rect.height().toLong()
                        if(actions && area>bestArea && area<500_000_000L && rect.left>=0 && rect.top>=0){
                            best=node
                            bestArea=area
                        }
                    }
                    for(i in 0 until node.childCount) node.getChild(i)?.let { queue.addLast(it) }
                }
            }
            val candidate=best ?: return "No visible scrollable container supports that direction."
            val box=Rect()
            candidate.getBoundsInScreen(box)
            val tgt=Target(UUID.randomUUID().toString(),root.packageName.toString(),
                root.windowId,candidate.className?.toString().orEmpty(),
                candidate.viewIdResourceName.orEmpty(),
                IconBox(box.left,box.top,box.right,box.bottom),direction=="down",
                SystemClock.elapsedRealtime()+10_000L)
            synchronized(lock){ pending=tgt }
            return "Confirm scroll ${direction} in ${tgt.pkg} within 10 seconds."
        } finally {root.recycle()}
    }

    fun isPending(): Boolean = synchronized(lock) {
        val t=pending
        if(t==null) false else if(SystemClock.elapsedRealtime()>=t.expiry){
            pending=null
            false
        } else true
    }

    fun confirm(service: ScreenAssistService): String {
        val approved=synchronized(lock){
            val t=pending
            pending=null // claim before dispatch; no retry, even if the OS result is uncertain
            t
        } ?: return "Scroll confirmation expired; no action requested."
        if(SystemClock.elapsedRealtime()>=approved.expiry)
            return "Scroll confirmation expired; no action requested."
        val root=safeRoot(service) ?: return "No permitted window available."
        try{
            if(root.packageName?.toString()!=approved.pkg || root.windowId!=approved.windowId)
                return "The screen changed. Scroll cancelled."
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited=0
            var matches=0
            var target:AccessibilityNodeInfo?=null
            while(queue.isNotEmpty() && visited<200){
                val node=queue.removeFirst()
                visited++
                if(!node.isPassword && !node.isEditable && node.isVisibleToUser){
                    if(node.isScrollable && node.className?.toString()==approved.classname &&
                        node.viewIdResourceName.orEmpty()==approved.resourceId) {
                        val r=Rect()
                        node.getBoundsInScreen(r)
                        if(r.left==approved.bounds.left && r.top==approved.bounds.top &&
                            r.right==approved.bounds.right && r.bottom==approved.bounds.bottom) {
                            matches++
                            target=node
                        }
                    }
                    for(i in 0 until node.childCount)node.getChild(i)?.let{ queue.addLast(it) }
                }
            }
            if(matches!=1 || target==null) return "Scroll target changed or ambiguous. No action requested."
            val direction=if(approved.forward) "scroll_down" else "scroll_up"
            if(!NexaUiActionJournal.claim(service,approved.token,direction,approved.pkg))
                return "Secure action journal unavailable or approval already used. No scroll requested."
            val action=if(approved.forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            val before=NexaUiActionVerifier.fingerprint(root)
            return if(target.performAction(action)) {
                NexaUiActionVerifier.observeAfter(service,approved.token,before)
                if (!NexaUiActionJournal.finish(service,approved.token,"android_accepted"))
                    "Android accepted scroll; journal outcome uncertain. No automatic retry."
                else "Android accepted one scroll request. Check the visible result."
            } else {
                NexaUiActionJournal.finish(service,approved.token,"android_rejected")
                "Android refused the scroll. No automatic retry."
            }
        }catch(_:Exception){return "Scroll outcome uncertain; no automatic retry."}
        finally{root.recycle()}
    }
}
