package dev.rilandev.personalaiassistant

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/** One owner-reviewed chat-list navigation step; no message data or send. */
internal object NexaWhatsAppChatActions {
    private data class Row(val target:AccessibilityNodeInfo,val evidence:Int,
        val bounds:Rect)
    internal data class Scan(val candidates:Int,val bestEvidence:Int,
        val target:AccessibilityNodeInfo?,val alreadyOpen:Boolean)

    private fun labelMatch(node:AccessibilityNodeInfo,recipient:String):Int {
        if(node.isEditable || node.isPassword || !node.isVisibleToUser)return 0
        val labels=listOf(node.text?.toString().orEmpty(),
            node.contentDescription?.toString().orEmpty())
        return labels.maxOfOrNull{NexaWhatsAppChatMatch.evidence(recipient,it)} ?:0
    }
    /**
     * Accessibility can report one visible chat row through both its clickable
     * wrapper and clickable descendant. These are one semantic target only
     * when their source identity or a short direct ancestry path proves it.
     * Never merge two sibling contact rows merely because they share a parent.
     */
    private fun sameSourceOrAncestor(a:AccessibilityNodeInfo,b:AccessibilityNodeInfo):Boolean {
        if(a==b)return true
        fun above(ancestor:AccessibilityNodeInfo,candidate:AccessibilityNodeInfo):Boolean {
            var current=try { candidate.parent } catch (_:Exception) { null }
            var steps=0
            while(current!=null && steps++<5) {
                if(ancestor==current)return true
                current=try { current.parent } catch (_:Exception) { null }
            }
            return false
        }
        return above(a,b) || above(b,a)
    }
    /** Return the on-screen portion only when most of the semantic target is
     * actually visible. Android can report decor bounds slightly outside the
     * app window during edge-to-edge/instrumented windows; rejecting every
     * partially clipped node loses a clear target, while accepting a sliver
     * would make a tap unsafe. The click still uses the live accessibility
     * node, never these bounds as a coordinate. */
    internal fun visibleTargetBounds(bounds:Rect,window:Rect):Rect? {
        if(bounds.isEmpty || window.isEmpty)return null
        val visible=Rect(bounds)
        if(!visible.intersect(window))return null
        val targetArea=bounds.width().toLong()*bounds.height().toLong()
        val visibleArea=visible.width().toLong()*visible.height().toLong()
        if(targetArea<=0L || visibleArea*5L<targetArea*4L)return null
        return visible
    }
    private fun messageComposerPresent(root:AccessibilityNodeInfo):Boolean {
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root)
        var seen=0
        var composers=0
        while(queue.isNotEmpty()&&seen++<220) {
            val n=queue.removeFirst()
            if(!n.isVisibleToUser || n.isPassword)continue
            if(n.isEditable && n.isEnabled) {
                val hints=listOf(n.hintText?.toString().orEmpty(),
                    n.viewIdResourceName.orEmpty(),n.contentDescription?.toString().orEmpty())
                    .joinToString(" ").lowercase(java.util.Locale.ROOT)
                if(n.actionList.any { it.id==AccessibilityNodeInfo.ACTION_SET_TEXT } &&
                    Regex("""(^|[^a-z])(type a message|message input|message_input|chat_input|composer|message)([^a-z]|$)""")
                        .containsMatchIn(hints) &&
                    !Regex("""search|recipient|password|payment""").containsMatchIn(hints))
                    composers++
            }
            for(i in 0 until n.childCount)n.getChild(i)?.let{queue.addLast(it)}
        }
        return queue.isEmpty() && composers==1
    }
    /** A row may have a TextView and an accessibility label; count only its
     * unique clickable ancestor. Two distinct rows are always ambiguous. */
    internal fun inspect(root:AccessibilityNodeInfo,recipient:String):Scan {
        val window=Rect().also{root.getBoundsInScreen(it)}
        if(window.isEmpty || recipient.length !in 3..55)
            return Scan(0,0,null,false)
        val rows=mutableListOf<Row>()
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root)
        var examined=0
        var headerName=false
        while(queue.isNotEmpty()&&examined++<240) {
            val node=queue.removeFirst()
            if(!node.isVisibleToUser || node.isPassword || node.isEditable)continue
            val score=labelMatch(node,recipient)
            if(score>0){
                val title=Rect().also{node.getBoundsInScreen(it)}
                if(NexaMessageInputPolicy.isHeaderLabel(title,window) &&
                    listOf(node.text?.toString().orEmpty(),
                           node.contentDescription?.toString().orEmpty())
                        .any { NexaMessageInputPolicy.recipientMatches(recipient,it) })
                    headerName=true
                var candidate:AccessibilityNodeInfo?=node
                var ancestors=0
                while(candidate!=null && !candidate.isClickable && ancestors++<5)
                    candidate=candidate.parent
                if(candidate!=null && candidate.isClickable && candidate.isEnabled &&
                    !candidate.isPassword && !candidate.isEditable) {
                    val rawRect=Rect().also{candidate.getBoundsInScreen(it)}
                    val rect=visibleTargetBounds(rawRect,window)
                    if(rect!=null && rect.width()>25 && rect.height()>20) {
                        val previousIndex=rows.indexOfFirst{
                            sameSourceOrAncestor(it.target,candidate)
                        }
                        if(previousIndex<0)rows.add(Row(candidate,score,Rect(rect)))
                        else {
                            val previous=rows[previousIndex]
                            val preferred=if(rect.width().toLong()*rect.height() <
                                previous.bounds.width().toLong()*previous.bounds.height())
                                Row(candidate,maxOf(score,previous.evidence),Rect(rect))
                            else Row(previous.target,maxOf(score,previous.evidence),previous.bounds)
                            rows[previousIndex]=preferred
                        }
                    }
                }
            }
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        // The visibility walk must finish. Otherwise an unseen second row
        // could make a seemingly unique match unsafe.
        if(queue.isNotEmpty())return Scan(0,0,null,false)
        val open=headerName && messageComposerPresent(root)
        val eligible=rows.filter{it.evidence>=85}
        return Scan(eligible.size,eligible.maxOfOrNull{it.evidence}?:0,
            eligible.singleOrNull()?.target,open)
    }
    fun openApproved(service:ScreenAssistService,recipient:String,
                     origin:String="overlay",claimedToken:(String)->Unit={}):String {
        if(NexaWhatsAppChatIntent.followupRecipient("open chat with "+recipient)==null)
            return "Invalid chat-recipient hint. No action taken."
        if(!ScreenAssistState.connected || ScreenAssistService.active!==service)
            return "Screen Assist not connected. No chat navigation attempted."
        val generation=NexaNativeActionSafety.approvalFence(service)
            ?:return "Owner paused device actions. No chat row tapped."
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return "WhatsApp chat list is not accessible."
        try {
            if(root.packageName?.toString()!="com.whatsapp" ||
                root.windowId<0 || !root.isVisibleToUser)
                return "The current accessible window is not a permitted WhatsApp chat. No chat was opened."
            val scan=inspect(root,recipient)
            if(scan.alreadyOpen)
                return "Matching chat header and composer are already visible. No tap necessary."
            if(scan.candidates!=1)
                return "Found "+scan.candidates+" possible chat rows; need one unique contact. Nothing tapped."
            // One-letter speech variations are permitted only when there is
            // exactly one uniquely observed chat row and the owner has
            // explicitly approved opening that conversation (not sending).
            // A completely different name or two possible rows fail closed.
            if(scan.bestEvidence<85)
                return "Chat contact could not be grounded. No tap performed."
            val target=scan.target ?:return "The matched chat row is no longer available."
            val expectedWindow=root.windowId
            val expectedClass=target.className?.toString().orEmpty()
            val expectedResource=target.viewIdResourceName.orEmpty()
            val expectedBounds=Rect().also {target.getBoundsInScreen(it)}
            val token=UUID.randomUUID().toString()
            val before=NexaUiActionVerifier.fingerprint(root)
            if(!NexaUiActionJournal.claim(service,token,"tap","com.whatsapp",origin=origin))
                return "The encrypted action journal refused the chat tap. Nothing opened."
            claimedToken(token)
            NexaUiActionVerifier.begin(token,"tap","com.whatsapp",root.windowId,before)
            var stale=false
            val clicked=NexaNativeActionSafety.dispatchWithFence(service,generation) {
                // A list can recycle a clickable row after review. Only a fresh
                // complete Accessibility tree inside the final Pause fence may
                // authorize this exact native click. Never reuse a stale node.
                val fresh=try {
                    if(!ScreenAssistState.connected || ScreenAssistService.active!==service)
                        null else service.externalWindowRoot()
                }catch(_:Exception){null}
                if(fresh==null){
                    stale=true
                    false
                }else try {
                    val freshScan=if(fresh.packageName?.toString()=="com.whatsapp" &&
                        fresh.windowId==expectedWindow && fresh.isVisibleToUser)
                        inspect(fresh,recipient) else Scan(0,0,null,false)
                    val candidate=freshScan.target
                    val rect=Rect().also { candidate?.getBoundsInScreen(it) }
                    val same=freshScan.candidates==1 && !freshScan.alreadyOpen &&
                        freshScan.bestEvidence==scan.bestEvidence &&
                        candidate!=null && candidate==target &&
                        candidate.className?.toString().orEmpty()==expectedClass &&
                        candidate.viewIdResourceName.orEmpty()==expectedResource &&
                        rect==expectedBounds
                    if(!same){
                        stale=true
                        false
                    }else candidate!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }catch(_:Exception){
                    stale=true
                    false
                }finally {try{fresh.recycle()}catch(_:Exception){}}
            }
            if(clicked==null){
                NexaUiActionVerifier.cancel(token)
                NexaUiActionJournal.finish(service,token,"android_rejected")
                return "Owner paused actions before opening chat. Nothing tapped."
            }
            return if(clicked) {
                NexaUiActionVerifier.observeAfter(service,token,before)
                NexaUiActionJournal.finish(service,token,"android_accepted")
                "Android accepted a uniquely grounded WhatsApp chat-row tap. Conversation header not yet verified."
            }else{
                NexaUiActionVerifier.cancel(token)
                NexaUiActionJournal.finish(service,token,"android_rejected")
                if(stale) "WhatsApp chat row or window changed before dispatch. No tap or retry."
                else "Android refused the chat-row tap. No automatic retry."
            }
        }catch(_:Exception){return "WhatsApp changed while locating the contact. No retry."}
        finally{try{root.recycle()}catch(_:Exception){}}
    }
    /** Fresh screen, matching header and message composer, no provider guess. */
    fun verifiedOpen(service:ScreenAssistService?,recipient:String):Boolean {
        if(service==null || !ScreenAssistState.connected || ScreenAssistService.active!==service)
            return false
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}?:return false
        return try {
            root.packageName?.toString()=="com.whatsapp" &&
                root.windowId>=0 && root.isVisibleToUser &&
                inspect(root,recipient).alreadyOpen
        }catch(_:Exception){false}
        finally{try{root.recycle()}catch(_:Exception){}}
    }
}
