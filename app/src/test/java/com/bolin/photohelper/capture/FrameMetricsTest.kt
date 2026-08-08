package com.bolin.photohelper.capture

import android.view.Surface
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameMetricsTest {
    @Test
    fun stalledCameraControlBecomesBoundedFailure() = runTest {
        val failure = runCatching { awaitCameraControl { awaitCancellation() } }.exceptionOrNull()

        assertTrue(failure is CameraControlTimeoutException)
        assertFalse(failure is CancellationException)
        assertEquals("Camera control timed out. Try again.", failure?.message)
    }

    @Test
    fun cameraControlWaitPreservesNullableCompletionAndCallerCancellation() = runTest {
        val completed: String? = awaitCameraControl { null }
        val callerCancellation = runCatching {
            withTimeout(100) { awaitCameraControl { awaitCancellation() } }
        }.exceptionOrNull()

        assertNull(completed)
        assertTrue(callerCancellation is TimeoutCancellationException)
        assertFalse(callerCancellation is CameraControlTimeoutException)
    }

    @Test
    fun gravityRollTracksTheCurrentDisplayRotation() {
        assertEquals(0f, gravityRollDegrees(0f, 9.8f, Surface.ROTATION_0)!!, 0.01f)
        assertEquals(0f, gravityRollDegrees(9.8f, 0f, Surface.ROTATION_90)!!, 0.01f)
        assertEquals(0f, gravityRollDegrees(0f, -9.8f, Surface.ROTATION_180)!!, 0.01f)
        assertEquals(0f, gravityRollDegrees(-9.8f, 0f, Surface.ROTATION_270)!!, 0.01f)
        assertNull(gravityRollDegrees(0.1f, 0.1f, Surface.ROTATION_0))
    }

    @Test
    fun captureTelemetryRequiresStillResultIdentityAndSettings() {
        val telemetry = capturedTelemetryOrNull(
            exposureCompensationIndex = 2,
            zoomRatio = 1.5f,
            whiteBalancePreset = WhiteBalancePreset.AUTO,
            lensId = "rear-wide",
            focalLengthMm = 5.2f,
            iso = 160,
            exposureTimeNanos = 8_000_000,
        )

        assertEquals(2, telemetry?.exposureCompensationIndex)
        assertEquals(1.5f, telemetry?.zoomRatio)
        assertEquals("rear-wide", telemetry?.lensId)
        assertEquals(160, telemetry?.iso)
        assertEquals(8_000_000L, telemetry?.exposureTimeNanos)
    }

    @Test
    fun captureTelemetryIsUnknownWhenStillResultIsIncomplete() {
        assertNull(
            capturedTelemetryOrNull(
                exposureCompensationIndex = 2,
                zoomRatio = 1.5f,
                whiteBalancePreset = WhiteBalancePreset.AUTO,
                lensId = "rear-wide",
                focalLengthMm = null,
                iso = 160,
                exposureTimeNanos = 8_000_000,
            ),
        )
    }

    @Test
    fun physicalCameraSwitchInvalidatesTheAdjustmentSessionAndOldBaseline() {
        val baseline = CameraTelemetry(lensId = "rear-wide")

        assertTrue(physicalCameraChanged("rear-wide", "rear-tele"))
        assertFalse(physicalCameraChanged(null, "rear-wide"))
        assertFalse(physicalCameraChanged("rear-wide", "rear-wide"))
        assertFalse(physicalCameraChanged("rear-wide", null))
        assertFalse(controlBaselineMatchesPhysicalCamera(baseline, "rear-tele"))
        assertTrue(controlBaselineMatchesPhysicalCamera(baseline, "rear-wide"))
    }

    @Test
    fun motionMetricIgnoresUniformExposureButDetectsSceneChange() {
        val baseline = listOf(20, 50, 80, 110)

        assertEquals(0f, FrameMetrics.motionScore(baseline, listOf(40, 70, 100, 130)), 0.001f)
        assertTrue(FrameMetrics.motionScore(baseline, listOf(110, 80, 50, 20)) > 0.08f)
    }

    @Test
    fun samplesCropWithRowPaddingAndUnsignedLuma() {
        val pixels = ByteBuffer.wrap(
            byteArrayOf(
                0, -1, 99,
                -128, -128, 99,
            ),
        )

        val result = FrameMetrics.measureYPlane(
            buffer = pixels,
            cropLeft = 0,
            cropTop = 0,
            cropWidth = 2,
            cropHeight = 2,
            rowStride = 3,
            pixelStride = 1,
        )

        assertEquals(0.5f, result.mean, 0.01f)
        assertEquals(0.25f, result.highlightFraction, 0.001f)
        assertEquals(0.25f, result.shadowFraction, 0.001f)
    }

    @Test
    fun measuresModerateBlueBiasAndRejectsUnusableColors() {
        val cool = FrameMetrics.measureArgb(
            pixels = intArrayOf(
                rgb(100, 100, 125),
                rgb(100, 100, 125),
                rgb(100, 100, 125),
                rgb(0, 0, 255),
            ),
            width = 2,
            height = 2,
        )
        val unusable = FrameMetrics.measureArgb(
            pixels = intArrayOf(
                rgb(0, 0, 0),
                rgb(255, 255, 255),
                rgb(0, 0, 255),
                rgb(255, 0, 0),
            ),
            width = 2,
            height = 2,
        )

        assertEquals(25f / 255f, cool.chromaBlueBias!!, 0.001f)
        assertNull(unusable.chromaBlueBias)
    }

    @Test
    fun backgroundPauseOutlivesFailedCapture() {
        val gate = AnalysisPauseGate()

        gate.startCapture()
        gate.setExternal(true)

        assertTrue(gate.finishCapture(keepPausedForReview = false))
    }

    @Test
    fun foregroundCannotReleaseAnActiveCaptureHold() {
        val gate = AnalysisPauseGate()

        gate.startCapture()
        assertTrue(gate.setExternal(false))
        assertFalse(gate.finishCapture(keepPausedForReview = false))
    }

    @Test
    fun backgroundPauseSurvivesCameraRebind() {
        val gate = AnalysisPauseGate()

        gate.setExternal(true)

        assertTrue(gate.resetSession())
    }

    @Test
    fun visualOptOutWipesAndRejectsAnInFlightFrame() {
        val gate = ObservationImageGate()
        gate.setEnabled(true)
        val staleTicket = gate.ticket()!!
        val staleJpeg = byteArrayOf(1, 2, 3)

        gate.setEnabled(false)
        gate.setEnabled(true)
        gate.publish(staleTicket, staleJpeg)

        assertArrayEquals(byteArrayOf(0, 0, 0), staleJpeg)
        assertNull(gate.copyLatest(gate.ticket()!!))
    }

    @Test
    fun stableFaceLockWorksWithoutOptionalTrackingId() {
        val tracker = StableFaceTracker()
        val face = FaceObservation(null, .3f, .2f, .7f, .8f)

        assertNull(tracker.update(observation(1, 1_000, face), 1))
        assertNull(tracker.update(observation(2, 1_250, face), 1))
        assertEquals(face, tracker.update(observation(3, 1_500, face), 1))
    }

    @Test
    fun faceIdentityChangeResetsStableLock() {
        val tracker = StableFaceTracker()
        val first = FaceObservation(7, .3f, .2f, .7f, .8f)
        val replacement = first.copy(trackingId = 8)
        tracker.update(observation(1, 1_000, first), 1)
        tracker.update(observation(2, 1_250, first), 1)

        assertNull(tracker.update(observation(3, 1_500, replacement), 1))
    }

    @Test
    fun stableFaceLockRejectsSmallOrPartlyHiddenFaces() {
        val smallTracker = StableFaceTracker()
        val small = FaceObservation(null, .45f, .35f, .55f, .65f)
        repeat(3) { index ->
            assertNull(smallTracker.update(observation(index.toLong(), 1_000L + index * 250L, small), 1))
        }

        val croppedTracker = StableFaceTracker()
        val cropped = FaceObservation(null, .3f, .2f, .7f, .8f, visibleFraction = .8f)
        repeat(3) { index ->
            assertNull(croppedTracker.update(observation(index.toLong(), 1_000L + index * 250L, cropped), 1))
        }
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue

    private fun observation(id: Long, timestamp: Long, face: FaceObservation) = FrameObservation(
        id = id,
        timestampMs = timestamp,
        meanLuma = .5f,
        highlightClipFraction = 0f,
        shadowClipFraction = 0f,
        faces = listOf(face),
        sourceWidth = 640,
        sourceHeight = 480,
    )
}
