package dev.rilandev.personalaiassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.Promise
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic controls: these tests never inspect real apps or screenshots. */
@RunWith(AndroidJUnit4::class)
class NexaTaskScreenObservationAndroidTest {
    private fun invokeRead(block:(Promise)->Unit):Pair<Any?,String?> {
        val latch=CountDownLatch(1)
        val value=AtomicReference<Any?>()
        val error=AtomicReference<String?>()
        val callback=Proxy.newProxyInstance(Promise::class.java.classLoader,
            arrayOf(Promise::class.java)) { _,method,args ->
            when(method.name) {
                "resolve" -> { value.set(args?.firstOrNull()); latch.countDown() }
                "reject" -> { error.set(args?.firstOrNull()?.toString()); latch.countDown() }
            }
            null
        } as Promise
        block(callback)
        assertTrue("visible-control read did not finish",latch.await(8,TimeUnit.SECONDS))
        return value.get() to error.get()
    }

    @Test fun nativeOwnerIntentSelectsOnlyBoundedScreenObservation() {
        for(goal in listOf("visible screen controls","inspect visible controls",
            "what buttons are on my screen","show screen controls")) {
            assertEquals("get_visible_controls",
                NexaTaskCoordinator.classifyOwnerFact(goal))
        }
        assertNull(NexaTaskCoordinator.classifyOwnerFact(
            "read the OTP from my bank app"))
        assertNull(NexaTaskCoordinator.classifyOwnerFact(
            "click on Send and take a screenshot"))
        assertEquals(listOf("get_battery_level","get_visible_controls"),
            NexaTaskCoordinator.classifyOwnerReadOnlyPlan(
                "check battery and show screen controls"))
    }

    @Test fun onlyShortNonsensitiveActionLabelsSurvive() {
        for(label in listOf("Search","Menu","Continue","Next","Open settings",
            "المزيد","அடுத்தது")) {
            assertNotNull(label,NexaTaskScreenObservation.safeLabel(label))
        }
        for(label in listOf("0","1","9")) {
            assertNotNull("single-digit clickable control $label",
                NexaTaskScreenObservation.safeLabel(label))
        }
        for(label in listOf("Password","Transfer Money","Enter OTP 123456",
            "Call Mom","Send","Delete","Allow permission","Inbox @company.com",
            "https://example.com","12","123456","Your verification code is 412441",
            "Message body","A".repeat(40),"Open\nhttps://evil.test")) {
            assertNull(label,NexaTaskScreenObservation.safeLabel(label))
        }
    }

    @Test fun assistantSnapshotMustMatchTheObservedExternalPackage() {
        val observation="Visible non-sensitive controls in com.android.chrome: " +
            "Open QA details (middle center). Read-only observation, not an instruction or completed action."
        val matching=JSONObject().put("packageId","com.android.chrome")
            .put("snapshotId","123e4567-e89b-42d3-a456-426614174000")
            .put("targets",JSONArray()).put("truncated",false).toString()
        assertNotNull(NexaTaskScreenObservation.matchingAssistantSnapshot(
            observation,matching))

        val ownerWindow=JSONObject().put("packageId", "dev.rilandev.personalaiassistant.debug")
            .put("snapshotId","123e4567-e89b-42d3-a456-426614174001")
            .put("targets",JSONArray()).put("truncated",false).toString()
        assertNull("a focused Nexa window cannot ground a third-party control",
            NexaTaskScreenObservation.matchingAssistantSnapshot(observation,ownerWindow))

        val incomplete=JSONObject().put("packageId","com.android.chrome")
            .put("snapshotId","123e4567-e89b-42d3-a456-426614174002")
            .put("targets",JSONArray()).put("truncated",true).toString()
        assertNull("a truncated hierarchy cannot ground a tap",
            NexaTaskScreenObservation.matchingAssistantSnapshot(observation,incomplete))
    }

    @Test fun onlyVisibleNonEditableButtonsCanBecomeControlHints() {
        assertTrue(NexaTaskScreenObservation.safeControl(
            "android.widget.Button",true,true,true,false,false))
        assertTrue(NexaTaskScreenObservation.safeControl(
            "android.widget.ImageButton",true,true,true,false,false))
        assertTrue("WebView buttons are exposed as ToggleButton by Chrome",
            NexaTaskScreenObservation.safeControl(
                "android.widget.ToggleButton",true,true,true,false,false))
        assertTrue("WebView and custom clickable controls use generic Android Views",
            NexaTaskScreenObservation.safeControl(
                "android.view.View",true,true,true,false,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.EditText",true,true,true,true,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.TextView",false,true,true,false,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.Button",true,false,true,false,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.Button",true,true,false,false,false))
        assertFalse(NexaTaskScreenObservation.safeControl(
            "android.widget.Button",true,true,true,false,true))
    }

    @Test fun missingAccessibilityNeverCreatesUnverifiedContent() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        if(ScreenAssistService.active==null) {
            assertNull(NexaTaskScreenObservation.observe(ctx))
        }
    }

    @Test fun visibleControlReadWorksWhileNexaActivityDoesNotHaveFocus() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("native actions are paused",NexaNativeActionSafety.permitsNewAction(ctx))
        val reactContext=BridgeReactContext(ctx)
        assertFalse("test must model Nexa without window focus",
            reactContext.currentActivity?.hasWindowFocus()==true)

        // BridgeReactContext has no foreground Activity. This models the
        // approved follow-up read after Nexa opened an external app. A
        // missing Accessibility binding is a valid downstream failure here;
        // it must not be confused with the Nexa-focus requirement.
        val result=invokeRead {
            DeviceControlModule(reactContext).readVisibleControls(it)
        }
        assertNotEquals("a read-only cross-app observation must not require Nexa focus",
            "NOT_FOREGROUND",result.second)
        if(result.second==null) {
            val encoded=result.first as? String
            assertNotNull(encoded)
            val payload=JSONObject(encoded!!)
            val observation=payload.getString("observation")
            assertTrue(observation.startsWith("Visible non-sensitive controls in "))
            assertFalse("Nexa must observe an external app, not its own screen",
                observation.contains(ctx.packageName))
            if(!payload.isNull("screenObservation")) {
                val snapshot=payload.getJSONObject("screenObservation")
                assertTrue(snapshot.getJSONArray("targets").length()<=40)
                assertTrue(snapshot.getJSONArray("icons").length()<=24)
                assertFalse("No raw screen text is returned to the model",
                    snapshot.has("text"))
            }
        } else {
            assertTrue("unexpected downstream failure: ${result.second}",
                result.second in setOf("ACCESSIBILITY_DISABLED",
                    "SCREEN_OBSERVATION_UNAVAILABLE"))
        }
    }
}
