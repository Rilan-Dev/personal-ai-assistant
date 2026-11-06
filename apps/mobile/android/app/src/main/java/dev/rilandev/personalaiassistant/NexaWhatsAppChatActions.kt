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
    /** Android can expose one chat row through both a clickable row and a
     * clickable accessibility wrapper with slightly different bounds. Merge
     * those overlapping targets, while keeping adjacent rows independent. */
    private fun sameRow(a:Rect,b:Rect):Boolean {
        val overlap=Rect(a)
        if(!overlap.intersect(b))return false
        val minWidth=minOf(a.width(),b.width())
        val minHeight=minOf(a.height(),b.height())
        return minWidth>0 && minHeight>0 &&
            overlap.width()*5>=minWidth*4 && overlap.height()*2>=minHeight
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
        while(queue.isNotEmpty()&&seen++<220) {
            val n=queue.removeFirst()
            if(!n.isVisibleToUser || n.isPassword)continue
            if(n.isEditable && n.isEnabled) {
                val hints=listOf(n.hintText?.toString().orEmpty(),
                    n.viewIdResourceName.orEmpty(),n.contentDescription?.toString().orEmpty())
                    .joinToString(" ").lowercase(java.util.Locale.ROOT)
                if(Regex("""(^|[^a-z])(type a message|message input|message_input|chat_input|composer|message)([^a-z]|$)""")
                        .containsMatchIn(hints) &&
                    !Regex("""search|recipient|password|payment""").containsMatchIn(hints))
                    return true
            }
            for(i in 0 until n.childCount)n.getChild(i)?.let{queue.addLast(it)}
        }
        return false
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
                if(NexaMessageInputPolicy.isHeaderLabel(title,window))headerName=true
                var candidate:AccessibilityNodeInfo?=node
                var ancestors=0
                while(candidate!=null && !candidate.isClickable && ancestors++<5)
                    candidate=candidate.parent
                if(candidate!=null && candidate.isClickable && candidate.isEnabled &&
                    !candidate.isPassword && !candidate.isEditable) {
                    val rawRect=Rect().also{candidate.getBoundsInScreen(it)}
                    val rect=visibleTargetBounds(rawRect,window)
                    if(rect!=null && rect.width()>25 && rect.height()>20) {
                        val previousIndex=rows.indexOfFirst{sameRow(it.bounds,rect)}
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
        val open=headerName && messageComposerPresent(root)
        val eligible=rows.filter{it.evidence>=85}
        return Scan(eligible.size,eligible.maxOfOrNull{it.evidence}?:0,
            eligible.singleOrNull()?.target,open)
    }
    fun openApproved(service:ScreenAssistService,recipient:String,
                     origin:String="overlay"):String {
        if(NexaWhatsAppChatIntent.followupRecipient("open chat with "+recipient)==null)
            return "Invalid chat-recipient hint. No action taken."
        if(!ScreenAssistState.connected || ScreenAssistService.active!==service)
            return "Screen Assist not connected. No chat navigation attempted."
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return "WhatsApp chat list is not accessible."
        try {
            if(root.packageName?.toString()!="com.whatsapp")
                return "The current app is not WhatsApp. No chat was opened."
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
            val token=UUID.randomUUID().toString()
            val before=NexaUiActionVerifier.fingerprint(root)
            if(!NexaUiActionJournal.claim(service,token,"tap","com.whatsapp",origin=origin))
                return "The encrypted action journal refused the chat tap. Nothing opened."
            NexaUiActionVerifier.begin(token,"tap","com.whatsapp",root.windowId,before)
            return if(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                NexaUiActionVerifier.observeAfter(service,token,before)
                NexaUiActionJournal.finish(service,token,"android_accepted")
                "Android accepted a uniquely grounded WhatsApp chat-row tap. Conversation header not yet verified."
            }else{
                NexaUiActionVerifier.cancel(token)
                NexaUiActionJournal.finish(service,token,"android_rejected")
                "Android refused the chat-row tap. No automatic retry."
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
                inspect(root,recipient).alreadyOpen
        }catch(_:Exception){false}
        finally{try{root.recycle()}catch(_:Exception){}}
    }
}
