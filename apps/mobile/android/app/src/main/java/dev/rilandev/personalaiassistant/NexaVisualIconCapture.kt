package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.Display
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Memory-only crops of UNLABELED image buttons. No full screenshot leaves Kotlin. */
internal data class VisualIconCrop(
    val iconId: String, val bounds: IconBox, val dataUri: String
)
internal data class VisualIconResult(
    val id: String, val packageName: String, val createdAt: Long,
    val expiresInMs: Int, val crops: List<VisualIconCrop>
)
internal object NexaVisualIconCapture {
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val expiry = Runnable { clear() }
    private var armedUntil = 0L
    private var busy = false
    private var result: VisualIconResult? = null
    private var resultAt = 0L

    fun arm(): Boolean = synchronized(lock) {
        if (!ScreenAssistState.connected || Build.VERSION.SDK_INT < 30) return@synchronized false
        clearLocked()
        armedUntil = SystemClock.elapsedRealtime() + 20_000
        handler.postDelayed(expiry,20_000)
        true
    }
    fun isArmed(): Boolean = synchronized(lock) {
        armedUntil > SystemClock.elapsedRealtime() && !busy
    }
    private fun acquire(): Boolean = synchronized(lock) {
        if (armedUntil <= SystemClock.elapsedRealtime() || busy || result != null) false
        else { busy = true; true }
    }
    private fun complete(pkg: String, images: List<VisualIconCrop>) = synchronized(lock) {
        if (!busy) return@synchronized
        busy = false
        armedUntil = 0
        resultAt = SystemClock.elapsedRealtime()
        result = VisualIconResult(UUID.randomUUID().toString(),pkg.take(150),
            System.currentTimeMillis(),60_000,images.take(6))
        handler.removeCallbacks(expiry)
        handler.postDelayed(expiry,60_000)
    }
    fun consume(): VisualIconResult? = synchronized(lock) {
        val item = result
        val remaining = (60_000L - (SystemClock.elapsedRealtime() - resultAt)).toInt()
        val output = if (item != null && remaining > 0) item.copy(expiresInMs = remaining) else null
        clearLocked()
        output
    }
    fun clear() = synchronized(lock) { clearLocked() }
    private fun clearLocked() {
        handler.removeCallbacks(expiry)
        armedUntil = 0
        busy = false
        result = null
        resultAt = 0L
    }
    fun capture(service: ScreenAssistService, pkg: String, candidates: List<ObservedIcon>) {
        if (Build.VERSION.SDK_INT < 30 || !acquire()) return
        val targets = candidates.filter { it.semantic == "unknown" }.take(6)
        if (targets.isEmpty()) { complete(pkg,emptyList()); return }
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        var wrapped: Bitmap? = null
                        try {
                            // A screenshot callback can arrive after the owner changes apps.
                            // Never return crops from a different window/package.
                            val current=service.rootInActiveWindow
                            val sameWindow=try {
                                current?.packageName?.toString()==pkg
                            } finally { current?.recycle() }
                            if (!sameWindow) { complete(pkg,emptyList()); return }
                            wrapped=Bitmap.wrapHardwareBuffer(buffer,screenshot.colorSpace)
                            val bitmap=wrapped
                            if (bitmap == null) { complete(pkg,emptyList()); return }
                            val crops = ArrayList<VisualIconCrop>()
                            targets.forEach { candidate ->
                                imageCrop(bitmap,candidate)?.let { uri ->
                                    crops.add(VisualIconCrop(candidate.id,candidate.bounds,uri))
                                }
                            }
                            complete(pkg,crops)
                        } catch (_: Exception) {
                            complete(pkg,emptyList())
                        } finally {
                            wrapped?.recycle()
                            buffer.close()
                        }
                    }
                    override fun onFailure(errorCode: Int) { complete(pkg,emptyList()) }
                })
        } catch (_: Exception) {
            complete(pkg,emptyList())
        }
    }

    private fun imageCrop(source: Bitmap, icon: ObservedIcon): String? {
        val box = icon.bounds
        // Never return an entire screen, huge crop, or invalid coordinates.
        val width = box.right - box.left
        val height = box.bottom - box.top
        if (width !in 8..240 || height !in 8..240 || box.left < 0 || box.top < 0 ||
            box.right > source.width || box.bottom > source.height) return null
        val clipped = Bitmap.createBitmap(source,box.left,box.top,width,height)
        try {
            val scale = minOf(1f,96f / maxOf(width,height))
            val resized = Bitmap.createScaledBitmap(clipped,
                maxOf(1,(width*scale).toInt()),maxOf(1,(height*scale).toInt()),true)
            try {
                val output = ByteArrayOutputStream()
                if (!resized.compress(Bitmap.CompressFormat.PNG,100,output)) return null
                val bytes = output.toByteArray()
                if (bytes.size > 48_000) return null
                return "data:image/png;base64," + Base64.encodeToString(bytes,Base64.NO_WRAP)
            } finally { if (resized !== clipped) resized.recycle() }
        } finally { clipped.recycle() }
    }
}
