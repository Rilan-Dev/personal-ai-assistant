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
        val before:String,val start:Long,var scrollEvents:Int=0,var windowEvents:Int=0,
        var contentEvents:Int=0,
        val allowLauncherWindowChange:Boolean=false,
        var changedWindowEventPackage:String?=null,
        var changedWindowEventId:Int?=null,
        val expectedClickIdentity:String?=null,
        var tapEvents:Int=0
    )
    private val pending=LinkedHashMap<String,Observation>()

    /** SHA-256 of bounded structural metadata, never page content. */
    fun fingerprint(root:AccessibilityNodeInfo):String {
        val content=StringBuilder()
        // Window IDs are transient Android handles. Omit them so an ID change
        // alone cannot masquerade as visible structural change after a swipe.
        content.append(root.packageName?.toString().orEmpty())
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
            // Only a transient SHA-256 fingerprint is retained, not raw labels.
            // Same-shaped home screens can differ only in icon descriptions.
            val label=node.contentDescription?.toString()
                ?:node.text?.toString().orEmpty()
            if(label.isNotBlank())content.append(':').append(label.take(80))
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        val digest=MessageDigest.getInstance("SHA-256")
            .digest(content.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString(""){"%02x".format(it.toInt() and 255)}
    }

    /** Start BEFORE performAction to catch immediate TYPE_VIEW_SCROLLED events. */
    fun begin(token:String,kind:String,pkg:String,windowId:Int,before:String,
              allowLauncherWindowChange:Boolean=false,
              expectedClickIdentity:String?=null) {
        if(kind!="tap"&&kind!="scroll")return
        synchronized(lock){
            val now=SystemClock.elapsedRealtime()
            pending.entries.removeAll{now-it.value.start>3000}
            if(pending.size>=8)pending.remove(pending.keys.first())
            pending[token]=Observation(token,kind,pkg,windowId,before,now,
                allowLauncherWindowChange=allowLauncherWindowChange,
                expectedClickIdentity=expectedClickIdentity)
        }
    }
    fun cancel(token:String)=synchronized(lock){pending.remove(token);Unit}

    /** Only safe app/window metadata can count; no event text is retained. */
    fun onAccessibilityEvent(event:AccessibilityEvent) {
        val type=event.eventType
        if(type!=AccessibilityEvent.TYPE_VIEW_SCROLLED&&
            type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&
            type!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED&&
            type!=AccessibilityEvent.TYPE_VIEW_CLICKED)return
        val pkg=event.packageName?.toString().orEmpty()
        val window=event.windowId
        val eventIdentity=if(type==AccessibilityEvent.TYPE_VIEW_CLICKED){
            val source=try{event.source}catch(_:Exception){null}
            try{source?.let{clickIdentity(it,pkg,window)}}
            finally{try{source?.recycle()}catch(_:Exception){}}
        }else null
        synchronized(lock){
            val now=SystemClock.elapsedRealtime()
            for(state in pending.values){
                if(now-state.start !in 0..2000)continue
                val sameWindow=pkg==state.pkg && window==state.windowId
                val permittedWindowChange = ScreenAssistSourcePolicy.matches(
                    pkg,pkg,BuildConfig.APPLICATION_ID) ||
                    (BuildConfig.DEBUG && pkg==BuildConfig.APPLICATION_ID)
                if(!sameWindow && state.kind=="tap" &&
                    type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    permittedWindowChange){
                    state.changedWindowEventPackage=pkg
                    state.changedWindowEventId=window
                    continue
                }
                if(!sameWindow)continue
                if(type==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED&&
                    NexaUiActionEvidencePolicy.isMatchingContentChangeEvent(
                        state.kind,state.pkg,state.windowId,pkg,window))
                    state.contentEvents++
                if(type==AccessibilityEvent.TYPE_VIEW_SCROLLED&&state.kind=="scroll")
                    state.scrollEvents++
                if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&state.kind=="tap")
                    state.windowEvents++
                if(type==AccessibilityEvent.TYPE_VIEW_CLICKED&&state.kind=="tap"&&
                    NexaUiActionEvidencePolicy.isMatchingTapEvent(
                        state.expectedClickIdentity,eventIdentity))state.tapEvents++
            }
        }
    }

    /** Transient hash of package/window, class, resource id and bounds only. */
    fun clickIdentity(node:AccessibilityNodeInfo,pkg:String,windowId:Int):String?=try{
        val bounds=Rect().also{node.getBoundsInScreen(it)}
        if(pkg.isBlank()||bounds.isEmpty)null else {
            val metadata=listOf(pkg,windowId.toString(),
                node.className?.toString().orEmpty().take(100),
                node.viewIdResourceName.orEmpty().take(140),
                bounds.left.toString(),bounds.top.toString(),
                bounds.right.toString(),bounds.bottom.toString()).joinToString("|")
            MessageDigest.getInstance("SHA-256")
                .digest(metadata.toByteArray(Charsets.UTF_8))
                .joinToString(""){ "%02x".format(it.toInt() and 255) }
        }
    }catch(_:Exception){null}

    /** Never mistake accepted dispatch for task completion. */
    fun observeAfter(service:ScreenAssistService,token:String,unusedPrior:String) {
        handler.postDelayed({
            val observation=synchronized(lock){pending.remove(token)}?:return@postDelayed
            if(!ScreenAssistState.connected||ScreenAssistService.active!==service){
                NexaUiActionJournal.markObservation(service,token,"unverified")
                return@postDelayed
            }
            val externalRoot=try{service.externalWindowRoot()}catch(_:Exception){null}
            // The owner root is deliberately debug-only and is already limited
            // by ScreenAssistService to the active, focused Nexa window.
            val ownerRoot=if(externalRoot==null && BuildConfig.DEBUG)
                try{service.assistantOwnerWindowRoot()}catch(_:Exception){null}
            else null
            val root=externalRoot ?: ownerRoot
            val observedPackage=root?.packageName?.toString()
            val allowLauncherWindowChange=root!=null &&
                observation.allowLauncherWindowChange &&
                NexaLauncherSwipe.isHome(service,root)
            val sameContext=root==null || NexaUiActionEvidencePolicy.sameEvidenceContext(
                observation.kind,observation.pkg,observation.windowId,
                observedPackage,root.windowId,allowLauncherWindowChange)
            val externalWindowChangeObserved = externalRoot != null &&
                NexaUiActionEvidencePolicy.isCorroboratedExternalWindowTransition(
                    observation.kind,observation.pkg,observation.windowId,
                    observation.changedWindowEventPackage,observation.changedWindowEventId,
                    observedPackage,root?.windowId ?: observation.windowId,
                    ScreenAssistSourcePolicy.matches(
                        observedPackage.orEmpty(),observedPackage.orEmpty(),BuildConfig.APPLICATION_ID))
            val ownerWindowChangeObserved = ownerRoot != null &&
                NexaUiActionEvidencePolicy.isCorroboratedOwnerWindowTransition(
                    observation.kind,observation.pkg,observation.windowId,
                    observation.changedWindowEventPackage,observation.changedWindowEventId,
                    observedPackage,root?.windowId ?: observation.windowId,
                    BuildConfig.APPLICATION_ID,BuildConfig.DEBUG)
            val after=try {
                if(root==null || !sameContext) null else fingerprint(root)
            }catch(_:Exception){null}finally{root?.recycle()}
            // Represent a corroborated external root as present without
            // comparing fingerprints across different app/window trees.
            val observedAfter = if(root != null && !sameContext &&
                (externalWindowChangeObserved || ownerWindowChangeObserved))
                "window-transition-present" else after
            val status=NexaUiActionEvidencePolicy.classify(
                observation.kind,observation.before,observedAfter,
                observation.scrollEvents,observation.windowEvents,sameContext,
                externalWindowChangeObserved,tapEvents=observation.tapEvents,
                ownerWindowChangeObserved=ownerWindowChangeObserved,
                contentEvents=observation.contentEvents)
            NexaUiActionJournal.markObservation(service,token,status)
        },900L)
    }
}
