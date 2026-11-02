package dev.rilandev.personalaiassistant

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

/**
 * Best-effort OS evidence collection following ONE owner-approved native action.
 * No retries, page text, images or semantic task-success claims.
 */
internal object NexaUiActionVerifier {
    private val handler=Handler(Looper.getMainLooper())
    private val lock=Any()
    private data class Observation(
        val token:String,val kind:String,val pkg:String,val windowId:Int,
        val before:String,val start:Long,var scrollEvents:Int=0,var windowEvents:Int=0
    )
    private val pending=LinkedHashMap<String,Observation>()

    /** SHA-256 of bounded structural metadata, never page content. */
    fun fingerprint(root:AccessibilityNodeInfo):String {
        val content=StringBuilder()
        content.append(root.packageName?.toString().orEmpty()).append('#').append(root.windowId)
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited=0
        while(queue.isNotEmpty()&&visited<120&&content.length<12000){
            val node=queue.removeFirst()
            visited++
            if(node.isPassword||node.isEditable||!node.isVisibleToUser)continue
            val r=Rect()
            node.getBoundsInScreen(r)
            content.append('|').append(node.className?.toString().orEmpty().take(100))
                .append(':').append(node.viewIdResourceName.orEmpty().take(140))
                .append(':').append(node.childCount).append(':').append(node.isScrollable)
                .append(':').append(r.left).append(',').append(r.top)
                .append(',').append(r.right).append(',').append(r.bottom)
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        val digest=MessageDigest.getInstance("SHA-256")
            .digest(content.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString(""){"%02x".format(it.toInt() and 255)}
    }

    /** Start BEFORE performAction to catch immediate TYPE_VIEW_SCROLLED events. */
    fun begin(token:String,kind:String,pkg:String,windowId:Int,before:String) {
        if(kind!="tap"&&kind!="scroll")return
        synchronized(lock){
            val now=SystemClock.elapsedRealtime()
            pending.entries.removeAll{now-it.value.start>3000}
            if(pending.size>=8)pending.remove(pending.keys.first())
            pending[token]=Observation(token,kind,pkg,windowId,before,now)
        }
    }
    fun cancel(token:String)=synchronized(lock){pending.remove(token);Unit}

    /** Only metadata from the same third-party app + window can count. */
    fun onAccessibilityEvent(event:AccessibilityEvent) {
        val type=event.eventType
        if(type!=AccessibilityEvent.TYPE_VIEW_SCROLLED&&
            type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return
        val pkg=event.packageName?.toString().orEmpty()
        val window=event.windowId
        synchronized(lock){
            val now=SystemClock.elapsedRealtime()
            for(state in pending.values){
                if(now-state.start !in 0..2000 || pkg!=state.pkg || window!=state.windowId)continue
                if(type==AccessibilityEvent.TYPE_VIEW_SCROLLED&&state.kind=="scroll")
                    state.scrollEvents++
                if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&state.kind=="tap")
                    state.windowEvents++
            }
        }
    }

    /** Never mistake accepted dispatch for task completion. */
    fun observeAfter(service:ScreenAssistService,token:String,unusedPrior:String) {
        handler.postDelayed({
            val observation=synchronized(lock){pending.remove(token)}?:return@postDelayed
            if(!ScreenAssistState.connected||ScreenAssistService.active!==service){
                NexaUiActionJournal.markObservation(service,token,"unverified")
                return@postDelayed
            }
            val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            val sameContext=root==null || (
                root.packageName?.toString()==observation.pkg &&
                root.windowId==observation.windowId)
            val after=if(root==null || !sameContext)null else try{fingerprint(root)}
                catch(_:Exception){null} finally{root.recycle()}
            val status=NexaUiActionEvidencePolicy.classify(
                observation.kind,observation.before,after,
                observation.scrollEvents,observation.windowEvents,sameContext)
            NexaUiActionJournal.markObservation(service,token,status)
        },900L)
    }
}
