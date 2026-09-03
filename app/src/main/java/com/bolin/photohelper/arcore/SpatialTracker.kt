package com.bolin.photohelper.arcore

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class SpatialState(
    val tiltDegrees: Float,
    val heightMeters: Float?,
    val isStill: Boolean,
    val stillnessDuration: Long,
)

private const val STILLNESS_THRESHOLD = 0.003f
private const val STILLNESS_WINDOW_MS = 500L

/**
 * Derives tilt, height, and stillness from ARCore pose data. Call [update] on
 * every AR frame (~60fps) and read the result.
 *
 * **Stillness** is defined as the translational movement magnitude falling below
 * [STILLNESS_THRESHOLD] m/frame for [STILLNESS_WINDOW_MS] continuously.
 */
class SpatialTracker {

    private var previousPosition: FloatArray? = null
    private var stillSinceMs: Long? = null
    private var lastState = SpatialState(
        tiltDegrees = 0f,
        heightMeters = null,
        isStill = false,
        stillnessDuration = 0,
    )

    fun update(frame: Frame): SpatialState {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            previousPosition = null
            stillSinceMs = null
            lastState = lastState.copy(isStill = false, stillnessDuration = 0)
            return lastState
        }

        val pose = camera.displayOrientedPose
        val position = floatArrayOf(pose.tx(), pose.ty(), pose.tz())
        val timestampMs = frame.timestamp / 1_000_000

        val tilt = tiltFromPose(pose)
        val height = floorHeight(frame, position)

        val prev = previousPosition
        val movement = if (prev != null) {
            sqrt(
                (position[0] - prev[0]) * (position[0] - prev[0]) +
                    (position[1] - prev[1]) * (position[1] - prev[1]) +
                    (position[2] - prev[2]) * (position[2] - prev[2]),
            )
        } else {
            Float.MAX_VALUE
        }
        previousPosition = position.copyOf()

        val nowStill = movement < STILLNESS_THRESHOLD
        val startMs = stillSinceMs
        if (nowStill) {
            if (startMs == null) stillSinceMs = timestampMs
        } else {
            stillSinceMs = null
        }

        val duration = if (nowStill && stillSinceMs != null) {
            timestampMs - (stillSinceMs ?: timestampMs)
        } else {
            0L
        }

        lastState = SpatialState(
            tiltDegrees = tilt,
            heightMeters = height,
            isStill = duration >= STILLNESS_WINDOW_MS,
            stillnessDuration = duration,
        )
        return lastState
    }

    fun reset() {
        previousPosition = null
        stillSinceMs = null
        lastState = SpatialState(0f, null, isStill = false, stillnessDuration = 0)
    }

    private fun tiltFromPose(pose: com.google.ar.core.Pose): Float {
        val q = floatArrayOf(pose.qx(), pose.qy(), pose.qz(), pose.qw())
        val sinPitch = 2f * (q[3] * q[0] - q[2] * q[1])
        val pitchRad = if (abs(sinPitch) >= 1f) {
            Math.copySign(Math.PI / 2, sinPitch.toDouble()).toFloat()
        } else {
            kotlin.math.asin(sinPitch)
        }
        return Math.toDegrees(pitchRad.toDouble()).toFloat()
    }

    private fun floorHeight(frame: Frame, cameraPosition: FloatArray): Float? {
        val planes = frame.getUpdatedTrackables(Plane::class.java)
        var lowestY: Float? = null
        for (plane in planes) {
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
            if (plane.trackingState != TrackingState.TRACKING) continue
            val planeY = plane.centerPose.ty()
            if (lowestY == null || planeY < lowestY) lowestY = planeY
        }
        return lowestY?.let { cameraPosition[1] - it }
    }
}
