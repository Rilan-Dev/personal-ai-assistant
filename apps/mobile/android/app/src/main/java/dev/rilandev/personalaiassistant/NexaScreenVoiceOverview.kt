package dev.rilandev.personalaiassistant

/** Called only AFTER the visible voice approval. Never sends screenshots, text fields or coordinates. */
internal object NexaScreenVoiceOverview {
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
