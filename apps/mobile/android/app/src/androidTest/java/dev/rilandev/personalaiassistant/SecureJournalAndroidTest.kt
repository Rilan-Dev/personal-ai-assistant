package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import java.lang.reflect.Proxy
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android Keystore integration tests. Must run on an emulator or real device. */
@RunWith(AndroidJUnit4::class)
class SecureJournalAndroidTest {
    private val appContext: Context get() = ApplicationProvider.getApplicationContext()
    private fun journal() = AgentJournalModule(object : ReactApplicationContext(appContext) {})

    private fun invoke(block: (Promise) -> Unit): Any? {
        var result: Any? = null
        var didResolve = false
        val callback = Proxy.newProxyInstance(
            Promise::class.java.classLoader, arrayOf(Promise::class.java)
        ) { _, method, args ->
            when (method.name) {
                "resolve" -> {
                    didResolve = true
                    result = args?.firstOrNull()
                }
                "reject" -> {
                    val code = args?.firstOrNull()?.toString() ?: "unknown"
                    fail("Native journal rejected unexpectedly: $code")
                }
            }
            null
        } as Promise
        block(callback)
        assertTrue("Native Promise did not resolve", didResolve)
        return result
    }

    @Test fun assistantTileIsRestrictedToSystemAndCannotRecordFromBackground() {
        val component = android.content.ComponentName(appContext, AssistantTileService::class.java)
        val info = appContext.packageManager.getServiceInfo(component, 0)
        assertTrue(info.exported)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", info.permission)
        // Tile launch only opens MainActivity; no wake-word service installed.
    }

    @Test fun encryptedSessionSurvivesRecreatingTheModule() {
        val session = UUID.randomUUID().toString()
        val request = "journal-instrumentation-" + UUID.randomUUID()
        val snapshot = JSONObject()
            .put("version", 1)
            .put("sessionId", session)
            .put("request", request)
            .put("status", "running")
            .put("observations", org.json.JSONArray())
            .put("updatedAt", System.currentTimeMillis()).toString()
        val first = journal()
        assertEquals(true, invoke { first.saveSession(session, snapshot, it) })
        val second = journal()
        val restored = invoke { second.loadSession(session, it) } as String
        assertEquals(request, JSONObject(restored).getString("request"))
        // Keystore ciphertext must not contain plaintext user commands.
        val prefs = appContext.getSharedPreferences("paa_secure_journal_v1", Context.MODE_PRIVATE)
        assertFalse(prefs.all.values.any { it.toString().contains(request) })
    }

    @Test fun actionClaimIsOneTimeAcrossModuleInstances() {
        val session = UUID.randomUUID().toString()
        val call = "msg-1"
        val fingerprint = "test-fingerprint-" + UUID.randomUUID()
        val first = journal()
        assertEquals(true, invoke { first.prepareApproval(session, call, fingerprint, it) })
        assertEquals(false, invoke { first.approveAction(session, call, "changed", it) })
        assertEquals(true, invoke { first.approveAction(session, call, fingerprint, it) })
        assertEquals(false, invoke { first.approveAction(session, call, fingerprint, it) })
        assertEquals(true, invoke { first.claimAction(session, call, fingerprint, true, it) })
        assertEquals(false, invoke { journal().claimAction(session, call, fingerprint, true, it) })
        assertEquals(true, invoke { first.finishAction(session, call, true, it) })
        assertEquals(false, invoke { journal().claimAction(session, call, fingerprint, true, it) })
    }

    @Test fun unapprovedAndConcurrentReplaysFailClosed() {
        val session = UUID.randomUUID().toString()
        val call = "device-1"
        val fp = "test-" + UUID.randomUUID()
        val first = journal()
        assertEquals(false, invoke { first.claimAction(session, call, fp, true, it) })
        assertEquals(true, invoke { first.claimAction(session, call, fp, false, it) })
        assertEquals(false, invoke { journal().claimAction(session, call, fp, false, it) })
    }
}
