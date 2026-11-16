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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bounded horizontal gesture only on the owner's current HOME launcher.
 * No arbitrary model coordinates, no gesture on messaging/payment/settings UI.
 */
internal object NexaLauncherSwipe {
    private const val MAX_LAUNCHER_NODES=300

    fun isHome(service:ScreenAssistService,root:AccessibilityNodeInfo):Boolean {
        val pkg=root.packageName?.toString().orEmpty()
        if(pkg.isBlank() || pkg==service.packageName) return false
        val home=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolved=try { service.packageManager.resolveActivity(home,0)
            ?.activityInfo?.packageName.orEmpty() }catch(_:Exception){""}
        val accepted=pkg==resolved || (pkg=="com.transsion.XOSLauncher" &&
            (resolved.isBlank() || resolved=="android"))
        if(BuildConfig.DEBUG)android.util.Log.d("NexaLauncherSwipe",
            "home package check rootPackage=$pkg resolvedHomePackage=$resolved accepted=$accepted")
        return accepted
    }

    /** Choose the center of the widest unobstructed row crossed by this swipe.
     * Launcher widget/app hit boxes are read locally and are never sent to the
     * model. A missing or truncated hierarchy fails closed instead of falling
     * back to a fixed screen coordinate.
     */
    private fun findEmptyHorizontalBandY(
        root:AccessibilityNodeInfo,bounds:Rect,startX:Float,endX:Float
    ):Float? {
        val height=bounds.height()
        val width=bounds.width()
        if(height<300 || width<300)return null
        val edge=max(16,(height*0.04f).roundToInt())
        val clear=max(12,(width*0.012f).roundToInt())
        val usableTop=bounds.top+edge
        val usableBottom=bounds.bottom-edge
        if(usableBottom-usableTop<clear*2)return null
        val trackLeft=min(startX,endX).roundToInt()
        val trackRight=max(startX,endX).roundToInt()
        val wholeArea=width.toLong()*height.toLong()
        val largestIgnoredContainer=wholeArea*45L/100L
        val blocked=ArrayList<IntRange>()
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited=0
        try {
            while(queue.isNotEmpty() && visited<MAX_LAUNCHER_NODES) {
                val node=queue.removeFirst()
                visited++
                try {
                    val visibleToUser=node.isVisibleToUser
                    if(visibleToUser && (node.isClickable || node.isLongClickable ||
                            node.isFocusable || node.isScrollable)) {
                        val item=Rect()
                        node.getBoundsInScreen(item)
                        val itemArea=item.width().toLong()*item.height().toLong()
                        // The page-sized launcher workspace is the swipe
                        // surface itself, not an obstruction. Smaller widgets,
                        // folder cards and icons remain protected.
                        val isWorkspaceSized=itemArea>=largestIgnoredContainer
                        val crossesPath=item.left<trackRight && item.right>trackLeft
                        if(!item.isEmpty && !isWorkspaceSized && crossesPath &&
                            item.top<usableBottom && item.bottom>usableTop) {
                            val top=max(usableTop,item.top-clear)
                            val bottom=min(usableBottom,item.bottom+clear)
                            if(bottom>=top)blocked.add(top..bottom)
                        }
                    }
                    // Launchers keep several off-page workspaces in their
                    // accessibility tree. Their invisible descendants cannot
                    // intersect this gesture and must not exhaust the bounded
                    // scan before the visible Home page has been inspected.
                    if(visibleToUser)for(index in 0 until node.childCount) {
                        val child=node.getChild(index) ?: continue
                        if(child.isVisibleToUser)queue.addLast(child)
                        else try{child.recycle()}catch(_:Exception){}
                    }
                } finally {
                    if(node!==root)try{node.recycle()}catch(_:Exception){}
                }
            }
            if(queue.isNotEmpty()){
                if(BuildConfig.DEBUG)android.util.Log.d("NexaLauncherSwipe",
                    "launcher hierarchy incomplete; visited=$visited")
                return null
            }
        } catch(_:Exception) {
            return null
        } finally {
            while(queue.isNotEmpty()) {
                val node=queue.removeFirst()
                if(node!==root)try{node.recycle()}catch(_:Exception){}
            }
        }

        val ordered=blocked.sortedBy { it.first }
        var cursor=usableTop
        var bestStart=-1
        var bestEnd=-1
        var bestGap=0
        for(interval in ordered) {
            val top=max(usableTop,interval.first)
            val bottom=min(usableBottom,interval.last)
            if(top>cursor && top-cursor>bestGap) {
                bestStart=cursor
                bestEnd=top
                bestGap=top-cursor
            }
            cursor=max(cursor,bottom+1)
            if(cursor>=usableBottom)break
        }
        if(usableBottom>cursor && usableBottom-cursor>bestGap) {
            bestStart=cursor
            bestEnd=usableBottom
            bestGap=usableBottom-cursor
        }
        val minimumGap=max(24,(width*0.04f).roundToInt())
        if(bestStart<0 || bestGap<minimumGap){
            if(BuildConfig.DEBUG)android.util.Log.d("NexaLauncherSwipe",
                "no empty horizontal band; visited=$visited blockers=${blocked.size} " +
                    "largestGapPx=$bestGap minimumGapPx=$minimumGap")
            return null
        }
        return (bestStart+bestEnd)/2f
    }

    fun gesture(service:ScreenAssistService,root:AccessibilityNodeInfo,
                direction:String,token:String,pauseGeneration:Long,claimedToken:(String)->Unit={}):String {
        if(direction !in setOf("left","right") || !isHome(service,root))
            return "Horizontal gesture is only available on the verified launcher."
        val bounds=Rect().also {root.getBoundsInScreen(it)}
        if(bounds.width()<300 || bounds.height()<380 || bounds.left<0 || bounds.top<0)
            return "Launcher bounds are unavailable. No gesture attempted."
        val startFraction=if(direction=="left")0.78f else 0.22f
        val endFraction=if(direction=="left")0.22f else 0.78f
        val startX=bounds.left+bounds.width()*startFraction
        val endX=bounds.left+bounds.width()*endFraction
        val y=findEmptyHorizontalBandY(root,bounds,startX,endX)
            ?:run{
                if(BuildConfig.DEBUG)android.util.Log.d("NexaLauncherSwipe",
                    "no safe band; rootPackage=${root.packageName} bounds=${bounds.flattenToString()}")
                return "No clear horizontal launcher area is available. No gesture attempted."
            }
        if(BuildConfig.DEBUG)android.util.Log.d("NexaLauncherSwipe",
            "dispatch one launcher scroll; direction=$direction rootPackage=${root.packageName} " +
                "windowId=${root.windowId} y=${y.roundToInt()} bounds=${bounds.flattenToString()}")
        val path=Path().apply {
            moveTo(startX,y)
            lineTo(endX,y)
        }
        val stroke=GestureDescription.StrokeDescription(path,0,380L)
        val description=GestureDescription.Builder().addStroke(stroke).build()
        val pkg=root.packageName.toString()
        val before=NexaUiActionVerifier.fingerprint(root)
        if(!NexaUiActionJournal.claim(service,token,"scroll_$direction",pkg))
            return "Action journal denied repeat/unapproved gesture."
        claimedToken(token)
        NexaUiActionVerifier.begin(token,"scroll",pkg,root.windowId,before,
            allowLauncherWindowChange=true)
        val queued=try {
            NexaNativeActionSafety.dispatchWithFence(service,pauseGeneration) {
                service.dispatchGesture(description,
                object:AccessibilityService.GestureResultCallback(){
                    override fun onCancelled(gestureDescription:GestureDescription?) {
                        NexaUiActionVerifier.cancel(token)
                        NexaUiActionJournal.markObservation(service,token,"unverified")
                    }
                },Handler(Looper.getMainLooper()))
            }
        }catch(_:Exception){false}
        if(queued!=true) {
            NexaUiActionVerifier.cancel(token)
            NexaUiActionJournal.finish(service,token,"android_rejected")
            return "Android refused the launcher gesture. No retry."
        }
        NexaUiActionVerifier.observeAfter(service,token,before)
        NexaUiActionJournal.finish(service,token,"android_accepted")
        return "Android queued one $direction launcher swipe; check visible page-change evidence."
    }
}
