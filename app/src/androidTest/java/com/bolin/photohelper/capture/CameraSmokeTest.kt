package com.bolin.photohelper.capture

import android.Manifest
import android.app.Instrumentation
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.percentOffset
import androidx.test.filters.RequiresDevice
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.bolin.photohelper.MainActivity
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import kotlin.math.abs
import kotlin.math.atan

class CameraSmokeTest {
    private val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(cameraPermission).around(compose)

    @Test
    fun virtualCameraCapturesAndEntersSavedReview() {
        openCameraAndWaitUntilReady()
        try {
            compose.onNodeWithTag(CaptureTestTags.SHUTTER).performClick()
            compose.waitUntil(timeoutMillis = 60_000) {
                compose.onAllNodesWithText("Original remains saved").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("Original remains saved").assertIsDisplayed()
            assertTrue(
                compose.onAllNodesWithContentDescription("Captured photo unavailable")
                    .fetchSemanticsNodes().isEmpty(),
            )
            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithContentDescription("Captured photo").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Captured photo").assertIsDisplayed()
        } finally {
            deleteCurrentTestCapture()
        }
    }

    @Test
    fun captureFinishesOrRecoversFromAStalledDriver() {
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        try {
            compose.onNodeWithTag(CaptureTestTags.SHUTTER).performClick()
            val timeoutMessage = "Camera did not finish saving the photo. Try again."
            compose.waitUntil(timeoutMillis = 22_000) {
                viewModel.uiState.value.review != null || viewModel.uiState.value.cameraPhase == CameraPhase.READY
            }

            val state = viewModel.uiState.value
            if (state.review != null) {
                compose.onNodeWithText("Original remains saved").assertIsDisplayed()
            } else {
                assertEquals(CameraPhase.READY, state.cameraPhase)
                assertEquals(timeoutMessage, state.transientMessage)
                assertTrue(state.shutterEnabled)
            }
        } finally {
            deleteCurrentTestCapture()
        }
    }

    @Test
    fun boundCameraAppliesAndResetsExposureAndExercisesFocus() {
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        val camera = viewModel.camera
        val capabilities = camera.capabilities.value
        assertTrue("Stage camera must expose EV compensation", capabilities.supportsExposureCompensation)

        val baseline = camera.telemetry.value.exposureCompensationIndex
        val target = when {
            baseline < capabilities.exposureCompensationRange.last -> baseline + 1
            baseline > capabilities.exposureCompensationRange.first -> baseline - 1
            else -> error("EV range has no usable step")
        }
        assertEquals(ApplyResult.Applied, runBlocking { camera.apply(CameraAdjustment.ExposureCompensation(target)) })
        assertEquals(target, camera.telemetry.value.exposureCompensationIndex)
        assertEquals(ApplyResult.Applied, runBlocking { camera.reset() })
        assertEquals(baseline, camera.telemetry.value.exposureCompensationIndex)

        val preview = compose.activity.window.decorView.findPreviewView()
            ?: error("Bound PreviewView not found")
        compose.waitUntil(timeoutMillis = 30_000) {
            preview.previewStreamState.value == PreviewView.StreamState.STREAMING
        }
        val focusResult = runBlocking { camera.focusAt(0.5f, 0.5f) }
        if (capabilities.supportsFocusMetering) {
            assertTrue(
                focusResult == ApplyResult.Applied ||
                    focusResult is ApplyResult.Failed && !focusResult.message.contains("unavailable", ignoreCase = true),
            )
        } else {
            assertTrue(focusResult is ApplyResult.Failed)
        }
    }

    @RequiresDevice
    @Test
    fun stagedPhysicalCameraMeetsExposureAndAnalysisGate() {
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        val camera = viewModel.camera
        val session = camera as CameraXSession
        val capabilities = camera.capabilities.value
        assertEquals(CameraCharacteristics.LENS_FACING_BACK, session.activeLensFacing)
        assertTrue("No active Camera2 ID was reported", !session.activeCameraId.isNullOrBlank())
        assertTrue("No focal length was reported for the active camera", session.activeFocalLengthsMm.all { it > 0f } && session.activeFocalLengthsMm.isNotEmpty())
        assertTrue("Rear camera did not start at 1×", abs(camera.telemetry.value.zoomRatio - 1f) <= 0.01f)
        assertTrue(
            "EV range must span at least -2..+2 steps; got ${capabilities.exposureCompensationRange}",
            capabilities.exposureCompensationRange.first <= -2 && capabilities.exposureCompensationRange.last >= 2,
        )

        compose.waitUntil(timeoutMillis = 5_000) {
            session.activeFocalLengthMm != null &&
                (session.activePhysicalCameraIds.isEmpty() || session.activePhysicalCameraId != null)
        }
        val activePhysicalCameraId = session.activePhysicalCameraId
        if (session.activePhysicalCameraIds.isNotEmpty()) {
            assertTrue(
                "The reported physical camera is not part of the selected logical camera",
                activePhysicalCameraId in session.activePhysicalCameraIds,
            )
        }
        val selectedCameraId = activePhysicalCameraId ?: session.activeCameraId ?: error("No selected camera ID")
        val manager = compose.activity.getSystemService(CameraManager::class.java)
        val selectedCharacteristics = manager.getCameraCharacteristics(selectedCameraId)
        val sensorSize = selectedCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?: error("Selected camera has no physical sensor size")
        val focalLength = session.activeFocalLengthMm ?: error("Selected camera has no active focal length")
        val horizontalFovDegrees = Math.toDegrees(
            2.0 * atan(sensorSize.width.toDouble() / (2.0 * focalLength.toDouble())),
        ).toFloat()
        assertTrue("The selected rear lens is not standard-wide: $horizontalFovDegrees°", horizontalFovDegrees in 50f..90f)

        val preview = compose.activity.window.decorView.findPreviewView()
            ?: error("Bound PreviewView not found")
        compose.waitUntil(timeoutMillis = 30_000) {
            preview.previewStreamState.value == PreviewView.StreamState.STREAMING
        }
        var warmupObservationId = -1L
        var warmupObservations = 0
        compose.waitUntil(timeoutMillis = 15_000) {
            camera.observation.value?.takeIf { it.id != warmupObservationId }?.let {
                warmupObservationId = it.id
                warmupObservations++
            }
            warmupObservations >= 20
        }
        deviceShell("dumpsys gfxinfo com.bolin.photohelper reset")
        deviceShell("dumpsys SurfaceFlinger --timestats -clear -enable")
        val observations = mutableListOf<FrameObservation>()
        var lastObservationId = -1L
        val surfaceStats = try {
            compose.waitUntil(timeoutMillis = 120_000) {
                camera.observation.value?.takeIf { it.id != lastObservationId }?.let {
                    observations += it
                    lastObservationId = it.id
                }
                observations.size >= 240
            }
            deviceShell("dumpsys SurfaceFlinger --timestats -dump")
        } finally {
            deviceShell("dumpsys SurfaceFlinger --timestats -disable -clear")
        }
        val intervals = observations.zipWithNext { first, second -> second.timestampMs - first.timestampMs }
        val medianInterval = intervals.sorted()[intervals.size / 2]
        assertTrue("Analysis ran faster than its 4 Hz ceiling: $intervals", intervals.all { it >= 200 })
        assertTrue("Analysis did not sustain its 4 Hz schedule: $intervals", medianInterval <= 400)
        val graphics = deviceShell("dumpsys gfxinfo com.bolin.photohelper")
        val uiFrames = Regex("Total frames rendered:\\s+(\\d+)").find(graphics)?.groupValues?.get(1)?.toInt()
            ?: error("Graphics report omitted total frames")
        val uiJankyFrames = Regex("Janky frames:\\s+(\\d+)").find(graphics)?.groupValues?.get(1)?.toInt()
            ?: error("Graphics report omitted janky frames")
        assertTrue("Idle analysis still redraws the whole app: $uiFrames frames", uiFrames <= observations.size / 4)
        val previewLayers = Regex("(?ms)^displayRefreshRate = .*?(?=^displayRefreshRate = |\\z)")
            .findAll(surfaceStats)
            .map { it.value }
            .filter { it.contains("layerName = SurfaceView[com.bolin.photohelper/") }
            .toList()
        assertTrue("SurfaceFlinger omitted the Photo Helper preview layer", previewLayers.isNotEmpty())
        val previewFrames = previewLayers.sumOf { layer ->
            Regex("(?m)^totalFrames = (\\d+)$").find(layer)?.groupValues?.get(1)?.toInt()
                ?: error("Preview layer omitted total frames")
        }
        val droppedPreviewFrames = previewLayers.sumOf { layer ->
            Regex("(?m)^droppedFrames = (\\d+)$").find(layer)?.groupValues?.get(1)?.toInt()
                ?: error("Preview layer omitted dropped frames")
        }
        val attemptedPreviewFrames = previewFrames + droppedPreviewFrames
        val jankPercent = droppedPreviewFrames * 100f / attemptedPreviewFrames
        assertTrue("Preview surface rendered too few frames: $attemptedPreviewFrames", attemptedPreviewFrames >= observations.size * 2)
        assertTrue(
            "Preview surface exceeded the 10% dropped-frame gate: $droppedPreviewFrames/$attemptedPreviewFrames",
            droppedPreviewFrames.toLong() * 100 <= attemptedPreviewFrames.toLong() * 10,
        )

        val baselineIndex = camera.telemetry.value.exposureCompensationIndex
        val targetIndex = if (baselineIndex + 2 <= capabilities.exposureCompensationRange.last) {
            baselineIndex + 2
        } else {
            baselineIndex - 2
        }
        val applyTimes = mutableListOf<Long>()
        val resetTimes = mutableListOf<Long>()
        val lumaTrials = mutableListOf<String>()

        repeat(5) {
            val before = camera.observation.value ?: error("No baseline observation")
            assertTrue("Stage target is too dark or clipped for EV qualification", before.meanLuma in 0.05f..0.95f)
            val applyStart = android.os.SystemClock.elapsedRealtime()
            assertEquals(
                ApplyResult.Applied,
                runBlocking { withTimeout(1_000) { camera.apply(CameraAdjustment.ExposureCompensation(targetIndex)) } },
            )
            applyTimes += android.os.SystemClock.elapsedRealtime() - applyStart
            assertEquals(targetIndex, camera.telemetry.value.exposureCompensationIndex)
            compose.waitUntil(timeoutMillis = (applyStart + 1_000 - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1)) {
                camera.observation.value?.timestampMs?.let { it >= applyStart + 400 } == true
            }
            val after = camera.observation.value ?: error("No post-EV observation")
            val lumaDelta = after.meanLuma - before.meanLuma
            assertTrue(
                "EV changed telemetry but not luma in the requested direction: $lumaDelta",
                if (targetIndex > baselineIndex) lumaDelta >= 0.01f else lumaDelta <= -0.01f,
            )

            val resetStart = android.os.SystemClock.elapsedRealtime()
            assertEquals(ApplyResult.Applied, runBlocking { withTimeout(1_000) { camera.reset() } })
            resetTimes += android.os.SystemClock.elapsedRealtime() - resetStart
            assertEquals(baselineIndex, camera.telemetry.value.exposureCompensationIndex)
            compose.waitUntil(timeoutMillis = (resetStart + 1_000 - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1)) {
                camera.observation.value?.timestampMs?.let { it >= resetStart + 400 } == true
            }
            val reset = camera.observation.value ?: error("No post-reset observation")
            val excursion = abs(after.meanLuma - before.meanLuma)
            val resetError = abs(reset.meanLuma - before.meanLuma)
            assertTrue(
                "Reset did not return luma toward its trial baseline",
                resetError < excursion && resetError <= maxOf(0.02f, excursion * 0.5f),
            )
            lumaTrials += "${before.meanLuma}>${after.meanLuma}>${reset.meanLuma}"
        }

        val chainedTargetA = baselineIndex + if (targetIndex > baselineIndex) 1 else -1
        assertEquals(
            ApplyResult.Applied,
            runBlocking { withTimeout(1_000) { camera.apply(CameraAdjustment.ExposureCompensation(chainedTargetA)) } },
        )
        assertEquals(chainedTargetA, camera.telemetry.value.exposureCompensationIndex)
        assertEquals(
            ApplyResult.Applied,
            runBlocking { withTimeout(1_000) { camera.apply(CameraAdjustment.ExposureCompensation(targetIndex)) } },
        )
        assertEquals(targetIndex, camera.telemetry.value.exposureCompensationIndex)
        assertEquals(ApplyResult.Applied, runBlocking { withTimeout(1_000) { camera.reset() } })
        assertEquals("One Reset did not restore the pre-chain EV baseline", baselineIndex, camera.telemetry.value.exposureCompensationIndex)

        assertEquals(
            ApplyResult.Applied,
            runBlocking { withTimeout(1_000) { camera.apply(CameraAdjustment.ExposureCompensation(targetIndex)) } },
        )
        val capture = runBlocking { withTimeout(60_000) { camera.capture() } }
        val saved = (capture as? CaptureResult.Saved)?.capture ?: error("Adjusted physical capture failed: $capture")
        val captureTelemetry = requireNotNull(saved.telemetry) {
            "The device did not report trustworthy metadata for the saved still"
        }
        try {
            assertEquals(targetIndex, captureTelemetry.exposureCompensationIndex)
            assertTrue("Saved still did not report ISO", captureTelemetry.iso?.let { it > 0 } == true)
            assertTrue(
                "Saved still did not report exposure time",
                captureTelemetry.exposureTimeNanos?.let { it > 0 } == true,
            )
        } finally {
            deleteTestCapture(saved.uri)
            assertEquals(ApplyResult.Applied, runBlocking { withTimeout(1_000) { camera.reset() } })
            camera.setAnalysisPaused(false)
        }
        assertEquals(selectedCameraId, session.activePhysicalCameraId ?: session.activeCameraId)
        reportPhysicalGate(
            "PHYSICAL_GATE cameraId=${session.activeCameraId} activePhysicalId=${session.activePhysicalCameraId} " +
                "horizontalFovDegrees=$horizontalFovDegrees lensFacing=BACK " +
                "focalLengthsMm=${session.activeFocalLengthsMm} physicalIds=${session.activePhysicalCameraIds} " +
                "analysisMedianMs=$medianInterval totalFrames=$attemptedPreviewFrames " +
                "jankyFrames=$droppedPreviewFrames jankPercent=$jankPercent " +
                "uiFrames=$uiFrames uiJankyFrames=$uiJankyFrames previewFrames=$previewFrames " +
                "evRange=${capabilities.exposureCompensationRange} " +
                "evStep=${capabilities.exposureCompensationStepEv} applyMs=$applyTimes resetMs=$resetTimes " +
                "chainedEv=$chainedTargetA>$targetIndex>$baselineIndex " +
                "lumaTrials=$lumaTrials captureEv=${captureTelemetry.exposureCompensationIndex} " +
                "captureIso=${captureTelemetry.iso} captureExposureNs=${captureTelemetry.exposureTimeNanos}",
        )
    }

    @RequiresDevice
    @Test
    fun stagedPhysicalCameraFlipsToSelfieAndBack() {
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        val session = viewModel.camera as CameraXSession
        var selfieUri: String? = null

        try {
            compose.onNodeWithContentDescription("Switch to front camera")
                .assertIsEnabled()
                .performClick()
            compose.waitUntil(timeoutMillis = 30_000) {
                viewModel.uiState.value.cameraPhase == CameraPhase.READY &&
                    session.activeLensFacing == CameraCharacteristics.LENS_FACING_FRONT
            }
            compose.onNodeWithText("LIVE · SELFIE").assertIsDisplayed()

            compose.onNodeWithTag(CaptureTestTags.SHUTTER).performClick()
            compose.waitUntil(timeoutMillis = 60_000) { viewModel.uiState.value.review != null }
            selfieUri = viewModel.uiState.value.review?.uri
            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithContentDescription("Captured photo").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Captured photo").assertIsDisplayed()
            compose.onNodeWithText("Retake").performClick()
            compose.waitUntil(timeoutMillis = 30_000) {
                viewModel.uiState.value.cameraPhase == CameraPhase.READY
            }

            compose.onNodeWithContentDescription("Switch to rear camera")
                .assertIsEnabled()
                .performClick()
            compose.waitUntil(timeoutMillis = 30_000) {
                viewModel.uiState.value.cameraPhase == CameraPhase.READY &&
                    session.activeLensFacing == CameraCharacteristics.LENS_FACING_BACK
            }
        } finally {
            selfieUri?.let(::deleteTestCapture) ?: deleteCurrentTestCapture()
        }
    }

    @RequiresDevice
    @Test
    fun stagedPhysicalCameraAcceptsTypedLensCommands() {
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        val session = viewModel.camera as CameraXSession

        compose.onNodeWithTag(CaptureTestTags.COMMENT).performTextInput("selfie mode")
        compose.onNodeWithText("Send").performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            viewModel.uiState.value.cameraPhase == CameraPhase.READY &&
                session.activeLensFacing == CameraCharacteristics.LENS_FACING_FRONT
        }

        compose.onNodeWithTag(CaptureTestTags.COMMENT).performTextInput("rear camera")
        compose.onNodeWithText("Send").performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            viewModel.uiState.value.cameraPhase == CameraPhase.READY &&
                session.activeLensFacing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    @RequiresDevice
    @Test
    fun stagedZoomCommentAppliesVerifiesAndResets() {
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        val camera = viewModel.camera
        val range = camera.capabilities.value.zoomRatioRange
        val baseline = camera.telemetry.value.zoomRatio
        val target = (baseline * 1.25f).coerceIn(range)
        assertTrue("Stage camera has no usable zoom-in step", target - baseline >= 0.01f)

        compose.onNodeWithTag(CaptureTestTags.COMMENT).performTextInput("too zoomed out")
        closeSoftKeyboard()
        compose.onNodeWithText("Send").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            val action = viewModel.uiState.value.recommendation?.action as? com.bolin.photohelper.coach.RecommendationAction.ApplySettings
            (action?.adjustment as? CameraAdjustment.ZoomRatio)?.ratio?.let { abs(it - target) <= 0.01f } == true
        }

        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { abs(camera.telemetry.value.zoomRatio - target) <= 0.01f }
        compose.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.transientMessage.orEmpty().startsWith("Zoom changed to")
        }
        assertTrue(viewModel.uiState.value.resetAvailable)

        compose.onNodeWithText("Reset").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            !viewModel.uiState.value.resetAvailable && abs(camera.telemetry.value.zoomRatio - baseline) <= 0.01f
        }
        reportPhysicalGate("PHYSICAL_GATE zoom=$baseline>$target>$baseline chain=COMMENT>APPLY>VERIFY>RESET")
    }

    @RequiresDevice
    @Test
    fun stagedCompoundCommentAppliesBothSettingsAndResetsTogether() {
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        val camera = viewModel.camera
        val capabilities = camera.capabilities.value
        val baseline = camera.telemetry.value
        val targetZoom = (baseline.zoomRatio * 1.25f).coerceIn(capabilities.zoomRatioRange)
        assertTrue("Stage camera has no usable zoom-in step", targetZoom - baseline.zoomRatio >= 0.01f)
        assertTrue(
            "Stage camera must support cooler white balance",
            WhiteBalancePreset.COOLER in capabilities.supportedWhiteBalancePresets,
        )
        assertTrue(
            "Stage camera already uses cooler white balance",
            baseline.whiteBalancePreset != WhiteBalancePreset.COOLER,
        )

        compose.onNodeWithTag(CaptureTestTags.COMMENT).performTextInput("too warm and too zoomed out")
        closeSoftKeyboard()
        compose.onNodeWithText("Send").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            val state = viewModel.uiState.value
            val action = state.recommendation?.action as? com.bolin.photohelper.coach.RecommendationAction.ApplySettings
            state.coachingPhase == CoachingPhase.RECOMMENDATION &&
                state.recommendation?.primaryLabel == "Apply both" &&
                action?.changes?.let { changes ->
                    changes.size == 2 &&
                        changes.any {
                            (it.adjustment as? CameraAdjustment.ZoomRatio)?.ratio?.let { ratio ->
                                abs(ratio - targetZoom) <= 0.01f
                            } == true
                        } &&
                        changes.any {
                            it.adjustment == CameraAdjustment.WhiteBalance(WhiteBalancePreset.COOLER)
                        }
                } == true
        }

        compose.onNodeWithText("Apply both").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            val telemetry = camera.telemetry.value
            abs(telemetry.zoomRatio - targetZoom) <= 0.01f &&
                telemetry.whiteBalancePreset == WhiteBalancePreset.COOLER
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            val state = viewModel.uiState.value
            state.coachingPhase == CoachingPhase.IDLE &&
                state.resetAvailable &&
                state.transientMessage ==
                "2 camera changes applied. Check the shot; Reset restores the previous settings."
        }

        compose.onNodeWithText("Reset").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            val telemetry = camera.telemetry.value
            !viewModel.uiState.value.resetAvailable &&
                abs(telemetry.zoomRatio - baseline.zoomRatio) <= 0.01f &&
                telemetry.whiteBalancePreset == baseline.whiteBalancePreset
        }
        reportPhysicalGate(
            "PHYSICAL_GATE compound=ZOOM_IN+WHITE_BALANCE_COOLER " +
                "zoom=${baseline.zoomRatio}>$targetZoom>${baseline.zoomRatio} " +
                "whiteBalance=${baseline.whiteBalancePreset}>${WhiteBalancePreset.COOLER}>${baseline.whiteBalancePreset} " +
                "chain=COMMENT>APPLY_BOTH>VERIFY_SETPOINTS>RESET",
        )
    }

    @RequiresDevice
    @Test
    fun stagedPreviewTapShowsTargetAndLocksFocus() {
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        assertTrue("Stage camera must support AF metering", viewModel.camera.capabilities.value.supportsFocusMetering)

        val sessionIdBeforeTyping = viewModel.camera.state.value.sessionId
        compose.onNodeWithTag(CaptureTestTags.COMMENT).performTextInput("focus missed")
        closeSoftKeyboard()
        compose.waitForIdle()
        assertEquals("Typing rebound the camera", sessionIdBeforeTyping, viewModel.camera.state.value.sessionId)
        compose.onNodeWithText("Send").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(CaptureTestTags.FOCUS_TARGET).fetchSemanticsNodes().isNotEmpty()
        }
        val focusTarget = compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET)
        val targetBounds = focusTarget.fetchSemanticsNode().boundsInRoot
        val markerX = (targetBounds.left + targetBounds.right) / 2f
        val markerY = (targetBounds.top + targetBounds.bottom) / 2f
        val focusAreaNodes = compose.onAllNodesWithTag(CaptureTestTags.FOCUS_AREA).fetchSemanticsNodes()
        val (mode, tapXFraction, tapYFraction, expectedX, expectedY) = if (focusAreaNodes.isNotEmpty()) {
            val focusArea = compose.onNodeWithTag(CaptureTestTags.FOCUS_AREA)
            val focusBounds = focusArea.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Focus marker was outside the preview tap area",
                markerX in focusBounds.left..focusBounds.right && markerY in focusBounds.top..focusBounds.bottom,
            )
            val x = .5f
            val y = .42f
            focusArea.performTouchInput { click(percentOffset(x, y)) }
            listOf("MANUAL", x, y, focusBounds.left + focusBounds.width * x, focusBounds.top + focusBounds.height * y)
        } else {
            assertTrue(
                "Qwen focus did not show its selected grid cell",
                compose.onAllNodesWithTag(CaptureTestTags.FOCUS_CELL).fetchSemanticsNodes().isNotEmpty(),
            )
            val action = viewModel.uiState.value.recommendation?.action as com.bolin.photohelper.coach.RecommendationAction.FocusAt
            focusTarget.performClick()
            listOf("QWEN_GRID", action.xFraction, action.yFraction, markerX, markerY)
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.transientMessage != "Focusing…" &&
                viewModel.uiState.value.coachingPhase != CoachingPhase.APPLYING
        }
        assertTrue(
            viewModel.uiState.value.transientMessage.orEmpty(),
            viewModel.uiState.value.transientMessage.orEmpty().startsWith("Focus locked"),
        )
        reportPhysicalGate(
            "PHYSICAL_GATE focusMode=$mode tapFraction=$tapXFraction,$tapYFraction tapPx=$expectedX,$expectedY " +
                "markerPx=$markerX,$markerY focus=LOCKED",
        )
    }

    private fun reportPhysicalGate(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString(Instrumentation.REPORT_KEY_STREAMRESULT, "$message\n") },
        )
    }

    private fun openCameraAndWaitUntilReady() {
        if (compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Open camera").performClick()
        }
        compose.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                compose.onNodeWithTag(CaptureTestTags.SHUTTER).assertIsEnabled()
            }.isSuccess
        }
    }

    private fun deleteCurrentTestCapture() {
        val uri = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
            .uiState.value.review?.uri ?: return
        deleteTestCapture(uri)
    }

    private fun deleteTestCapture(uri: String) {
        val parsedUri = Uri.parse(uri)
        var rowFound = false
        var relativePath: String? = null
        var deleted = 0
        try {
            compose.activity.contentResolver.query(
                parsedUri,
                arrayOf(MediaStore.Images.Media.RELATIVE_PATH),
                null,
                null,
                null,
            )?.use { cursor ->
                rowFound = cursor.moveToFirst()
                if (rowFound) relativePath = cursor.getString(0)
            }
        } finally {
            deleted = compose.activity.contentResolver.delete(parsedUri, null, null)
        }
        assertTrue("The exact test-created MediaStore image was not deleted", deleted > 0)
        assertTrue("The test-created MediaStore row was missing", rowFound)
        assertEquals("Pictures/PhotoHelper/", relativePath)
    }

    private fun deviceShell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    private fun View.findPreviewView(): PreviewView? {
        if (this is PreviewView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) getChildAt(index).findPreviewView()?.let { return it }
        return null
    }
}
