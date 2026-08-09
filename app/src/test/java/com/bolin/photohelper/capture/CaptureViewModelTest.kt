package com.bolin.photohelper.capture

import com.bolin.photohelper.coach.DefaultCoachEngine
import com.bolin.photohelper.coach.ControlIntent
import com.bolin.photohelper.coach.IntentClassification
import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.VisualHint
import com.bolin.photohelper.coach.VisualIntent
import com.bolin.photohelper.visual.ComplaintResult
import com.bolin.photohelper.visual.VisualResult
import com.bolin.photohelper.voice.VoiceIo
import com.bolin.photohelper.voice.VoiceResult
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typed complaint produces local exposure recommendation`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f))
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)

        viewModel.updateComment("the whole shot is too bright")
        viewModel.submitComment()
        runCurrent()

        assertEquals(CoachingPhase.RECOMMENDATION, viewModel.uiState.value.coachingPhase)
        assertNotNull(viewModel.uiState.value.recommendation)
    }

    @Test
    fun `typed zoom complaint requires one apply verifies telemetry and resets`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(zoomRatio = 1f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
        }
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)

        viewModel.updateComment("too zoomed out")
        viewModel.submitComment()
        runCurrent()

        assertEquals(0, camera.applyCalls)
        val recommendation = (viewModel.uiState.value.decision as LocalDecision.Recommend).recommendation
        assertEquals(
            CameraAdjustment.ZoomRatio(1.25f),
            (recommendation.action as com.bolin.photohelper.coach.RecommendationAction.ApplySettings).adjustment,
        )

        viewModel.applyRecommendation()
        runCurrent()
        assertEquals(1, camera.applyCalls)

        listOf(1_500L, 1_600L, 1_700L).forEachIndexed { index, timestamp ->
            camera.observation.value = observation(id = index + 2L, timestamp = timestamp, zoomRatio = 1.25f)
            runCurrent()
        }

        assertEquals("Zoom changed to 1.25×. Is the framing closer?", viewModel.uiState.value.transientMessage)
        assertTrue(viewModel.uiState.value.resetAvailable)

        viewModel.reset()
        runCurrent()
        assertEquals(1, camera.resetCalls)
        assertFalse(viewModel.uiState.value.resetAvailable)
    }

    @Test
    fun `compound complaint applies every setting with one approval and one reset`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(zoomRatio = 2f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
            telemetry.value = CameraTelemetry(zoomRatio = 2f)
        }
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)

        viewModel.updateComment("It's too warm, and too zoomed in!")
        viewModel.submitComment()
        runCurrent()

        assertEquals(0, camera.appliedBatches.size)
        assertEquals("Apply both", viewModel.uiState.value.recommendation?.primaryLabel)

        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(
            listOf(
                CameraAdjustment.ZoomRatio(1.6f),
                CameraAdjustment.WhiteBalance(WhiteBalancePreset.COOLER),
            ),
            camera.appliedBatches.single(),
        )
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
        assertEquals("2 camera changes applied. Check the shot; Reset restores the previous settings.", viewModel.uiState.value.transientMessage)
        assertTrue(viewModel.uiState.value.resetAvailable)

        viewModel.reset()
        runCurrent()
        assertEquals(1, camera.resetCalls)
        assertFalse(viewModel.uiState.value.resetAvailable)
    }

    @Test
    fun `compound apply replans every setting from fresh telemetry`() = runTest(dispatcher) {
        var now = 1_000L
        val camera = FakeCamera(observation(timestamp = now, zoomRatio = 2f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
            telemetry.value = CameraTelemetry(zoomRatio = 2f)
        }
        val viewModel = viewModel(camera, nowMs = { now })
        viewModel.updateComment("too warm and too zoomed in")
        viewModel.submitComment()
        runCurrent()

        now = 1_250L
        camera.telemetry.value = CameraTelemetry(
            zoomRatio = 4f,
            whiteBalancePreset = WhiteBalancePreset.WARMER,
        )
        camera.observation.value = observation(id = 2, timestamp = now, zoomRatio = 4f)
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(
            listOf(
                CameraAdjustment.ZoomRatio(3.2f),
                CameraAdjustment.WhiteBalance(WhiteBalancePreset.COOLER),
            ),
            camera.appliedBatches.single(),
        )
    }

    @Test
    fun `backgrounding an in flight compound apply resets after acknowledgement`() = runTest(dispatcher) {
        val result = CompletableDeferred<ApplyResult>()
        val camera = FakeCamera(observation(zoomRatio = 2f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
            telemetry.value = CameraTelemetry(zoomRatio = 2f)
            applyGate = result
        }
        val viewModel = viewModel(camera)
        viewModel.updateComment("too warm and too zoomed in")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()
        assertEquals(CoachingPhase.APPLYING, viewModel.uiState.value.coachingPhase)

        viewModel.onBackground()
        result.complete(ApplyResult.Applied)
        runCurrent()

        assertEquals(1, camera.appliedBatches.size)
        assertEquals(1, camera.resetCalls)
        assertFalse(viewModel.uiState.value.resetAvailable)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `failed compound transaction never claims success or offers reset`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(zoomRatio = 2f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
            telemetry.value = CameraTelemetry(zoomRatio = 2f)
            applyGate = CompletableDeferred(ApplyResult.Failed("Original camera settings restored."))
        }
        val viewModel = viewModel(camera)
        viewModel.updateComment("too warm and too zoomed in")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(1, camera.appliedBatches.size)
        assertEquals(CoachingPhase.TRANSIENT_ERROR, viewModel.uiState.value.coachingPhase)
        assertEquals("Original camera settings restored.", viewModel.uiState.value.transientMessage)
        assertFalse(viewModel.uiState.value.resetAvailable)
    }

    @Test
    fun `unknown wording can use a model intent but only local planning can apply`() = runTest(dispatcher) {
        val camera = FakeCamera(observation())
        val viewModel = viewModel(
            camera,
            visualEnabled = true,
            complaintResult = {
                ComplaintResult.Available(IntentClassification.Intent(ControlIntent.EXPOSURE_BRIGHTER))
            },
        )
        viewModel.setCameraPermission(true)

        viewModel.updateComment("Could you lift the overall light a touch?")
        viewModel.submitComment()
        runCurrent()

        assertTrue(viewModel.uiState.value.decision is LocalDecision.Recommend)
        assertEquals(0, camera.applyCalls)

        camera.observation.value = observation(id = 2)
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(1, camera.applyCalls)
        assertEquals(CameraAdjustment.ExposureCompensation(2), camera.lastAdjustment)
    }

    @Test
    fun `configured model interprets known wording before local rules`() = runTest(dispatcher) {
        var modelCalls = 0
        val camera = FakeCamera(observation(zoomRatio = 1f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
            telemetry.value = CameraTelemetry(zoomRatio = 1f)
        }
        val viewModel = viewModel(
            camera,
            visualEnabled = true,
            complaintResult = {
                modelCalls++
                ComplaintResult.Available(IntentClassification.Intent(ControlIntent.ZOOM_IN))
            },
        )

        viewModel.updateComment("too dark")
        viewModel.submitComment()
        runCurrent()

        assertEquals(1, modelCalls)
        assertEquals(
            CameraAdjustment.ZoomRatio(1.25f),
            viewModel.uiState.value.recommendation?.action
                ?.let { it as com.bolin.photohelper.coach.RecommendationAction.ApplySettings }
                ?.adjustment,
        )
    }

    @Test
    fun `configured model failure preserves the known local recommendation`() = runTest(dispatcher) {
        var modelCalls = 0
        val viewModel = viewModel(
            FakeCamera(observation()),
            visualEnabled = true,
            complaintResult = {
                modelCalls++
                ComplaintResult.Unavailable
            },
        )

        viewModel.updateComment("too dark")
        viewModel.submitComment()
        runCurrent()

        assertEquals(1, modelCalls)
        assertTrue(viewModel.uiState.value.decision is LocalDecision.Recommend)
        assertEquals(CoachingPhase.RECOMMENDATION, viewModel.uiState.value.coachingPhase)
        assertEquals("AI interpretation unavailable—using local coaching.", viewModel.uiState.value.transientMessage)
    }

    @Test
    fun `unknown compound wording can use strict model intents then one local atomic plan`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(zoomRatio = 2f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
            telemetry.value = CameraTelemetry(zoomRatio = 2f)
        }
        val viewModel = viewModel(
            camera,
            visualEnabled = true,
            complaintResult = {
                ComplaintResult.Available(
                    IntentClassification.Intent(
                        listOf(
                            ControlIntent.ZOOM_OUT,
                            ControlIntent.WHITE_BALANCE_COOLER,
                        ),
                    ),
                )
            },
        )
        viewModel.setCameraPermission(true)

        viewModel.updateComment("Could you reduce the amber cast while opening the crop slightly?")
        viewModel.submitComment()
        runCurrent()

        assertEquals("Apply both", viewModel.uiState.value.recommendation?.primaryLabel)
        assertEquals(0, camera.appliedBatches.size)

        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(
            listOf(
                CameraAdjustment.ZoomRatio(1.6f),
                CameraAdjustment.WhiteBalance(WhiteBalancePreset.COOLER),
            ),
            camera.appliedBatches.single(),
        )
    }

    @Test
    fun `unknown compound semantics take priority over a single family visual hint`() = runTest(dispatcher) {
        var complaintCalls = 0
        var visualCalls = 0
        val camera = FakeCamera(observation(blueBias = .08f, zoomRatio = 2f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
            telemetry.value = CameraTelemetry(zoomRatio = 2f)
        }
        val viewModel = viewModel(
            camera,
            visualEnabled = true,
            complaintResult = {
                complaintCalls++
                ComplaintResult.Available(
                    IntentClassification.Intent(
                        listOf(ControlIntent.ZOOM_OUT, ControlIntent.WHITE_BALANCE_WARMER),
                    ),
                )
            },
            visualResult = {
                visualCalls++
                VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER))
            },
        )

        viewModel.updateComment("cold but the framing feels cramped")
        viewModel.submitComment()
        runCurrent()

        assertEquals(1, complaintCalls)
        assertEquals(0, visualCalls)
        assertEquals("Apply both", viewModel.uiState.value.recommendation?.primaryLabel)
    }

    @Test
    fun `hosted scalar intent cannot partially satisfy an unknown compound complaint`() = runTest(dispatcher) {
        var complaintCalls = 0
        val camera = FakeCamera(observation(zoomRatio = 2f))
        val viewModel = viewModel(
            camera,
            visualEnabled = true,
            complaintResult = {
                complaintCalls++
                ComplaintResult.Available(IntentClassification.Intent(ControlIntent.WHITE_BALANCE_WARMER))
            },
        )

        viewModel.updateComment("The crop feels cramped. Move higher.")
        viewModel.submitComment()
        runCurrent()

        assertEquals(1, complaintCalls)
        assertTrue(viewModel.uiState.value.decision is LocalDecision.Clarify)
        assertEquals(null, viewModel.uiState.value.recommendation)
        assertEquals(0, camera.applyCalls)
    }

    @Test
    fun `single eligible visual family still uses frame interpretation`() = runTest(dispatcher) {
        var complaintCalls = 0
        var visualCalls = 0
        val viewModel = viewModel(
            camera = FakeCamera(observation(blueBias = .08f)),
            visualEnabled = true,
            complaintResult = {
                complaintCalls++
                ComplaintResult.Unavailable
            },
            visualResult = {
                visualCalls++
                VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER))
            },
        )

        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()

        assertEquals(0, complaintCalls)
        assertEquals(1, visualCalls)
        assertTrue(viewModel.uiState.value.decision is LocalDecision.Recommend)
    }

    @Test
    fun `configured model sees guarded wording before local clarification fallback`() = runTest(dispatcher) {
        var modelCalls = 0
        val viewModel = viewModel(
            FakeCamera(observation()),
            visualEnabled = true,
            complaintResult = {
                modelCalls++
                ComplaintResult.Unavailable
            },
        )

        viewModel.updateComment("no longer too dark")
        viewModel.submitComment()
        runCurrent()

        assertEquals(1, modelCalls)
        assertTrue(viewModel.uiState.value.decision is LocalDecision.Clarify)
    }

    @Test
    fun `apply acknowledges camera then verifies a fresh observation`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(id = 1, highlights = .3f, luma = .7f, timestamp = 1_000))
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()
        camera.observation.value = observation(id = 2, highlights = .3f, luma = .7f, timestamp = 1_250)
        runCurrent()
        camera.observation.value = observation(id = 3, highlights = .3f, luma = .7f, timestamp = 1_500)
        runCurrent()
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()
        assertNotNull(camera.lastAdjustment)
        assertEquals(1, camera.applyCalls)
        assertEquals(CoachingPhase.VERIFYING, viewModel.uiState.value.coachingPhase)

        camera.observation.value = observation(id = 4, highlights = .08f, luma = .55f, timestamp = 1_600)
        runCurrent()
        assertEquals(CoachingPhase.VERIFYING, viewModel.uiState.value.coachingPhase)

        camera.observation.value = observation(id = 5, highlights = .07f, luma = .54f, timestamp = 1_850)
        runCurrent()
        assertEquals(CoachingPhase.VERIFYING, viewModel.uiState.value.coachingPhase)

        camera.observation.value = observation(id = 6, highlights = .06f, luma = .53f, timestamp = 2_100)
        runCurrent()

        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
        assertEquals(null, viewModel.uiState.value.recommendation)
        assertTrue(viewModel.uiState.value.resetAvailable)
    }

    @Test
    fun `editing cannot cancel an in-flight camera apply`() = runTest(dispatcher) {
        val applyResult = CompletableDeferred<ApplyResult>()
        val camera = FakeCamera(observation(highlights = .3f)).apply { applyGate = applyResult }
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()
        viewModel.updateComment("too dark")
        runCurrent()
        applyResult.complete(ApplyResult.Applied)
        runCurrent()

        assertEquals("too bright", viewModel.uiState.value.comment)
        assertEquals(CoachingPhase.VERIFYING, viewModel.uiState.value.coachingPhase)
        assertTrue(viewModel.uiState.value.resetAvailable)
    }

    @Test
    fun `leaving review cannot interrupt an in-flight camera apply`() = runTest(dispatcher) {
        val applyResult = CompletableDeferred<ApplyResult>()
        val camera = FakeCamera(observation(highlights = .3f)).apply { applyGate = applyResult }
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()
        viewModel.capture()
        runCurrent()
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()
        viewModel.leaveReview()

        assertNotNull(viewModel.uiState.value.review)
        assertEquals(CoachingPhase.APPLYING, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `leaving a completed review preserves a blocked hardware state`() = runTest(dispatcher) {
        val camera = FakeCamera(observation())
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()
        viewModel.capture()
        runCurrent()
        assertNotNull(viewModel.uiState.value.review)

        camera.state.value = CameraState(CameraPhase.BLOCKED, "Retry the camera", sessionId = 0)
        runCurrent()
        viewModel.leaveReview()

        assertEquals(CameraPhase.BLOCKED, viewModel.uiState.value.cameraPhase)
        assertEquals(null, viewModel.uiState.value.review)
    }

    @Test
    fun `rapid double apply invokes camera hardware once`() = runTest(dispatcher) {
        val applyResult = CompletableDeferred<ApplyResult>()
        val camera = FakeCamera(observation(highlights = .3f)).apply { applyGate = applyResult }
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(1, camera.applyCalls)
    }

    @Test
    fun `apply replans a live recommendation from current telemetry`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f, timestamp = 1_000))
        val viewModel = viewModel(camera, nowMs = { 1_250 })
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        camera.telemetry.value = CameraTelemetry(exposureCompensationIndex = 2)
        camera.observation.value = observation(id = 2, highlights = .3f, timestamp = 1_250)
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(CameraAdjustment.ExposureCompensation(0), camera.lastAdjustment)
    }

    @Test
    fun `apply refuses a recommendation when no fresh observation exists`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f, timestamp = 1_000))
        val viewModel = viewModel(camera, nowMs = { 2_000 })
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(0, camera.applyCalls)
        assertEquals(CoachingPhase.TRANSIENT_ERROR, viewModel.uiState.value.coachingPhase)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("stale"))
    }

    @Test
    fun `backgrounding during apply restores the camera after acknowledgement`() = runTest(dispatcher) {
        val applyResult = CompletableDeferred<ApplyResult>()
        val camera = FakeCamera(observation(highlights = .3f)).apply { applyGate = applyResult }
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()
        viewModel.onBackground()
        viewModel.onForeground()
        applyResult.complete(ApplyResult.Applied)
        runCurrent()

        assertEquals(1, camera.resetCalls)
        assertFalse(viewModel.uiState.value.resetAvailable)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `backgrounding a chained apply performs one reset transaction`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f))
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        assertTrue(viewModel.uiState.value.resetAvailable)

        val secondApply = CompletableDeferred<ApplyResult>()
        camera.applyGate = secondApply
        viewModel.submitComment("too bright")
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        viewModel.onBackground()
        secondApply.complete(ApplyResult.Applied)
        runCurrent()

        assertEquals(1, camera.resetCalls)
        assertFalse(viewModel.uiState.value.resetAvailable)
    }

    @Test
    fun `backgrounding a failed chained apply restores the prior baseline`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f))
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        assertTrue(viewModel.uiState.value.resetAvailable)

        val secondApply = CompletableDeferred<ApplyResult>()
        camera.applyGate = secondApply
        viewModel.submitComment("too bright")
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        viewModel.onBackground()
        secondApply.complete(ApplyResult.Failed("driver rejected adjustment"))
        runCurrent()

        assertEquals(1, camera.resetCalls)
        assertFalse(viewModel.uiState.value.resetAvailable)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `background discontinuity invalidates the retained live observation`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f, timestamp = 1_000))
        val viewModel = viewModel(camera, nowMs = { 1_000 })
        runCurrent()

        viewModel.onBackground()
        viewModel.onForeground()
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        val recommendation = (viewModel.uiState.value.decision as LocalDecision.Recommend).recommendation
        assertEquals(null, recommendation.observationId)
        viewModel.applyRecommendation()
        runCurrent()
        assertEquals(0, camera.applyCalls)
    }

    @Test
    fun `background discontinuity rejects a frame emitted before foreground`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f, timestamp = 1_000))
        val viewModel = viewModel(camera, nowMs = { 1_250 })
        runCurrent()

        viewModel.onBackground()
        camera.observation.value = observation(id = 2, highlights = .3f, timestamp = 1_250)
        viewModel.onForeground()
        runCurrent()
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        val recommendation = (viewModel.uiState.value.decision as LocalDecision.Recommend).recommendation
        assertEquals(null, recommendation.observationId)
    }

    @Test
    fun `background discontinuity requires a new stable face lock`() = runTest(dispatcher) {
        val subject = face(trackingId = 7, centerX = .2f)
        val camera = FakeCamera(observation(timestamp = 1_000, faces = listOf(subject)))
        val viewModel = viewModel(camera, nowMs = { 1_750 })
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(subject))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(subject))
        runCurrent()

        viewModel.onBackground()
        viewModel.onForeground()
        camera.observation.value = observation(id = 4, timestamp = 1_750, faces = listOf(subject))
        runCurrent()
        viewModel.updateComment("subject too far left")
        viewModel.submitComment()
        runCurrent()

        assertFalse(viewModel.uiState.value.decision is LocalDecision.Recommend)
    }

    @Test
    fun `background discontinuity drops comparison samples`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f, timestamp = 1_000))
        val viewModel = viewModel(camera, nowMs = { 2_000 })
        runCurrent()
        camera.observation.value = observation(id = 2, highlights = .3f, timestamp = 1_250)
        runCurrent()
        camera.observation.value = observation(id = 3, highlights = .3f, timestamp = 1_500)
        runCurrent()

        viewModel.onBackground()
        viewModel.onForeground()
        camera.observation.value = observation(id = 4, highlights = .3f, timestamp = 1_750)
        runCurrent()
        camera.observation.value = observation(id = 5, highlights = .3f, timestamp = 2_000)
        runCurrent()
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        val recommendation = (viewModel.uiState.value.decision as LocalDecision.Recommend).recommendation
        val action = recommendation.action as com.bolin.photohelper.coach.RecommendationAction.ApplySettings
        val target = action.target as com.bolin.photohelper.coach.VerificationTarget.Exposure
        assertEquals(null, target.baselineObservation)
    }

    @Test
    fun `background discontinuity preserves capture review evidence`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f))
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()
        viewModel.capture()
        runCurrent()
        val review = viewModel.uiState.value.review!!

        viewModel.onBackground()
        viewModel.onForeground()
        assertEquals(review, viewModel.uiState.value.review)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        val recommendation = (viewModel.uiState.value.decision as LocalDecision.Recommend).recommendation
        assertEquals(com.bolin.photohelper.coach.ObservationOrigin.CAPTURE_REVIEW, recommendation.origin)
        assertEquals(review.observation?.id, recommendation.observationId)
    }

    @Test
    fun `one tap applies a supported white balance recommendation`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(blueBias = .08f))
        val viewModel = viewModel(camera)
        viewModel.updateComment("warmer")
        viewModel.submitComment()
        runCurrent()

        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(CameraAdjustment.WhiteBalance(WhiteBalancePreset.WARMER), camera.lastAdjustment)
        assertEquals(1, camera.applyCalls)
        assertEquals(CoachingPhase.VERIFYING, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `capture enters review without deleting or rewriting result`() = runTest(dispatcher) {
        val camera = FakeCamera(observation())
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()

        viewModel.capture()
        runCurrent()

        assertEquals("content://photo/1", viewModel.uiState.value.review?.uri)
        assertEquals(1, camera.captureCalls)
    }

    @Test
    fun `capture review plans retake from saved capture telemetry`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f)).apply {
            telemetry.value = CameraTelemetry(exposureCompensationIndex = 4)
        }
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()
        viewModel.capture()
        runCurrent()
        assertEquals(4, viewModel.uiState.value.review?.telemetry?.exposureCompensationIndex)

        camera.telemetry.value = CameraTelemetry(exposureCompensationIndex = -4)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        assertEquals(4, viewModel.uiState.value.review?.telemetry?.exposureCompensationIndex)

        val recommendation = (viewModel.uiState.value.decision as LocalDecision.Recommend).recommendation
        val adjustment = (recommendation.action as com.bolin.photohelper.coach.RecommendationAction.ApplySettings).adjustment
        assertEquals(CameraAdjustment.ExposureCompensation(2), adjustment)
    }

    @Test
    fun `saved photo can be adjusted verified and retaken without losing the original`() = runTest(dispatcher) {
        var now = 1_000L
        val camera = FakeCamera(observation(id = 1, highlights = .3f, luma = .7f)).apply {
            telemetry.value = CameraTelemetry(exposureCompensationIndex = 4)
        }
        val viewModel = viewModel(camera, nowMs = { now })
        viewModel.setCameraPermission(true)
        runCurrent()
        viewModel.capture()
        runCurrent()
        val originalUri = viewModel.uiState.value.review!!.uri

        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(CameraAdjustment.ExposureCompensation(2), camera.lastAdjustment)
        assertTrue(viewModel.uiState.value.retakeSettingsActive)
        repeat(3) { index ->
            now = 1_500L + index * 250L
            camera.observation.value = observation(
                id = 2L + index,
                highlights = .06f,
                luma = .53f,
                timestamp = now,
            )
            runCurrent()
        }
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)

        camera.telemetry.value = CameraTelemetry(exposureCompensationIndex = 2)
        viewModel.capture()
        runCurrent()

        assertEquals("content://photo/2", viewModel.uiState.value.review?.uri)
        assertEquals(listOf(originalUri, "content://photo/2"), camera.savedUris)
    }

    @Test
    fun `capture review refuses settings change when still metadata is unknown`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f)).apply {
            captureTelemetryKnown = false
        }
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()
        viewModel.capture()
        runCurrent()

        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        val advisory = viewModel.uiState.value.decision as LocalDecision.Advisory
        assertEquals("Capture settings are unavailable", advisory.headline)
    }

    @Test
    fun `visual failure preserves identical local clarification`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(blueBias = .08f, timestamp = 1_000))
        val viewModel = viewModel(camera, visualEnabled = true) { VisualResult.Unavailable }
        viewModel.setCameraPermission(true)

        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()

        assertTrue(viewModel.uiState.value.decision is LocalDecision.Clarify)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("using local coaching"))
    }

    @Test
    fun `rejected saved credentials disable visual AI for the session without deleting the key`() = runTest(dispatcher) {
        var clearCalls = 0
        val camera = FakeCamera(observation(blueBias = .08f, timestamp = 1_000))
        val preferences = FakePreferences(visualEnabled = true)
        val viewModel = viewModel(
            camera = camera,
            visualEnabled = true,
            preferences = preferences,
            clearApiKey = { clearCalls++ },
            visualResult = { VisualResult.CredentialsRejected },
        )

        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()

        assertFalse(viewModel.uiState.value.settings.visualAiEnabled)
        assertTrue(viewModel.uiState.value.settings.keyConfigured)
        assertFalse(camera.observationImagesEnabled)
        assertEquals("Saved key rejected—test it again", viewModel.uiState.value.settings.keyStatus)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("disabled"))
        assertTrue(viewModel.uiState.value.decision is LocalDecision.Clarify)
        assertEquals(0, clearCalls)
        assertTrue(preferences.visualAiEnabledWrites.isEmpty())

        viewModel.setVisualAiEnabled(true)

        assertFalse(viewModel.uiState.value.settings.visualAiEnabled)
        assertFalse(camera.observationImagesEnabled)
        assertTrue(preferences.visualAiEnabledWrites.isEmpty())
    }

    @Test
    fun `turning visual AI off ignores an in-flight result`() = runTest(dispatcher) {
        val result = CompletableDeferred<VisualResult>()
        val camera = FakeCamera(observation(blueBias = .08f, timestamp = 1_000))
        val viewModel = viewModel(camera, visualEnabled = true) { result.await() }
        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()
        assertEquals(CoachingPhase.REQUESTING_VISUAL_INTERPRETATION, viewModel.uiState.value.coachingPhase)

        viewModel.setVisualAiEnabled(false)
        result.complete(VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)))
        runCurrent()

        assertFalse(viewModel.uiState.value.settings.visualAiEnabled)
        assertTrue(viewModel.uiState.value.decision is LocalDecision.Clarify)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `reset cancels an in-flight visual complaint`() = runTest(dispatcher) {
        val result = CompletableDeferred<VisualResult>()
        val camera = FakeCamera(observation(highlights = .3f, timestamp = 1_000))
        val viewModel = viewModel(camera, visualEnabled = true) { result.await() }
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        assertTrue(viewModel.uiState.value.resetAvailable)

        camera.observation.value = observation(id = 2, blueBias = .08f, timestamp = 1_000)
        runCurrent()
        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()
        assertEquals(CoachingPhase.REQUESTING_VISUAL_INTERPRETATION, viewModel.uiState.value.coachingPhase)

        viewModel.reset()
        runCurrent()
        result.complete(VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)))
        runCurrent()

        assertFalse(viewModel.uiState.value.resetAvailable)
        assertEquals(null, viewModel.uiState.value.decision)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `literal reset command restores the active control baseline`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f))
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        assertTrue(viewModel.uiState.value.resetAvailable)

        viewModel.updateComment("reset")
        viewModel.submitComment()
        runCurrent()

        assertFalse(viewModel.uiState.value.resetAvailable)
        assertEquals(null, viewModel.uiState.value.decision)
        assertEquals("Automatic camera settings restored.", viewModel.uiState.value.transientMessage)
    }

    @Test
    fun `accepted visual hint is replanned and labelled`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(blueBias = .08f, timestamp = 1_000))
        val viewModel = viewModel(camera, visualEnabled = true) {
            VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER))
        }
        viewModel.setCameraPermission(true)

        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()

        val decision = viewModel.uiState.value.decision as LocalDecision.Recommend
        assertTrue(decision.recommendation.fromVisualHint)
    }

    @Test
    fun `guidance stops instead of switching to another face`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(timestamp = 1_000, faces = listOf(face(trackingId = 7, centerX = .2f))))
        val viewModel = viewModel(camera)
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(face(trackingId = 7, centerX = .2f)))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(face(trackingId = 7, centerX = .2f)))
        runCurrent()
        viewModel.updateComment("subject too far left")
        viewModel.submitComment()
        runCurrent()

        viewModel.startGuidance()
        assertEquals(7, viewModel.uiState.value.activeGuidance?.subjectTrackingId)

        camera.observation.value = observation(id = 2, faces = listOf(face(trackingId = 8, centerX = .5f)))
        runCurrent()

        assertEquals(CoachingPhase.TRANSIENT_ERROR, viewModel.uiState.value.coachingPhase)
        assertEquals(null, viewModel.uiState.value.activeGuidance)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("tracked person changed"))
    }

    @Test
    fun `guidance requires a new stable lock after subject loss`() = runTest(dispatcher) {
        val subject = face(trackingId = 7, centerX = .2f)
        val camera = FakeCamera(observation(timestamp = 1_000, faces = listOf(subject)))
        val viewModel = viewModel(camera)
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(subject))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(subject))
        runCurrent()
        viewModel.updateComment("subject too far left")
        viewModel.submitComment()
        runCurrent()

        camera.observation.value = observation(id = 4, timestamp = 1_750)
        runCurrent()
        camera.observation.value = observation(id = 5, timestamp = 2_000, faces = listOf(subject))
        runCurrent()
        viewModel.startGuidance()

        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
        assertEquals(null, viewModel.uiState.value.activeGuidance)
        assertTrue(viewModel.uiState.value.decision is LocalDecision.Advisory)
    }

    @Test
    fun `restarted and completed guidance cannot reuse stale state`() = runTest(dispatcher) {
        var now = 1_500L
        val voice = FakeVoice()
        val feedback = mutableListOf<Feedback>()
        val camera = FakeCamera(observation(timestamp = 1_000, faces = listOf(face(trackingId = 7, centerX = .32f))))
        val viewModel = viewModel(camera, nowMs = { now }, voice = voice, feedback = feedback::add)
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(face(trackingId = 7, centerX = .32f)))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(face(trackingId = 7, centerX = .32f)))
        runCurrent()
        viewModel.updateComment("subject too far left")
        viewModel.submitComment()
        runCurrent()
        viewModel.startGuidance()
        val instruction = viewModel.uiState.value.activeGuidance!!.instruction
        assertTrue(voice.spoken.any { it == instruction to "guidance" })

        now = 1_750L
        camera.observation.value = observation(id = 4, timestamp = now, faces = listOf(face(trackingId = 7, centerX = .35f)))
        runCurrent()
        viewModel.startGuidance()
        now = 2_250L
        camera.observation.value = observation(id = 5, timestamp = now, faces = listOf(face(trackingId = 7, centerX = .35f)))
        runCurrent()

        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
        assertEquals(null, viewModel.uiState.value.recommendation)
        assertTrue(Feedback.SUCCESS in feedback)
        assertTrue(voice.spoken.any { it.second == "result" })
    }

    @Test
    fun `face occupancy guidance requires the requested size change for 500 milliseconds`() = runTest(dispatcher) {
        var now = 1_500L
        val largeFace = face(trackingId = 7, centerX = .5f, width = .6f)
        val camera = FakeCamera(observation(timestamp = 1_000, faces = listOf(largeFace)))
        val viewModel = viewModel(camera, nowMs = { now })
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(largeFace))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(largeFace))
        runCurrent()
        viewModel.updateComment("face occupies too much")
        viewModel.submitComment()
        runCurrent()
        viewModel.startGuidance()

        assertEquals(CoachingPhase.GUIDING, viewModel.uiState.value.coachingPhase)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("obstacles"))
        listOf(.58f, .53f, .52f).forEachIndexed { index, width ->
            now = 1_750L + index * 250L
            camera.observation.value = observation(
                id = 4L + index,
                timestamp = now,
                faces = listOf(face(trackingId = 7, centerX = .5f, width = width)),
            )
            runCurrent()
            assertEquals(
                "width=$width message=${viewModel.uiState.value.transientMessage}",
                CoachingPhase.GUIDING,
                viewModel.uiState.value.coachingPhase,
            )
        }

        now = 2_500L
        camera.observation.value = observation(
            id = 7,
            timestamp = now,
            faces = listOf(face(trackingId = 7, centerX = .5f, width = .51f)),
        )
        runCurrent()

        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
        assertEquals("That matches your request.", viewModel.uiState.value.transientMessage)
    }

    @Test
    fun `one tap focuses at the user chosen preview point and clears the recommendation`() = runTest(dispatcher) {
        val camera = FakeCamera(
            observation(timestamp = 1_000, faces = listOf(face(trackingId = 7, centerX = .42f))),
            supportsFocusMetering = true,
        )
        val viewModel = viewModel(camera, nowMs = { 1_500 })
        runCurrent()
        camera.observation.value = observation(
            id = 2,
            timestamp = 1_250,
            faces = listOf(face(trackingId = 7, centerX = .42f)),
        )
        runCurrent()
        camera.observation.value = observation(
            id = 3,
            timestamp = 1_500,
            faces = listOf(face(trackingId = 7, centerX = .42f)),
        )
        runCurrent()
        viewModel.updateComment("focus missed")
        viewModel.submitComment()
        runCurrent()

        viewModel.focusAt(.2f, .7f)
        runCurrent()

        assertEquals(.2f to .7f, camera.focusPoint)
        assertEquals(null, viewModel.uiState.value.decision)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `rapid double focus invokes camera hardware once`() = runTest(dispatcher) {
        val focusResult = CompletableDeferred<ApplyResult>()
        val camera = FakeCamera(
            observation(timestamp = 1_000, faces = listOf(face(trackingId = 7, centerX = .42f))),
            supportsFocusMetering = true,
        ).apply { focusGate = focusResult }
        val viewModel = viewModel(camera, nowMs = { 1_500 })
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(face(7, .42f)))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(face(7, .42f)))
        runCurrent()
        viewModel.updateComment("focus missed")
        viewModel.submitComment()
        runCurrent()

        viewModel.focusAt(.2f, .7f)
        runCurrent()
        viewModel.focusAt(.2f, .7f)
        runCurrent()

        assertEquals(1, camera.focusCalls)
    }

    @Test
    fun `focus target expires and refuses a stale frame`() = runTest(dispatcher) {
        var now = 1_500L
        val camera = FakeCamera(
            observation(timestamp = 1_000, faces = listOf(face(trackingId = 7, centerX = .42f))),
            supportsFocusMetering = true,
        )
        val viewModel = viewModel(camera, nowMs = { now })
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(face(trackingId = 7, centerX = .42f)))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(face(trackingId = 7, centerX = .42f)))
        runCurrent()
        viewModel.updateComment("focus missed")
        viewModel.submitComment()
        runCurrent()

        now = 2_251L
        testScheduler.advanceTimeBy(LIVE_OBSERVATION_FRESH_MS + 1)
        runCurrent()
        viewModel.focusAt(.5f, .5f)

        assertEquals(null, camera.focusPoint)
        assertEquals(null, viewModel.uiState.value.recommendation)
        assertEquals(CoachingPhase.TRANSIENT_ERROR, viewModel.uiState.value.coachingPhase)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("stale"))
    }

    @Test
    fun `focus remains available when a detected face changes`() = runTest(dispatcher) {
        val camera = FakeCamera(
            observation(timestamp = 1_000, faces = listOf(face(trackingId = 7, centerX = .42f))),
            supportsFocusMetering = true,
        )
        val viewModel = viewModel(camera, nowMs = { 1_500 })
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(face(7, .42f)))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(face(7, .42f)))
        runCurrent()
        viewModel.updateComment("focus missed")
        viewModel.submitComment()
        runCurrent()

        camera.observation.value = observation(id = 4, timestamp = 1_501, faces = listOf(face(8, .42f)))
        runCurrent()
        viewModel.focusAt(.2f, .7f)
        runCurrent()

        assertEquals(.2f to .7f, camera.focusPoint)
        assertEquals(null, viewModel.uiState.value.recommendation)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `focus is advisory and non executable from capture review`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(faces = listOf(face(7, .42f))), supportsFocusMetering = true)
        val viewModel = viewModel(camera)
        runCurrent()
        viewModel.setCameraPermission(true)
        viewModel.capture()
        runCurrent()

        viewModel.updateComment("focus missed")
        viewModel.submitComment()
        runCurrent()
        viewModel.focusAt(.5f, .5f)

        assertTrue(viewModel.uiState.value.decision is LocalDecision.Advisory)
        assertEquals(null, camera.focusPoint)
    }

    @Test
    fun `failed autofocus reports failure without pretending to lock`() = runTest(dispatcher) {
        val camera = FakeCamera(
            observation(timestamp = 1_000, faces = listOf(face(trackingId = 7, centerX = .42f))),
            supportsFocusMetering = true,
            focusResult = ApplyResult.Failed("The camera could not lock focus there"),
        )
        val viewModel = viewModel(camera, nowMs = { 1_500 })
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(face(7, .42f)))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(face(7, .42f)))
        runCurrent()
        viewModel.updateComment("focus missed")
        viewModel.submitComment()
        runCurrent()

        viewModel.focusAt(.42f, .5f)
        runCurrent()

        assertEquals(CoachingPhase.TRANSIENT_ERROR, viewModel.uiState.value.coachingPhase)
        assertEquals("The camera could not lock focus there", viewModel.uiState.value.transientMessage)
        assertTrue(viewModel.uiState.value.decision is LocalDecision.Recommend)
    }

    @Test
    fun `permission refresh replaces stale granted states`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeCamera(observation()))
        viewModel.refreshPermissions(cameraGranted = true, microphoneGranted = true)
        viewModel.refreshPermissions(cameraGranted = false, microphoneGranted = false)

        assertEquals(PermissionState.DENIED, viewModel.uiState.value.cameraPermission)
        assertEquals(PermissionState.DENIED, viewModel.uiState.value.microphonePermission)
    }

    @Test
    fun `on-device voice result submits the heard complaint`() = runTest(dispatcher) {
        val voice = FakeVoice(
            available = true,
            result = { VoiceResult.Heard("the whole shot is too bright") },
        )
        val viewModel = viewModel(FakeCamera(observation(highlights = .3f)), voice = voice)
        viewModel.refreshPermissions(cameraGranted = true, microphoneGranted = true)

        viewModel.startVoiceInput()
        runCurrent()

        assertEquals("the whole shot is too bright", viewModel.uiState.value.comment)
        assertEquals(CoachingPhase.RECOMMENDATION, viewModel.uiState.value.coachingPhase)
        assertNotNull(viewModel.uiState.value.recommendation)
    }

    @Test
    fun `finishing voice input requests a final result without cancelling it`() = runTest(dispatcher) {
        val result = CompletableDeferred<VoiceResult>()
        val voice = FakeVoice(available = true, result = { result.await() })
        val camera = FakeCamera(observation(zoomRatio = 1f)).apply {
            capabilities.value = capabilities.value.copy(zoomRatioRange = 1f..10f)
            telemetry.value = CameraTelemetry(zoomRatio = 1f)
        }
        val viewModel = viewModel(camera, voice = voice)
        viewModel.refreshPermissions(cameraGranted = true, microphoneGranted = true)

        viewModel.startVoiceInput()
        runCurrent()
        val stopCallsWhileListening = voice.stopCalls

        viewModel.finishVoiceInput()

        assertEquals(1, voice.finishListeningCalls)
        assertEquals(stopCallsWhileListening, voice.stopCalls)
        assertEquals(CoachingPhase.LISTENING, viewModel.uiState.value.coachingPhase)

        result.complete(VoiceResult.Heard("make it brighter and zoom in"))
        runCurrent()

        assertEquals(CoachingPhase.RECOMMENDATION, viewModel.uiState.value.coachingPhase)
        assertEquals(
            2,
            (viewModel.uiState.value.recommendation?.action as com.bolin.photohelper.coach.RecommendationAction.ApplySettings).changes.size,
        )
    }

    @Test
    fun `voice failure keeps typed fallback and shutter available`() = runTest(dispatcher) {
        val voice = FakeVoice(
            available = true,
            result = { VoiceResult.Failed("I didn’t catch that") },
        )
        val viewModel = viewModel(FakeCamera(observation()), voice = voice)
        viewModel.refreshPermissions(cameraGranted = true, microphoneGranted = true)

        viewModel.startVoiceInput()
        runCurrent()

        assertEquals(CoachingPhase.TRANSIENT_ERROR, viewModel.uiState.value.coachingPhase)
        assertEquals("I didn’t catch that", viewModel.uiState.value.transientMessage)
        assertTrue(viewModel.uiState.value.shutterEnabled)
    }

    @Test
    fun `stalled voice input times out to the typed fallback`() = runTest(dispatcher) {
        val voice = FakeVoice(available = true, result = { awaitCancellation() })
        val viewModel = viewModel(FakeCamera(observation()), voice = voice)
        viewModel.refreshPermissions(cameraGranted = true, microphoneGranted = true)

        viewModel.startVoiceInput()
        runCurrent()
        assertEquals(CoachingPhase.LISTENING, viewModel.uiState.value.coachingPhase)
        val stopCallsWhileListening = voice.stopCalls

        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(CoachingPhase.TRANSIENT_ERROR, viewModel.uiState.value.coachingPhase)
        assertEquals(
            "Voice input timed out. Try again or type your comment.",
            viewModel.uiState.value.transientMessage,
        )
        assertEquals(stopCallsWhileListening + 1, voice.stopCalls)
        assertTrue(viewModel.uiState.value.shutterEnabled)
    }

    @Test
    fun `backgrounding cancels active voice input`() = runTest(dispatcher) {
        val voice = FakeVoice(available = true, result = { awaitCancellation() })
        val viewModel = viewModel(FakeCamera(observation()), voice = voice)
        viewModel.refreshPermissions(cameraGranted = true, microphoneGranted = true)
        viewModel.startVoiceInput()
        runCurrent()
        assertEquals(CoachingPhase.LISTENING, viewModel.uiState.value.coachingPhase)
        val stopCallsBeforeBackground = voice.stopCalls

        viewModel.onBackground()
        runCurrent()

        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
        assertEquals(stopCallsBeforeBackground + 1, voice.stopCalls)
    }

    @Test
    fun `key test clears input and storage copies`() = runTest(dispatcher) {
        val input = "secret-key".toCharArray()
        var storageCopy: CharArray? = null
        val camera = FakeCamera(observation())
        val preferences = FakePreferences(false)
        val viewModel = viewModel(
            camera = camera,
            preferences = preferences,
            visualResult = { VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)) },
            saveApiKey = { storageCopy = it },
        )

        viewModel.testAndSaveKey(input)
        runCurrent()

        assertTrue(input.all { it == '\u0000' })
        assertTrue(storageCopy!!.all { it == '\u0000' })
        assertFalse(viewModel.uiState.value.settings.testingKey)
        assertTrue(viewModel.uiState.value.settings.keyConfigured)
        assertTrue(viewModel.uiState.value.settings.visualAiEnabled)
        assertEquals(listOf(true), preferences.visualAiEnabledWrites)
        assertTrue(camera.observationImagesEnabled)
    }

    @Test
    fun `cancelled key test clears input and testing state`() = runTest(dispatcher) {
        val input = "secret-key".toCharArray()
        val viewModel = viewModel(
            camera = FakeCamera(observation()),
            visualResult = { awaitCancellation() },
        )
        viewModel.testAndSaveKey(input)
        runCurrent()
        assertTrue(viewModel.uiState.value.settings.testingKey)

        viewModel.onBackground()
        runCurrent()

        assertTrue(input.all { it == '\u0000' })
        assertFalse(viewModel.uiState.value.settings.testingKey)
        assertEquals("Key test cancelled", viewModel.uiState.value.settings.keyStatus)
    }

    @Test
    fun `key testing does not cancel an in-flight camera apply`() = runTest(dispatcher) {
        val applyResult = CompletableDeferred<ApplyResult>()
        val keyResult = CompletableDeferred<VisualResult>()
        val camera = FakeCamera(observation(highlights = .3f)).apply { applyGate = applyResult }
        val viewModel = viewModel(camera, visualResult = { keyResult.await() })
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        assertEquals(1, camera.applyCalls)

        viewModel.testAndSaveKey("secret-key".toCharArray())
        runCurrent()
        applyResult.complete(ApplyResult.Applied)
        runCurrent()

        assertTrue(viewModel.uiState.value.resetAvailable)
        keyResult.complete(VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)))
        runCurrent()
        assertTrue(viewModel.uiState.value.settings.keyConfigured)
    }

    @Test
    fun `key testing does not cancel an in-flight camera reset`() = runTest(dispatcher) {
        val resetResult = CompletableDeferred<ApplyResult>()
        val keyResult = CompletableDeferred<VisualResult>()
        val camera = FakeCamera(observation(highlights = .3f))
        val viewModel = viewModel(camera, visualResult = { keyResult.await() })
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        assertTrue(viewModel.uiState.value.resetAvailable)
        camera.resetGate = resetResult
        viewModel.reset()
        runCurrent()
        assertEquals(1, camera.resetCalls)

        viewModel.testAndSaveKey("secret-key".toCharArray())
        runCurrent()
        resetResult.complete(ApplyResult.Applied)
        runCurrent()

        assertFalse(viewModel.uiState.value.resetAvailable)
        keyResult.complete(VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)))
        runCurrent()
        assertTrue(viewModel.uiState.value.settings.keyConfigured)
    }

    @Test
    fun `key testing does not cancel an in-flight camera focus`() = runTest(dispatcher) {
        val focusResult = CompletableDeferred<ApplyResult>()
        val keyResult = CompletableDeferred<VisualResult>()
        val camera = FakeCamera(
            observation(timestamp = 1_000, faces = listOf(face(trackingId = 7, centerX = .42f))),
            supportsFocusMetering = true,
        ).apply { focusGate = focusResult }
        val viewModel = viewModel(camera, nowMs = { 1_500 }, visualResult = { keyResult.await() })
        runCurrent()
        camera.observation.value = observation(id = 2, timestamp = 1_250, faces = listOf(face(7, .42f)))
        runCurrent()
        camera.observation.value = observation(id = 3, timestamp = 1_500, faces = listOf(face(7, .42f)))
        runCurrent()
        viewModel.updateComment("focus missed")
        viewModel.submitComment()
        runCurrent()
        viewModel.focusAt(.2f, .7f)
        runCurrent()
        assertEquals(1, camera.focusCalls)

        viewModel.testAndSaveKey("secret-key".toCharArray())
        runCurrent()
        focusResult.complete(ApplyResult.Applied)
        runCurrent()

        assertEquals(null, viewModel.uiState.value.decision)
        assertEquals("Focus locked at the selected point.", viewModel.uiState.value.transientMessage)
        keyResult.complete(VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)))
        runCurrent()
        assertTrue(viewModel.uiState.value.settings.keyConfigured)
    }

    @Test
    fun `clearing a key cancels its in-flight test before it can save`() = runTest(dispatcher) {
        val keyResult = CompletableDeferred<VisualResult>()
        var saveCalls = 0
        val viewModel = viewModel(
            camera = FakeCamera(observation()),
            visualResult = { keyResult.await() },
            saveApiKey = {
                saveCalls++
                it.fill('\u0000')
            },
        )
        viewModel.testAndSaveKey("secret-key".toCharArray())
        runCurrent()
        assertTrue(viewModel.uiState.value.settings.testingKey)

        viewModel.clearKey()
        runCurrent()
        keyResult.complete(VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)))
        runCurrent()

        assertEquals(0, saveCalls)
        assertFalse(viewModel.uiState.value.settings.keyConfigured)
        assertEquals("No key saved", viewModel.uiState.value.settings.keyStatus)
    }

    @Test
    fun `failed key deletion disables uploads while keeping retry state`() = runTest(dispatcher) {
        val camera = FakeCamera(observation())
        val viewModel = viewModel(
            camera = camera,
            visualEnabled = true,
            clearApiKey = { error("keystore unavailable") },
        )

        viewModel.clearKey()

        assertTrue(viewModel.uiState.value.settings.keyConfigured)
        assertFalse(viewModel.uiState.value.settings.visualAiEnabled)
        assertFalse(camera.observationImagesEnabled)
        assertEquals("Could not clear key", viewModel.uiState.value.settings.keyStatus)
    }

    @Test
    fun `failed background reset keeps reset recovery available`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f, luma = .7f))
        camera.resetResult = ApplyResult.Failed("reset failed")
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        assertTrue(viewModel.uiState.value.resetAvailable)

        viewModel.onBackground()
        runCurrent()

        assertTrue(viewModel.uiState.value.resetAvailable)
        assertEquals("reset failed", viewModel.uiState.value.transientMessage)
    }

    @Test
    fun `foreground input waits for a background restore to finish`() = runTest(dispatcher) {
        val resetResult = CompletableDeferred<ApplyResult>()
        val camera = FakeCamera(observation(highlights = .3f)).apply { resetGate = resetResult }
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        viewModel.applyRecommendation()
        runCurrent()
        assertTrue(viewModel.uiState.value.resetAvailable)

        viewModel.onBackground()
        runCurrent()
        viewModel.onForeground()
        viewModel.updateComment("too dark")
        viewModel.submitComment()
        runCurrent()

        assertEquals(CoachingPhase.APPLYING, viewModel.uiState.value.coachingPhase)
        assertEquals("too bright", viewModel.uiState.value.comment)
        assertEquals(1, camera.resetCalls)

        resetResult.complete(ApplyResult.Applied)
        runCurrent()
        assertFalse(viewModel.uiState.value.resetAvailable)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `stale visual result restores the local clarification`() = runTest(dispatcher) {
        val result = CompletableDeferred<VisualResult>()
        val camera = FakeCamera(observation(blueBias = .08f, timestamp = 1_000))
        val viewModel = viewModel(camera, visualEnabled = true) { result.await() }
        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()
        assertEquals(CoachingPhase.REQUESTING_VISUAL_INTERPRETATION, viewModel.uiState.value.coachingPhase)

        camera.observation.value = observation(id = 2, blueBias = .30f, timestamp = 1_250)
        runCurrent()
        result.complete(VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)))
        runCurrent()

        assertTrue(viewModel.uiState.value.decision is LocalDecision.Clarify)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("using local coaching"))
    }

    @Test
    fun `editing a submitted complaint cancels its visual result`() = runTest(dispatcher) {
        val result = CompletableDeferred<VisualResult>()
        val camera = FakeCamera(observation(blueBias = .08f, timestamp = 1_000))
        val viewModel = viewModel(camera, visualEnabled = true) { result.await() }
        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()

        viewModel.updateComment("looks yellow")
        result.complete(VisualResult.Available(VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER)))
        runCurrent()

        assertEquals("looks yellow", viewModel.uiState.value.comment)
        assertEquals(null, viewModel.uiState.value.decision)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
    }

    @Test
    fun `key load failure restores local coaching`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(blueBias = .08f, timestamp = 1_000))
        val viewModel = viewModel(
            camera = camera,
            visualEnabled = true,
            loadApiKey = { error("keystore unavailable") },
        )
        viewModel.updateComment("looks blue")
        viewModel.submitComment()
        runCurrent()

        assertTrue(viewModel.uiState.value.decision is LocalDecision.Clarify)
        assertEquals(CoachingPhase.IDLE, viewModel.uiState.value.coachingPhase)
        assertFalse(viewModel.uiState.value.settings.visualAiEnabled)
        assertFalse(viewModel.uiState.value.settings.keyConfigured)
        assertFalse(camera.observationImagesEnabled)
        assertEquals("Saved key unavailable—enter it again", viewModel.uiState.value.settings.keyStatus)
    }

    @Test
    fun `camera session change invalidates recommendation before apply`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f))
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()
        assertNotNull(viewModel.uiState.value.recommendation)

        camera.state.value = CameraState(CameraPhase.READY, sessionId = 1)
        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(0, camera.applyCalls)
        assertEquals(null, viewModel.uiState.value.recommendation)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("camera session", ignoreCase = true))
    }

    @Test
    fun `physical lens switch waits for an observation from the new lens`() = runTest(dispatcher) {
        val camera = FakeCamera(observation(highlights = .3f, lensId = "rear-wide")).apply {
            telemetry.value = CameraTelemetry(lensId = "rear-wide")
        }
        val viewModel = viewModel(camera)
        viewModel.updateComment("too bright")
        viewModel.submitComment()
        runCurrent()

        camera.telemetry.value = CameraTelemetry(lensId = "rear-tele")
        viewModel.applyRecommendation()
        runCurrent()

        assertEquals(0, camera.applyCalls)
        assertTrue(viewModel.uiState.value.transientMessage.orEmpty().contains("lens", ignoreCase = true))
    }

    @Test
    fun `retrying a blocked camera returns the UI to starting`() = runTest(dispatcher) {
        val camera = FakeCamera(observation())
        val viewModel = viewModel(camera)
        camera.state.value = CameraState(CameraPhase.BLOCKED, "Unable to start camera")
        runCurrent()

        viewModel.retryCamera()

        assertEquals(CameraPhase.STARTING, viewModel.uiState.value.cameraPhase)
        assertEquals(null, viewModel.uiState.value.transientMessage)
    }

    @Test
    fun `rapid double shutter saves only once`() = runTest(dispatcher) {
        val camera = FakeCamera(observation())
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()

        viewModel.capture()
        viewModel.capture()
        runCurrent()

        assertEquals(1, camera.captureCalls)
    }

    @Test
    fun `stalled capture times out and restores the shutter`() = runTest(dispatcher) {
        val camera = FakeCamera(observation()).apply { stallCapture = true }
        val viewModel = viewModel(camera)
        viewModel.setCameraPermission(true)
        runCurrent()

        viewModel.capture()
        runCurrent()
        assertEquals(CameraPhase.CAPTURING, viewModel.uiState.value.cameraPhase)

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(CameraPhase.READY, viewModel.uiState.value.cameraPhase)
        assertTrue(viewModel.uiState.value.shutterEnabled)
        assertEquals("Camera did not finish saving the photo. Try again.", viewModel.uiState.value.transientMessage)
    }

    private fun viewModel(
        camera: FakeCamera,
        visualEnabled: Boolean = false,
        preferences: PreferenceStore = FakePreferences(visualEnabled),
        saveApiKey: (CharArray) -> Unit = { it.fill('\u0000') },
        clearApiKey: () -> Unit = {},
        loadApiKey: () -> CharArray? = { if (visualEnabled) "test-key".toCharArray() else null },
        nowMs: () -> Long = { 1_000 },
        voice: VoiceIo = FakeVoice(),
        feedback: (Feedback) -> Unit = {},
        complaintResult: suspend () -> ComplaintResult = { ComplaintResult.Unavailable },
        visualResult: suspend () -> VisualResult = { VisualResult.Unavailable },
    ) = CaptureViewModel(
        camera = camera,
        coach = DefaultCoachEngine(),
        voice = voice,
        preferences = preferences,
        hasApiKey = { visualEnabled },
        loadApiKey = loadApiKey,
        saveApiKey = saveApiKey,
        clearApiKey = clearApiKey,
        interpretVisual = { _, _ -> visualResult() },
        interpretComplaint = { _, _ -> complaintResult() },
        createTestImage = { byteArrayOf(1) },
        feedback = feedback,
        nowMs = nowMs,
    )

    private class FakeCamera(
        initialObservation: FrameObservation,
        supportsFocusMetering: Boolean = false,
        var focusResult: ApplyResult = ApplyResult.Applied,
    ) : CaptureHardware {
        override val state = MutableStateFlow(CameraState(CameraPhase.READY))
        override val capabilities = MutableStateFlow(
            CameraCapabilities(
                exposureCompensationRange = -6..6,
                exposureCompensationStepEv = 1f / 3f,
                supportedWhiteBalancePresets = WhiteBalancePreset.entries.toSet(),
                supportsFocusMetering = supportsFocusMetering,
            ),
        )
        override val telemetry = MutableStateFlow(CameraTelemetry())
        override val observation = MutableStateFlow<FrameObservation?>(initialObservation)
        var lastAdjustment: CameraAdjustment? = null
        var focusPoint: Pair<Float, Float>? = null
        var focusCalls = 0
        var applyCalls = 0
        val appliedBatches = mutableListOf<List<CameraAdjustment>>()
        var captureCalls = 0
        var resetCalls = 0
        val savedUris = mutableListOf<String>()
        var stallCapture = false
        var captureTelemetryKnown = true
        var resetResult: ApplyResult = ApplyResult.Applied
        var observationImagesEnabled = false
        var applyGate: CompletableDeferred<ApplyResult>? = null
        var focusGate: CompletableDeferred<ApplyResult>? = null
        var resetGate: CompletableDeferred<ApplyResult>? = null

        override suspend fun apply(adjustment: CameraAdjustment): ApplyResult {
            applyCalls++
            lastAdjustment = adjustment
            return applyGate?.await() ?: ApplyResult.Applied
        }

        override suspend fun applyAtomically(adjustments: List<CameraAdjustment>): ApplyResult {
            appliedBatches += adjustments.toList()
            applyCalls++
            lastAdjustment = adjustments.singleOrNull()
            return applyGate?.await() ?: ApplyResult.Applied
        }

        override suspend fun focusAt(xFraction: Float, yFraction: Float): ApplyResult {
            focusCalls++
            focusPoint = xFraction to yFraction
            return focusGate?.await() ?: focusResult
        }

        override suspend fun reset(): ApplyResult {
            resetCalls++
            return resetGate?.await() ?: resetResult
        }

        override suspend fun capture(): CaptureResult {
            captureCalls++
            if (stallCapture) awaitCancellation()
            val uri = "content://photo/$captureCalls"
            savedUris += uri
            return CaptureResult.Saved(
                SavedCapture(
                    "capture-$captureCalls",
                    uri,
                    observation.value,
                    telemetry.value.takeIf { captureTelemetryKnown },
                ),
            )
        }

        override suspend fun observationImage(capture: SavedCapture?): ByteArray = byteArrayOf(1, 2, 3)
        override fun setAnalysisPaused(paused: Boolean) = Unit
        override fun setObservationImageEnabled(enabled: Boolean) {
            observationImagesEnabled = enabled
        }
        override fun close() = Unit
    }

    private class FakeVoice(
        private val available: Boolean = false,
        private val result: suspend () -> VoiceResult = { VoiceResult.Unavailable("unavailable") },
    ) : VoiceIo {
        var stopCalls = 0
        var finishListeningCalls = 0
        val spoken = mutableListOf<Pair<String, String>>()

        override fun isOnDeviceRecognitionAvailable() = available
        override suspend fun listenOnce(locale: Locale): VoiceResult = result()
        override fun speak(text: String, utteranceId: String) {
            spoken += text to utteranceId
        }
        override fun stop() {
            stopCalls++
        }
        override fun finishListening() {
            finishListeningCalls++
        }
        override fun close() = Unit
    }

    private class FakePreferences(private val visualEnabled: Boolean) : PreferenceStore {
        val visualAiEnabledWrites = mutableListOf<Boolean>()
        override fun onboardingComplete() = true
        override fun setOnboardingComplete() = Unit
        override fun settings(keyConfigured: Boolean) = SettingsUiState(
            visualAiEnabled = visualEnabled,
            keyConfigured = keyConfigured,
        )
        override fun setSpokenGuidance(enabled: Boolean) = Unit
        override fun setHaptics(enabled: Boolean) = Unit
        override fun setTechnicalDetail(enabled: Boolean) = Unit
        override fun setVisualAiEnabled(enabled: Boolean) {
            visualAiEnabledWrites += enabled
        }
    }

    private fun observation(
        id: Long = 1,
        luma: Float = .5f,
        highlights: Float = 0f,
        blueBias: Float = 0f,
        timestamp: Long = 1_000,
        faces: List<FaceObservation> = emptyList(),
        lensId: String? = null,
        zoomRatio: Float? = null,
    ) = FrameObservation(
        id = id,
        timestampMs = timestamp,
        meanLuma = luma,
        highlightClipFraction = highlights,
        shadowClipFraction = 0f,
        chromaBlueBias = blueBias,
        faces = faces,
        lensId = lensId,
        zoomRatio = zoomRatio,
        sourceWidth = 640,
        sourceHeight = 480,
    )

    private fun face(trackingId: Int, centerX: Float, width: Float = .2f) = FaceObservation(
        trackingId = trackingId,
        left = centerX - width / 2f,
        top = .25f,
        right = centerX + width / 2f,
        bottom = .65f,
    )
}
