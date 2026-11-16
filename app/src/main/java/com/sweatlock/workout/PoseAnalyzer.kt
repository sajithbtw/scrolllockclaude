package com.sweatlock.workout

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/** CameraX analyzer -> ML Kit pose -> normalized landmarks (delivered on main thread). */
class PoseAnalyzer(
    private val onLandmarks: (Map<Int, Landmark>) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        val rotation = proxy.imageInfo.rotationDegrees
        // After rotation, width/height swap for 90/270 degrees.
        val w = if (rotation % 180 == 0) proxy.width else proxy.height
        val h = if (rotation % 180 == 0) proxy.height else proxy.width

        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { pose ->
                onLandmarks(pose.allPoseLandmarks.associate {
                    it.landmarkType to Landmark(it.position.x / w, it.position.y / h, it.inFrameLikelihood)
                })
            }
            .addOnCompleteListener { proxy.close() }
    }
}
