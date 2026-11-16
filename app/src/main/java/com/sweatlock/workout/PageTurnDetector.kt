package com.sweatlock.workout

import kotlin.math.abs

data class PageState(val count: Int, val rejected: Boolean, val motion: Float, val moving: Boolean)

/**
 * Counts page turns from tiny greyscale frames (GRID_W x GRID_H, mean already removed).
 *
 * A page turn = a burst of motion, then the view settles, AND the settled picture is clearly
 * different from the picture before the burst. A hand wave (or any motion that returns to the
 * same picture) leaves the picture unchanged, so it is NOT counted. Turns closer together than
 * [minGapMs] are rejected, so you can't just flick pages.
 *
 * Thresholds are in 0..255 luminance units; tune them if your lighting/camera needs it.
 */
class PageTurnDetector(
    private val minGapMs: Long = 3000,
    private val motionHigh: Float = 4f,   // frame-to-frame change that starts a "movement"
    private val motionLow: Float = 1.8f,  // below this the view counts as calm
    private val changeMin: Float = 3f,    // settled picture must differ from the old one by this
    private val settleFrames: Int = 4,    // calm frames in a row needed to call it settled
    private val minMoveMs: Long = 100,
    private val maxMoveMs: Long = 2500
) {
    companion object {
        const val GRID_W = 48
        const val GRID_H = 36
    }

    private var prev: FloatArray? = null
    private var reference: FloatArray? = null
    private var moving = false
    private var moveStart = 0L
    private var calm = 0
    private var count = 0
    private var lastCount = -1_000_000L

    fun update(grid: FloatArray, ts: Long): PageState {
        val p = prev
        prev = grid
        if (p == null) { reference = grid; return PageState(count, false, 0f, false) }

        val motion = meanAbsDiff(grid, p)
        var rejected = false

        if (!moving) {
            if (motion > motionHigh) { moving = true; moveStart = ts; calm = 0 } else reference = grid
        } else {
            calm = if (motion < motionLow) calm + 1 else 0
            if (calm >= settleFrames) {
                val dur = ts - moveStart
                val change = meanAbsDiff(grid, reference!!)
                if (dur in minMoveMs..maxMoveMs && change > changeMin) {
                    if (ts - lastCount >= minGapMs) { count++; lastCount = ts } else rejected = true
                }
                moving = false
                reference = grid
            } else if (ts - moveStart > maxMoveMs) {
                moving = false // too long: probably the camera was moved, not a page
                reference = grid
            }
        }
        return PageState(count, rejected, motion, moving)
    }

    private fun meanAbsDiff(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += abs(a[i] - b[i])
        return s / a.size
    }
}
