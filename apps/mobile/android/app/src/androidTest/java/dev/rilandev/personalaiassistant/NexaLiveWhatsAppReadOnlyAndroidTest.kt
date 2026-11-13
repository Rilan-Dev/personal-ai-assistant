package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.app.UiAutomation
import android.content.Context
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in READ-ONLY physical QA. Invoke only with an explicit nexaRecipientHint
 * while ordinary WhatsApp is foreground. Never logs contact names, chat text,
 * node labels, screenshots, phone IDs, field contents, or transcript text.
 * No click, text entry, gesture, search, send or permission change.
 *
 * This demonstrates whether the production matcher has a UNIQUE visible
 * candidate; it is NOT a recipient-opening / composer-typing acceptance test.
 */
@RunWith(AndroidJUnit4::class)
class NexaLiveWhatsAppReadOnlyAndroidTest {
    @Test fun visibleWhatsAppHasOneGroundedRecipientOrVerifiedHeader() {
        val args=InstrumentationRegistry.getArguments()
        val recipient=args.getString("nexaRecipientHint").orEmpty().trim()
        assertTrue("Live QA needs an explicit reviewed recipient hint",
            recipient.length in 3..55 && recipient.none { it.isISOControl() })
        val instrument=InstrumentationRegistry.getInstrumentation()
        val context=instrument.targetContext
        val locked=(context.getSystemService(Context.KEYGUARD_SERVICE)
            as? KeyguardManager)?.isKeyguardLocked==true
        assertTrue("Screen locked. No external window inspection performed.",!locked)
        val automation=instrument.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        assertTrue("Read-only UiAutomation not available",automation!=null)
        try {
            val info=automation.serviceInfo
            info.flags=info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            automation.serviceInfo=info
        }catch(_:Exception) { /* active root fallback only */ }
        var root:android.view.accessibility.AccessibilityNodeInfo?=null
        for(w in automation.windows.sortedByDescending { it.layer }) {
            if(w.type!=AccessibilityWindowInfo.TYPE_APPLICATION ||
                !(w.isActive || w.isFocused))continue
            val candidate=try{w.root}catch(_:Exception){null}?:continue
            if(candidate.packageName?.toString()=="com.whatsapp"){
                root=candidate
                break
            }
            try{candidate.recycle()}catch(_:Exception){}
        }
        if(root==null){
            val active=try{automation.rootInActiveWindow}catch(_:Exception){null}
            if(active?.packageName?.toString()=="com.whatsapp")root=active
            else try{active?.recycle()}catch(_:Exception){}
        }
        val current=root
        assertTrue("The active screen is not an ordinary WhatsApp window.",current!=null)
        try {
            assertTrue("External WhatsApp root is not visible/valid",
                current!!.isVisibleToUser && current.windowId>=0)
            val scan=NexaWhatsAppChatActions.inspect(current,recipient)
            // These aggregate counters are safe to print; never print the
            // recipient or any third-party screen text.
            println("LIVE_WHATSAPP_READONLY_CANDIDATES="+scan.candidates)
            println("LIVE_WHATSAPP_READONLY_ALREADY_OPEN="+scan.alreadyOpen)
            assertTrue("The requested recipient is not uniquely grounded in the current visible window.",
                scan.alreadyOpen || scan.candidates==1)
        }finally{try{current?.recycle()}catch(_:Exception){}}
    }
}
