package com.bolin.photohelper.capture

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.percentOffset
import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.Recommendation
import com.bolin.photohelper.coach.RecommendationAction
import com.bolin.photohelper.coach.RecommendationBasis
import com.bolin.photohelper.coach.SettingChange
import com.bolin.photohelper.coach.VerificationTarget
import com.bolin.photohelper.ui.PhotoHelperTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaptureScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onboardingUsesTwoExplicitSteps() {
        val step = mutableStateOf(0)
        var openedCamera = false
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = CaptureUiState(onboardingStep = step.value),
                    onOnboardingContinue = { step.value = 1 },
                    onOpenCamera = { openedCamera = true },
                )
            }
        }

        compose.onNodeWithText("Tell the camera what looks wrong.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Connect Qwen. You stay in control.").assertIsDisplayed()
        compose.onNodeWithText("Alibaba Cloud Model Studio (Bailian) API key").assertIsDisplayed()
        compose.onNodeWithText("Photo Helper is designed to use an image-capable LLM.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Open camera").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(openedCamera) }
    }

    @Test
    fun deniedCameraShowsRecoveryWithoutBlankPreview() {
        var openedSettings = false
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(cameraPermission = PermissionState.DENIED),
                    onOpenAppSettings = { openedSettings = true },
                    preview = { Box(Modifier.fillMaxSize().testTag("fake_preview")) },
                )
            }
        }

        compose.onNodeWithText("Camera access is off").assertIsDisplayed()
        compose.onNodeWithTag("fake_preview").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()
        compose.runOnIdle { assertTrue(openedSettings) }
    }

    @Test
    fun retryFromBlockedCameraReentersPreviewCompositionForBinding() {
        val state = mutableStateOf(readyState(cameraPhase = CameraPhase.BLOCKED))
        val bindAttempt = mutableStateOf(0)
        var binds = 0
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state.value,
                    onRetryCamera = {
                        state.value = state.value.copy(cameraPhase = CameraPhase.STARTING)
                        bindAttempt.value++
                    },
                    preview = {
                        DisposableEffect(bindAttempt.value) {
                            binds++
                            onDispose { }
                        }
                        Box(Modifier.fillMaxSize().testTag("retry_preview"))
                    },
                )
            }
        }

        compose.onNodeWithTag("retry_preview").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag("retry_preview").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, binds) }
    }

    @Test
    fun recommendationKeepsPreviewAndShutterActionable() {
        var applied = false
        var captured = false
        val state = readyState(
            coachingPhase = CoachingPhase.RECOMMENDATION,
            decision = LocalDecision.Recommend(exposureRecommendation()),
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state,
                    onApplyRecommendation = { applied = true },
                    onShutter = { captured = true },
                    preview = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray)
                                .testTag("fake_preview"),
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag("fake_preview").assertIsDisplayed()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithContentDescription("Take photo")
            .assertIsEnabled()
            .assert(hasClickAction())
            .performClick()
        compose.runOnIdle {
            assertTrue(applied)
            assertTrue(captured)
        }
    }

    @Test
    fun cameraFlipSwitchesToAccessibleSelfieState() {
        val frontCamera = mutableStateOf(false)
        var flips = 0
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(),
                    isFrontCamera = frontCamera.value,
                    canFlipCamera = true,
                    onFlipCamera = {
                        flips++
                        frontCamera.value = true
                    },
                )
            }
        }

        compose.onNodeWithContentDescription("Switch to front camera")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        compose.onNodeWithText("LIVE · SELFIE").assertIsDisplayed()
        compose.onNodeWithContentDescription("Switch to rear camera").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, flips) }
    }

    @Test
    fun flashControlCyclesOffFlashAndContinuousLight() {
        val mode = mutableStateOf(FlashMode.OFF)
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState().copy(
                        capabilities = CameraCapabilities(hasFlashUnit = true),
                        flashMode = mode.value,
                    ),
                    onFlashModeCycle = {
                        mode.value = when (mode.value) {
                            FlashMode.OFF -> FlashMode.ON
                            FlashMode.ON -> FlashMode.TORCH
                            FlashMode.TORCH -> FlashMode.OFF
                        }
                    },
                )
            }
        }

        compose.onNodeWithContentDescription("Flash off. Tap for flash on").performClick()
        compose.onNodeWithContentDescription("Flash on. Tap for continuous light").performClick()
        compose.onNodeWithContentDescription("Continuous light on. Tap to turn off").performClick()
        compose.onNodeWithContentDescription("Flash off. Tap for flash on").assertIsDisplayed()
    }

    @Test
    fun cameraFlipIsDisabledWhenUnavailableOrCameraIsBusy() {
        val state = mutableStateOf(readyState())
        val available = mutableStateOf(false)
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state.value,
                    canFlipCamera = available.value,
                )
            }
        }

        compose.onNodeWithContentDescription("Switch to front camera").assertIsNotEnabled()
        compose.runOnIdle {
            available.value = true
            state.value = state.value.copy(coachingPhase = CoachingPhase.APPLYING)
        }
        compose.onNodeWithContentDescription("Switch to front camera").assertIsNotEnabled()
    }

    @Test
    fun executableRecommendationAlwaysGetsOneTapApply() {
        var applied = 0
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(exposureRecommendation().copy(primaryLabel = null)),
                    ),
                    onApplyRecommendation = { applied++ },
                )
            }
        }

        compose.onNodeWithText("Apply").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test
    fun compoundRecommendationShowsEveryChangeWithOneApplyAction() {
        var applied = 0
        val recommendation = exposureRecommendation().copy(
            headline = "2 camera changes ready",
            actionText = "Zoom · 1.6× digital zoom\nColor · Cooler white balance",
            consequence = "These changes affect the whole photo and can be reset together.",
            primaryLabel = "Apply both",
            action = RecommendationAction.ApplySettings(
                listOf(
                    SettingChange(
                        CameraAdjustment.ZoomRatio(1.6f),
                        VerificationTarget.Zoom(direction = -1, baselineRatio = 2f, targetRatio = 1.6f),
                    ),
                    SettingChange(
                        CameraAdjustment.WhiteBalance(WhiteBalancePreset.COOLER),
                        VerificationTarget.ColorBalance(direction = -1, baselineBlueBias = 0f),
                    ),
                ),
            ),
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(recommendation),
                    ),
                    onApplyRecommendation = { applied++ },
                )
            }
        }

        compose.onNodeWithText("Zoom · 1.6× digital zoom", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Color · Cooler white balance", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Apply both").assertCountEquals(1)
        compose.onNodeWithText("Apply both").performClick()
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test
    fun physicalRecommendationStartsGuidanceInsteadOfApplying() {
        var applied = 0
        var guided = 0
        val guidance = Recommendation(
            complaintId = "move",
            cameraSessionId = 0,
            headline = "Move the camera",
            actionText = "Aim the phone slightly right",
            consequence = "I’ll check the framing.",
            primaryLabel = null,
            action = RecommendationAction.GuidePosition(
                instruction = "Aim the phone slightly right.",
                target = VerificationTarget.FacePosition(0.35f..0.65f, 0.3f..0.7f),
            ),
            basis = RecommendationBasis.USER_PREFERENCE,
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(guidance),
                    ),
                    onApplyRecommendation = { applied++ },
                    onStartGuidance = { guided++ },
                )
            }
        }

        compose.onNodeWithText("Start guidance").assertIsDisplayed().performClick()
        compose.onNodeWithText("Apply").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, applied)
            assertEquals(1, guided)
        }
    }

    @Test
    fun focusRecommendationShowsOneTappableMarkerInsteadOfARegularActionButton() {
        var focused = 0
        val subject = FaceObservation(7, .32f, .25f, .52f, .65f)
        val recommendation = Recommendation(
            complaintId = "focus-missed",
            cameraSessionId = 0,
            headline = "Choose what should be sharp",
            actionText = "Tap the subject in the preview",
            consequence = "The camera will try to lock focus at that point.",
            primaryLabel = null,
            action = RecommendationAction.TapToFocus,
            basis = RecommendationBasis.USER_PREFERENCE,
            subjectTrackingId = subject.trackingId,
            subjectFace = subject,
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(recommendation),
                    ).copy(
                        observation = FrameObservation(
                            id = 1,
                            timestampMs = 1_000,
                            meanLuma = .5f,
                            highlightClipFraction = 0f,
                            shadowClipFraction = 0f,
                            faces = listOf(subject),
                            sourceWidth = 640,
                            sourceHeight = 480,
                        ),
                        capabilities = CameraCapabilities(supportsFocusMetering = true),
                    ),
                    onFocusTarget = { _, _ -> focused++ },
                )
            }
        }
        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET)
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
            .performClick()
        compose.onNodeWithText("Dismiss")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
        compose.onNodeWithText("Apply").assertDoesNotExist()
        compose.onNodeWithText("Start guidance").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, focused) }
    }

    @Test
    fun focusRecommendationShowsAUsableTargetWithoutAnyFace() {
        var focusPoint: Pair<Float, Float>? = null
        val recommendation = Recommendation(
            complaintId = "focus-any-subject",
            cameraSessionId = 0,
            headline = "Choose what should be sharp",
            actionText = "Tap the subject in the preview",
            consequence = "The camera will try to lock focus at that point.",
            primaryLabel = null,
            action = RecommendationAction.TapToFocus,
            basis = RecommendationBasis.USER_PREFERENCE,
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(recommendation),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                    onFocusTarget = { x, y -> focusPoint = x to y },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(.5f, focusPoint?.first ?: -1f, .01f)
            assertEquals(.42f, focusPoint?.second ?: -1f, .01f)
        }
    }

    @Test
    fun modelFocusShowsItsGridCellWithoutAConfirmationCard() {
        var focusPoint: Pair<Float, Float>? = null
        val recommendation = Recommendation(
            complaintId = "focus-watch",
            cameraSessionId = 0,
            headline = "Subject located",
            actionText = "Tap the marked point to focus",
            consequence = "The camera will focus in the matching grid cell.",
            primaryLabel = null,
            action = RecommendationAction.FocusAt(
                xFraction = 2.5f / 6f,
                yFraction = 4.5f / 8f,
                leftFraction = 2f / 6f,
                topFraction = 4f / 8f,
                rightFraction = 3f / 6f,
                bottomFraction = 5f / 8f,
            ),
            basis = RecommendationBasis.USER_PREFERENCE,
            fromVisualHint = true,
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(recommendation),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                    onFocusTarget = { x, y -> focusPoint = x to y },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_CELL).assertIsDisplayed()
        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET).assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("Choose manually").assertDoesNotExist()
        compose.onNodeWithText("Focus here").assertDoesNotExist()
        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET).performClick()
        compose.onNodeWithTag(CaptureTestTags.FOCUS_AREA).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2.5f / 6f, focusPoint?.first ?: -1f, .001f)
            assertEquals(4.5f / 8f, focusPoint?.second ?: -1f, .001f)
        }
    }

    @Test
    fun selfiePreviewMirrorsModelFocusPointBeforeFocusing() {
        var focusPoint: Pair<Float, Float>? = null
        val targetX = 2.5f / 6f
        val recommendation = Recommendation(
            complaintId = "focus-watch-selfie",
            cameraSessionId = 0,
            headline = "Subject located",
            actionText = "Tap the marked point to focus",
            consequence = "The camera will focus in the matching grid cell.",
            primaryLabel = null,
            action = RecommendationAction.FocusAt(
                xFraction = targetX,
                yFraction = 4.5f / 8f,
                leftFraction = 2f / 6f,
                topFraction = 4f / 8f,
                rightFraction = 3f / 6f,
                bottomFraction = 5f / 8f,
            ),
            basis = RecommendationBasis.USER_PREFERENCE,
            fromVisualHint = true,
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(recommendation),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                    isFrontCamera = true,
                    onFocusTarget = { x, y -> focusPoint = x to y },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET).performClick()
        compose.runOnIdle {
            assertEquals(1f - targetX, focusPoint?.first ?: -1f, .001f)
            assertEquals(4.5f / 8f, focusPoint?.second ?: -1f, .001f)
        }
    }

    @Test
    fun modelFocusDoesNotShowAConfirmationCard() {
        val recommendation = Recommendation(
            complaintId = "focus-headphones",
            cameraSessionId = 0,
            headline = "Subject located",
            actionText = "Tap the marked point to focus",
            consequence = "The camera will focus in the matching grid cell.",
            primaryLabel = null,
            action = RecommendationAction.FocusAt(
                xFraction = .75f,
                yFraction = .75f,
                leftFraction = .7f,
                topFraction = .7f,
                rightFraction = .8f,
                bottomFraction = .8f,
            ),
            basis = RecommendationBasis.USER_PREFERENCE,
            fromVisualHint = true,
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(recommendation),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                )
            }
        }

        compose.onNodeWithTag("focus_recommendation_card").assertDoesNotExist()
        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET).assertIsDisplayed()
    }

    @Test
    fun voiceCountdownIsVisibleAndCancellable() {
        var cancelled = 0
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState().copy(countdownSecondsRemaining = 3),
                    onCancelCoaching = { cancelled++ },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.COUNTDOWN).assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test
    fun liveTranscriptIsReadOnlyAndTheMicStartsThenSends() {
        val state = mutableStateOf(readyState().copy(comment = "focus on the watch"))
        var micTaps = 0
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state.value,
                    onMicrophone = {
                        micTaps++
                        state.value = state.value.copy(
                            coachingPhase = if (micTaps == 1) CoachingPhase.LISTENING else CoachingPhase.INTERPRETING,
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.COMMENT).assertIsDisplayed().assert(hasClickAction().not())
        compose.onNodeWithText("Send").assertDoesNotExist()
        compose.onNodeWithContentDescription("Describe shot by voice").performClick()
        compose.onNodeWithContentDescription("Finish voice comment").performClick()
        compose.runOnIdle { assertEquals(2, micTaps) }
    }

    @Test
    fun focusRecommendationLetsTheUserTapAnyPreviewPoint() {
        var focusPoint: Pair<Float, Float>? = null
        val recommendation = Recommendation(
            complaintId = "focus-any-subject",
            cameraSessionId = 0,
            headline = "Choose what should be sharp",
            actionText = "Tap the subject in the preview",
            consequence = "The camera will try to lock focus at that point.",
            primaryLabel = null,
            action = RecommendationAction.TapToFocus,
            basis = RecommendationBasis.USER_PREFERENCE,
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(recommendation),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                    onFocusTarget = { x, y -> focusPoint = x to y },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_AREA).performTouchInput {
            click(percentOffset(.25f, .25f))
        }
        compose.runOnIdle {
            assertEquals(.25f, focusPoint?.first ?: -1f, .01f)
            assertEquals(.25f, focusPoint?.second ?: -1f, .01f)
        }
    }

    @Test
    fun readyPreviewTapShowsTheFocusMarkerAtThatPoint() {
        var focusPoint: Pair<Float, Float>? = null
        val state = mutableStateOf(
            readyState().copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state.value,
                    onFocusTarget = { x, y ->
                        focusPoint = x to y
                        state.value = state.value.copy(
                            coachingPhase = CoachingPhase.APPLYING,
                            focusIndicator = FocusPoint(x, y),
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_AREA).performTouchInput {
            click(percentOffset(.25f, .25f))
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET).assertIsDisplayed()
        compose.onNodeWithTag(CaptureTestTags.FOCUS_AREA).performTouchInput {
            click(percentOffset(.75f, .25f))
        }
        compose.runOnIdle {
            assertEquals(.75f, focusPoint?.first ?: -1f, .01f)
            assertEquals(.25f, focusPoint?.second ?: -1f, .01f)
        }
    }

    @Test
    fun advisoryKeepsResetOneTapAway() {
        var reset = false
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        decision = LocalDecision.Advisory("Use Reset", "Restore the camera baseline."),
                        resetAvailable = true,
                    ),
                    onReset = { reset = true },
                )
            }
        }

        compose.onNodeWithText("Reset").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(reset) }
    }

    @Test
    fun modelDerivedAdvisoryKeepsProviderProvenance() {
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        decision = LocalDecision.Advisory(
                            "White-balance adjustment is unavailable",
                            "This camera cannot apply that color change.",
                            fromVisualHint = true,
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("AI-interpreted by Qwen via Alibaba Cloud; camera controls checked on device")
            .assertIsDisplayed()
    }

    @Test
    fun visualLoadingLeavesClarificationChipsUsable() {
        var selected: ClarificationChip? = null
        var cancelled = false
        val chip = ClarificationChip("Size", "face too big")
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.REQUESTING_VISUAL_INTERPRETATION,
                        decision = LocalDecision.Clarify("Which part feels wrong?", listOf(chip)),
                    ),
                    onClarificationSelected = { selected = it },
                    onCancelCoaching = { cancelled = true },
                )
            }
        }

        compose.onNodeWithText("Looking at the scene with Qwen…").assertIsDisplayed()
        compose.onNodeWithText("Size").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(chip, selected)
            assertTrue(cancelled)
        }
    }

    @Test
    fun captureReviewUsesRetakeWordingAndSavedOriginalMessage() {
        var applied = false
        var retaken = false
        val capture = SavedCapture(
            id = "saved-1",
            uri = "content://photo-helper/not-present",
            observation = null,
            telemetry = CameraTelemetry(),
        )
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        cameraPhase = CameraPhase.REVIEWING,
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        review = capture,
                        decision = LocalDecision.Recommend(exposureRecommendation()),
                    ),
                    onApplyRecommendation = { applied = true },
                    onRetake = { retaken = true },
                )
            }
        }

        compose.onNodeWithText("Original remains saved").assertIsDisplayed()
        compose.onNodeWithText("Apply for retake").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Retake").assertIsDisplayed().performClick()
        compose.onNodeWithTag(CaptureTestTags.SHUTTER).assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(applied)
            assertTrue(retaken)
        }
    }

    @Test
    fun captureReviewDisablesNavigationWhileApplying() {
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        cameraPhase = CameraPhase.REVIEWING,
                        coachingPhase = CoachingPhase.APPLYING,
                        review = SavedCapture(
                            id = "saved-1",
                            uri = "content://photo-helper/not-present",
                            observation = null,
                            telemetry = CameraTelemetry(),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("Retake").assertIsNotEnabled()
        compose.onAllNodesWithText("Done").assertAll(isNotEnabled())
    }

    @Test
    fun captureReviewKeepsActionsAdjacentWhenNoDecisionIsShown() {
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        cameraPhase = CameraPhase.REVIEWING,
                        review = SavedCapture(
                            id = "saved-1",
                            uri = "content://photo-helper/not-present",
                            observation = null,
                            telemetry = CameraTelemetry(),
                        ),
                    ).copy(comment = "make the retake cooler"),
                )
            }
        }

        val transcript = compose.onNodeWithTag(CaptureTestTags.COMMENT).fetchSemanticsNode().boundsInRoot
        val retake = compose.onNodeWithText("Retake").fetchSemanticsNode().boundsInRoot
        val gap = retake.top - transcript.bottom

        assertTrue("Review actions left excessive dead space below the transcript: $gap px", gap in 0f..transcript.height)
    }

    @Test
    fun captureReviewFloatsResetAfterAnAppliedCommand() {
        var reset = false
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        cameraPhase = CameraPhase.REVIEWING,
                        review = SavedCapture("saved", "content://missing", null, CameraTelemetry()),
                        resetAvailable = true,
                    ),
                    onReset = { reset = true },
                )
            }
        }

        val resetButton = compose.onNodeWithTag(CaptureTestTags.RESET).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag(CaptureTestTags.REVIEW_CONTROLS).fetchSemanticsNode().boundsInRoot

        assertTrue("Reset should float across the review controls edge", resetButton.top < controls.top)
        assertTrue("Reset should overlap the review controls edge", resetButton.bottom > controls.top)
        compose.onNodeWithTag(CaptureTestTags.RESET).performClick()
        compose.runOnIdle { assertTrue(reset) }
    }

    @Test
    fun captureReviewKeepsThePreviewCompositionBound() {
        val state = mutableStateOf(readyState())
        var disposals = 0
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state.value,
                    preview = {
                        DisposableEffect(Unit) { onDispose { disposals++ } }
                        Box(Modifier.fillMaxSize().testTag("stable_preview"))
                    },
                )
            }
        }

        state.value = readyState(
            cameraPhase = CameraPhase.REVIEWING,
            review = SavedCapture("saved", "content://missing", null, CameraTelemetry()),
        )
        compose.runOnIdle { assertEquals(0, disposals) }
        state.value = readyState()
        compose.runOnIdle { assertEquals(0, disposals) }
    }

    @Test
    fun guidancePublishesInstructionAndOneTapCancel() {
        var cancelled = false
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.GUIDING,
                        activeGuidance = ActiveGuidance(
                            instruction = "Aim the phone slightly right",
                            target = VerificationTarget.FacePosition(0.4f..0.6f, 0.35f..0.65f),
                            startedAtMs = 1L,
                        ),
                    ),
                    onCancelCoaching = { cancelled = true },
                )
            }
        }

        compose.onNodeWithText("Aim the phone slightly right").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assert(hasClickAction()).performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun talkBackTraversesAdviceBeforeCameraChrome() {
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(exposureRecommendation()),
                    ).copy(comment = "make it darker"),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.ROOT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
        compose.onNodeWithTag(CaptureTestTags.RESPONSE_CARD)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, false))
        compose.onNodeWithText("Darken by 0.7 EV")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
        compose.onNodeWithText("Apply")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
        compose.onNodeWithText("Dismiss")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
        compose.onNodeWithTag(CaptureTestTags.COMMENT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 3f))
        compose.onNodeWithTag(CaptureTestTags.SHUTTER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 4f))
        compose.onNodeWithTag(CaptureTestTags.PREVIEW_CHROME)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 5f))
    }

    @Test
    fun guideOpensExplainsCoreControlsAndCloses() {
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(state = readyState())
            }
        }

        compose.onNodeWithContentDescription("Open Photo Helper guide")
            .assertIsDisplayed()
            .assert(hasClickAction())
            .performClick()
        compose.onNodeWithText("Photo Helper guide").assertIsDisplayed()
        compose.onNodeWithText("You can combine brightness, zoom, and color", substring = true).assertExists()
        compose.onNodeWithText("Voice is not always on", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Say “switch camera,” “selfie mode,” or “rear camera”", substring = true)
            .assertExists()
        compose.onNodeWithText("What you can ask")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Brightness").assertExists()
        compose.onNodeWithText("Focus").assertExists()
        compose.onNodeWithText("Zoom").assertExists()
        compose.onNodeWithText("Color").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Photo Helper guide").assertDoesNotExist()
    }

    @Test
    fun guideReportsActiveCameraCapabilitiesAndCollapsesTechnicalDetails() {
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState().copy(
                        capabilities = CameraCapabilities(
                            exposureCompensationRange = -6..6,
                            exposureCompensationStepEv = 1f / 3f,
                            zoomRatioRange = 1f..4f,
                            supportedWhiteBalancePresets = setOf(WhiteBalancePreset.AUTO, WhiteBalancePreset.WARMER),
                            supportsFocusMetering = false,
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithContentDescription("Open Photo Helper guide").performClick()
        compose.onNodeWithText("On this camera")
            .assertExists()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Brightness · Available").assertExists()
        compose.onNodeWithText("Tap to focus · Unavailable on this camera").assertExists()
        compose.onNodeWithText("Digital zoom · Available · 1.0×–4.0×").assertExists()
        compose.onNodeWithText("White balance · Available · Auto, Warmer").assertExists()
        compose.onNodeWithText("ISO and shutter speed · Not adjustable in this version; phone support varies").assertExists()
        compose.onNodeWithText("Exact color temperature (Kelvin) · Not adjustable; available native presets are used instead").assertExists()
        compose.onNodeWithText("ISO").assertDoesNotExist()

        compose.onNodeWithText("Camera technical details · Show")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Exposure compensation (EV)").performScrollTo().assertExists()
        compose.onNodeWithText("ISO").performScrollTo().assertExists()
        compose.onNodeWithText("Shutter speed").performScrollTo().assertExists()

        compose.onNodeWithText("Camera technical details · Hide")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("ISO").assertDoesNotExist()
    }

    @Test
    fun guideRemainsUsableAtLargeTextWhileCameraCapabilitiesLoad() {
        val portrait = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalConfiguration provides portrait,
                LocalDensity provides Density(systemDensity.density, fontScale = 2f),
            ) {
                PhotoHelperTheme {
                    CaptureScreen(state = readyState(cameraPhase = CameraPhase.STARTING))
                }
            }
        }

        compose.onNodeWithContentDescription("Open Photo Helper guide").assertIsDisplayed().performClick()
        compose.onNodeWithText("Checking camera controls…").assertExists()
        compose.onNodeWithText("Camera technical details · Show").performScrollTo().performClick()
        compose.onNodeWithText("Shutter speed").performScrollTo().assertExists()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Photo Helper guide").assertDoesNotExist()
    }

    @Test
    fun settingsMasksAndGatesVisualAiKey() {
        val key = mutableStateOf("")
        var tested = false
        var haptics = true
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(settingsOpen = true),
                    apiKeyInput = key.value,
                    onApiKeyChanged = { key.value = it },
                    onTestKey = { tested = true },
                    onHapticsChanged = { haptics = it },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.SETTINGS).assertExists()
        compose.onNodeWithText("AI interpretation enabled").assertIsNotEnabled()
        compose.onNodeWithText("Test, save & enable").assertIsNotEnabled()
        compose.onNodeWithText("Alibaba Cloud Model Studio (Bailian) API key")
            .performScrollTo()
            .performTextInput("disposable-key")
        compose.onNodeWithText("Test, save & enable").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Haptics").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(tested)
            assertFalse(haptics)
        }
    }

    @Test
    fun settingsLimitsApiKeyInputTo512Characters() {
        val key = mutableStateOf("")
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(settingsOpen = true),
                    apiKeyInput = key.value,
                    onApiKeyChanged = { key.value = it },
                )
            }
        }

        compose.onNodeWithText("Alibaba Cloud Model Studio (Bailian) API key")
            .performScrollTo()
            .performTextInput("x".repeat(513))

        compose.runOnIdle { assertEquals(512, key.value.length) }
    }

    @Test
    fun savedKeyDoesNotPretendBlankInputCanBeRetested() {
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = readyState(settingsOpen = true).copy(
                        settings = SettingsUiState(keyConfigured = true, keyStatus = "Key tested and saved"),
                    ),
                )
            }
        }

        compose.onNodeWithText("Test, save & enable").assertIsNotEnabled()
        compose.onNodeWithText("Clear key").assertIsEnabled()
    }

    @Test
    fun missingKeyWarningOpensSettingsAndClearsAfterSetup() {
        val state = mutableStateOf(readyState())
        var openedSettings = false
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state.value,
                    onSettingsOpen = { openedSettings = true },
                )
            }
        }

        val warning = "Set up your Qwen API key for Photo Helper to work as intended."
        compose.onNodeWithText(warning).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(openedSettings)
            state.value = state.value.copy(settings = SettingsUiState(keyConfigured = true))
        }
        compose.onNodeWithText(warning).assertDoesNotExist()
    }

    @Test
    fun landscapeKeepsCaptureButtonsInAVerticalRightRail() {
        val landscape = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides landscape) {
                PhotoHelperTheme {
                    CaptureScreen(state = readyState(coachingPhase = CoachingPhase.LISTENING))
                }
            }
        }

        compose.onNodeWithContentDescription("Finish voice comment")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Listening"))
            .assert(hasClickAction())
        compose.onNodeWithText("■").assertIsDisplayed()
        compose.onNodeWithContentDescription("Take photo").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(CaptureTestTags.PREVIEW).assertIsDisplayed()

        val root = compose.onNodeWithTag(CaptureTestTags.ROOT).fetchSemanticsNode().boundsInRoot
        val captureBar = compose.onNodeWithTag(CaptureTestTags.CAPTURE_BAR).fetchSemanticsNode().boundsInRoot
        val auto = compose.onNodeWithTag(CaptureTestTags.AUTO_ENHANCE).fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithTag(CaptureTestTags.SHUTTER).fetchSemanticsNode().boundsInRoot
        val mic = compose.onNodeWithTag(CaptureTestTags.MICROPHONE).fetchSemanticsNode().boundsInRoot
        val guide = compose.onNodeWithContentDescription("Open Photo Helper guide").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithContentDescription("Open settings").fetchSemanticsNode().boundsInRoot

        assertTrue("Capture rail is not on the right edge", captureBar.right == root.right)
        assertTrue("Capture rail is not vertical", captureBar.height > captureBar.width)
        assertTrue("Capture buttons are not ordered vertically", auto.center.y < shutter.center.y)
        assertTrue("Capture buttons are not ordered vertically", shutter.center.y < mic.center.y)
        assertTrue("Guide overlaps the capture rail", guide.right <= captureBar.left)
        assertTrue("Settings overlaps the capture rail", settings.right <= captureBar.left)
    }

    @Test
    fun listeningMicrophoneShowsStopSymbolAndReachableFinishAction() {
        val state = mutableStateOf(readyState(coachingPhase = CoachingPhase.LISTENING))
        var micTaps = 0
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state.value,
                    onMicrophone = {
                        micTaps++
                        state.value = state.value.copy(
                            coachingPhase = if (micTaps == 1) CoachingPhase.INTERPRETING else CoachingPhase.LISTENING,
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.MICROPHONE)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Listening"))
            .assert(hasClickAction())
        compose.onNodeWithText("■").assertIsDisplayed()
        compose.onNodeWithContentDescription("Finish voice comment").performClick()
        compose.runOnIdle { assertEquals(1, micTaps) }
    }

    @Test
    fun autoEnhanceButtonFlanksTheShutterOppositeTheMic() {
        var autoTaps = 0
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(state = readyState(), onAutoEnhance = { autoTaps++ })
            }
        }

        val auto = compose.onNodeWithTag(CaptureTestTags.AUTO_ENHANCE).fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithTag(CaptureTestTags.SHUTTER).fetchSemanticsNode().boundsInRoot
        val mic = compose.onNodeWithTag(CaptureTestTags.MICROPHONE).fetchSemanticsNode().boundsInRoot
        assertTrue(auto.center.x < shutter.center.x)
        assertTrue(shutter.center.x < mic.center.x)
        compose.onNodeWithContentDescription("Make this shot look nicer").performClick()
        compose.runOnIdle { assertEquals(1, autoTaps) }
    }

    @Test
    fun microphonePublishesErrorState() {
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(state = readyState(coachingPhase = CoachingPhase.TRANSIENT_ERROR))
            }
        }

        compose.onNodeWithContentDescription("Describe shot by voice")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Error"))
    }

    @Test
    fun aiFallbackIsInformationalRatherThanAFalseSuccess() {
        val message = "AI interpretation unavailable—using local coaching."
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(state = readyState().copy(transientMessage = message))
            }
        }

        compose.onNodeWithText("ⓘ $message").assertIsDisplayed()
        compose.onNodeWithText("✓ $message").assertDoesNotExist()
    }

    @Test
    fun tappingATransientMessageDismissesOnlyTheMessage() {
        val message = "Camera session changed—check the shot again."
        val decision = LocalDecision.Advisory("Keep framing", "This decision remains actionable.")
        val state = mutableStateOf(readyState(decision = decision).copy(transientMessage = message))
        var decisionDismissed = false
        compose.setContent {
            PhotoHelperTheme {
                CaptureScreen(
                    state = state.value,
                    onDismissDecision = { decisionDismissed = true },
                    onDismissTransientMessage = {
                        state.value = state.value.copy(transientMessage = null)
                    },
                )
            }
        }

        compose.onNodeWithText("✓ $message").performClick()

        compose.onNodeWithText("✓ $message").assertDoesNotExist()
        compose.onNodeWithText("Keep framing").assertIsDisplayed()
        compose.runOnIdle { assertFalse(decisionDismissed) }
    }

    @Test
    fun portraitKeepsCompactCaptureBarBelowTheTranscript() {
        val portrait = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides portrait) {
                PhotoHelperTheme { CaptureScreen(state = readyState().copy(comment = "focus on the cup")) }
            }
        }

        val root = compose.onNodeWithTag(CaptureTestTags.ROOT).fetchSemanticsNode().boundsInRoot
        val transcript = compose.onNodeWithTag(CaptureTestTags.COMMENT).fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithTag(CaptureTestTags.SHUTTER).fetchSemanticsNode().boundsInRoot
        val captureBar = compose.onNodeWithTag(CaptureTestTags.CAPTURE_BAR).fetchSemanticsNode().boundsInRoot
        val gap = shutter.top - transcript.bottom

        assertTrue("Shutter left excessive dead space below the transcript: $gap px", gap in 0f..transcript.height)
        assertTrue("Capture bar is too tall", captureBar.height <= root.height * 0.18f)
        assertTrue("Shutter overlaps the bottom edge", shutter.bottom < root.bottom)
    }

    @Test
    fun portraitLargeTextKeepsShutterVisibleWithCoachingContent() {
        val portrait = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalConfiguration provides portrait,
                LocalDensity provides Density(systemDensity.density, fontScale = 2f),
            ) {
                PhotoHelperTheme {
                    CaptureScreen(
                        state = readyState(
                            coachingPhase = CoachingPhase.RECOMMENDATION,
                            decision = LocalDecision.Recommend(exposureRecommendation()),
                            resetAvailable = true,
                        ),
                    )
                }
            }
        }

        val root = compose.onNodeWithTag(CaptureTestTags.ROOT).fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithTag(CaptureTestTags.SHUTTER).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Large text pushed the shutter below the screen", shutter.bottom < root.bottom)
    }

    @Test
    fun portraitLargeTextKeepsGuidanceCancelVisible() {
        val portrait = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalConfiguration provides portrait,
                LocalDensity provides Density(systemDensity.density, fontScale = 2f),
            ) {
                PhotoHelperTheme {
                    CaptureScreen(
                        state = readyState(
                            coachingPhase = CoachingPhase.GUIDING,
                            activeGuidance = ActiveGuidance(
                                instruction = "Keep the phone level",
                                target = VerificationTarget.Level(),
                                startedAtMs = 1_000,
                            ),
                        ),
                    )
                }
            }
        }

        val root = compose.onNodeWithTag(CaptureTestTags.ROOT).fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithText("Cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Large text pushed Cancel below the screen", cancel.bottom < root.bottom)
    }

    private fun readyState(
        cameraPermission: PermissionState = PermissionState.GRANTED,
        cameraPhase: CameraPhase = CameraPhase.READY,
        coachingPhase: CoachingPhase = CoachingPhase.IDLE,
        decision: LocalDecision? = null,
        review: SavedCapture? = null,
        settingsOpen: Boolean = false,
        activeGuidance: ActiveGuidance? = null,
        resetAvailable: Boolean = false,
    ) = CaptureUiState(
        onboardingStep = 2,
        cameraPermission = cameraPermission,
        cameraPhase = cameraPhase,
        coachingPhase = coachingPhase,
        decision = decision,
        review = review,
        settingsOpen = settingsOpen,
        activeGuidance = activeGuidance,
        resetAvailable = resetAvailable,
    )

    private fun exposureRecommendation() = Recommendation(
        complaintId = "too-bright",
        cameraSessionId = 0,
        headline = "The whole frame is overexposed",
        actionText = "Darken by 0.7 EV",
        consequence = "This should reduce clipping across the photo.",
        primaryLabel = "Apply",
        action = RecommendationAction.ApplySettings(
            adjustment = CameraAdjustment.ExposureCompensation(-2),
            target = VerificationTarget.Exposure(
                direction = -1,
                baselineLuma = 0.8f,
                baselineClipFraction = 0.2f,
            ),
        ),
        basis = RecommendationBasis.MEASURED_DIAGNOSIS,
    )
}
