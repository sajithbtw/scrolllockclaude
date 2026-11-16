package com.sweatlock.workout

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/** Shrinks each camera frame to a 48x36 brightness grid (mean removed) for [PageTurnDetector]. */
class PageAnalyzer(private val onGrid: (FloatArray) -> Unit) : ImageAnalysis.Analyzer {
    override fun analyze(proxy: ImageProxy) {
        try {
            val plane = proxy.planes[0]            // Y (brightness) plane
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pixStride = plane.pixelStride
            val w = proxy.width
            val h = proxy.height
            val gw = PageTurnDetector.GRID_W
            val gh = PageTurnDetector.GRID_H
            val out = FloatArray(gw * gh)

            for (gy in 0 until gh) for (gx in 0 until gw) {
                val cx = ((gx + 0.5f) * w / gw).toInt()
                val cy = ((gy + 0.5f) * h / gh).toInt()
                var sum = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val x = (cx + dx * 2).coerceIn(0, w - 1)
                    val y = (cy + dy * 2).coerceIn(0, h - 1)
                    sum += buf.get(y * rowStride + x * pixStride).toInt() and 0xFF
                }
                out[gy * gw + gx] = sum / 9f
            }
            val mean = out.average().toFloat()  // removes auto-exposure brightness shifts
            for (i in out.indices) out[i] -= mean
            onGrid(out)
        } finally {
            proxy.close()
        }
    }
}
