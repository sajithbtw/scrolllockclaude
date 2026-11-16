package com.sweatlock.workout

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Every task the user can pick. [minInterval] is the anti-cheat minimum gap between counted reps.
 * PLANK counts seconds held; PAGE_TURN counts turned pages (camera on the book, no pose needed).
 */
enum class Exercise(
    val label: String,
    val unit: String,
    val emoji: String,
    val defaultGoal: Int,
    val minInterval: Long,
    val step: Int = 1,
    val isStudy: Boolean = false,
    val showsAngle: Boolean = false
) {
    PUSHUP("Pushups", "pushups", "\uD83D\uDCAA", 10, 1000, showsAngle = true),
    SQUAT("Squats", "squats", "\uD83E\uDDB5", 15, 1000, showsAngle = true),
    LUNGE("Lunges", "lunges", "\uD83C\uDFC3", 10, 1000, showsAngle = true),
    SITUP("Sit-ups", "sit-ups", "\uD83E\uDDD8", 10, 1000, showsAngle = true),
    JUMPING_JACK("Jumping jacks", "jumping jacks", "\uD83E\uDD38", 20, 700),
    HIGH_KNEES("High knees", "knee raises", "\uD83E\uDDB6", 30, 400),
    PLANK("Plank hold", "seconds", "\u23F1\uFE0F", 20, 0, step = 5),
    PAGE_TURN("Study: turn pages", "pages", "\uD83D\uDCD6", 5, 3000, isStudy = true)
}

enum class RepPhase { UP, DOWN }

/** Normalized (0..1) landmark. [visibility] is ML Kit's inFrameLikelihood. */
data class Landmark(val x: Float, val y: Float, val visibility: Float)

data class RepState(
    val reps: Int,
    val phase: RepPhase,
    val angle: Float,
    val fullBodyVisible: Boolean,
    val lastRepRejected: Boolean = false // true when a rep was too fast
)

/**
 * Pure Kotlin (no Android imports) so it is trivially unit-testable.
 * Feed it landmarks + a timestamp; it returns the current rep state.
 * Phase UP = resting position, DOWN = active position; a rep is counted on returning to rest.
 */
class RepCounter(private val exercise: Exercise) {
    private val minRepIntervalMs = if (exercise.minInterval > 0) exercise.minInterval else 1000L

    private var reps = 0
    private var phase = RepPhase.UP
    private var lastRepTime = -minRepIntervalMs
    private var lastMetric = 180f
    private var heldMs = 0L          // plank
    private var lastTs: Long? = null // plank

    private companion object {
        val ARM_L = Triple(11, 13, 15); val ARM_R = Triple(12, 14, 16)
        val LEG_L = Triple(23, 25, 27); val LEG_R = Triple(24, 26, 28)
        val HIP_L = Triple(11, 23, 25); val HIP_R = Triple(12, 24, 26)      // shoulder-hip-knee
        val BODY_L = Triple(11, 23, 27); val BODY_R = Triple(12, 24, 28)    // shoulder-hip-ankle
        val REQUIRED = (11..16) + (23..28)
        const val MIN_VISIBILITY = 0.6f
        const val MIN_BODY_SPAN = 0.4f // body must cover >= 40% of the frame (width or height)
    }

    fun update(lm: Map<Int, Landmark>, timestampMs: Long): RepState {
        if (!fullBodyInFrame(lm)) {
            lastTs = timestampMs
            return RepState(reps, phase, lastMetric, false)
        }
        return when (exercise) {
            Exercise.PUSHUP -> angleRep(timestampMs, sideAngle(lm, ARM_L, ARM_R), 90f, 160f)
            Exercise.SQUAT -> angleRep(timestampMs, sideAngle(lm, LEG_L, LEG_R), 100f, 160f)
            Exercise.LUNGE -> angleRep(
                timestampMs,
                minOf(angle(lm, LEG_L), angle(lm, LEG_R)), 105f, 160f
            )
            Exercise.SITUP -> angleRep(timestampMs, sideAngle(lm, HIP_L, HIP_R), 85f, 115f)
            Exercise.JUMPING_JACK -> jumpingJack(lm, timestampMs)
            Exercise.HIGH_KNEES -> highKnees(lm, timestampMs)
            Exercise.PLANK -> plank(lm, timestampMs)
            Exercise.PAGE_TURN -> RepState(reps, phase, 0f, true) // handled by PageTurnDetector
        }
    }

    // ---- rep state machine -------------------------------------------------------------

    private fun step(active: Boolean, rest: Boolean, ts: Long, metric: Float): RepState {
        lastMetric = metric
        var rejected = false
        when (phase) {
            RepPhase.UP -> if (active) phase = RepPhase.DOWN
            RepPhase.DOWN -> if (rest) {
                phase = RepPhase.UP
                if (ts - lastRepTime >= minRepIntervalMs) { reps++; lastRepTime = ts } else rejected = true
            }
        }
        return RepState(reps, phase, metric, true, rejected)
    }

    /** Active when the joint angle drops below [activeBelow]; rest when it passes [restAbove]. */
    private fun angleRep(ts: Long, angle: Float, activeBelow: Float, restAbove: Float) =
        step(angle < activeBelow, angle > restAbove, ts, angle)

    // ---- exercise specifics ------------------------------------------------------------

    private fun jumpingJack(lm: Map<Int, Landmark>, ts: Long): RepState {
        val shoulderW = abs(lm[11]!!.x - lm[12]!!.x).coerceAtLeast(0.01f)
        val spread = abs(lm[27]!!.x - lm[28]!!.x) / shoulderW
        val handsUp = lm[15]!!.y < lm[11]!!.y && lm[16]!!.y < lm[12]!!.y
        val handsDown = lm[15]!!.y > lm[11]!!.y && lm[16]!!.y > lm[12]!!.y
        return step(handsUp && spread > 1.4f, handsDown && spread < 1.15f, ts, spread)
    }

    private fun highKnees(lm: Map<Int, Landmark>, ts: Long): RepState {
        val torso = hypot(
            (lm[11]!!.x + lm[12]!!.x) / 2 - (lm[23]!!.x + lm[24]!!.x) / 2,
            (lm[11]!!.y + lm[12]!!.y) / 2 - (lm[23]!!.y + lm[24]!!.y) / 2
        ).coerceAtLeast(0.01f)
        val liftL = (lm[23]!!.y - lm[25]!!.y) / torso
        val liftR = (lm[24]!!.y - lm[26]!!.y) / torso
        val best = maxOf(liftL, liftR)
        // knee at hip height = raised; knee well below hip = down
        return step(best > -0.05f, liftL < -0.4f && liftR < -0.4f, ts, best)
    }

    /** Counts seconds held while the body is a straight, roughly horizontal line. */
    private fun plank(lm: Map<Int, Landmark>, ts: Long): RepState {
        val t = if (sideVis(lm, BODY_L) >= sideVis(lm, BODY_R)) BODY_L else BODY_R
        val ang = angle(lm, t)
        val dx = abs(lm[t.first]!!.x - lm[t.third]!!.x)
        val dy = abs(lm[t.first]!!.y - lm[t.third]!!.y)
        val holding = ang >= 155f && dy < 0.5f * dx
        val prev = lastTs
        if (holding && prev != null) heldMs += (ts - prev).coerceIn(0L, 300L)
        lastTs = ts
        reps = (heldMs / 1000L).toInt()
        phase = if (holding) RepPhase.DOWN else RepPhase.UP
        lastMetric = ang
        return RepState(reps, phase, ang, true)
    }

    // ---- geometry ----------------------------------------------------------------------

    /** All key joints visible + inside the frame + body big enough (stops "phone in face" cheating). */
    private fun fullBodyInFrame(lm: Map<Int, Landmark>): Boolean {
        val pts = REQUIRED.map { lm[it] ?: return false }
        if (pts.any { it.visibility < MIN_VISIBILITY || it.x !in 0f..1f || it.y !in 0f..1f }) return false
        val w = pts.maxOf { it.x } - pts.minOf { it.x }
        val h = pts.maxOf { it.y } - pts.minOf { it.y }
        return maxOf(w, h) >= MIN_BODY_SPAN
    }

    private fun sideVis(lm: Map<Int, Landmark>, t: Triple<Int, Int, Int>) =
        lm[t.first]!!.visibility + lm[t.second]!!.visibility + lm[t.third]!!.visibility

    /** Uses the more visible side (most exercises are filmed side-on). */
    private fun sideAngle(lm: Map<Int, Landmark>, l: Triple<Int, Int, Int>, r: Triple<Int, Int, Int>) =
        angle(lm, if (sideVis(lm, l) >= sideVis(lm, r)) l else r)

    private fun angle(lm: Map<Int, Landmark>, t: Triple<Int, Int, Int>): Float {
        val a = lm[t.first]!!; val b = lm[t.second]!!; val c = lm[t.third]!!
        val rad = atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)
        var deg = abs(Math.toDegrees(rad.toDouble())).toFloat()
        if (deg > 180f) deg = 360f - deg
        return deg
    }
}
