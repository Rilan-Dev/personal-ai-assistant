package dev.rilandev.personalaiassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * User-started, launcher-only icon discovery. The service never reads other
 * application windows, never clicks, and cannot receive model-selected coordinates.
 * Android Accessibility Settings must separately enable this specific service.
 */
internal object LauncherNavigationState {
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var active: Boolean = false
    private var expiry: Long = 0
    private var expectedHome: String = ""
    private var swipeCount: Int = 0
    private var busy: Boolean = false
    private var seenHome: Boolean = false
    private var outcome: String = "idle"
    private var resultExpires: Long = 0
    @Volatile var connected: Boolean = false
    private val timeout = Runnable {
        synchronized(lock) {
            if (active) finishLocked("timeout")
        }
    }
    private fun finishLocked(result: String) {
        active = false
        busy = false
        expectedHome = ""
        expiry = 0
        outcome = result
        resultExpires = SystemClock.elapsedRealtime() + 60_000
        handler.removeCallbacks(timeout)
    }
    fun begin(home: String): Boolean = synchronized(lock) {
        if (!connected || home.isBlank() || active) return@synchronized false
        outcome = "searching"
        resultExpires = 0
        active = true
        expectedHome = home
        swipeCount = 0
        busy = false
        seenHome = false
        expiry = SystemClock.elapsedRealtime() + 25_000
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 25_000)
        true
    }
    fun allows(pkg: String): Boolean = synchronized(lock) {
        val accepted = active && SystemClock.elapsedRealtime() < expiry && pkg == expectedHome
        if (accepted) seenHome = true
        accepted
    }
    fun markMissingHome(pkg: String) = synchronized(lock) {
        if (active && seenHome && pkg.isNotBlank() && pkg != expectedHome) finishLocked("wrong_screen")
    }
    fun found(): Unit = synchronized(lock) {
        if (active) finishLocked("found")
    }
    fun beginSwipe(): Int = synchronized(lock) {
        if (!active || busy) return@synchronized -1
        if (swipeCount >= 8) {
            finishLocked("not_found")
            return@synchronized -1
        }
        busy = true
        swipeCount += 1
        swipeCount
    }
    fun swipeFinished(ok: Boolean) = synchronized(lock) {
        busy = false
        if (!ok && active) finishLocked("gesture_blocked")
    }
    fun stop(result: String = "cancelled") = synchronized(lock) {
        if (active) finishLocked(result)
    }
    fun resetOnDestroy() {
        connected = false
        stop("service_stopped")
    }
    fun status(): Pair<String, Int> = synchronized(lock) {
        if (!active && resultExpires > 0 && SystemClock.elapsedRealtime() > resultExpires) {
            outcome = "expired"
            resultExpires = 0
        }
        Pair(outcome, swipeCount)
    }
}

class NexaLauncherNavigationService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val analyze = object : Runnable {
        override fun run() { inspect() }
    }
    override fun onServiceConnected() {
        LauncherNavigationState.connected = true
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || LauncherNavigationState.status().first != "searching") return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        // Ignore system-owned transient overlays; abort if user moves to another app.
        if (pkg == "com.android.systemui") return
        if (!LauncherNavigationState.allows(pkg)) {
            LauncherNavigationState.markMissingHome(pkg)
            return
        }
        handler.removeCallbacks(analyze)
        handler.postDelayed(analyze, 250L)
    }
    private fun inspect() {
        val root = rootInActiveWindow ?: return
        try {
            val pkg = root.packageName?.toString().orEmpty()
            if (!LauncherNavigationState.allows(pkg)) {
                LauncherNavigationState.markMissingHome(pkg)
                return
            }
            if (hasGitHubIcon(root)) {
                LauncherNavigationState.found()
                return
            }
            swipe(pkg)
        } finally {
            root.recycle()
        }
    }
    private fun hasGitHubIcon(root: AccessibilityNodeInfo): Boolean {
        val nodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
        nodes.add(root)
        var count = 0
        while (!nodes.isEmpty() && count < 250) {
            val node = nodes.removeFirst()
            count++
            if (node.isVisibleToUser && !node.isPassword) {
                val name = (node.contentDescription?.toString()
                    ?: node.text?.toString()).orEmpty().trim()
                // Exact, not a substring of potentially private launcher widget text.
                if (name.equals("GitHub", ignoreCase = true)) {
                    if (node !== root) node.recycle()
                    nodes.forEach { if (it !== root) it.recycle() }
                    return true
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { nodes.add(it) }
                }
            }
            if (node !== root) node.recycle()
        }
        return false
    }
    private fun swipe(homePackage:String) {
        if(!NexaNativeActionSafety.permitsNewAction(this)){
            LauncherNavigationState.stop("owner_paused")
            return
        }
        if(!homePackage.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")) ||
            !LauncherNavigationState.allows(homePackage)) {
            LauncherNavigationState.stop("wrong_screen")
            return
        }
        val fence=NexaNativeActionSafety.approvalFence(this)
            ?:run { LauncherNavigationState.stop("owner_paused");return }
        val index = LauncherNavigationState.beginSwipe()
        if (index < 0) return
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val path = Path()
        // Four left swipes followed by four right swipes. No vertical gesture
        // can open notifications or an app drawer.
        val left = index <= 4
        path.moveTo(width * (if (left) .83f else .17f), height * .52f)
        path.lineTo(width * (if (left) .17f else .83f), height * .52f)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 380))
            .build()
        // This bounded launcher gesture belongs to the owner-initiated
        // navigation request, but is still a consequential Android effect.
        // Reserve its permanent one-time token BEFORE any gesture dispatch.
        val token=java.util.UUID.randomUUID().toString()
        val kind=if(left)"scroll_left" else "scroll_right"
        if(!NexaUiActionJournal.claim(this,token,kind,homePackage,origin="chat")) {
            LauncherNavigationState.stop("action_journal_unavailable")
            return
        }
        try {
            val accepted=NexaNativeActionSafety.dispatchWithFence(this,fence) {
                val current=rootInActiveWindow
                    ?:return@dispatchWithFence false
                val stillOnHome=try{
                    current.packageName?.toString()==homePackage &&
                        current.isVisibleToUser &&
                        LauncherNavigationState.allows(homePackage)
                }catch(_:Exception){false}
                finally{try{current.recycle()}catch(_:Exception){}}
                if(!stillOnHome)return@dispatchWithFence false
                dispatchGesture(gesture,object:GestureResultCallback(){
                override fun onCompleted(gestureDescription:GestureDescription?) {
                    Thread({
                        val saved=NexaUiActionJournal.finish(
                            this@NexaLauncherNavigationService,token,"android_accepted")
                        handler.post {
                            LauncherNavigationState.swipeFinished(saved)
                            if(saved) {
                                handler.removeCallbacks(analyze)
                                handler.postDelayed(analyze,550L)
                            }
                        }
                    },"nexa-launcher-gesture-result").start()
                }
                override fun onCancelled(gestureDescription:GestureDescription?) {
                    Thread({
                        NexaUiActionJournal.finish(
                            this@NexaLauncherNavigationService,token,"android_rejected")
                        handler.post{LauncherNavigationState.swipeFinished(false)}
                    },"nexa-launcher-gesture-rejected").start()
                }
                },handler)
            }
            if(accepted!=true){
                NexaUiActionJournal.finish(this,token,"android_rejected")
                LauncherNavigationState.stop(
                    if(accepted==null)"owner_paused" else "gesture_blocked")
                LauncherNavigationState.swipeFinished(false)
            }
        }catch(_:Exception){
            NexaUiActionJournal.finish(this,token,"unknown")
            LauncherNavigationState.stop("gesture_outcome_uncertain")
        }
    }
    override fun onInterrupt() {
        handler.removeCallbacks(analyze)
        LauncherNavigationState.stop("interrupted")
    }
    override fun onDestroy() {
        handler.removeCallbacks(analyze)
        LauncherNavigationState.resetOnDestroy()
        super.onDestroy()
    }
}

class NexaLauncherModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName() = "PAANexaLauncher"
    @ReactMethod fun getNavigationStatus(p: Promise) {
        val (state, steps) = LauncherNavigationState.status()
        val result = Arguments.createMap()
        result.putBoolean("enabled", LauncherNavigationState.connected)
        result.putString("state", state)
        result.putInt("swipes", steps)
        p.resolve(result)
    }
    @ReactMethod fun findGitHubIcon(p: Promise) {
        if (context.currentActivity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Start launcher navigation from the visible Nexa app")
            return
        }
        if (!NexaNativeActionSafety.permitsNewAction(context)) {
            p.reject("ACTIONS_PAUSED","Resume Nexa actions before starting launcher navigation")
            return
        }
        if (context.packageManager.getLaunchIntentForPackage("com.github.android") == null) {
            p.reject("NOT_INSTALLED", "GitHub is not installed or not visible")
            return
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val home = context.packageManager.resolveActivity(intent, 0)?.activityInfo
        val expected = home?.packageName.orEmpty()
        if (expected.isBlank() || expected == "android" ||
            expected == "com.android.systemui" || expected == context.packageName) {
            p.reject("HOME_UNAVAILABLE", "A supported default Android launcher could not be verified")
            return
        }
        if (!LauncherNavigationState.begin(expected)) {
            p.reject("NAVIGATION_UNAVAILABLE", "Enable Nexa Launcher Navigation in Android Accessibility settings")
            return
        }
        when(NexaApprovedExternalAction.launch(context,intent,expected,"chat")){
            NexaApprovedExternalAction.Result.ACCEPTED->
                p.resolve(true) // The launcher still must be visually checked.
            NexaApprovedExternalAction.Result.DENIED->{
                LauncherNavigationState.stop("launch_failed")
                p.reject("HOME_FAILED",
                    "The home launcher changed or Nexa actions were paused. No launch.")
            }
            NexaApprovedExternalAction.Result.UNCERTAIN->{
                LauncherNavigationState.stop("launch_uncertain")
                p.reject("HOME_UNCERTAIN",
                    "The launcher handoff is uncertain. No automatic retry.")
            }
        }
    }
    @ReactMethod fun cancelNavigation(p: Promise) {
        LauncherNavigationState.stop()
        p.resolve(true)
    }
}
