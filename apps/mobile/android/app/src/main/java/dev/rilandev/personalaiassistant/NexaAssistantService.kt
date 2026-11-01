package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/** System-selected entry point. Never opens microphone on its own. */
class NexaAssistantService : VoiceInteractionService()

/** A compact system assistant session, not an app-wide overlay. */
class NexaVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = NexaVoiceSession(this)
}

internal class NexaVoiceSession(private val app: Context) : VoiceInteractionSession(app) {
    private val handler = Handler(Looper.getMainLooper())
    private var label: TextView? = null
    private val refresh = object : Runnable {
        override fun run() {
            label?.text = when (RealtimeVoiceService.status) {
                "listening" -> if (RealtimeVoiceService.microphoneMuted)
                    "Nexa voice active · microphone muted" else "Nexa voice active · microphone in use"
                "connecting" -> "Connecting to the selected voice provider…"
                "error" -> RealtimeVoiceService.message
                else -> "Nexa ready · tap Start to talk"
            }
            if (label != null) handler.postDelayed(this, 750L)
        }
    }
    private fun button(text: String, action: () -> Unit): Button = Button(app).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }
    override fun onCreateContentView(): View {
        val density = app.resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val panel = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.rgb(21, 26, 46))
                cornerRadius = 24 * density
            }
        }
        panel.addView(TextView(app).apply {
            text = "✦  Nexa AI"
            textSize = 20f
            setTextColor(Color.WHITE)
        })
        label = TextView(app).also {
            it.setTextColor(Color.WHITE)
            it.textSize = 15f
            it.setPadding(0, pad / 2, 0, pad / 2)
            panel.addView(it)
        }
        panel.addView(TextView(app).apply {
            text = "Audio starts only when you tap Start. Android shows a mic indicator and a Stop notification."
            setTextColor(Color.LTGRAY)
        })
        panel.addView(button("Start live voice") { startOwnerVoice() })
        panel.addView(button("Stop live voice") {
            if (RealtimeVoiceService.status == "connecting" || RealtimeVoiceService.status == "listening") {
                try {
                    app.startService(Intent(app, RealtimeVoiceService::class.java).apply {
                        action = RealtimeVoiceService.ACTION_STOP
                    })
                } catch (_: Exception) { label?.text = "Stop failed · use the notification Stop control" }
            }
        })
        panel.addView(button("Open Nexa") {
            try {
                app.startActivity(Intent(app, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                hide()
            } catch (_: Exception) { label?.text = "Unable to open Nexa" }
        })
        panel.addView(button("Dismiss") { hide() })
        return panel
    }
    private fun startOwnerVoice() {
        if (RealtimeVoiceService.status == "listening" || RealtimeVoiceService.status == "connecting") {
            label?.text = "Voice already active. Use Stop to end it."
            return
        }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            label?.text = "Open Nexa and grant microphone permission first."
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            label?.text = "Open Nexa and grant notification permission first."
            return
        }
        if (try { AgentJournalModule.openVoiceCredentials(app) } catch (_: Exception) { null } == null) {
            label?.text = "Configure a live voice provider in Nexa Settings."
            return
        }
        try {
            app.startForegroundService(Intent(app, RealtimeVoiceService::class.java).apply {
                action = RealtimeVoiceService.ACTION_START
            })
            label?.text = "Start requested; awaiting provider connection."
        } catch (_: Exception) { label?.text = "Android blocked startup. Open Nexa to retry." }
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }
    override fun onHide() {
        handler.removeCallbacks(refresh)
        super.onHide()
    }
    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        label = null
        super.onDestroy()
    }
}

class NexaAssistantRoleModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName(): String = "PAANexaAssistantRole"
    private fun roleManager(): RoleManager? = if (Build.VERSION.SDK_INT >= 29)
        context.getSystemService(RoleManager::class.java) else null
    @ReactMethod fun getAssistantRoleStatus(p: Promise) {
        val manager = roleManager()
        val result = Arguments.createMap()
        result.putBoolean("available", manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true)
        result.putBoolean("selected", manager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true)
        result.putBoolean("wakeWordReady", false)
        p.resolve(result)
    }
    @ReactMethod fun requestAssistantRole(p: Promise) {
        val activity = context.currentActivity
        if (activity?.hasWindowFocus() != true) {
            p.reject("NOT_FOREGROUND", "Open Nexa to select it as your assistant")
            return
        }
        val manager = roleManager()
        if (manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) != true) {
            p.resolve("unavailable")
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            p.resolve("already_selected")
            return
        }
        try {
            activity.startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            p.resolve("selection_opened") // Not evidence of a granted role
        } catch (_: Exception) { p.reject("ROLE_REQUEST_FAILED", "Android could not show assistant selection") }
    }
}
