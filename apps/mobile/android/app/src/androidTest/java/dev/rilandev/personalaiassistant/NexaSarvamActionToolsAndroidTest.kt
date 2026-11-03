package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaSarvamActionToolsAndroidTest {
    @Test fun modelToolRegistryListsReviewedToolsOnly() {
        val tools=NexaSarvamActionTools.definitions()
        val names=(0 until tools.length()).map {
            tools.getJSONObject(it).getJSONObject("function").getString("name")
        }.toSet()
        assertTrue(names.contains("get_battery_level"))
        assertTrue(names.contains("request_read_visible_screen"))
        assertTrue(names.contains("request_scroll_screen"))
        assertTrue(names.contains("request_tap_visible_label"))
        assertTrue(names.contains("request_open_installed_app"))
        assertFalse(names.contains("send_message"))
        assertFalse(names.contains("place_call"))
        assertFalse(names.contains("answer_call"))
        assertEquals(names.size,tools.length())
    }

    @Test fun modelActionMustMatchUsersSpokenRequest() {
        assertTrue(NexaSarvamActionTools.ownerRequested("request_scroll_screen",
            """{"direction":"left"}""","Could you please scroll left?"))
        assertFalse(NexaSarvamActionTools.ownerRequested("request_scroll_screen",
            """{"direction":"right"}""","Could you please scroll left?"))
        assertFalse(NexaSarvamActionTools.ownerRequested("request_scroll_screen",
            """{"direction":"right"}""","Do not swipe right."))
        assertTrue(NexaSarvamActionTools.ownerRequested("request_tap_visible_label",
            """{"label":"Ask Meta AI or Search"}""",
            "Pick the Ask Meta AI or Search input box"))
        assertFalse(NexaSarvamActionTools.ownerRequested("request_tap_visible_label",
            """{"label":"Delete account"}""","Read my screen"))
        assertTrue(NexaSarvamActionTools.ownerRequested("request_open_installed_app",
            """{"app":"WhatsApp"}""","Can you open my WhatsApp?"))
        assertFalse(NexaSarvamActionTools.ownerRequested("request_open_installed_app",
            """{"app":"WhatsApp"}""","Tell me about WhatsApp"))
        assertFalse(NexaSarvamActionTools.ownerRequested("request_open_installed_app",
            """{"app":"WhatsApp"}""","Don't open WhatsApp"))
        assertFalse(NexaSarvamActionTools.ownerRequested("send_message",
            """{"recipient":"Mom"}""","Send my mom a WhatsApp message"))
        assertFalse(NexaSarvamActionTools.ownerRequested("request_open_app",
            """{"app":"whatsapp","extra":true}""","Open WhatsApp"))
    }

    @Test fun oneTimeScreenAccessRequiresExplicitSpokenIntent() {
        assertTrue(NexaSarvamActionTools.ownerRequested("request_read_visible_screen",
            "{}","Read my screen"))
        assertTrue(NexaSarvamActionTools.ownerRequested("request_read_visible_screen",
            "{}","What are you seeing here on my screen?"))
        assertFalse(NexaSarvamActionTools.ownerRequested("request_read_visible_screen",
            "{}","I am writing a message"))
        assertFalse(NexaSarvamActionTools.ownerRequested("request_read_visible_screen",
            """{"private":true}""","Read my screen"))
    }
}
