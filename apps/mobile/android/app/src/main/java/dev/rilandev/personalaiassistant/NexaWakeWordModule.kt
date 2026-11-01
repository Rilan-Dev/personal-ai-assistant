package dev.rilandev.personalaiassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.common.LifecycleState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** Owner opt-in; local speech model is downloaded and SHA-256 verified. */
class NexaWakeWordModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    companion object {
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        const val MODEL_HASH = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private val worker = Executors.newSingleThreadExecutor()
        @Volatile var installing = false
        @Volatile var installMessage = ""
        fun modelDir(ctx: Context): File = File(ctx.filesDir, "nexa-offline-wake/" + MODEL_NAME)
        fun modelReady(ctx: Context): Boolean {
            val f = modelDir(ctx)
            return f.isDirectory && File(f, "am/final.mdl").isFile &&
                File(f, "conf/model.conf").isFile &&
                try { File(f, ".verified-sha256").readText().trim() == MODEL_HASH } catch (_: Exception) { false }
        }
    }
    override fun getName() = "PAANexaWakeWord"
    private fun foreground(): Boolean {
        val a = context.currentActivity
        return a != null && !a.isFinishing && context.lifecycleState == LifecycleState.RESUMED
    }
    @ReactMethod fun getStatus(p: Promise) {
        val data = Arguments.createMap()
        data.putBoolean("modelReady", modelReady(context))
        data.putBoolean("installing", installing)
        data.putBoolean("listening", NexaWakeWordService.listening)
        data.putString("state", NexaWakeWordService.state)
        data.putString("message", if (installing) installMessage else NexaWakeWordService.message)
        data.putBoolean("permissionGranted",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        p.resolve(data)
    }
    @ReactMethod fun installModel(p: Promise) {
        if (!foreground()) { p.reject("NOT_FOREGROUND", "Open Nexa to install an offline model"); return }
        if (modelReady(context)) { p.resolve("already_installed"); return }
        synchronized(NexaWakeWordModule::class.java) {
            if (installing) { p.reject("INSTALL_BUSY", "Model already downloading"); return }
            installing = true
            installMessage = "Downloading 40 MB model on-device"
        }
        p.resolve("install_started")
        worker.execute {
            val target = modelDir(context)
            val root = target.parentFile!!
            val archive = File(root, MODEL_NAME + ".zip.partial")
            val stage = File(root, MODEL_NAME + ".extracting")
            try {
                root.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                var length = 0L
                val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                try {
                    if (connection.responseCode != 200) error("Model HTTP error")
                    if (connection.contentLengthLong > 50L * 1024 * 1024) error("Archive too large")
                    connection.inputStream.use { input ->
                        archive.outputStream().use { output ->
                            val buf = ByteArray(32768)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                length += n
                                if (length > 50L * 1024 * 1024) error("Archive limit exceeded")
                                digest.update(buf, 0, n)
                                output.write(buf, 0, n)
                            }
                        }
                    }
                } finally { connection.disconnect() }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                if (hash != MODEL_HASH) error("Model SHA-256 does not match expected")
                stage.deleteRecursively()
                stage.mkdirs()
                val rootPath = stage.canonicalFile
                var extracted = 0L
                var files = 0
                ZipInputStream(archive.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.name.startsWith(MODEL_NAME + "/")) error("ZIP model root mismatch")
                        val relative = entry.name.removePrefix(MODEL_NAME + "/")
                        val out = File(stage, relative).canonicalFile
                        if (out != rootPath && !out.path.startsWith(rootPath.path + File.separator))
                            error("ZIP path traversal refused")
                        if (entry.isDirectory) out.mkdirs()
                        else {
                            if (++files > 2500) error("Too many model files")
                            out.parentFile?.mkdirs()
                            out.outputStream().use { output ->
                                val buf = ByteArray(32768)
                                while (true) {
                                    val n = zip.read(buf)
                                    if (n < 0) break
                                    extracted += n
                                    if (extracted > 180L * 1024 * 1024) error("Extraction limit exceeded")
                                    output.write(buf, 0, n)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
                if (!File(stage, "am/final.mdl").isFile ||
                    !File(stage, "conf/model.conf").isFile) error("Model incomplete")
                File(stage, ".verified-sha256").writeText(MODEL_HASH)
                if (target.exists()) target.deleteRecursively()
                if (!stage.renameTo(target)) error("Model install failed")
                installMessage = "Model installed and checksum verified"
            } catch (e: Exception) {
                installMessage = "Model install failed: " + (e.message ?: "unknown error").take(140)
            } finally {
                archive.delete()
                stage.deleteRecursively()
                installing = false
            }
        }
    }
    @ReactMethod fun startListening(p: Promise) {
        if (!foreground()) { p.reject("NOT_FOREGROUND", "Start listening in the visible Nexa app"); return }
        if (!modelReady(context)) { p.reject("MODEL_REQUIRED", "Download the offline model first"); return }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            p.reject("MIC_REQUIRED", "Grant microphone permission"); return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            p.reject("NOTIFICATIONS_REQUIRED", "Grant notification permission"); return
        }
        if (RealtimeVoiceService.status == "listening" || RealtimeVoiceService.status == "connecting") {
            p.reject("VOICE_BUSY", "Stop live voice before enabling wake-word listening"); return
        }
        try {
            val intent = Intent(context, NexaWakeWordService::class.java)
                .setAction(NexaWakeWordService.START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
            p.resolve("start_requested")
        } catch (_: Exception) { p.reject("MIC_START_DENIED", "Android blocked microphone service") }
    }
    @ReactMethod fun stopListening(p: Promise) {
        try {
            context.stopService(Intent(context, NexaWakeWordService::class.java))
            p.resolve(true)
        } catch (_: Exception) { p.reject("STOP_FAILED", "Could not stop wake-word listening") }
    }
}
