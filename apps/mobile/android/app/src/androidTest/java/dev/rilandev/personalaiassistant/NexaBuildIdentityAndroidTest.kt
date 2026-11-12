package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fail closed before physical USB QA when an APK came from a different source
 * commit than the fetched, audited Git ref. No network, permissions, app state
 * changes, device actions, or release certificates are involved.
 */
@RunWith(AndroidJUnit4::class)
class NexaBuildIdentityAndroidTest {
    @Test fun installedAppRevisionMatchesReviewedSource() {
        val args=InstrumentationRegistry.getArguments()
        val expected=args.getString("nexaExpectedRevision").orEmpty().trim()
        val actual=BuildConfig.PAA_SOURCE_REVISION
        assertTrue("Build source revision must be recorded",actual.isNotBlank())
        if(expected.isNotBlank()){
            assertTrue("Expected source revision must be a complete Git SHA-1",
                expected.matches(Regex("[a-f0-9]{40}")))
            assertEquals("USB QA APK was not assembled from the selected source",
                expected,actual)
            assertEquals("Physical QA may not run against a release or regular debug app",
                "dev.rilandev.personalaiassistant.physicalqa",
                BuildConfig.APPLICATION_ID)
        }
        assertEquals(BuildConfig.APPLICATION_ID,
            InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }
}
