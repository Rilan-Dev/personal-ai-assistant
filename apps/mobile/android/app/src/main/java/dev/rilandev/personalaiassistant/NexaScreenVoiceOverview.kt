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
