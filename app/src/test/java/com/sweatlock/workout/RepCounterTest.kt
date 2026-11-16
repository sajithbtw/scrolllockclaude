package com.sweatlock.workout

import com.sweatlock.data.computeStreak
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class RepCounterTest {
    private fun lm(vararg p: Pair<Int, Pair<Float, Float>>) =
        p.associate { (i, xy) -> i to Landmark(xy.first, xy.second, 0.9f) }

    /** Full-body pose (spread over the frame) whose left elbow has the given angle. */
    private fun pose(elbowDeg: Double): Map<Int, Landmark> {
        val m = mutableMapOf<Int, Landmark>()
        for (i in listOf(12, 14, 16)) m[i] = Landmark(0.5f, 0.3f, 0.9f)
        for (i in listOf(23, 24)) m[i] = Landmark(0.5f, 0.6f, 0.9f)
        for (i in listOf(25, 26)) m[i] = Landmark(0.5f, 0.75f, 0.9f)
        for (i in listOf(27, 28)) m[i] = Landmark(0.5f, 0.9f, 0.9f)
        m[11] = Landmark(0.3f, 0.3f, 0.9f)
        m[13] = Landmark(0.3f, 0.5f, 0.9f)
        val r = Math.toRadians(elbowDeg)
        m[15] = Landmark((0.3 + 0.2 * sin(r)).toFloat(), (0.5 - 0.2 * cos(r)).toFloat(), 0.9f)
        return m
    }

    @Test fun countsOneSlowRep() {
        val c = RepCounter(Exercise.PUSHUP)
        c.update(pose(170.0), 0); c.update(pose(70.0), 1500)
        assertEquals(1, c.update(pose(170.0), 3000).reps)
    }

    @Test fun rejectsFastRep() {
        val c = RepCounter(Exercise.PUSHUP)
        c.update(pose(170.0), 0); c.update(pose(70.0), 1500); c.update(pose(170.0), 3000)
        c.update(pose(70.0), 3100)
        val s = c.update(pose(170.0), 3200)
        assertEquals(1, s.reps); assertTrue(s.lastRepRejected)
    }

    @Test fun ignoresWhenBodyOutOfFrame() {
        val c = RepCounter(Exercise.PUSHUP)
        assertFalse(c.update(pose(170.0) - 27, 0).fullBodyVisible)
    }

    @Test fun ignoresTinyBody() {
        val c = RepCounter(Exercise.PUSHUP)
        val tiny = pose(170.0).mapValues { (_, l) -> Landmark(0.5f + (l.x - 0.5f) * 0.2f, 0.5f + (l.y - 0.5f) * 0.2f, l.visibility) }
        assertFalse(c.update(tiny, 0).fullBodyVisible)
    }

    // ---- jumping jacks ----
    private fun jack(open: Boolean): Map<Int, Landmark> {
        val wristY = if (open) 0.1f else 0.55f
        val ankleL = if (open) 0.25f else 0.45f
        val ankleR = if (open) 0.75f else 0.55f
        return lm(
            11 to (0.4f to 0.3f), 12 to (0.6f to 0.3f),
            13 to (0.35f to 0.2f), 14 to (0.65f to 0.2f),
            15 to (0.3f to wristY), 16 to (0.7f to wristY),
            23 to (0.43f to 0.6f), 24 to (0.57f to 0.6f),
            25 to (0.4f to 0.75f), 26 to (0.6f to 0.75f),
            27 to (ankleL to 0.9f), 28 to (ankleR to 0.9f)
        )
    }

    @Test fun countsJumpingJack() {
        val c = RepCounter(Exercise.JUMPING_JACK)
        c.update(jack(false), 0); c.update(jack(true), 1000)
        assertEquals(1, c.update(jack(false), 2000).reps)
    }

    // ---- plank (counts seconds held) ----
    private fun plankPose() = lm(
        11 to (0.2f to 0.5f), 12 to (0.2f to 0.5f),
        13 to (0.2f to 0.6f), 14 to (0.2f to 0.6f),
        15 to (0.2f to 0.7f), 16 to (0.2f to 0.7f),
        23 to (0.5f to 0.52f), 24 to (0.5f to 0.52f),
        25 to (0.65f to 0.51f), 26 to (0.65f to 0.51f),
        27 to (0.8f to 0.5f), 28 to (0.8f to 0.5f)
    )

    @Test fun plankCountsSecondsHeld() {
        val c = RepCounter(Exercise.PLANK)
        var s = c.update(plankPose(), 0)
        for (t in 250L..3000L step 250L) s = c.update(plankPose(), t)
        assertTrue("held reps = ${s.reps}", s.reps >= 2)
    }

    @Test fun standingIsNotAPlank() {
        val c = RepCounter(Exercise.PLANK)
        var s = c.update(jack(false), 0)
        for (t in 250L..3000L step 250L) s = c.update(jack(false), t)
        assertEquals(0, s.reps)
    }

    @Test fun streakCountsConsecutiveDays() {
        assertEquals(3, computeStreak(listOf(10, 9, 8, 5), 10))
        assertEquals(2, computeStreak(listOf(9, 8), 10)) // not done yet today, streak still alive
        assertEquals(0, computeStreak(listOf(5), 10))
    }
}

class PageTurnDetectorTest {
    private fun grid(seed: Int): FloatArray {
        val r = Random(seed)
        val a = FloatArray(PageTurnDetector.GRID_W * PageTurnDetector.GRID_H) { r.nextFloat() * 200f }
        val m = a.average().toFloat()
        return FloatArray(a.size) { a[it] - m }
    }

    private class Feeder(val det: PageTurnDetector) {
        var ts = 0L
        var last: PageState? = null
        var anyRejected = false
        fun feed(g: FloatArray, n: Int = 1) {
            repeat(n) {
                val s = det.update(g, ts)
                last = s
                if (s.rejected) anyRejected = true
                ts += 33
            }
        }
        fun chaos(seedBase: Int) { for (i in 0..5) feed(grid(seedBase + i)) }
    }

    @Test fun countsRealPageTurn() {
        val f = Feeder(PageTurnDetector())
        f.feed(grid(1), 30); f.chaos(100); f.feed(grid(2), 30)
        assertEquals(1, f.last!!.count)
    }

    @Test fun ignoresHandWaveThatLeavesSamePage() {
        val f = Feeder(PageTurnDetector())
        f.feed(grid(1), 30); f.chaos(100); f.feed(grid(1), 30)
        assertEquals(0, f.last!!.count)
    }

    @Test fun rejectsRapidSecondTurn() {
        val f = Feeder(PageTurnDetector())
        f.feed(grid(1), 30); f.chaos(100); f.feed(grid(2), 30)
        f.chaos(200); f.feed(grid(3), 30)
        assertEquals(1, f.last!!.count)
        assertTrue(f.anyRejected)
    }
}
