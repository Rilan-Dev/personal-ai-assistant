package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Synthetic interrupted Voice workers; never sends audio, starts an app,
 * touches a real recipient or triggers an external Android side effect.
 */
@RunWith(AndroidJUnit4::class)
class NexaVoiceSessionGuardAndroidTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun session()="voice-"+UUID.randomUUID().toString()

    @Test fun stoppedVoiceCannotDispatchAfterApprovalOrAfterNewSessionStarts(){
        val paused=NexaNativeActionSafety.isPaused(context)
        val old=session()
        val fresh=session()
        try{
            assertTrue(NexaNativeActionSafety.setPaused(context,false))
            assertTrue(NexaNativeActionSafety.armVoiceSession(old))
            val fence=NexaNativeActionSafety.approvalFence(context)!!
            var invoked=0
            NexaVoiceSessionGuard.during(old) {
                assertTrue(NexaNativeActionSafety.permitsNewAction(context))
                assertEquals(true,NexaNativeActionSafety.dispatchWithFence(context,fence){
                    invoked++;true
                })
            }
            assertEquals(1,invoked)
            NexaNativeActionSafety.endVoiceSession(old)
            assertTrue(NexaNativeActionSafety.armVoiceSession(fresh))
            NexaVoiceSessionGuard.during(old) {
                assertFalse(NexaNativeActionSafety.permitsNewAction(context))
                assertNull(NexaNativeActionSafety.approvalFence(context))
                assertNull(NexaNativeActionSafety.dispatchWithFence(context,fence){
                    invoked++;true
                })
                assertFalse(NexaUiActionJournal.claim(context,
                    UUID.randomUUID().toString(),"tap","com.example.safe",
                    origin="live-voice"))
            }
            assertEquals(1,invoked)
            NexaVoiceSessionGuard.during(fresh){
                val newFence=NexaNativeActionSafety.approvalFence(context)
                assertEquals(fence,newFence)
                assertEquals(true,NexaNativeActionSafety.dispatchWithFence(
                    context,newFence!!){invoked++;true})
            }
            assertEquals(2,invoked)
        }finally{
            NexaNativeActionSafety.endVoiceSession(old)
            NexaNativeActionSafety.endVoiceSession(fresh)
            assertTrue(NexaNativeActionSafety.setPaused(context,paused))
        }
    }

    @Test fun stopSerializesWithWorkerBetweenApprovalAndDispatch(){
        val paused=NexaNativeActionSafety.isPaused(context)
        val old=session()
        try{
            assertTrue(NexaNativeActionSafety.setPaused(context,false))
            assertTrue(NexaNativeActionSafety.armVoiceSession(old))
            val fence=NexaNativeActionSafety.approvalFence(context)!!
            val entered=CountDownLatch(1)
            val proceed=CountDownLatch(1)
            val result=AtomicReference<Boolean?>()
            var dispatched=false
            val worker=Thread({
                NexaVoiceSessionGuard.during(old){
                    entered.countDown()
                    check(proceed.await(4,TimeUnit.SECONDS))
                    result.set(NexaNativeActionSafety.dispatchWithFence(
                        context,fence){dispatched=true;true})
                }
            },"voice-stale-synthetic")
            worker.start()
            assertTrue(entered.await(4,TimeUnit.SECONDS))
            NexaNativeActionSafety.endVoiceSession(old)
            proceed.countDown()
            worker.join(5000)
            assertFalse(worker.isAlive)
            assertNull(result.get())
            assertFalse(dispatched)
        }finally{
            NexaNativeActionSafety.endVoiceSession(old)
            assertTrue(NexaNativeActionSafety.setPaused(context,paused))
        }
    }
}
