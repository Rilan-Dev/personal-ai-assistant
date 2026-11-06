package dev.rilandev.personalaiassistant

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaQaFixtureIsolationAndroidTest {
    @Test fun syntheticScreenFixturesUseOnlyTheLightweightDebugProcess() {
        val appContext=InstrumentationRegistry.getInstrumentation().targetContext
        val info=appContext.packageManager.getActivityInfo(
            ComponentName(appContext.packageName,NexaInputQaActivity::class.java.name),
            0
        )

        assertEquals(appContext.packageName,info.packageName)
        assertEquals("${appContext.packageName}:nexa_qa",info.processName)
        assertTrue("Only the debug QA fixture is shell-launchable",info.exported)
    }
}
