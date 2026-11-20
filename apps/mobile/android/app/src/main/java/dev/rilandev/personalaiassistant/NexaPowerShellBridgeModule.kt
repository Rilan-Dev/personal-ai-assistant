package dev.rilandev.personalaiassistant

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/** The local socket exists only in a debuggable Android build. */
class NexaPowerShellBridgeModule(
    private val context: ReactApplicationContext
) : ReactContextBaseJavaModule(context) {
    @Volatile private var server: NexaPowerShellBridgeServer? = null

    override fun getName(): String = "PAAPowerShellBridge"

    override fun getConstants(): MutableMap<String, Any> =
        mutableMapOf("debugBuild" to BuildConfig.DEBUG)

    @ReactMethod
    fun start(promise: Promise) {
        if (!BuildConfig.DEBUG) {
            promise.resolve(false)
            return
        }
        try {
            val current = server ?: NexaPowerShellBridgeServer(context).also { server = it }
            promise.resolve(current.start())
        } catch (_: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun stop(promise: Promise) {
        try {
            server?.close()
            server = null
            promise.resolve(true)
        } catch (_: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun reply(requestId: String, responseJson: String, promise: Promise) {
        try {
            promise.resolve(server?.reply(requestId, responseJson) == true)
        } catch (_: Exception) {
            promise.resolve(false)
        }
    }
}
