package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Rect
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.UUID

/** User-confirmed semantic scrolling with a bounded screen-relative fallback for opaque app views. */
internal object NexaScreenScroll {
    private data class Target(
        val token: String, val pkg: String, val windowId: Int,
        val classname: String, val resourceId: String, val bounds: IconBox,
        val direction: String, val actionId: Int,
        val gesturePlan: NexaScreenScrollGesturePlan? = null,
        val expiry: Long, val pauseGeneration:Long
    )
    private val lock=Any()
    private var pending: Target? = null
    private const val MAX_SCROLL_NODES = 512
    private const val GESTURE_ACTION_ID = -2
    private const val GESTURE_TARGET_CLASS = "__screen_scroll_gesture__"
    fun cancel() = synchronized(lock) { pending=null }
    /** Directional actions work on scroll views that do not expose logical order. */
    internal fun scrollActions(direction:String):List<Int> = when(direction) {
        "down" -> listOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id,
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        "up" -> listOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id,
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        "left" -> listOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id)
        "right" -> listOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id)
        else -> emptyList()
    }

    internal fun selectScrollAction(direction:String, availableActionIds:Collection<Int>):Int? {
        val available=availableActionIds.toHashSet()
        return scrollActions(direction).firstOrNull { it in available }
    }


    private fun safeRoot(service: ScreenAssistService): AccessibilityNodeInfo? {
        if (!ScreenAssistState.connected) return null
        // OEM Accessibility getters can throw after the service disconnects.
        // An overlay tap must not terminate Nexa's main process.
        return try {
            val guard=service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (guard?.isKeyguardLocked==true) return null
            // In debug/live-QA builds Nexa itself can be the focused surface
            // while a stale external app (for example Settings) remains in a
            // background task. Prefer the focused owner window in that case;
            // on a third-party foreground app it is null and we keep the
            // external-window behavior used for cross-app control.
            val root=(if(BuildConfig.DEBUG)service.assistantOwnerWindowRoot() else null)
                ?: service.externalWindowRoot()
                ?: return null
            try {
                val pkg=root.packageName?.toString().orEmpty()
                if (pkg.isBlank() || (pkg==service.packageName && !BuildConfig.DEBUG) ||
                    pkg=="com.android.systemui" ||
                    pkg=="android" || pkg.startsWith("com.google.android.inputmethod")) {
                    try { root.recycle() } catch (_: Exception) {}
                    null
                } else root
            } catch (_: Exception) {
                try { root.recycle() } catch (_: Exception) {}
                null
            }
        } catch (_: Exception) { null }
    }
    fun prepare(service: ScreenAssistService, direction: String,
                requireUniqueContainer:Boolean=false): String {
        cancel()
        if(direction !in setOf("up","down","left","right")) return "Unsupported scroll direction."
        val generation=NexaNativeActionSafety.approvalFence(service)
            ?:return "Owner paused device actions. No scroll review started."
        val root=safeRoot(service) ?: run {
            if(BuildConfig.DEBUG)android.util.Log.d("NexaScreenScroll",
                "preflight window unavailable; direction=$direction")
            return "No permitted app window to scroll."
        }
        try{
            val horizontalHome=direction in setOf("left","right") &&
                !requireUniqueContainer && NexaLauncherSwipe.isHome(service,root)
            if(BuildConfig.DEBUG && direction in setOf("left","right"))
                android.util.Log.d("NexaScreenScroll",
                    "preflight direction=$direction rootPackage=${root.packageName} " +
                        "windowId=${root.windowId} launcherHome=$horizontalHome")
            // Launcher Workspace nodes may expose a semantic horizontal scroll
            // action that reports acceptance without moving between Home pages.
            // Use the existing bounded, proportional gesture on the verified
            // Home window before considering semantic scroll containers.
            if(horizontalHome) {
                val full=Rect()
                root.getBoundsInScreen(full)
                val target=Target(UUID.randomUUID().toString(),root.packageName.toString(),
                    root.windowId,"__home_launcher_swipe__","",
                    IconBox(full.left,full.top,full.right,full.bottom),direction,-1,null,
                    SystemClock.elapsedRealtime()+10_000L,generation)
                synchronized(lock){pending=target}
                return "Confirm one $direction swipe on your Android home screen."
            }
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited=0
            var scrollableNodeCount=0
            var best: AccessibilityNodeInfo?=null
            var bestArea=0L
            var bestActionId=-1
            // Bounded target search refuses multiple independently scrollable
            // containers; ordinary owner scrolling retains prior behavior.
            val eligibleContainers=linkedSetOf<String>()
            while(queue.isNotEmpty() && visited<MAX_SCROLL_NODES){
                val node=queue.removeFirst()
                visited++
                if (!node.isPassword && !node.isEditable && node.isVisibleToUser){
                    if(node.isScrollable){
                        scrollableNodeCount++
                        val rect=Rect()
                        node.getBoundsInScreen(rect)
                        val actionIds=node.actionList.map { it.id }
                        val actionId=selectScrollAction(direction,actionIds)
                        if(BuildConfig.DEBUG)android.util.Log.d("NexaScreenScroll",
                            "scan direction=$direction package=${root.packageName} " +
                                "class=${node.className} bounds=${rect.left},${rect.top},${rect.right},${rect.bottom} " +
                                "actions=${actionIds.joinToString(",")} selected=$actionId")
                        val area=rect.width().toLong()*rect.height().toLong()
                        if(actionId!=null && area>0L && area<500_000_000L &&
                            rect.left>=0 && rect.top>=0){
                            eligibleContainers.add(
                                listOf(node.className?.toString().orEmpty(),
                                    node.viewIdResourceName.orEmpty(),rect.left,
                                    rect.top,rect.right,rect.bottom).joinToString(":"))
                            if(area>bestArea){
                                best=node
                                bestArea=area
                                bestActionId=actionId
                            }
                        }
                    }
                    for(i in 0 until node.childCount) node.getChild(i)?.let { queue.addLast(it) }
                }
            }
            if(queue.isNotEmpty()) {
                if(BuildConfig.DEBUG)android.util.Log.w("NexaScreenScroll",
                    "preflight hierarchy truncated direction=$direction visited=$visited " +
                        "remaining=${queue.size}")
                return "Scrollable hierarchy exceeds the safe inspection limit. No scroll proposed."
            }
            if(requireUniqueContainer && eligibleContainers.size!=1)
                return "Bounded scroll search requires one unique scrollable container. No gesture proposed."
            val candidate=best ?: run {
                if(BuildConfig.DEBUG)android.util.Log.d("NexaScreenScroll",
                    "no target direction=$direction package=${root.packageName} " +
                        "inspected=$visited scrollableNodes=$scrollableNodeCount")
                if(requireUniqueContainer || scrollableNodeCount!=0)
                    return "No visible scrollable container supports that direction."
                if(hasVisibleInputMethodWindow(service))
                    return "The keyboard is visible. No screen swipe proposed."
                val overlay=NexaToolOverlayService.screenGestureOverlayState()
                if(overlay?.expanded==true)
                    return "Collapse the Nexa panel before reviewing a screen swipe."
                if(overlay!=null && overlay.bounds==null)
                    return "Nexa's visible bubble bounds are unavailable. No screen swipe proposed."
                val appBounds=Rect().also { root.getBoundsInScreen(it) }
                val gesturePlan=NexaScreenScrollGesturePlanner.plan(appBounds,direction,
                    listOfNotNull(overlay?.bounds),service.resources.displayMetrics.density)
                    ?: return "No clear, safe swipe path is available in this app window."
                val target=Target(UUID.randomUUID().toString(),root.packageName.toString(),
                    root.windowId,GESTURE_TARGET_CLASS,"",
                    IconBox(appBounds.left,appBounds.top,appBounds.right,appBounds.bottom),
                    direction,GESTURE_ACTION_ID,gesturePlan,
                    SystemClock.elapsedRealtime()+10_000L,generation)
                synchronized(lock){pending=target}
                return "Confirm one $direction screen swipe in ${target.pkg}."
            }
            val box=Rect()
            candidate.getBoundsInScreen(box)
            val tgt=Target(UUID.randomUUID().toString(),root.packageName.toString(),
                root.windowId,candidate.className?.toString().orEmpty(),
                candidate.viewIdResourceName.orEmpty(),
                IconBox(box.left,box.top,box.right,box.bottom),direction,bestActionId,
                null,
                SystemClock.elapsedRealtime()+10_000L,generation)
            synchronized(lock){ pending=tgt }
            return "Confirm scroll ${direction} in ${tgt.pkg} within 10 seconds."
        } catch (error: Exception) {
            if(BuildConfig.DEBUG)android.util.Log.w("NexaScreenScroll",
                "preflight failed direction=$direction exception=${error.javaClass.simpleName}")
            cancel()
            return "Screen changed during scroll inspection. No action requested."
        } finally { try { root.recycle() } catch (_: Exception) {} }
    }

    fun isPending(): Boolean = synchronized(lock) {
        val t=pending
        if(t==null) false else if(SystemClock.elapsedRealtime()>=t.expiry){
            pending=null
            false
        } else true
    }

    /** One distinct, freshly visible target is required at both review and
     * final owner-fenced dispatch. Incomplete trees and changed action sets
     * cannot authorize a gesture.
     */
    private fun reviewedTarget(root:AccessibilityNodeInfo, approved:Target):AccessibilityNodeInfo? {
        val action=approved.actionId
        if(BuildConfig.DEBUG)android.util.Log.w("NexaScreenScroll",
            "review root package=${root.packageName} window=${root.windowId} " +
                "visible=${root.isVisibleToUser} targetPackage=${approved.pkg} " +
                "targetWindow=${approved.windowId} class=${approved.classname} " +
                "resource=${approved.resourceId} bounds=${approved.bounds} action=$action")
        if(action<0 || !root.isVisibleToUser) return null
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited=0
        var matching=0
        var result:AccessibilityNodeInfo?=null
        while(queue.isNotEmpty() && visited<MAX_SCROLL_NODES) {
            val node=queue.removeFirst()
            visited++
            if(node.isVisibleToUser && !node.isPassword && !node.isEditable) {
                if(node.isScrollable &&
                    node.className?.toString()==approved.classname &&
                    node.viewIdResourceName.orEmpty()==approved.resourceId &&
                    node.actionList.any { it.id==action }) {
                    val rect=Rect().also { node.getBoundsInScreen(it) }
                    if(!rect.isEmpty && rect.left==approved.bounds.left &&
                        rect.top==approved.bounds.top &&
                        rect.right==approved.bounds.right &&
                        rect.bottom==approved.bounds.bottom) {
                        matching++
                        result=node
                    }
                }
                for(i in 0 until node.childCount)
                    node.getChild(i)?.let { queue.addLast(it) }
            }
        }
        val accepted=queue.isEmpty() && matching==1
        if(BuildConfig.DEBUG)android.util.Log.w("NexaScreenScroll",
            "review result accepted=$accepted visited=$visited remaining=${queue.size} " +
                "matching=$matching expectedAction=$action")
        return if(accepted) result else null
    }

    private fun hasVisibleInputMethodWindow(service:ScreenAssistService):Boolean = try {
        service.windows.orEmpty().any { window ->
            if(window.type!=AccessibilityWindowInfo.TYPE_INPUT_METHOD) false
            else Rect().also { window.getBoundsInScreen(it) }.let { !it.isEmpty }
        }
    } catch (_:Exception) { true }

    private fun sameRootBounds(root:AccessibilityNodeInfo,approved:Target):Boolean {
        val bounds=Rect().also { root.getBoundsInScreen(it) }
        return bounds.left==approved.bounds.left && bounds.top==approved.bounds.top &&
            bounds.right==approved.bounds.right && bounds.bottom==approved.bounds.bottom
    }

    private fun reviewedOverlayPlan(service:ScreenAssistService,root:AccessibilityNodeInfo,
                                    approved:Target):NexaScreenScrollGesturePlan? {
        if(approved.classname!=GESTURE_TARGET_CLASS || approved.actionId!=GESTURE_ACTION_ID ||
            approved.gesturePlan==null || !root.isVisibleToUser ||
            root.packageName?.toString()!=approved.pkg || root.windowId!=approved.windowId ||
            !sameRootBounds(root,approved) || hasVisibleInputMethodWindow(service)) return null
        val overlay=NexaToolOverlayService.screenGestureOverlayState()
        if(overlay?.expanded==true || (overlay!=null && overlay.bounds==null)) return null
        val bounds=Rect().also { root.getBoundsInScreen(it) }
        return NexaScreenScrollGesturePlanner.plan(bounds,approved.direction,
            listOfNotNull(overlay?.bounds),service.resources.displayMetrics.density)
            ?.takeIf { it==approved.gesturePlan }
    }

    private fun dispatchGesture(service:ScreenAssistService,root:AccessibilityNodeInfo,
                                approved:Target,origin:String,
                                claimedToken:(String)->Unit):String {
        if(reviewedOverlayPlan(service,root,approved)==null)
            return "Screen, overlay or swipe path changed after review. No gesture performed."
        val plan=approved.gesturePlan ?: return "Reviewed swipe path is missing. No gesture performed."
        if(!NexaUiActionJournal.claim(service,approved.token,"scroll_${approved.direction}",
                approved.pkg,origin=origin))
            return "Secure action journal unavailable or approval already used. No swipe requested."
        claimedToken(approved.token)
        val before=NexaUiActionVerifier.fingerprint(root)
        NexaUiActionVerifier.begin(approved.token,"scroll",approved.pkg,root.windowId,before)
        val path=Path().apply {
            moveTo(plan.startX,plan.startY)
            lineTo(plan.endX,plan.endY)
        }
        val description=GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path,0L,360L)).build()
        val queued=NexaNativeActionSafety.dispatchWithFence(service,approved.pauseGeneration) {
            val fresh=safeRoot(service) ?: return@dispatchWithFence false
            try {
                if(reviewedOverlayPlan(service,fresh,approved)==null) false
                else service.dispatchGesture(description,
                    object:AccessibilityService.GestureResultCallback(){
                        override fun onCancelled(gestureDescription:GestureDescription?) {
                            NexaUiActionVerifier.cancel(approved.token)
                            NexaUiActionJournal.markObservation(service,approved.token,"gesture_cancelled")
                        }
                    },Handler(Looper.getMainLooper()))
            } finally { try { fresh.recycle() } catch (_:Exception) {} }
        }
        if(queued!=true) {
            NexaUiActionVerifier.cancel(approved.token)
            NexaUiActionJournal.finish(service,approved.token,"android_rejected")
            return "Android refused the reviewed screen swipe. No retry."
        }
        NexaUiActionVerifier.observeAfter(service,approved.token,before)
        return if(NexaUiActionJournal.finish(service,approved.token,"android_accepted"))
            "Android queued one ${approved.direction} screen swipe. Check the visible result."
        else "Android queued one screen swipe; journal outcome uncertain. No retry."
    }

    fun confirm(service: ScreenAssistService, origin:String="overlay", claimedToken:(String)->Unit={}): String {
        val approved=synchronized(lock){
            val t=pending
            pending=null // claim before dispatch; no retry, even if the OS result is uncertain
            t
        } ?: return "Scroll confirmation expired; no action requested."
        if(SystemClock.elapsedRealtime()>=approved.expiry)
            return "Scroll confirmation expired; no action requested."
        if(!NexaNativeActionSafety.approvalFenceCurrent(service,approved.pauseGeneration))
            return "Owner paused actions since review. Start a new scroll review."
        val root=safeRoot(service) ?: return "No permitted window available."
        try{
            if(root.packageName?.toString()!=approved.pkg || root.windowId!=approved.windowId)
                return "The screen changed. Scroll cancelled."
            if(approved.classname=="__home_launcher_swipe__") {
                if(!NexaLauncherSwipe.isHome(service,root))
                    return "Launcher changed; no swipe allowed."
                return NexaLauncherSwipe.gesture(service,root,approved.direction,approved.token,approved.pauseGeneration,claimedToken)
            }
            if(approved.classname==GESTURE_TARGET_CLASS)
                return dispatchGesture(service,root,approved,origin,claimedToken)
            if(reviewedTarget(root,approved)==null)
                return "Scroll target changed, truncated or ambiguous. No action requested."
            val direction="scroll_"+approved.direction
            if(!NexaUiActionJournal.claim(service,approved.token,direction,approved.pkg,origin=origin))
                return "Secure action journal unavailable or approval already used. No scroll requested."
            claimedToken(approved.token)
            val action=approved.actionId
            val before=NexaUiActionVerifier.fingerprint(root)
            NexaUiActionVerifier.begin(approved.token,"scroll",approved.pkg,root.windowId,before)
            val performed=NexaNativeActionSafety.dispatchWithFence(
                service,approved.pauseGeneration) {
                // Android can replace the entire Accessibility window between
                // the owner's confirmation and the actual dispatch callback.
                // Never execute a stale AccessibilityNodeInfo or retry a race.
                val freshRoot=safeRoot(service) ?: return@dispatchWithFence false
                try {
                    if(freshRoot.packageName?.toString()!=approved.pkg ||
                        freshRoot.windowId!=approved.windowId) false
                    else reviewedTarget(freshRoot,approved)?.performAction(action) ?: false
                } finally {
                    try { freshRoot.recycle() } catch (_:Exception) {}
                }
            }
            if(performed==null) {
                NexaUiActionVerifier.cancel(approved.token)
                NexaUiActionJournal.finish(service,approved.token,"android_rejected")
                return "Owner paused actions before scroll dispatch. No gesture performed."
            }
            return if(performed) {
                NexaUiActionVerifier.observeAfter(service,approved.token,before)
                if (!NexaUiActionJournal.finish(service,approved.token,"android_accepted"))
                    "Android accepted scroll; journal outcome uncertain. No automatic retry."
                else "Android accepted one scroll request. Check the visible result."
            } else {
                NexaUiActionVerifier.cancel(approved.token)
                NexaUiActionJournal.finish(service,approved.token,"android_rejected")
                "Android refused the scroll. No automatic retry."
            }
        }catch(_:Exception){NexaUiActionVerifier.cancel(approved.token);return "Scroll outcome uncertain; no automatic retry."}
        finally{try{root.recycle()}catch(_:Exception){}}
    }
}
