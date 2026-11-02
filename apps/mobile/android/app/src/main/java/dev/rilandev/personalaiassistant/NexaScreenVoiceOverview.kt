package dev.rilandev.personalaiassistant

/** Called only AFTER the visible voice approval. Never sends screenshots, text fields or coordinates. */
internal object NexaScreenVoiceOverview {
    /** Only called after the owner presses Approve in the native voice panel. */
    fun findApproved(target: String): String {
        if(target !in setOf("menu","search","settings","back","close","call",
                "video_call","send","attach","camera","microphone"))
            return "Unsupported icon request. No screen action occurred."
        val service=ScreenAssistService.active
            ?:return "Nexa Screen Assist is not enabled. No icon observation occurred."
        val (snapshot, reason)=NexaScreenActions.inspect(service)
        NexaScreenActions.reset() // Never leave an actionable selection after voice reading.
        if(snapshot==null)return reason
        val matching=snapshot.icons.filter {
            it.semantic==target || (target=="menu" && it.semantic=="more_options")
        }
        if(matching.isEmpty())return "The $target icon was not identified in the visible app. No action occurred."
        if(matching.size>1)return "Multiple $target controls were found. Open Nexa Tools to inspect them separately. No action occurred."
        val icon=matching.single()
        val root=service.externalWindowRoot()
        val position=try {
            if(root==null || root.packageName?.toString()!=snapshot.packageName ||
                root.windowId!=snapshot.windowId) null
            else {
                val bounds=android.graphics.Rect()
                root.getBoundsInScreen(bounds)
                val width=bounds.width()
                val height=bounds.height()
                val x=(icon.bounds.left+icon.bounds.right)/2
                val y=(icon.bounds.top+icon.bounds.bottom)/2
                if(width<=0 || height<=0 || x !in bounds.left..bounds.right ||
                    y !in bounds.top..bounds.bottom) null
                else {
                    val h=when {
                        (x-bounds.left)*3 < width -> "left"
                        (x-bounds.left)*3 > width*2 -> "right"
                        else -> "center"
                    }
                    val v=when {
                        (y-bounds.top)*3 < height -> "top"
                        (y-bounds.top)*3 > height*2 -> "bottom"
                        else -> "middle"
                    }
                    "$v $h"
                }
            }
        } catch (_:Exception) { null } finally { root?.recycle() }
        val description=if(position!=null) " near the $position of the app window" else " on this screen"
        return ("The ${icon.label} icon is visible"+description+
            ". This is a read-only location hint. No tap, call, message or other action was performed.").take(350)
    }

    /**
     * The owner has just tapped the visible overlay's "Approve once" button.
     * Reads bounded *non-editable* accessibility labels from one external
     * foreground application window, never passwords, input values or a
     * screenshot. The returned labels are spoken/sent to the voice provider,
     * and the approval UI must make that transmission explicit.
     */
    fun readVisibleTextApproved():String {
        val service=ScreenAssistService.active
            ?:return "Enable Nexa Screen Assist in Android Accessibility Settings first."
        val root=service.externalWindowRoot()
            ?:return "The current external app does not expose readable screen content."
        try {
            val pkg=root.packageName?.toString().orEmpty()
            if(pkg.isBlank() || pkg==service.packageName)
                return "This window is not available for screen reading."
            val queue=java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(root)
            val labels=LinkedHashSet<String>()
            var inspected=0
            while(queue.isNotEmpty() && inspected<130 && labels.size<16) {
                val node=queue.removeFirst()
                inspected++
                if(!node.isVisibleToUser || node.isPassword || node.isEditable)continue
                val content=node.text?.toString()?.takeIf{it.isNotBlank()}
                    ?:node.contentDescription?.toString().orEmpty()
                val clean=content.replace(Regex("[\\p{Cntrl}]+")," ").trim().take(85)
                if(clean.isNotBlank() && clean.length>=2)labels.add(clean)
                for(i in 0 until node.childCount)
                    node.getChild(i)?.let{ queue.addLast(it) }
            }
            if(labels.isEmpty())return "No readable, non-editable labels were exposed by the current app."
            return ("Visible screen labels from "+pkg+": "+
                labels.joinToString("; ").take(630)+
                ". Read-only observation. No tap or edit occurred.").take(800)
        }catch (_:Exception) {
            return "Android blocked screen reading. No screen information was shared."
        }finally {root.recycle()}
    }

    /** One screen-scroll action after exactly one owner approval. */
    fun scrollApproved(direction:String):String {
        if(direction !in setOf("up","down","left","right"))return "Invalid screen direction."
        val service=ScreenAssistService.active
            ?:return "Enable Nexa Screen Assist in Android Accessibility Settings first."
        val ready=NexaScreenScroll.prepare(service,direction)
        if(!NexaScreenScroll.isPending())return ready
        return NexaScreenScroll.confirm(service)
    }

    /** Only harmless navigation icons, freshly matched against current nodes. */
    fun tapSafeApproved(target:String):String {
        if(target !in setOf("menu","search","settings","back","close"))
            return "Unsafe or unknown icon. No action was performed."
        val service=ScreenAssistService.active
            ?:return "Enable Nexa Screen Assist before requesting a tap."
        val (snap,description)=NexaScreenActions.inspect(service)
        if(snap==null)return description
        val matches=snap.icons.filter{
            it.semantic==target || (target=="menu" && it.semantic=="more_options")
        }.filter{NexaScreenActionPolicy.permits(it)}
        if(matches.size!=1) {
            NexaScreenActions.reset()
            return if(matches.isEmpty())
                "The requested $target navigation icon is not uniquely visible."
            else "Multiple $target icons are visible. Please select one manually."
        }
        if(NexaScreenActions.select(snap.token,matches.single().id)==null) {
            NexaScreenActions.reset()
            return "The icon changed before the approved tap. No action occurred."
        }
        return NexaScreenActions.confirm(service,snap.token)
    }

    fun readApproved(): String {
        val service=ScreenAssistService.active
            ?:return "Nexa Screen Assist is not enabled. Open Android Accessibility settings first."
        val (snapshot,reason)=NexaScreenActions.inspect(service)
        // A voice overview cannot arm a later click: discard any action snapshot.
        NexaScreenActions.reset()
        if(snapshot==null)return reason
        val recognized=snapshot.icons.filter { it.semantic!="unknown" }
            .distinctBy { it.semantic to it.label }
            .take(10)
        if(recognized.isEmpty())return "No reliably labeled icons in the current app."
        return ("Visible controls in "+snapshot.packageName+": "+
            recognized.joinToString(", "){ it.label + " (" + it.semantic + ")" }+
            ". This is an observation only; no screen action was executed.").take(480)
    }
}
