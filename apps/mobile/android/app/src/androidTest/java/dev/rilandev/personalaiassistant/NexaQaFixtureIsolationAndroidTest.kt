package dev.rilandev.personalaiassistant

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaQaFixtureIsolationAndroidTest {
    @Test fun syntheticScreenFixturesLiveOutsideTheReactNativeAppProcess() {
        val appContext=InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureContext=InstrumentationRegistry.getInstrumentation().context
        val info=fixtureContext.packageManager.getActivityInfo(
            ComponentName(fixtureContext.packageName,NexaInputQaActivity::class.java.name),
            0
        )

        assertEquals(fixtureContext.packageName,info.packageName)
        assertEquals("${fixtureContext.packageName}:nexa_qa",info.processName)
        assertTrue("Only the instrumentation fixture is shell-launchable",info.exported)
        assertNotEquals("Fixtures must not be components of the React Native app",
            appContext.packageName,info.packageName)
    }
}
