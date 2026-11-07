package dev.rilandev.personalaiassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * No test launches a third-party activity or performs an Accessibility
 * gesture. Validate DENY paths and the actual durable one-shot journal.
 */
@RunWith(AndroidJUnit4::class)
class NexaExternalDispatchSafetyAndroidTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()

    @Test fun invalidOriginOrUnresolvedIntentNeverLaunches(){
        val wasPaused=NexaNativeActionSafety.isPaused(ctx)
        try{
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            val benign=Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=nexa"))
            assertEquals(NexaApprovedExternalAction.Result.DENIED,
                NexaApprovedExternalAction.launch(ctx,benign,
                    "com.android.chrome","untrusted-model"))
            assertEquals(NexaApprovedExternalAction.Result.DENIED,
                NexaApprovedExternalAction.launch(ctx,benign,
                    "not a valid package","chat"))
            // No claim or Activity launch when a valid owner changes Pause.
            assertTrue(NexaNativeActionSafety.setPaused(ctx,true))
            assertEquals(NexaApprovedExternalAction.Result.DENIED,
                NexaApprovedExternalAction.launch(ctx,benign,
                    "com.android.chrome","chat"))
            assertTrue(NexaBrowserTaskActions.notificationsApproved(ctx,null)
                .contains("paused",ignoreCase=true))
            assertTrue(NexaBrowserTaskActions.goBackApproved(ctx)
                .contains("paused",ignoreCase=true))
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(ctx,wasPaused))
        }
    }

    @Test fun oneShotNavigationClaimsCannotBeReplayed(){
        val wasPaused=NexaNativeActionSafety.isPaused(ctx)
        try{
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            val first="nav-"+UUID.randomUUID()
            val second="nav-"+UUID.randomUUID()
            assertTrue(NexaUiActionJournal.claim(ctx,first,
                "nav_notifications","com.android.systemui",origin="chat"))
            assertFalse(NexaUiActionJournal.claim(ctx,first,
                "nav_notifications","com.android.systemui",origin="chat"))
            assertTrue(NexaUiActionJournal.finish(ctx,first,"android_rejected"))
            assertFalse(NexaUiActionJournal.claim(ctx,first,
                "nav_notifications","com.android.systemui",origin="chat"))
            assertTrue(NexaUiActionJournal.claim(ctx,second,
                "nav_back","com.example.safe",origin="overlay"))
            assertTrue(NexaUiActionJournal.finish(ctx,second,"unknown"))
            assertFalse(NexaUiActionJournal.claim(ctx,second,
                "nav_back","com.example.safe",origin="overlay"))
            assertTrue(NexaNativeActionSafety.setPaused(ctx,true))
            assertFalse(NexaUiActionJournal.claim(ctx,"nav-"+UUID.randomUUID(),
                "nav_notifications","com.android.systemui",origin="chat"))
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(ctx,wasPaused))
        }
    }
}
