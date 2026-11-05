package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.Promise
import java.lang.reflect.Proxy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Keystore integration: recipient preference is an explicit owner
 * choice, never a transcript-based guess or a recipient identity.
 */
@RunWith(AndroidJUnit4::class)
class NexaMessageMemoryAndroidTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Suppress("DEPRECATION")
    private fun journal()=AgentJournalModule(BridgeReactContext(context))

    private fun invoke(block:(Promise)->Unit):Pair<Any?,String?> {
        var value:Any?=null
        var error:String?=null
        var complete=false
        val promise=Proxy.newProxyInstance(Promise::class.java.classLoader,
            arrayOf(Promise::class.java)) { _,method,args ->
            when(method.name) {
                "resolve" -> {complete=true;value=args?.firstOrNull()}
                "reject" -> {complete=true;error=args?.firstOrNull()?.toString()}
            }
            null
        } as Promise
        block(promise)
        assertTrue("Native preference bridge must settle",complete)
        return value to error
    }

    @Test fun explicitAppChoiceIsEncryptedScopedAndRevocable() {
        val alias="mom test local"
        val other="dad test local"
        val module=journal()
        try {
            assertEquals(true,invoke{module.rememberMessageApp(alias,"whatsapp",it)}.first)
            assertEquals("whatsapp",invoke{journal().rememberedMessageApp(alias,it)}.first)
            assertNull(invoke{journal().rememberedMessageApp(other,it)}.first)
            val prefs=context.getSharedPreferences("paa_secure_journal_v1",Context.MODE_PRIVATE)
            assertFalse("Neither raw alias nor chosen app may be stored as plaintext",
                prefs.all.entries.any { (key,v)->
                    key.contains(alias) || v.toString().contains(alias) ||
                    v.toString().contains("whatsapp")
                })
            assertEquals(true,invoke{journal().forgetMessageApp(alias,it)}.first)
            assertNull(invoke{journal().rememberedMessageApp(alias,it)}.first)
        } finally {
            invoke{journal().forgetMessageApp(alias,it)}
        }
    }
    @Test fun invalidAppsAndUnverifiedContactStringsAreRejected() {
        assertEquals("MESSAGE_MEMORY_WRITE",
            invoke{journal().rememberMessageApp("Mom","unknown-untrusted-app",it)}.second)
        assertEquals("MESSAGE_MEMORY_WRITE",
            invoke{journal().rememberMessageApp("+971551234567","sms",it)}.second)
        assertNull(invoke{journal().rememberedMessageApp("never saved alias",it)}.first)
    }

    @Test fun ownerChoiceHasExplicitProvenanceAndBoundedRetention() {
        val alias="project tester "+System.nanoTime().toString().takeLast(6)
        try{
            assertEquals(true,invoke {
                journal().rememberMessageAppForDays(alias,"sms",7,it)
            }.first)
            val raw=invoke { journal().messageAppPreferenceDetails(alias,it) }.first
            assertTrue(raw is String)
            val record=JSONObject(raw as String)
            assertEquals(2,record.getInt("version"))
            assertEquals("owner_confirmed",record.getString("source"))
            assertEquals("sms",record.getString("appId"))
            assertTrue(record.getLong("savedAt")>0L)
            val validity=record.getLong("expiresAt")-record.getLong("savedAt")
            assertEquals(7L*86_400_000L,validity)
            assertFalse("Recipient aliases are not returned in preference details",
                raw.contains(alias))
            assertEquals("sms",invoke{journal().rememberedMessageApp(alias,it)}.first)
            assertEquals("MESSAGE_MEMORY_WRITE",
                invoke{journal().rememberMessageAppForDays(alias,"imo",0,it)}.second)
            assertEquals("MESSAGE_MEMORY_WRITE",
                invoke{journal().rememberMessageAppForDays(alias,"imo",366,it)}.second)
            assertEquals("sms",invoke{journal().rememberedMessageApp(alias,it)}.first)
            assertEquals(true,invoke{journal().forgetMessageApp(alias,it)}.first)
            assertNull(invoke{journal().messageAppPreferenceDetails(alias,it)}.first)
        }finally{invoke{journal().forgetMessageApp(alias,it)}}
    }

}
