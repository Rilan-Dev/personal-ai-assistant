package dev.rilandev.personalaiassistant

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shared adaptive glass UI for Android's three Nexa entry points: the
 * system-selected assistant, owner-enabled tool bubble and wake fallback.
 *
 * Auto follows Android light/dark mode. On Android 12+, accent comes from
 * Android's wallpaper-derived system color palette; older phones use Nexa's
 * cyan/violet fallback. No wallpaper or screen pixels are read or uploaded.
 * Native translucent drawables approximate glass without blurring other apps.
 */
internal object NexaOverlayTheme {
    private const val PREF = "nexa_overlay_ui"
    private const val KEY = "theme_mode"
    const val AUTO = "auto"
    const val DARK = "dark"
    const val LIGHT = "light"

    data class Palette(
        val isDark: Boolean,
        val card: Int,
        val cardHighlight: Int,
        val inset: Int,
        val outline: Int,
        val title: Int,
        val secondary: Int,
        val accent: Int,
        val button: Int,
        val buttonText: Int,
        val success: Int
    )

    fun mode(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, AUTO)
            ?.takeIf { it == AUTO || it == DARK || it == LIGHT } ?: AUTO

    fun cycleMode(ctx: Context): String {
        val next = when (mode(ctx)) { AUTO -> DARK; DARK -> LIGHT; else -> AUTO }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, next).apply()
        return next
    }

    private fun dynamicColor(ctx: Context, resource: String, fallback: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback
        return try {
            val id = ctx.resources.getIdentifier(resource, "color", "android")
            if (id == 0) fallback else ctx.getColor(id)
        } catch (_: Exception) { fallback }
    }

    fun palette(ctx: Context): Palette {
        val systemDark = (ctx.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (mode(ctx)) { DARK -> true; LIGHT -> false; else -> systemDark }
        val accent = if (dark) dynamicColor(ctx, "system_accent1_300", Color.rgb(132, 190, 255))
            else dynamicColor(ctx, "system_accent1_600", Color.rgb(64, 79, 203))
        return if (dark) Palette(
            true, Color.argb(226, 18, 27, 49), Color.argb(233, 42, 50, 86),
            Color.argb(218, 36, 46, 75), Color.argb(205, 102, 124, 189),
            Color.WHITE, Color.rgb(198, 211, 236), accent,
            Color.rgb(48, 57, 104), Color.WHITE, Color.rgb(94, 238, 180)
        ) else Palette(
            false, Color.argb(236, 249, 251, 255), Color.argb(242, 235, 239, 255),
            Color.argb(228, 230, 235, 250), Color.argb(210, 145, 154, 195),
            Color.rgb(25, 34, 59), Color.rgb(78, 88, 116), accent,
            Color.rgb(221, 227, 252), Color.rgb(29, 39, 86), Color.rgb(0, 119, 89)
        )
    }

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    fun card(ctx: Context, p: Palette, radius: Int = 28): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(p.cardHighlight, p.card, p.card)).apply {
            cornerRadius = dp(ctx, radius).toFloat()
            setStroke(dp(ctx, 1).coerceAtLeast(1), p.outline)
        }

    fun inset(ctx: Context, p: Palette, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply {
            setColor(p.inset)
            cornerRadius = dp(ctx, radius).toFloat()
            setStroke(dp(ctx, 1).coerceAtLeast(1), p.outline)
        }

    fun label(ctx: Context, value: String, p: Palette, size: Float = 14f,
              muted: Boolean = false): TextView = TextView(ctx).apply {
        text = value
        textSize = size
        setTextColor(if (muted) p.secondary else p.title)
        setPadding(dp(ctx, 3), dp(ctx, 5), dp(ctx, 3), dp(ctx, 5))
    }

    fun action(ctx: Context, value: String, p: Palette,
               run: () -> Unit): Button = Button(ctx).apply {
        text = value
        contentDescription = value
        textSize = 12f
        isAllCaps = false
        minHeight = dp(ctx, 44)
        setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0)
        setTextColor(p.buttonText)
        background = inset(ctx, p, 17).apply { setColor(p.button) }
        setOnClickListener { run() }
    }

    /** Decorative, not an indication that the mic is capturing. Voice status
     * is always shown separately as text and in the system notification. */
    fun waveform(ctx: Context, p: Palette): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = "Assistant waveform illustration"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val heights = intArrayOf(4, 10, 17, 24, 14, 29, 16, 22, 10, 4)
            heights.forEach { h ->
                val bar = View(ctx).apply {
                    background = GradientDrawable().apply {
                        setColor(p.accent)
                        cornerRadius = dp(ctx, 3).toFloat()
                    }
                }
                addView(bar, LinearLayout.LayoutParams(dp(ctx, 4),
                    dp(ctx, h)).apply { setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0) })
            }
        }

    fun header(ctx: Context, p: Palette, heading: String): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(ctx, "✦  NEXA  ·  LIVE ASSISTANT", p, 12f, true))
            addView(label(ctx, heading, p, 24f))
        }
}
