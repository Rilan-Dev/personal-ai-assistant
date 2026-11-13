package dev.rilandev.personalaiassistant

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import dev.rilandev.maestroportable.MaestroQuery
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

    /** A relative relationship is only trustworthy while its ONE matching
     * anchor remains on the same current screen and at the reviewed bounds.
     * Re-read a fresh root at dispatch, never trust stale node references.
     */
    private fun anchorStillUnique(root:AccessibilityNodeInfo,
                                  anchor:String,reviewedAnchorBounds:Rect):Boolean {
        return try {
            val screen=Rect().also { root.getBoundsInScreen(it) }
            if(screen.isEmpty || !root.isVisibleToUser)return false
            val expected=listOf(reviewedAnchorBounds.left,reviewedAnchorBounds.top,
                reviewedAnchorBounds.right,reviewedAnchorBounds.bottom).joinToString(":")
            val unique=linkedSetOf<String>()
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var inspected=0
            while(queue.isNotEmpty() && inspected++<200) {
                val node=queue.removeFirst()
                if(!node.isVisibleToUser || node.isPassword || node.isEditable)continue
                if(node.isEnabled &&
                    listOf(node.text?.toString().orEmpty(),
                        node.contentDescription?.toString().orEmpty())
                        .any { it.trim().equals(anchor,ignoreCase=true) }) {
                    val box=Rect().also { node.getBoundsInScreen(it) }
                    if(!box.isEmpty && screen.contains(box))
                        unique.add(listOf(box.left,box.top,box.right,
                            box.bottom).joinToString(":"))
                }
                for(i in 0 until node.childCount)
                    node.getChild(i)?.let { queue.addLast(it) }
            }
            queue.isEmpty() && unique.size==1 && unique.single()==expected
        }catch (_:Exception){false}
    }

    fun approved(service:ScreenAssistService, requested:String,
                 origin:String="overlay", claimedToken:(String)->Unit={}):String =
        approvedRelative(service,requested,null,origin,claimedToken)

    /**
     * A separately owner-reviewed, non-sensitive relative selector. This
     * preserves the original action executor, Pause fence and permanent claim.
     * A relationship chooses a target only; it cannot authorize a gesture.
     */
    fun approvedRelative(service:ScreenAssistService, requested:String,
                         relative:NexaMaestroRelativeTap?,
                         origin:String="overlay",
                         claimedToken:(String)->Unit={}):String {
        val label=requested.trim()
        if(relative!=null && (label!=relative.target ||
            NexaMaestroRelativeTap.fromAction(relative.toAction())!=relative))
            return "Invalid relative target. No action attempted."
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
            val anchors=linkedMapOf<String,AccessibilityNodeInfo>()
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
                if(relative!=null && !node.isEditable && node.isEnabled &&
                    listOf(node.text?.toString().orEmpty(),
                        node.contentDescription?.toString().orEmpty())
                        .any { it.trim().equals(relative.anchor,ignoreCase=true) }) {
                    val anchorBounds=Rect().also { node.getBoundsInScreen(it) }
                    if(!anchorBounds.isEmpty && rootRect.contains(anchorBounds)) {
                        anchors[listOf(anchorBounds.left,anchorBounds.top,
                            anchorBounds.right,anchorBounds.bottom).joinToString(":")]=node
                    }
                }
                val visible=if(node.isEditable)
                    listOf(node.contentDescription?.toString().orEmpty(),
                        node.hintText?.toString().orEmpty())
                    else listOf(node.text?.toString().orEmpty(),
                        node.contentDescription?.toString().orEmpty())
                val requestedKey=labelKey(label)
                val match=visible.any {
                    it.trim().equals(label,ignoreCase=true) ||
                        requestedKey.isNotBlank() && labelKey(it)==requestedKey ||
                        (relative==null && pkg=="com.whatsapp" &&
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
            if(relative==null && targets.size!=1)
                return "Multiple controls named $label are visible. No tap performed."
            if(relative!=null && anchors.size!=1)
                return "The relative anchor is missing or ambiguous. No tap performed."
            val candidates=targets.values.toList()
            // The historical search-field exception permits focus only.
            // Maestro's generic selector intentionally rejects editable nodes.
            // The same owner-reviewed hierarchy must still hold at final
            // dispatch, not merely at initial proposal review.
            var reviewedHierarchyQuery:MaestroQuery?=null
            var reviewedHierarchyCandidates:List<NexaMaestroTapCandidate> = emptyList()
            var reviewedHierarchyIndex:Int?=null
            val found=if(candidates.any { it.isEditable }) {
                if(relative!=null || candidates.size!=1 || !label.equals("search",ignoreCase=true))
                    return "Multiple or unsafe editable controls. No tap performed."
                candidates.single()
            } else {
                val grounded=candidates.map { candidate ->
                    val box=Rect().also { candidate.getBoundsInScreen(it) }
                    NexaMaestroTapCandidate(
                        label, candidate.viewIdResourceName.orEmpty(), box,
                        candidate.isVisibleToUser, candidate.isEditable,
                        candidate.isPassword, candidate.isClickable, candidate.isEnabled
                    )
                }
                val contextAnchors=anchors.values.map { anchorNode ->
                    val box=Rect().also { anchorNode.getBoundsInScreen(it) }
                    NexaMaestroTapCandidate(
                        relative!!.anchor,anchorNode.viewIdResourceName.orEmpty(),box,
                        anchorNode.isVisibleToUser,false,false,false,anchorNode.isEnabled
                    )
                }
                val query=if(relative==null) MaestroQuery(text=label)
                else MaestroQuery(
                    text=label,
                    belowText=relative.anchor.takeIf { relative.direction=="below" },
                    aboveText=relative.anchor.takeIf { relative.direction=="above" },
                    leftOfText=relative.anchor.takeIf { relative.direction=="left" },
                    rightOfText=relative.anchor.takeIf { relative.direction=="right" },
                    childOfText=relative.anchor.takeIf { relative.direction=="child" }
                )
                // A true child relationship must use actual Accessibility
                // ancestry, not the flattened adapter's synthetic sibling list.
                // Geometric relations retain BOTH independent selection gates.
                val index=if(relative?.direction=="child") {
                    NexaMaestroAccessibilityHierarchy.choose(
                        root,pkg,root.windowId,rootRect,query,grounded
                    )
                } else {
                    val geometric=NexaMaestroTargetSelection.choose(
                        pkg,root.windowId,pkg,root.windowId,rootRect,
                        query,grounded,contextAnchors
                    )
                    if(geometric!=null && relative!=null &&
                        NexaMaestroAccessibilityHierarchy.choose(
                            root,pkg,root.windowId,rootRect,query,grounded
                        )!=geometric) null
                    else geometric
                } ?:return "The target or its unique live hierarchy could not be verified. No tap performed."
                if(relative!=null) {
                    reviewedHierarchyQuery=query
                    reviewedHierarchyCandidates=grounded
                    reviewedHierarchyIndex=index
                }
                candidates[index]
            }
            val reviewedBounds=Rect().also { found.getBoundsInScreen(it) }
            val reviewedAnchorBounds=anchors.values.singleOrNull()?.let { anchor ->
                Rect().also { anchor.getBoundsInScreen(it) }
            }
            val token=UUID.randomUUID().toString()
            val before=NexaUiActionVerifier.fingerprint(root)
            if(!NexaUiActionJournal.claim(service,token,"tap",pkg,origin=origin))
                return "Action journal rejected this request. No tap occurred."
            claimedToken(token)
            NexaUiActionVerifier.begin(token,"tap",pkg,root.windowId,before)
            // Clicking a verified search input transfers focus only. Never
            // use ACTION_SET_TEXT or read/edit the field's current contents.
            val clicked=NexaNativeActionSafety.dispatchWithFence(service,generation) {
                // A permitted window can change between approval and dispatch.
                // Revalidate current package, window and candidate geometry
                // before consuming the one-shot click. Never retry uncertainty.
                val liveRoot=service.externalWindowRoot()
                    ?:return@dispatchWithFence false
                try {
                    val currentBounds=Rect().also { found.getBoundsInScreen(it) }
                    val liveHierarchyQuery=reviewedHierarchyQuery
                    val liveHierarchyIndex=reviewedHierarchyIndex
                    val hierarchyStillVerified=relative==null ||
                        (liveHierarchyQuery!=null && liveHierarchyIndex!=null &&
                            NexaMaestroAccessibilityHierarchy.choose(
                                liveRoot,pkg,root.windowId,rootRect,
                                liveHierarchyQuery,reviewedHierarchyCandidates
                            )==liveHierarchyIndex)
                    if(!NexaMaestroTargetSelection.sameWindow(
                            pkg,root.windowId,
                            liveRoot.packageName?.toString().orEmpty(),liveRoot.windowId
                        ) || !hierarchyStillVerified ||
                        !found.isVisibleToUser || !found.isEnabled ||
                        currentBounds!=reviewedBounds ||
                        (relative!=null && (reviewedAnchorBounds==null ||
                            !anchorStillUnique(liveRoot,relative.anchor,reviewedAnchorBounds)))) false
                    else found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } finally {
                    try {liveRoot.recycle()} catch (_:Exception) {}
                }
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
