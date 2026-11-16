package dev.rilandev.personalaiassistant

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/** Pure, conservative predicates; no field contents are recorded or sent to AI. */
internal object NexaMessageInputPolicy {
    /** Exact reviewed request binding. Never stored or sent to a model.
     * Each field is length-delimited so two structured requests cannot
     * collide by moving values across field boundaries.
     */
    fun approvedDraftDigest(appId:String,pkg:String,recipient:String,message:String):String {
        val data=listOf(appId,pkg,recipient,message).joinToString("") {
            it.length.toString()+":"+it
        }
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 0xff)}
    }
    fun recipientMatches(expected:String, visibleLabel:String):Boolean {
        val a=expected.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"),"")
        val b=visibleLabel.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"),"")
        return a.length in 2..50 && a==b
    }
    /** A matching name in a chat bubble is not the conversation title.
     * Only consider a bounded, visible label near the top app header. */
    fun isHeaderLabel(bounds:Rect,window:Rect):Boolean {
        if(window.width()<=0 || window.height()<=0 ||
            bounds.width()<=0 || bounds.height()<=0 ||
            !window.contains(bounds)) return false
        val headerBottom=window.top + window.height()/3
        return bounds.top >= window.top &&
            bounds.bottom <= headerBottom &&
            bounds.height() <= window.height()/8
    }
    fun isMessageField(description:String, editable:Boolean, password:Boolean,
        enabled:Boolean, visible:Boolean, hasSetText:Boolean, hasExistingText:Boolean):Boolean {
        if(!editable || password || !enabled || !visible || !hasSetText || hasExistingText)
            return false
        val hint=description.lowercase(java.util.Locale.ROOT)
        if(hint.isBlank() || hint.length>160) return false
        if(Regex("\\b(search|recipient|address|email|phone|password|otp|verify|code|subject|payment|amount|url|website)\\b")
                .containsMatchIn(hint)) return false
        return listOf("message","type a message","write a message","chat input",
            "chat_input","composer","message_input","message_edit","text message")
            .any { hint.contains(it) }
    }
}

/** Narrow semantic checks for the one-shot WhatsApp Send action. */
internal object NexaWhatsAppSendPolicy {
    fun isValidExactText(value:String):Boolean = value.length in 1..500 &&
        value.isNotBlank() &&
        value.none{it=='\u0000'||(it.code<32&&it!='\n'&&it!='\t')}

    fun isSendButton(label:String,className:String,enabled:Boolean,visible:Boolean,
                     clickable:Boolean,hasClickAction:Boolean):Boolean {
        val exactLabel=label.trim().lowercase(java.util.Locale.ROOT)
        val buttonClass=className.lowercase(java.util.Locale.ROOT).contains("button")
        return exactLabel in setOf("send","send message") && buttonClass &&
            enabled && visible && clickable && hasClickAction
    }

    fun isBesideComposer(window:Rect,composer:Rect,button:Rect):Boolean {
        if(window.width()<=0 || window.height()<=0 ||
            composer.width()<=0 || composer.height()<=0 ||
            button.width()<=0 || button.height()<=0 ||
            !window.contains(composer) || !window.contains(button))return false
        val rowTolerance=maxOf(composer.height(),button.height())/2
        val aligned=button.top<=composer.bottom+rowTolerance &&
            button.bottom>=composer.top-rowTolerance
        return aligned && button.centerX()>composer.centerX()
    }

    fun hasVerifiedSentEvidence(packageName:String,sameWindow:Boolean,
        recipientVisible:Boolean,composerEmpty:Boolean,exactMessageVisible:Boolean):Boolean =
        packageName=="com.whatsapp" && sameWindow && recipientVisible &&
            composerEmpty && exactMessageVisible

    fun hasVerifiedSentMessageEvidence(appId:String,packageName:String,sameWindow:Boolean,
        recipientVisible:Boolean,composerEmpty:Boolean,exactMessageVisible:Boolean):Boolean =
        NexaMessageChannels.isAgentSendTarget(appId,packageName) && sameWindow &&
            recipientVisible && composerEmpty && exactMessageVisible
}

/**
 * A separate reviewed one-shot text entry tool, unlike generic icon clicking:
 * - never enters search, passwords or recipient fields;
 * - never clears or overwrites a populated text box;
 * - requires the exact selected app package and visibly matching recipient;
 * - compares the same window, field identity and bounds at confirmation;
 * - consumes the approval and encrypted action-journal claim before dispatch;
 * - draft insertion never presses Send. The dedicated send method below has
 *   its own exact-text revalidation, high-risk approval and no-replay claim.
 */
internal object NexaMessageInputActions {
    private data class Prepared(val token:String,val packageName:String,val windowId:Int,
        val fieldClass:String,val fieldId:String,val bounds:Rect,val expiresAt:Long,
        val reviewedDigest:String,val pauseGeneration:Long)
    private data class Inspection(val matches:Int,val candidate:AccessibilityNodeInfo?,
        val recipientVisible:Boolean)
    private data class SendButtonInspection(val matches:Int,
        val candidate:AccessibilityNodeInfo?,val complete:Boolean)
    private data class SendTarget(val className:String,val viewId:String,val bounds:Rect)
    private val lock=Any()
    private var prepared:Prepared?=null
    fun cancel() = synchronized(lock){ prepared=null }
    fun ready():Boolean = synchronized(lock) {
        val p=prepared
        if(p==null) false else if(SystemClock.elapsedRealtime()>=p.expiresAt) {
            prepared=null
            false
        } else true
    }
    private fun inspect(root:AccessibilityNodeInfo, hint:String,
                        requireEmpty:Boolean=true):Inspection {
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited=0
        var count=0
        var target:AccessibilityNodeInfo?=null
        var recipientSeen=false
        val windowRect=Rect().also{root.getBoundsInScreen(it)}
        while(queue.isNotEmpty() && visited++<200) {
            val node=queue.removeFirst()
            if(!node.isVisibleToUser || node.isPassword) continue
            if(!node.isEditable) {
                val labels=listOf(node.text?.toString().orEmpty(),
                    node.contentDescription?.toString().orEmpty())
                if(labels.any{NexaMessageInputPolicy.recipientMatches(hint,it)}) {
                    val labelBounds=Rect().also{node.getBoundsInScreen(it)}
                    if(NexaMessageInputPolicy.isHeaderLabel(labelBounds,windowRect))
                        recipientSeen=true
                }
            }
            if(node.isEditable) {
                val desc=listOf(node.hintText?.toString().orEmpty(),
                    node.viewIdResourceName.orEmpty(),
                    node.contentDescription?.toString().orEmpty()).joinToString(" ")
                if(NexaMessageInputPolicy.isMessageField(desc,true,node.isPassword,
                    node.isEnabled,node.isVisibleToUser,
                    node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_SET_TEXT},
                    requireEmpty && !NexaEditableNode.isEmpty(node))) {
                    val bounds=Rect().also{node.getBoundsInScreen(it)}
                    if(bounds.width()>0 && bounds.height()>0 && bounds.left>=0 && bounds.top>=0) {
                        count++
                        target=node
                    }
                }
            }
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        // A truncated Accessibility tree might conceal a second composer or
        // a competing recipient header. Never authorize a partial scan.
        if(queue.isNotEmpty())return Inspection(0,null,false)
        return Inspection(count,target,recipientSeen)
    }

    /** Verify a selected chat using only the exact allowlisted app, header and
     * unique composer. No message contents are returned or persisted.
     */
    fun verifiedMessageChat(service:ScreenAssistService?,appId:String,
                            recipient:String):Boolean {
        val pkg=NexaMessageChannels.agentSendPackage(appId)?:return false
        if(!NexaMessageChannels.isSafeAgentRecipient(recipient))return false
        if(service==null||!ScreenAssistState.connected||ScreenAssistService.active!==service)
            return false
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}?:return false
        return try{
            if(root.packageName?.toString()!=pkg||root.windowId<0||!root.isVisibleToUser)
                false
            else {
                val found=inspect(root,recipient,requireEmpty=false)
                found.recipientVisible&&found.matches==1
            }
        }catch(_:Exception){false}
        finally{try{root.recycle()}catch(_:Exception){}}
    }

    /** Independent native reinspection used by the chat-task evidence ledger. */
    fun verifyVisibleMessageDraft(service:ScreenAssistService?,appId:String,
                                  recipient:String,exactText:String):Boolean {
        val pkg=NexaMessageChannels.agentSendPackage(appId)?:return false
        if(!NexaMessageChannels.isSafeAgentRecipient(recipient)||
            !NexaWhatsAppSendPolicy.isValidExactText(exactText)||
            service==null||!ScreenAssistState.connected||ScreenAssistService.active!==service)
            return false
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}?:return false
        return try{
            if(root.packageName?.toString()!=pkg||root.windowId<0||!root.isVisibleToUser)
                false
            else {
                val found=inspect(root,recipient,requireEmpty=false)
                val field=found.candidate
                found.recipientVisible&&found.matches==1&&field!=null&&
                    NexaEditableNode.containsExactTextAfterDispatch(field,exactText)
            }
        }catch(_:Exception){false}
        finally{try{root.recycle()}catch(_:Exception){}}
    }

    /** Post-action reading must be from the currently active chat and field,
     * never from the stale node used at review. This does not resend text.
     */
    private fun verifyCurrentDraft(service:ScreenAssistService,p:Prepared,
                                   recipient:String,expected:String):Boolean {
        if(!ScreenAssistState.connected || ScreenAssistService.active!==service)return false
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}?:return false
        return try {
            if(!root.isVisibleToUser || root.packageName?.toString()!=p.packageName ||
                root.windowId!=p.windowId)return false
            val visible=inspect(root,recipient,requireEmpty=false)
            val field=visible.candidate
            if(!visible.recipientVisible || visible.matches!=1 || field==null ||
                field.className?.toString().orEmpty()!=p.fieldClass ||
                field.viewIdResourceName.orEmpty()!=p.fieldId ||
                Rect().also { field.getBoundsInScreen(it) }!=p.bounds)return false
            NexaEditableNode.containsExactTextAfterDispatch(field,expected)
        }catch(_:Exception){false}
        finally{try{root.recycle()}catch(_:Exception){}}
    }

    private fun inspectSendButton(root:AccessibilityNodeInfo,composer:Rect):SendButtonInspection {
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited=0
        var count=0
        var candidate:AccessibilityNodeInfo?=null
        val window=Rect().also{root.getBoundsInScreen(it)}
        while(queue.isNotEmpty()&&visited++<200){
            val node=queue.removeFirst()
            if(node.isVisibleToUser&&!node.isPassword){
                val label=node.contentDescription?.toString()?.takeIf{it.isNotBlank()}
                    ?:node.text?.toString().orEmpty()
                val bounds=Rect().also{node.getBoundsInScreen(it)}
                val hasClick=node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_CLICK}
                if(NexaWhatsAppSendPolicy.isSendButton(label,
                        node.className?.toString().orEmpty(),node.isEnabled,
                        node.isVisibleToUser,node.isClickable,hasClick)&&
                    NexaWhatsAppSendPolicy.isBesideComposer(window,composer,bounds)){
                    count++
                    candidate=node
                }
            }
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        return if(queue.isNotEmpty())SendButtonInspection(0,null,false)
            else SendButtonInspection(count,candidate,true)
    }

    private fun exactVisibleBubbleCount(root:AccessibilityNodeInfo,expected:String):Int? {
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited=0
        var count=0
        while(queue.isNotEmpty()&&visited++<200){
            val node=queue.removeFirst()
            if(node.isVisibleToUser&&!node.isPassword&&!node.isEditable&&
                node.text?.toString()==expected)count++
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        return if(queue.isNotEmpty())null else count
    }

    /** Rechecks the exact approved WhatsApp draft and unique semantic Send
     * control, commits a durable one-shot claim, then taps exactly once. No
     * raw message text or label is persisted, and uncertain outcomes never retry.
     */
    fun sendReviewedWhatsApp(service:ScreenAssistService,recipient:String,
                             exactText:String,origin:String="chat"):String =
        sendReviewedMessage(service,"whatsapp",recipient,exactText,origin)

    /** Generic app-bound one-shot send; the package mapping, chat header,
     * draft, one adjacent semantic Send control and resulting bubble are all
     * rechecked on device. An uncertain tap is permanently non-replayable.
     */
    fun sendReviewedMessage(service:ScreenAssistService,appId:String,recipient:String,
                            exactText:String,origin:String="chat"):String {
        val expectedPackage=NexaMessageChannels.agentSendPackage(appId)
            ?:return "This messaging app is not enabled for reviewed Send. Nothing was sent."
        if(!NexaMessageChannels.isSafeAgentRecipient(recipient)||
            !NexaWhatsAppSendPolicy.isValidExactText(exactText))
            return "The exact approved message text is invalid. Nothing was sent."
        if(!ScreenAssistState.connected||ScreenAssistService.active!==service)
            return "Nexa Screen Assist is disconnected. Nothing was sent."
        if(!verifiedMessageChat(service,appId,recipient))
            return "The exact messaging conversation is not verified. Nothing was sent."
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return "The messaging screen is unavailable. Nothing was sent."
        val token=UUID.randomUUID().toString()
        val digest=NexaMessageInputPolicy.approvedDraftDigest(
            appId,expectedPackage,recipient,exactText)
        var claimed=false
        var actionEntered=false
        var actionUncertain=false
        fun finish(outcome:String):Boolean=NexaUiActionJournal.finish(service,token,outcome)
        try{
            if(root.packageName?.toString()!=expectedPackage||
                root.windowId<0||!root.isVisibleToUser)
                return "The foreground window is not the approved messaging app. Nothing was sent."
            val initial=inspect(root,recipient,requireEmpty=false)
            val field=initial.candidate
            if(!initial.recipientVisible||initial.matches!=1||field==null||
                !NexaEditableNode.containsExactTextAfterDispatch(field,exactText))
                return "The exact approved draft is not visible in the matching conversation. Nothing was sent."
            val fieldBounds=Rect().also{field.getBoundsInScreen(it)}
            val initialButton=inspectSendButton(root,fieldBounds)
            val target=initialButton.candidate
            if(!initialButton.complete||initialButton.matches!=1||target==null)
                return "One unique Send button beside the verified composer was not found. Nothing was sent."
            val targetBounds=Rect().also{target.getBoundsInScreen(it)}
            val sendTarget=SendTarget(target.className?.toString().orEmpty(),
                target.viewIdResourceName.orEmpty(),targetBounds)
            val previousBubbleCount=exactVisibleBubbleCount(root,exactText)
                ?:return "The WhatsApp view was incomplete. Nothing was sent."
            val pauseGeneration=NexaNativeActionSafety.approvalFence(service)
                ?:return "Device actions are paused. Nothing was sent."
            if(!NexaUiActionJournal.claim(service,token,"message_send",
                    expectedPackage,digest,origin))
                return "The durable one-time send journal is unavailable. Nothing was sent."
            claimed=true
            val dispatched=NexaNativeActionSafety.dispatchWithFence(service,pauseGeneration){
                val fresh=try{
                    if(!ScreenAssistState.connected||ScreenAssistService.active!==service)null
                    else service.externalWindowRoot()
                }catch(_:Exception){null}
                if(fresh==null){actionUncertain=true;false}
                else try{
                    val sameWindow=fresh.packageName?.toString()==expectedPackage&&
                        fresh.windowId==root.windowId&&fresh.isVisibleToUser
                    val current=if(sameWindow)inspect(fresh,recipient,requireEmpty=false)
                        else Inspection(0,null,false)
                    val currentField=current.candidate
                    val currentFieldBounds=currentField?.let{Rect().also{r->it.getBoundsInScreen(r)}}
                    val exactDraft=current.recipientVisible&&current.matches==1&&
                        currentField!=null&&currentFieldBounds==fieldBounds&&
                        NexaEditableNode.containsExactTextAfterDispatch(currentField,exactText)
                    val buttonScan=if(exactDraft)inspectSendButton(fresh,currentFieldBounds!!)
                        else SendButtonInspection(0,null,true)
                    val button=buttonScan.candidate
                    val buttonBounds=button?.let{Rect().also{r->it.getBoundsInScreen(r)}}
                    val sameButton=buttonScan.complete&&buttonScan.matches==1&&button!=null&&
                        button.className?.toString().orEmpty()==sendTarget.className&&
                        button.viewIdResourceName.orEmpty()==sendTarget.viewId&&
                        buttonBounds==sendTarget.bounds
                    if(!sameWindow||!exactDraft||!sameButton){
                        actionUncertain=false
                        false
                    }else{
                        actionEntered=true
                        button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }catch(_:Exception){
                    actionUncertain=actionEntered
                    false
                }finally{try{fresh.recycle()}catch(_:Exception){}}
            }
            if(dispatched==null){
                val persisted=finish("android_rejected")
                return if(persisted)"Owner paused actions before Send. Nothing was sent."
                    else "Send was blocked, but journal completion is uncertain. Do not retry."
            }
            if(!dispatched){
                val uncertain=actionUncertain||actionEntered
                val persisted=finish(if(uncertain)"unknown" else "android_rejected")
                return if(persisted&&!uncertain)
                    "The approved chat or Send button changed before dispatch. Nothing was sent."
                    else "Send outcome is uncertain after dispatch. Inspect the conversation; do not retry."
            }
            if(!finish("android_accepted"))
                return "Android accepted the Send tap, but the journal is uncertain. Inspect the conversation; do not retry."
            SystemClock.sleep(700L)
            val verified=verifySentMessage(service,appId,recipient,exactText,root.windowId,
                previousBubbleCount+1)
            if(!verified)
                return "Send outcome is uncertain. Inspect the conversation; do not retry."
            if(!NexaUiActionJournal.markObservation(service,token,"observed_change"))
                return "The message is visible, but durable sent evidence was not saved. Inspect manually; do not retry."
            return "sent_message_visible"
        }catch(_:Exception){
            if(claimed){
                try{finish(if(actionEntered)"unknown" else "android_rejected")}catch(_:Exception){}
            }
            return "Send outcome is uncertain. Inspect the conversation; do not retry."
        }finally{try{root.recycle()}catch(_:Exception){}}
    }

    fun verifySentWhatsApp(service:ScreenAssistService?,recipient:String,
                           exactText:String,expectedWindow:Int?=null,
                           minimumBubbleCount:Int=1):Boolean =
        verifySentMessage(service,"whatsapp",recipient,exactText,expectedWindow,
            minimumBubbleCount)

    fun verifySentMessage(service:ScreenAssistService?,appId:String,recipient:String,
                          exactText:String,expectedWindow:Int?=null,
                          minimumBubbleCount:Int=1):Boolean {
        if(minimumBubbleCount<1)return false
        val expectedPackage=NexaMessageChannels.agentSendPackage(appId)?:return false
        if(service==null||!ScreenAssistState.connected||ScreenAssistService.active!==service)
            return false
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}?:return false
        return try{
            val sameWindow=root.packageName?.toString()==expectedPackage&&
                root.isVisibleToUser&&(expectedWindow==null||root.windowId==expectedWindow)
            val current=if(sameWindow)inspect(root,recipient,requireEmpty=false)
                else Inspection(0,null,false)
            val field=current.candidate
            val composerEmpty=current.matches==1&&field!=null&&NexaEditableNode.isEmpty(field)
            val bubbles=exactVisibleBubbleCount(root,exactText)
            NexaWhatsAppSendPolicy.hasVerifiedSentMessageEvidence(appId,expectedPackage,sameWindow,
                current.recipientVisible,composerEmpty,
                bubbles!=null&&bubbles>=minimumBubbleCount)
        }catch(_:Exception){false}
        finally{try{root.recycle()}catch(_:Exception){}}
    }

    fun prepare(service:ScreenAssistService):String {
        cancel()
        val draft=NexaMessageDraftState.peek() ?:
            return "No active reviewed draft. No text has been entered."
        if(!ScreenAssistState.connected || ScreenAssistService.active !== service)
            return "Nexa Screen Assist is disconnected. No text was entered."
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?: return "Open the chosen chat and keep the overlay visible."
        try {
            if(root.packageName?.toString()!=draft.packageName ||
                root.windowId<0 || !root.isVisibleToUser)
                return "Wrong or unavailable messaging window. No text entered."
            val scanned=inspect(root,draft.recipientHint)
            if(!scanned.recipientVisible)
                return "Recipient title is not visibly identical to the requested contact. Open that chat and retry."
            if(scanned.matches!=1)
                return "Expected one empty message input box, found ${scanned.matches}. No text entered."
            val node=scanned.candidate ?: return "No verified composer was found."
            val rect=Rect().also{node.getBoundsInScreen(it)}
            val pauseGeneration=NexaNativeActionSafety.approvalFence(service)
                ?:return "Device actions are paused. No message-field review started."
            synchronized(lock) {
                prepared=Prepared(UUID.randomUUID().toString(),draft.packageName,
                    root.windowId,node.className?.toString().orEmpty(),
                    node.viewIdResourceName.orEmpty(),rect,
                    SystemClock.elapsedRealtime()+25_000L,
                    NexaMessageInputPolicy.approvedDraftDigest(draft.appId,
                        draft.packageName,draft.recipientHint,draft.message),pauseGeneration)
            }
            return "One empty input box is visible in ${draft.appId} for ${draft.recipientHint}. Confirm once to fill the prepared text. Nothing has been sent."
        } catch(_:Exception) {
            cancel()
            return "Screen changed during message-field inspection. No text was entered."
        } finally {try{root.recycle()}catch(_:Exception){}}
    }

    fun confirm(service:ScreenAssistService,origin:String="overlay"):String {
        val approval=synchronized(lock){val x=prepared; prepared=null; x}
            ?: return "Message input confirmation was not requested or expired."
        if(SystemClock.elapsedRealtime()>=approval.expiresAt ||
            !ScreenAssistState.connected || ScreenAssistService.active !== service ||
            !NexaNativeActionSafety.approvalFenceCurrent(service,approval.pauseGeneration)) {
            NexaMessageDraftState.clear()
            return "Message approval expired, owner paused actions, or Accessibility disconnected. Nothing was typed."
        }
        val draft=NexaMessageDraftState.take() ?:
            return "Message draft expired. No text was entered."
        if(draft.packageName!=approval.packageName ||
            NexaMessageInputPolicy.approvedDraftDigest(draft.appId,draft.packageName,
                draft.recipientHint,draft.message)!=approval.reviewedDigest)
            return "The approved recipient, channel or exact message changed. No text was entered."
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?: return "Messaging conversation is no longer visible. No action dispatched."
        // A durable claim may precede Binder, verifier and journal failures.
        // Every exceptional post-claim exit must retain a non-replayable,
        // explicitly uncertain outcome rather than a successful UI claim.
        var unsettledClaim:String?=null
        fun settle(token:String,outcome:String):Boolean {
            val persisted=NexaUiActionJournal.finish(service,token,outcome)
            if(persisted)unsettledClaim=null
            return persisted
        }
        try {
            if(root.packageName?.toString()!=approval.packageName ||
                root.windowId!=approval.windowId || !root.isVisibleToUser)
                return "The chat window changed. No text was entered."
            val scanned=inspect(root,draft.recipientHint)
            if(!scanned.recipientVisible || scanned.matches!=1)
                return "Recipient or message input changed. No text was entered."
            val node=scanned.candidate ?: return "Message input disappeared."
            val box=Rect().also{node.getBoundsInScreen(it)}
            if(box!=approval.bounds ||
                node.className?.toString().orEmpty()!=approval.fieldClass ||
                node.viewIdResourceName.orEmpty()!=approval.fieldId)
                return "Message field moved or changed. No text was entered."
            if(!NexaUiActionJournal.claim(service,approval.token,"message_fill",
                    approval.packageName,approval.reviewedDigest,origin=origin))
                return "The durable one-time action journal is unavailable. No text entered."
            unsettledClaim=approval.token
            return try {
                val args=Bundle().apply{
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        draft.message)
                }
                var stale=false
                var outcomeUnknown=false
                var actionEntered=false
                val accepted=NexaNativeActionSafety.dispatchWithFence(
                    service,approval.pauseGeneration) {
                    // An Android composer may be recycled, replaced or
                    // populated after the owner approves it. Reacquire and
                    // recheck the exact empty field INSIDE the Pause fence.
                    val fresh=try {
                        if(!ScreenAssistState.connected || ScreenAssistService.active!==service)
                            null else service.externalWindowRoot()
                    }catch(_:Exception){null}
                    if(fresh==null) {
                        stale=true
                        false
                    }else try {
                        val visible=if(fresh.packageName?.toString()==approval.packageName &&
                            fresh.windowId==approval.windowId && fresh.isVisibleToUser)
                            inspect(fresh,draft.recipientHint) else Inspection(0,null,false)
                        val candidate=visible.candidate
                        val same=visible.recipientVisible && visible.matches==1 &&
                            candidate!=null && candidate==node &&
                            candidate.className?.toString().orEmpty()==approval.fieldClass &&
                            candidate.viewIdResourceName.orEmpty()==approval.fieldId &&
                            Rect().also { candidate.getBoundsInScreen(it) }==approval.bounds
                        if(!same) {
                            stale=true
                            false
                        } else {
                            actionEntered=true
                            candidate!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)
                        }
                    }catch(_:Exception){
                        if(actionEntered)outcomeUnknown=true else stale=true
                        false
                    }finally {try{fresh.recycle()}catch(_:Exception){}}
                }
                if(accepted==null) {
                    val persisted=settle(approval.token,"android_rejected")
                    return if(persisted)
                        "Owner paused device actions since review. No message was entered."
                    else "Owner paused actions, but journal completion is uncertain. No retry or Send."
                }
                val verified=accepted &&
                    verifyCurrentDraft(service,approval,draft.recipientHint,draft.message)
                val persisted=settle(approval.token,
                    if(outcomeUnknown)"unknown" else
                        if(accepted)"android_accepted" else "android_rejected")
                if(!persisted)
                    "Message text-entry journal outcome could not be stored. Inspect the field; no retry or Send."
                else if(verified) {
                    if(NexaUiActionJournal.markObservation(service,approval.token,"observed_change"))
                        "Exact prepared text is visible in the selected message input. NOT SENT. Review the recipient and tap Send yourself."
                    else "Text was observed in the selected field, but durable verification was not saved. Inspect it manually. NOT SENT."
                } else if(accepted)
                    "Android accepted the text-entry request, but its contents were not verified. Review it manually. NOT SENT by Nexa."
                else if(outcomeUnknown) "Message-input result is uncertain. Inspect the current chat; no automatic retry and no send by Nexa."
                else if(stale) "Chat, recipient or empty composer changed before text entry. No insertion or retry."
                else "Android refused text entry. No retry; nothing was sent by Nexa."
            }catch(_:Exception) {
                try{settle(approval.token,"unknown")}catch(_:Exception){}
                "Text-entry result is uncertain after durable claim. Inspect the field manually; no retry and no Send by Nexa."
            }
        }catch(_:Exception){
            val token=unsettledClaim
            if(token!=null){
                try{settle(token,"unknown")}catch(_:Exception){}
                return "Message composer outcome is uncertain after a durable claim. Inspect the field; no retry or Send."
            }
            return "Messaging app changed during confirmation. No automatic retry or send."
        }finally{try{root.recycle()}catch(_:Exception){}}
    }
}
