package dev.rilandev.personalaiassistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * User-invoked, small non-modal overlay. A navigation aid, not remote device
 * control. Never reads the screen, starts microphone, clicks other apps or
 * claims Accessibility permission. Never starts on boot.
 */
class NexaToolOverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "dev.rilandev.personalaiassistant.OVERLAY_SHOW"
        const val ACTION_HIDE = "dev.rilandev.personalaiassistant.OVERLAY_HIDE"
        @Volatile var visible = false
            private set
    }
    private lateinit var wm: WindowManager
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false

    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(19, 26, 48))
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), Color.rgb(91, 102, 158))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_SHOW || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) {
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            render()
        }
        return START_NOT_STICKY
    }
    private fun openTab(tab: String) {
        getSharedPreferences("nexa_overlay", MODE_PRIVATE)
            .edit().putString("pending_tab", tab).apply()
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            collapse()
        } catch (_: Exception) { collapse() }
    }
    private fun collapse() {
        expanded = false
        render()
    }
    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            minHeight = dp(44)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(63, 68, 129))
            setOnClickListener { action() }
        }
    private fun render() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val old = view
        if (old != null) {
            try { wm.removeView(old) } catch (_: Exception) {}
            view = null
            visible = false
        }
        val layout: View = if (!expanded) {
            TextView(this).apply {
                text = "✦"
                contentDescription = "Nexa tools. Tap to expand, drag to move."
                setTextColor(Color.WHITE)
                textSize = 23f
                gravity = Gravity.CENTER
                background = panelBackground()
                setOnClickListener { expanded = true; render() }
                var startX = 0f
                var startY = 0f
                var originX = 0
                var originY = 0
                var moved = false
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            startY = event.rawY
                            originX = params?.x ?: 0
                            originY = params?.y ?: 0
                            moved = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (abs(event.rawX - startX) > dp(8) ||
                                abs(event.rawY - startY) > dp(8)) moved = true
                            if (moved) {
                                params?.let { p ->
                                    p.x = (originX - (event.rawX - startX)).toInt().coerceAtLeast(0)
                                    p.y = (originY + (event.rawY - startY)).toInt()
                                    try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!moved) v.performClick()
                            true
                        }
                        else -> false
                    }
                }
            }
        } else {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = panelBackground()
                addView(TextView(context).apply {
                    text = "✦ Nexa · Quick tools"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                })
                addView(TextView(context).apply {
                    text = "User-controlled · no background microphone"
                    textSize = 11f
                    setTextColor(Color.LTGRAY)
                })
                addView(button("Chat") { openTab("chat") })
                addView(button("Voice") { openTab("voice") })
                addView(button("Tools") { openTab("tools") })
                addView(button("Stop live voice") {
                    try {
                        startService(Intent(context, RealtimeVoiceService::class.java).apply {
                            action = RealtimeVoiceService.ACTION_STOP
                        })
                    } catch (_: Exception) {}
                    collapse()
                })
                addView(button("Collapse") { collapse() })
                addView(button("Hide Nexa") { stopSelf() })
            }
        }
        val p = WindowManager.LayoutParams(
            if (expanded) dp(222) else dp(52),
            if (expanded) WindowManager.LayoutParams.WRAP_CONTENT else dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            x = dp(12)
            y = dp(204)
        }
        params = p
        try {
            wm.addView(layout, p)
            view = layout
            visible = true
        } catch (_: Exception) {
            view = null
            visible = false
            stopSelf()
        }
    }
    override fun onDestroy() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        visible = false
        super.onDestroy()
    }
}
