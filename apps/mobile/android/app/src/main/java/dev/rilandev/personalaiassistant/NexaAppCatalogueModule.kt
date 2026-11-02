package dev.rilandev.personalaiassistant

import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.Locale

/** Local owner-initiated catalogue search, not a general installed-app inventory. */
class NexaAppCatalogueModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName(): String = "PAANexaAppCatalogue"

    @ReactMethod fun findLaunchableApps(query: String, promise: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            promise.reject("NOT_FOREGROUND", "Search apps from the visible Nexa screen")
            return
        }
        val term = query.trim().lowercase(Locale.ROOT)
        if (term.length !in 2..60) {
            promise.reject("INVALID_QUERY", "Enter at least two and at most 60 characters")
            return
        }
        try {
            val manager = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                manager.queryIntentActivities(intent, 0)
            }
            val results = Arguments.createArray()
            val seen = HashSet<String>()
            activities.asSequence()
                .mapNotNull { activity ->
                    val pkg = activity.activityInfo?.packageName ?: return@mapNotNull null
                    val label = activity.loadLabel(manager)?.toString()?.trim().orEmpty()
                    if (label.isEmpty() ||
                        !(label.lowercase(Locale.ROOT).contains(term) || pkg.lowercase(Locale.ROOT).contains(term)))
                        return@mapNotNull null
                    label.take(80) to pkg
                }
                .sortedWith(compareBy({ it.first.lowercase(Locale.ROOT) }, { it.second }))
                .filter { seen.add(it.second) }
                .take(12)
                .forEach { (label, pkg) ->
                    val item = Arguments.createMap()
                    item.putString("label", label)
                    item.putString("packageName", pkg)
                    item.putString("source", "package_manager")
                    item.putString("launcherPage", "unknown")
                    results.pushMap(item)
                }
            promise.resolve(results)
        } catch (_: SecurityException) {
            promise.reject("PACKAGE_VISIBILITY_DENIED", "Android denied launcher discovery")
        } catch (_: Exception) {
            promise.reject("CATALOGUE_UNAVAILABLE", "Unable to inspect launchable application labels")
        }
    }
}
