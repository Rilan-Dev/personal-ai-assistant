package dev.rilandev.personalaiassistant

import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit read-only physical acceptance. Requires the real owner-enabled
 * Nexa AccessibilityService. It MUST NOT use instrumentation UiAutomation
 * to bypass Android's service consent when Nexa Accessibility is disabled.
 *
 * Invoke only with nexaRecipientHint while ordinary WhatsApp is foreground.
 * Never logs the contact name, other screen labels, chat content, screenshots,
 * account IDs, message contents, or third-party UI coordinates. No actions.
 */
@RunWith(AndroidJUnit4::class)
class NexaLiveWhatsAppReadOnlyAndroidTest {
    @Test fun visibleWhatsAppHasOneGroundedRecipientOrVerifiedHeader() {
        val recipient=InstrumentationRegistry.getArguments()
            .getString("nexaRecipientHint").orEmpty().trim()
        assertTrue("An explicit owner-reviewed recipient hint is required",
            recipient.length in 3..55 && recipient.none { it.code<32 })
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val locked=(ctx.getSystemService(Context.KEYGUARD_SERVICE)
            as? KeyguardManager)?.isKeyguardLocked==true
        assertTrue("Screen locked; no external window inspected",!locked)

        // The service is connected ONLY after Android's owner-mediated
        // Accessibility setting enables Nexa. Never query external windows
        // through privileged shell instrumentation if it is absent.
        assertTrue("OWNER_ACCESSIBILITY_REQUIRED: Nexa service disconnected",
            ScreenAssistState.connected)
        val service=ScreenAssistService.active
        assertTrue("OWNER_ACCESSIBILITY_REQUIRED: no live service",service!=null)
        val root=try { service!!.externalWindowRoot() }catch(_:Exception){null}
        assertTrue("No permitted external app root; no inspection performed",root!=null)
        try {
            assertTrue("Only visible ordinary WhatsApp is in scope",
                root!!.packageName?.toString()=="com.whatsapp" &&
                    root.windowId>=0 && root.isVisibleToUser)
            val scan=NexaWhatsAppChatActions.inspect(root,recipient)
            // Redacted aggregate evidence only; no text/labels or raw nodes.
            println("LIVE_WHATSAPP_READONLY_CANDIDATES="+scan.candidates)
            println("LIVE_WHATSAPP_READONLY_ALREADY_OPEN="+scan.alreadyOpen)
            assertTrue("Requested recipient is not uniquely grounded on this visible screen",
                scan.alreadyOpen || scan.candidates==1)
        }finally{try{root?.recycle()}catch(_:Exception){}}
    }
}
