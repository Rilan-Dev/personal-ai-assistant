package dev.rilandev.personalaiassistant

import android.graphics.Rect
import kotlin.math.max
import kotlin.math.roundToInt

/** A single screen-relative swipe, planned from the current app window. */
internal data class NexaScreenScrollGesturePlan(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

/**
 * Dynamic fallback for apps such as browser WebViews that expose no semantic
 * Accessibility scroll node. The caller still needs a fresh owner-approved
 * action, same-window checks, the action journal, and the pause fence.
 */
internal object NexaScreenScrollGesturePlanner {
    private const val MIN_WIDTH_PX = 240
    private const val MIN_HEIGHT_PX = 420
    private const val DIRECTIONAL_TRAVEL = 0.36f
    private const val EDGE_MARGIN_FRACTION = 0.09f

    fun plan(
        appBounds: Rect,
        direction: String,
        occlusions: List<Rect>,
        density: Float,
    ): NexaScreenScrollGesturePlan? {
        if (direction !in setOf("up", "down", "left", "right") ||
            density <= 0f || appBounds.isEmpty ||
            appBounds.width() < MIN_WIDTH_PX || appBounds.height() < MIN_HEIGHT_PX) return null

        val marginX = max((density * 24f).roundToInt(), (appBounds.width() * EDGE_MARGIN_FRACTION).roundToInt())
        val marginY = max((density * 32f).roundToInt(), (appBounds.height() * EDGE_MARGIN_FRACTION).roundToInt())
        val safe = Rect(
            appBounds.left + marginX,
            appBounds.top + marginY,
            appBounds.right - marginX,
            appBounds.bottom - marginY,
        )
        if (safe.width() < MIN_WIDTH_PX / 2 || safe.height() < MIN_HEIGHT_PX / 2) return null

        val exclusionMargin = max((density * 12f).roundToInt(), 8)
        val blockers = occlusions.map { Rect(it).apply { inset(-exclusionMargin, -exclusionMargin) } }
        val centerX = (safe.left + safe.right) / 2f
        val centerY = (safe.top + safe.bottom) / 2f
        val xCandidates = candidateCoordinates(centerX, safe.left, safe.right)
        val yCandidates = candidateCoordinates(centerY, safe.top, safe.bottom)

        val plan = when (direction) {
            "down" -> xCandidates.firstNotNullOfOrNull { x ->
                val top = safe.top + safe.height() * 0.50f
                val bottom = safe.top + safe.height() * 0.90f
                val startY = bottom
                val endY = max(top, bottom - safe.height() * DIRECTIONAL_TRAVEL)
                candidate(floatPlan(x, startY, x, endY), blockers)
            }
            "up" -> xCandidates.firstNotNullOfOrNull { x ->
                val top = safe.top + safe.height() * 0.10f
                val bottom = safe.top + safe.height() * 0.50f
                val startY = top
                val endY = minOf(bottom, top + safe.height() * DIRECTIONAL_TRAVEL)
                candidate(floatPlan(x, startY, x, endY), blockers)
            }
            "left" -> yCandidates.firstNotNullOfOrNull { y ->
                val left = safe.left + safe.width() * 0.10f
                val right = safe.left + safe.width() * 0.90f
                val startX = right
                val endX = max(left, right - safe.width() * DIRECTIONAL_TRAVEL)
                candidate(floatPlan(startX, y, endX, y), blockers)
            }
            else -> yCandidates.firstNotNullOfOrNull { y ->
                val left = safe.left + safe.width() * 0.10f
                val right = safe.left + safe.width() * 0.90f
                val startX = left
                val endX = minOf(right, left + safe.width() * DIRECTIONAL_TRAVEL)
                candidate(floatPlan(startX, y, endX, y), blockers)
            }
        }
        return plan?.takeIf { isInside(safe, it) }
    }

    private fun candidateCoordinates(center: Float, start: Int, end: Int): List<Float> {
        val span = (end - start).toFloat()
        return listOf(center, start + span * 0.35f, start + span * 0.65f,
            start + span * 0.20f, start + span * 0.80f)
            .distinctBy { it.roundToInt() }
    }

    private fun floatPlan(startX: Float, startY: Float, endX: Float, endY: Float) =
        NexaScreenScrollGesturePlan(startX, startY, endX, endY)

    private fun candidate(
        plan: NexaScreenScrollGesturePlan,
        blockers: List<Rect>,
    ): NexaScreenScrollGesturePlan? {
        val pathBounds = Rect(
            plan.startX.toInt().coerceAtMost(plan.endX.toInt()),
            plan.startY.toInt().coerceAtMost(plan.endY.toInt()),
            plan.startX.toInt().coerceAtLeast(plan.endX.toInt()) + 1,
            plan.startY.toInt().coerceAtLeast(plan.endY.toInt()) + 1,
        )
        return plan.takeUnless { blockers.any { blocker -> Rect.intersects(pathBounds, blocker) } }
    }

    private fun isInside(bounds: Rect, plan: NexaScreenScrollGesturePlan): Boolean =
        plan.startX >= bounds.left && plan.startX < bounds.right &&
            plan.endX >= bounds.left && plan.endX < bounds.right &&
            plan.startY >= bounds.top && plan.startY < bounds.bottom &&
            plan.endY >= bounds.top && plan.endY < bounds.bottom
}
