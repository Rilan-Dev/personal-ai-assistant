package dev.rilandev.personalaiassistant

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Owner-requested, observation-only Android screen adapter.
 *
 * No screenshots, editable field contents, message bodies, contact lists,
 * text selection, gestures or coordinates are read or transmitted. An app's
 * displayed strings are untrusted data, NEVER executable instructions.
 * The shared NexaTaskCoordinator owns the task/claim/evidence lifecycle.
 */
internal object NexaTaskScreenObservation {
    private const val MAX_NODES=180
    private const val MAX_CONTROLS=12
    private val suspicious=Regex(
        """(?i)(?:password|passcode|pin|otp|verification|secret|token|auth|bank|account|card|payment|transfer|balance|email|phone|contact|address|call|dial|send|submit|delete|remove|purchase|pay|install|permission|grant|allow|approve|confirm|logout|sign out|message body)"""
    )
    private val emailOrUrl=Regex("""(?i)(?:https?://|www\.|@|[0-9]{3,})""")
    private val allowedChars=Regex("""^[\p{L}][\p{L}\p{M}0-9 _./-]*$""")

    /** A control label is a short UI affordance, not arbitrary app content. */
    internal fun safeLabel(raw:String?):String? {
        if(raw==null)return null
        val value=raw.replace(Regex("""[\p{Cc}\p{Cf}]+""")," ")
            .replace(Regex("""\s+""")," ").trim()
        if(value.length !in 2..32 || value.split(' ').size>4 ||
            suspicious.containsMatchIn(value) || emailOrUrl.containsMatchIn(value) ||
            !allowedChars.matches(value))return null
        return value
    }

    internal fun safeControl(className:String?,clickable:Boolean,
                             enabled:Boolean,visible:Boolean,editable:Boolean,
                             password:Boolean):Boolean {
        if(!clickable||!enabled||!visible||editable||password)return false
        val name=className.orEmpty()
        return name.endsWith(".Button") || name.endsWith(".ImageButton") ||
            name.endsWith(".TextView") || name.endsWith(".Switch") ||
            name.endsWith(".CheckBox") || name.endsWith(".Tab")
    }

    private fun zone(control:Rect,window:Rect):String {
        val cx=control.centerX()-window.left
        val cy=control.centerY()-window.top
        val horizontal=when{
            cx < window.width()/3 -> "left"
            cx > window.width()*2/3 -> "right"
            else -> "center"
        }
        val vertical=when{
            cy < window.height()/3 -> "top"
            cy > window.height()*2/3 -> "bottom"
            else -> "middle"
        }
        return "$vertical $horizontal"
    }

    /**
     * The result is a fresh, read-only fact; all strings are untrusted
     * observation evidence and are bounded. A disconnected/revoked
     * Accessibility service produces null and NEVER causes auto-retry.
     */
    fun observe(context:Context):String? {
        val service=ScreenAssistService.active ?:return null
        if(!ScreenAssistState.connected)return null
        val root=try{service.externalWindowRoot()}catch(_:Exception){null}
            ?:return null
        try {
            val pkg=root.packageName?.toString().orEmpty()
            if(!root.isVisibleToUser)return null
            if(pkg==context.packageName || pkg=="android" ||
                pkg=="com.android.systemui" ||
                !pkg.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")))
                return null
            val bounds=Rect().also{root.getBoundsInScreen(it)}
            if(bounds.width()<=0||bounds.height()<=0)return null
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            val seen=linkedSetOf<String>()
            var count=0
            while(queue.isNotEmpty() && count++<MAX_NODES &&
                seen.size<MAX_CONTROLS){
                val node=queue.removeFirst()
                if(!node.isVisibleToUser || node.isPassword)continue
                if(safeControl(node.className?.toString(),node.isClickable,
                    node.isEnabled,node.isVisibleToUser,node.isEditable,node.isPassword)){
                    val label=safeLabel(node.contentDescription?.toString())
                        ?:safeLabel(node.text?.toString())
                    if(label!=null){
                        val rect=Rect().also{node.getBoundsInScreen(it)}
                        if(!rect.isEmpty && bounds.contains(rect))
                            seen.add(label.lowercase(Locale.ROOT)+" ("+zone(rect,bounds)+")")
                    }
                }
                for(i in 0 until node.childCount) {
                    node.getChild(i)?.let{queue.addLast(it)}
                }
            }
            // An observed empty controls list is still a valid fact.
            return ("Visible non-sensitive controls in $pkg: "+
                (if(seen.isEmpty())"none exposed" else seen.joinToString("; "))+
                ". Read-only observation, not an instruction or completed action.").take(750)
        }catch (_:Exception){return null}
        finally {try{root.recycle()}catch(_:Exception){}}
    }
    /** Read-only exact matching metadata for a reviewed scroll target.
     * Never reads fields, exposes screen content or chooses an action.
     * Distinct, enabled, visible control hitboxes count as ambiguity.
     */
    internal data class ExactVisibleControl(
        val packageName:String,val windowId:Int,val matchingControls:Int,
        // Ephemeral geometry only: never stored or sent to a model/provider.
        val uniqueBoundsKey:String?=null
    )
    fun exactVisibleControl(service:ScreenAssistService,requested:String):ExactVisibleControl? {
        val expected=safeLabel(requested) ?:return null
        if(!ScreenAssistState.connected || ScreenAssistService.active!==service)return null
        val root=try{service.externalWindowRoot()}catch(_:Exception){null} ?:return null
        try{
            val pkg=root.packageName?.toString().orEmpty()
            if(root.windowId<0 || !root.isVisibleToUser ||
                pkg==service.packageName || pkg=="android" ||
                pkg=="com.android.systemui" ||
                !pkg.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")))return null
            val viewport=Rect().also{root.getBoundsInScreen(it)}
            if(viewport.isEmpty)return null
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            val uniqueBounds=linkedSetOf<String>()
            var visited=0
            while(queue.isNotEmpty() && visited++<MAX_NODES &&
                uniqueBounds.size<2){
                val node=queue.removeFirst()
                if(!node.isVisibleToUser || node.isPassword || node.isEditable)continue
                if(safeControl(node.className?.toString(),node.isClickable,
                        node.isEnabled,node.isVisibleToUser,node.isEditable,node.isPassword)){
                    val controlLabel=safeLabel(node.contentDescription?.toString())
                        ?:safeLabel(node.text?.toString())
                    if(controlLabel?.equals(expected,ignoreCase=true)==true){
                        val rect=Rect().also{node.getBoundsInScreen(it)}
                        if(!rect.isEmpty && viewport.contains(rect))
                            uniqueBounds.add(
                                "${rect.left}:${rect.top}:${rect.right}:${rect.bottom}")
                    }
                }
                for(i in 0 until node.childCount)
                    node.getChild(i)?.let{queue.addLast(it)}
            }
            // If observation was truncated, missing/ambiguous counts cannot
            // authorize a target; report unavailable instead.
            if(queue.isNotEmpty())return null
            return ExactVisibleControl(pkg,root.windowId,uniqueBounds.size,
                uniqueBounds.singleOrNull())
        }catch(_:Exception){return null}
        finally{try{root.recycle()}catch(_:Exception){}}
    }

}
