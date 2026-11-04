package dev.rilandev.personalaiassistant

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/** No arbitrary coordinates, clipboard access, IME key events or submit clicks. */
internal object NexaTextInputPolicy {
    private val forbidden = Regex(
        """\b(password|passcode|pin|otp|one[ -]?time|verification|security|secret|recovery|payment|card|cvv|cvc|iban|bank|account|recipient|email address|phone number|amount|transfer|purchase|checkout|submit|send)\b""",
        RegexOption.IGNORE_CASE)
    private fun normalized(value:String):String =
        value.lowercase(java.util.Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]+""")," ").trim()
    fun safeTarget(target:String):Boolean =
        target.length in 2..60 && target.none{it.code<32} &&
        !forbidden.containsMatchIn(target) &&
        normalized(target).length>=2
    fun matches(target:String, fieldHints:List<String>, editable:Boolean,password:Boolean,
                visible:Boolean,enabled:Boolean,empty:Boolean,setText:Boolean):Boolean {
        if(!safeTarget(target)||!editable||password||!visible||!enabled||!empty||!setText)return false
        val wanted=normalized(target)
        return fieldHints.filter{it.isNotBlank()}.any { value ->
            val candidate=normalized(value)
            candidate.isNotBlank() && !forbidden.containsMatchIn(candidate) &&
                (candidate==wanted ||
                    candidate.startsWith("$wanted ") &&
                    wanted in setOf("search","notes","note","description","comment",
                        "title","query","subject","message","compose","text"))
        }
    }
}

/** Only the owner-supplied text is kept in RAM; no persistent text or screenshots. */
internal object NexaTextInputState {
    data class Draft(val targetLabel:String,val exactText:String,val expiry:Long)
    private val lock=Any()
    private var value:Draft?=null
    fun clear()=synchronized(lock){ value=null }
    fun stage(target:String,text:String,now:Long=SystemClock.elapsedRealtime()):Boolean =
        synchronized(lock) {
            value=null
            if(!NexaTextInputPolicy.safeTarget(target)||text.length !in 1..500 ||
                text.isBlank()||text.any{it=='\u0000' || (it.code<32 && it!='\n' && it!='\t')})
                return@synchronized false
            value=Draft(target,text,now+90_000L)
            true
        }
    fun peek(now:Long=SystemClock.elapsedRealtime()):Draft?=synchronized(lock) {
        if(value!=null && now>=value!!.expiry)value=null
        value
    }
    fun take(now:Long=SystemClock.elapsedRealtime()):Draft?=synchronized(lock) {
        val v=peek(now); value=null;v
    }
}

/** One inspected empty field; explicit second owner approval; no submission. */
internal object NexaTextInputActions {
    private data class Pending(val token:String,val packageName:String,val windowId:Int,
        val clazz:String,val resource:String,val box:Rect,val target:String,val expiry:Long)
    private data class Found(val count:Int,val node:AccessibilityNodeInfo?)
    private val lock=Any()
    private var pending:Pending?=null
    fun cancel()=synchronized(lock){pending=null}
    fun ready():Boolean=synchronized(lock){
        val p=pending
        if(p==null)false else if(SystemClock.elapsedRealtime()>=p.expiry){
            pending=null;false
        }else true
    }
    private fun find(root:AccessibilityNodeInfo,target:String):Found {
        val window=Rect().also{root.getBoundsInScreen(it)}
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count=0
        var selected:AccessibilityNodeInfo?=null
        var visited=0
        while(queue.isNotEmpty()&&visited++<200){
            val node=queue.removeFirst()
            if(!node.isVisibleToUser||node.isPassword)continue
            if(node.isEditable){
                val hints=listOf(node.hintText?.toString().orEmpty(),
                    node.contentDescription?.toString().orEmpty(),
                    node.viewIdResourceName?.substringAfterLast('/')?.replace('_',' ').orEmpty())
                if(NexaTextInputPolicy.matches(target,hints,node.isEditable,node.isPassword,
                    node.isVisibleToUser,node.isEnabled,node.text.isNullOrEmpty(),
                    node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_SET_TEXT})){
                    val bounds=Rect().also{node.getBoundsInScreen(it)}
                    if(bounds.width()>0&&bounds.height()>0&&window.contains(bounds)){
                        count++
                        selected=node
                    }
                }
            }
            for(i in 0 until node.childCount)node.getChild(i)?.let{queue.addLast(it)}
        }
        return Found(count,selected)
    }
    fun prepare(service:ScreenAssistService):String {
        cancel()
        val draft=NexaTextInputState.peek() ?:return "No active reviewed text-entry request."
        if(!ScreenAssistState.connected || ScreenAssistService.active !== service)
            return "Enable Nexa Screen Assist. No text entered."
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return "No permitted external application is visible."
        try{
            val pkg=root.packageName?.toString().orEmpty()
            if(pkg.isBlank()||pkg==service.packageName)return "Cannot type into this window."
            val found=find(root,draft.targetLabel)
            if(found.count!=1)return "Found ${found.count} matching empty text fields. Need exactly one; nothing typed."
            val node=found.node ?:return "The field is no longer available."
            val bounds=Rect().also{node.getBoundsInScreen(it)}
            synchronized(lock){pending=Pending(UUID.randomUUID().toString(),pkg,root.windowId,
                node.className?.toString().orEmpty(),node.viewIdResourceName.orEmpty(),
                bounds,draft.targetLabel,SystemClock.elapsedRealtime()+25_000L)}
            return "One empty '${draft.targetLabel}' input is visible in $pkg. Approve one text insertion; no submit or send."
        }catch(_:Exception){cancel();return "Screen changed during inspection. Nothing typed."}
        finally{try{root.recycle()}catch(_:Exception){}}
    }
    fun confirm(service:ScreenAssistService):String{
        val approval=synchronized(lock){val p=pending;pending=null;p}
            ?:return "No pending approved text entry."
        if(SystemClock.elapsedRealtime()>=approval.expiry ||
            !ScreenAssistState.connected || ScreenAssistService.active !== service){
            NexaTextInputState.clear()
            return "Approval expired or Screen Assist disconnected. No entry attempted."
        }
        val draft=NexaTextInputState.take()?:return "Text-entry request expired."
        if(draft.targetLabel!=approval.target)return "Target changed. No text entered."
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return "The target application is no longer visible."
        try{
            if(root.packageName?.toString()!=approval.packageName ||
                root.windowId!=approval.windowId)
                return "Application or window changed; text entry cancelled."
            val found=find(root,approval.target)
            if(found.count!=1)return "Target became ambiguous or nonempty. Nothing entered."
            val node=found.node?:return "Target is no longer present."
            val box=Rect().also{node.getBoundsInScreen(it)}
            if(box!=approval.box || node.className?.toString().orEmpty()!=approval.clazz ||
               node.viewIdResourceName.orEmpty()!=approval.resource)
               return "Field identity or position changed. Nothing entered."
            if(!NexaUiActionJournal.claim(service,approval.token,"text_fill",approval.packageName))
                return "Encrypted action journal unavailable; text entry cancelled."
            return try {
                val args=Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        draft.exactText)
                }
                val accepted=node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)
                val matches=accepted && node.text?.toString()==draft.exactText
                NexaUiActionJournal.finish(service,approval.token,
                    if(accepted)"android_accepted" else "android_rejected")
                if(matches){
                    NexaUiActionJournal.markObservation(service,approval.token,"observed_change")
                    "Requested text is visible in the approved field. No Enter, Send or Submit action performed."
                }else if(accepted) "Android accepted text insertion, but the field content was not verified. Review it manually; not submitted."
                else "Android refused text insertion. Nothing submitted; no retries."
            }catch(_:Exception){
                NexaUiActionJournal.finish(service,approval.token,"unknown")
                "Entry outcome is uncertain; inspect the field manually. No retries or submission."
            }
        }catch(_:Exception){return "Screen changed; no automated retry or submission."}
        finally{try{root.recycle()}catch(_:Exception){}}
    }
}
