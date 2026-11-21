package dev.rilandev.personalaiassistant

import android.content.Context

/**
 * Instrumentation shares the installed app's private preferences. Tests that
 * change permission policy must restore the owner's exact prior choices.
 */
internal object NexaTestPermissionPreferences {
    private const val STORE = "nexa_voice_action_permissions_v1"

    fun <T> withIsolatedVoicePermissionPolicy(context: Context, block: () -> T): T {
        val preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val original = preferences.all.mapValues { (_, value) ->
            if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
        }
        check(preferences.edit().clear().commit()) {
            "Could not isolate Nexa voice permission preferences for the test"
        }
        try {
            return block()
        } finally {
            val editor = preferences.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit()) {
                "Could not restore Nexa voice permission preferences after the test"
            }
        }
    }
}
