package dev.rilandev.personalaiassistant

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

/** Grounded, one-shot semantic tap: never trusts arbitrary X/Y from an LLM. */
internal object NexaScreenLabelTap {
    private val unsafe = Regex("""\b(send|submit|pay|purchase|buy|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|otp|logout|sign out)\b""",
        RegexOption.IGNORE_CASE)

    /** Accessible names often contain bullets or join app words (MyMotor).
     * Strip only separators; never use fuzzy match or ambiguous coordinates.
     */
    private fun labelKey(raw:String):String =
        raw.lowercase(java.util.Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]"""),"")

    fun approved(service:ScreenAssistService, requested:String,
                 origin:String="overlay"):String {
        val label=requested.trim()
        if(label.length !in 2..45 || unsafe.containsMatchIn(label))
            return "Unsafe or invalid requested control; no tap was sent."
        val generation=NexaNativeActionSafety.approvalFence(service)
            ?:return "Owner paused device actions. No tap permitted."
        val root=service.externalWindowRoot() ?:return "No permitted external app is visible. Enable Screen Assist."
        try {
            val pkg=root.packageName?.toString().orEmpty()
            if(pkg.isBlank() || pkg==service.packageName || pkg=="android" ||
                pkg=="com.android.systemui")return "The window is not permitted for interaction."
            val rootRect=Rect().also{root.getBoundsInScreen(it)}
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            // A single chat-list row often exposes its title in multiple
            // descendant nodes. Count the actual clickable target, not each
            // repeated accessibility label.
            val targets=linkedMapOf<String,AccessibilityNodeInfo>()
            var inspected=0
            while(queue.isNotEmpty() && inspected++<200) {
                val node=queue.removeFirst()
                if(!node.isVisibleToUser || node.isPassword)continue
                // Editable search fields can be focused, never read or filled.
                // Other editable controls (password, recipient, payment,
                // message boxes) remain unavailable to generic label taps.
                val isSearchInput=node.isEditable &&
                    label.contains("search",ignoreCase=true) &&
                    listOf(node.contentDescription?.toString().orEmpty(),
                        node.hintText?.toString().orEmpty(),
                        node.viewIdResourceName.orEmpty())
                        .any {it.contains("search",ignoreCase=true)}
                if(node.isEditable && !isSearchInput)continue
                val visible=if(node.isEditable)
                    listOf(node.contentDescription?.toString().orEmpty(),
                        node.hintText?.toString().orEmpty())
                    else listOf(node.text?.toString().orEmpty(),
                        node.contentDescription?.toString().orEmpty())
                val requestedKey=labelKey(label)
                val match=visible.any {
                    it.trim().equals(label,ignoreCase=true) ||
                        requestedKey.isNotBlank() && labelKey(it)==requestedKey ||
                        (pkg=="com.whatsapp" &&
                            NexaWhatsAppChatMatch.evidence(label,it)>=85)
                } ||
                    isSearchInput && label.equals("search",ignoreCase=true)
                if(match) {
                    var candidate:AccessibilityNodeInfo?=node
                    repeat(3) {
                        if(candidate!=null && !candidate!!.isClickable)
                            candidate=candidate?.parent
                    }
                    val rect=Rect()
                    candidate?.getBoundsInScreen(rect)
                    if(candidate!=null && (candidate!!.isClickable || isSearchInput) && candidate!!.isEnabled &&
                        rect.width()>0 && rect.height()>0 && rootRect.contains(rect)) {
                        val key=listOf(rect.left,rect.top,rect.right,rect.bottom)
                            .joinToString(":")
                        targets[key]=candidate
                    }
                }
                for(i in 0 until node.childCount)node.getChild(i)?.let{ queue.addLast(it) }
            }
            if(targets.isEmpty())return "No enabled clickable control named $label was found on this screen."
            if(targets.size!=1)return "Multiple controls named $label are visible. No tap performed."
            val found=targets.values.single()
            val token=UUID.randomUUID().toString()
            val before=NexaUiActionVerifier.fingerprint(root)
            if(!NexaUiActionJournal.claim(service,token,"tap",pkg,origin=origin))
                return "Action journal rejected this request. No tap occurred."
            NexaUiActionVerifier.begin(token,"tap",pkg,root.windowId,before)
            // Clicking a verified search input transfers focus only. Never
            // use ACTION_SET_TEXT or read/edit the field's current contents.
            val clicked=NexaNativeActionSafety.dispatchWithFence(service,generation) {
                found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            if(clicked==null){
                NexaUiActionVerifier.cancel(token)
                NexaUiActionJournal.finish(service,token,"android_rejected")
                return "Owner paused actions before tap. No tap dispatched."
            }
            return if(clicked) {
                NexaUiActionVerifier.observeAfter(service,token,before)
                NexaUiActionJournal.finish(service,token,"android_accepted")
                "Android accepted one tap on the visible $label control. Check the resulting screen; completion is not yet confirmed."
            } else {
                NexaUiActionVerifier.cancel(token)
                NexaUiActionJournal.finish(service,token,"android_rejected")
                "Android refused the $label tap. No retry was performed."
            }
        } catch (_:Exception){
            return "The screen changed or Android blocked the tap. No automatic retry."
        } finally {root.recycle()}
    }
}
