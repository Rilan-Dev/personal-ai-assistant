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

/**
 * A separate reviewed one-shot text entry tool, unlike generic icon clicking:
 * - never enters search, passwords or recipient fields;
 * - never clears or overwrites a populated text box;
 * - requires the exact selected app package and visibly matching recipient;
 * - compares the same window, field identity and bounds at confirmation;
 * - consumes the approval and encrypted action-journal claim before dispatch;
 * - never presses Send, nor retries a failed/uncertain dispatch.
 */
internal object NexaMessageInputActions {
    private data class Prepared(val token:String,val packageName:String,val windowId:Int,
        val fieldClass:String,val fieldId:String,val bounds:Rect,val expiresAt:Long,
        val reviewedDigest:String,val pauseGeneration:Long)
    private data class Inspection(val matches:Int,val candidate:AccessibilityNodeInfo?,
        val recipientVisible:Boolean)
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
    private fun inspect(root:AccessibilityNodeInfo, hint:String):Inspection {
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
                    !NexaEditableNode.isEmpty(node))) {
                    val bounds=Rect().also{node.getBoundsInScreen(it)}
                    if(bounds.width()>0 && bounds.height()>0 && bounds.left>=0 && bounds.top>=0) {
                        count++
                        target=node
                    }
                }
            }
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        return Inspection(count,target,recipientSeen)
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
            if(root.packageName?.toString()!=draft.packageName)
                return "Wrong messaging application is visible. No text entered."
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

    fun confirm(service:ScreenAssistService):String {
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
        try {
            if(root.packageName?.toString()!=approval.packageName ||
                root.windowId!=approval.windowId)
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
                    approval.packageName,approval.reviewedDigest))
                return "The durable one-time action journal is unavailable. No text entered."
            return try {
                val args=Bundle().apply{
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        draft.message)
                }
                val accepted=NexaNativeActionSafety.dispatchWithFence(
                    service,approval.pauseGeneration) {
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)
                }
                if(accepted==null) {
                    NexaUiActionJournal.finish(service,approval.token,"android_rejected")
                    return "Owner paused device actions since review. No message was entered."
                }
                val verified=accepted &&
                    NexaEditableNode.containsExactTextAfterDispatch(node,draft.message)
                NexaUiActionJournal.finish(service,approval.token,
                    if(accepted)"android_accepted" else "android_rejected")
                if(verified) {
                    NexaUiActionJournal.markObservation(service,approval.token,"observed_change")
                    "Exact prepared text is visible in the selected message input. NOT SENT. Review the recipient and tap Send yourself."
                } else if(accepted)
                    "Android accepted the text-entry request, but its contents were not verified. Review it manually. NOT SENT by Nexa."
                else "Android refused text entry. No retry; nothing was sent by Nexa."
            }catch(_:Exception) {
                NexaUiActionJournal.finish(service,approval.token,"unknown")
                "Text-entry result is uncertain. Inspect the field manually; no retry and no send by Nexa."
            }
        }catch(_:Exception){
            return "Messaging app changed during confirmation. No automatic retry or send."
        }finally{try{root.recycle()}catch(_:Exception){}}
    }
}
