package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Local owner emergency-stop acceptance, no real app is launched. */
@RunWith(AndroidJUnit4::class)
class NexaNativeActionSafetyAndroidTest {
    @Test fun pauseAndResumeInvalidateEveryStagedNativeActionFence(){
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val wasPaused=NexaNativeActionSafety.isPaused(ctx)
        try{
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            val oldFence=NexaNativeActionSafety.approvalFence(ctx)
            assertNotNull(oldFence)
            var invoked=0
            assertEquals(true,NexaNativeActionSafety.dispatchWithFence(ctx,oldFence!!){
                invoked++;true
            })
            assertEquals(1,invoked)
            assertTrue(NexaNativeActionSafety.setPaused(ctx,true))
            assertNull(NexaNativeActionSafety.approvalFence(ctx))
            assertFalse(NexaNativeActionSafety.approvalFenceCurrent(ctx,oldFence))
            assertNull(NexaNativeActionSafety.dispatchWithFence(ctx,oldFence){
                invoked++;true
            })
            assertEquals(1,invoked)
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            assertFalse("An old reviewed UI action cannot resume after Pause",
                NexaNativeActionSafety.approvalFenceCurrent(ctx,oldFence))
            assertNull(NexaNativeActionSafety.dispatchWithFence(ctx,oldFence){
                invoked++;true
            })
            assertEquals(1,invoked)
            val fresh=NexaNativeActionSafety.approvalFence(ctx)
            assertNotNull(fresh)
            assertNotEquals(oldFence,fresh)
            assertEquals(false,NexaNativeActionSafety.dispatchWithFence(ctx,fresh!!){
                invoked++;false
            })
            assertEquals(2,invoked)
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(ctx,wasPaused))
        }
    }

    @Test fun pausePersistsAndBlocksNewClaimAfterNoReplayReceipt(){
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val previouslyPaused=NexaNativeActionSafety.isPaused(ctx)
        try {
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            assertFalse(NexaNativeActionSafety.isPaused(ctx))
            val before="owner-"+UUID.randomUUID()
            assertTrue(NexaUiActionJournal.claim(ctx,before,"tap",
                "com.example.safe",origin="overlay"))
            val beforePauseGeneration=NexaNativeActionSafety.pauseGeneration(ctx)
            assertTrue(NexaNativeActionSafety.setPaused(ctx,true))
            assertEquals(beforePauseGeneration+1L,
                NexaNativeActionSafety.pauseGeneration(ctx))
            assertTrue(NexaNativeActionSafety.isPaused(ctx))
            assertFalse(NexaNativeActionSafety.permitsNewAction(ctx))
            assertFalse("Overlay must stop",NexaUiActionJournal.claim(
                ctx,"owner-"+UUID.randomUUID(),"tap","com.example.safe",origin="overlay"))
            assertFalse("Voice must stop",NexaUiActionJournal.claim(
                ctx,"owner-"+UUID.randomUUID(),"scroll_down","com.example.safe",
                origin="live-voice"))
            assertFalse("Chat launch must stop",NexaUiActionJournal.claim(
                ctx,"owner-"+UUID.randomUUID(),"app_launch","com.example.safe",
                origin="chat"))
            assertFalse("Reviewed text must stop",NexaUiActionJournal.claim(
                ctx,"owner-"+UUID.randomUUID(),"text_fill","com.example.safe",
                "a".repeat(64),origin="overlay"))
            val stoppedGeneration=NexaNativeActionSafety.pauseGeneration(ctx)
            assertTrue(NexaNativeActionSafety.setPaused(ctx,false))
            assertTrue(NexaNativeActionSafety.permitsNewAction(ctx))
            assertEquals("Resume cannot restore old Voice approval generation",
                stoppedGeneration,NexaNativeActionSafety.pauseGeneration(ctx))
            assertFalse("Resuming does not reset a permanently consumed token",
                NexaUiActionJournal.claim(ctx,before,"tap","com.example.safe",
                    origin="overlay"))
            assertTrue("Fresh owner choice can be used after explicit resumption",
                NexaUiActionJournal.claim(ctx,"owner-"+UUID.randomUUID(),"tap",
                    "com.example.safe",origin="overlay"))
        }finally{
            assertTrue(NexaNativeActionSafety.setPaused(ctx,previouslyPaused))
        }
    }
}
