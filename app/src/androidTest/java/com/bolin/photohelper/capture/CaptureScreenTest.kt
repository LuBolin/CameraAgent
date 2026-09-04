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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.Recommendation
import com.bolin.photohelper.coach.RecommendationAction
import com.bolin.photohelper.coach.RecommendationBasis
import com.bolin.photohelper.coach.SettingChange
import com.bolin.photohelper.coach.VerificationTarget
import com.bolin.photohelper.ui.PhotoHelperTheme
import com.bolin.photohelper.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaptureScreenTest {
    @get:Rule
    val compose = createComposeRule()

    // ── Landing, permissions, recovery ─────────────────────────────

    @Test
    fun landingStartsTheCameraWithASingleTap() {
        var started = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = CaptureUiState(onboardingStep = 0),
                    actions = TestActions(onboardingContinue = { started++ }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.LANDING).assertIsDisplayed()
        compose.onNodeWithText("Tap to Start").assertIsDisplayed()
        // No wall of onboarding copy, and no key setup before the camera opens.
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithText("Alibaba Cloud Model Studio (Bailian) API key").assertDoesNotExist()
        compose.onNodeWithText("Tap to Start").performClick()
        compose.runOnIdle { assertEquals(1, started) }
    }

    @Test
    fun landingOffersTheGuideWithoutBlockingTheCamera() {
        compose.setContent {
            PhotoHelperTheme { TestCaptureScreen(state = CaptureUiState(onboardingStep = 0)) }
        }

        compose.onNodeWithText("How it works").performClick()
        compose.onNodeWithText("Two controls: the ring and a mic.").assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Two controls: the ring and a mic.").assertDoesNotExist()
    }

    @Test
    fun permissionWallStaysOutOfTheWayUntilTheUserHasAskedToStart() {
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = CaptureUiState(onboardingStep = 0, cameraPermission = PermissionState.DENIED),
                )
            }
        }

        // Asking for the camera is what the landing tap is for; the wall must not
        // cover the only thing there is to press.
        compose.onNodeWithText("Camera access needed").assertDoesNotExist()
        compose.onNodeWithText("Tap to Start").assertIsDisplayed()
    }

    @Test
    fun deniedCameraShowsRecoveryWithoutBlankPreview() {
        var openedSettings = false
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(cameraPermission = PermissionState.DENIED),
                    actions = TestActions(openAppSettings = { openedSettings = true }),
                    preview = { Box(Modifier.fillMaxSize().testTag("fake_preview")) },
                )
            }
        }

        compose.onNodeWithText("Camera access needed").assertIsDisplayed()
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
                TestCaptureScreen(
                    state = state.value,
                    actions = TestActions(
                        retryCamera = {
                            state.value = state.value.copy(cameraPhase = CameraPhase.STARTING)
                            bindAttempt.value++
                        },
                    ),
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

    // ── The Helper Orb ─────────────────────────────────────────────

    @Test
    fun recommendationKeepsThePreviewVisibleAndApplyOneTapAway() {
        var applied = false
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(exposureRecommendation()),
                    ),
                    actions = TestActions(applyRecommendation = { applied = true }),
                    preview = {
                        Box(Modifier.fillMaxSize().background(Color.DarkGray).testTag("fake_preview"))
                    },
                )
            }
        }

        compose.onNodeWithTag("fake_preview").assertIsDisplayed()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertTrue(applied) }
    }

    @Test
    fun orbTakesThePhoto() {
        var captured = false
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(),
                    actions = TestActions(shutter = { captured = true }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB)
            .assertIsDisplayed()
            .assert(hasClickAction())
            .performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(captured) }
    }

    @Test
    fun orbLongPressAutoEnhancesAndMicButtonTalks() {
        var micTaps = 0
        var autoTaps = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(),
                    actions = TestActions(microphone = { micTaps++ }, autoEnhance = { autoTaps++ }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, autoTaps) }

        compose.onNodeWithTag(CaptureTestTags.MICROPHONE).performClick()
        compose.runOnIdle { assertEquals(1, micTaps) }
    }

    @Test
    fun orbAnnouncesItsStateForTalkBack() {
        val phase = mutableStateOf(CoachingPhase.IDLE)
        compose.setContent {
            PhotoHelperTheme { TestCaptureScreen(state = readyState(coachingPhase = phase.value)) }
        }

        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Idle"))
        compose.onNodeWithContentDescription(ORB_IDLE_DESCRIPTION).assertExists()

        compose.runOnIdle { phase.value = CoachingPhase.LISTENING }
        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Listening"))

        compose.runOnIdle { phase.value = CoachingPhase.INTERPRETING }
        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Working"))

        compose.runOnIdle { phase.value = CoachingPhase.RECOMMENDATION }
        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Decided"))

        // A failure must not be signalled by colour alone.
        compose.runOnIdle { phase.value = CoachingPhase.TRANSIENT_ERROR }
        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Error"))
    }

    @Test
    fun listeningOrbFinishesTheVoiceCommentOnTap() {
        var micTaps = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(coachingPhase = CoachingPhase.LISTENING),
                    actions = TestActions(microphone = { micTaps++ }),
                )
            }
        }

        compose.onNodeWithContentDescription("Listening. Tap to finish.")
            .assert(hasClickAction())
            .performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, micTaps) }
    }

    @Test
    fun orbGoesInertWhileTheAgentIsThinking() {
        var shutterTaps = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(coachingPhase = CoachingPhase.INTERPRETING),
                    actions = TestActions(shutter = { shutterTaps++ }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, shutterTaps) }
    }

    @Test
    fun decidedOrbConfirmsTheRecommendation() {
        var applied = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(exposureRecommendation()),
                    ),
                    actions = TestActions(applyRecommendation = { applied++ }),
                )
            }
        }

        compose.onNodeWithContentDescription("Ready. Tap to confirm.").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test
    fun firstUseHintShowsOnceAndRetiresOnTheFirstOrbTouch() {
        val state = mutableStateOf(readyState().copy(showFirstUseHint = true))
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = state.value,
                    actions = TestActions(
                        firstUseHintSeen = { state.value = state.value.copy(showFirstUseHint = false) },
                    ),
                )
            }
        }

        compose.onNodeWithText(FIRST_USE_HINT).assertIsDisplayed()
        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(FIRST_USE_HINT).assertDoesNotExist()
    }

    // ── Camera chrome ──────────────────────────────────────────────

    @Test
    fun chromeShowsOnlyFourPersistentControls() {
        compose.setContent {
            PhotoHelperTheme { TestCaptureScreen(state = readyState()) }
        }

        compose.onNodeWithTag(CaptureTestTags.MICROPHONE).assertIsDisplayed()
        compose.onNodeWithContentDescription("Switch to selfie camera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB).assertIsDisplayed()

        // Flash removed from persistent controls. Accessible only via voice.
        compose.onNodeWithContentDescription("Flash: Flash off. Tap to cycle.").assertDoesNotExist()
        compose.onNodeWithText("LIVE").assertDoesNotExist()
    }

    @Test
    fun cameraFlipSwitchesToAccessibleSelfieState() {
        val frontCamera = mutableStateOf(false)
        var flips = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(),
                    isFrontCamera = frontCamera.value,
                    canFlipCamera = true,
                    actions = TestActions(
                        flipCamera = {
                            flips++
                            frontCamera.value = true
                        },
                    ),
                )
            }
        }

        compose.onNodeWithContentDescription("Switch to selfie camera")
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithContentDescription("Switch to rear camera").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, flips) }
    }

    @Test
    fun cameraFlipIsAbsentWhenNoSecondLensExists() {
        compose.setContent {
            PhotoHelperTheme { TestCaptureScreen(state = readyState(), canFlipCamera = false) }
        }

        compose.onNodeWithContentDescription("Switch to selfie camera").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    // ── Decisions ──────────────────────────────────────────────────

    @Test
    fun executableRecommendationAlwaysGetsOneTapApply() {
        var applied = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(exposureRecommendation().copy(primaryLabel = null)),
                    ),
                    actions = TestActions(applyRecommendation = { applied++ }),
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
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(recommendation),
                    ),
                    actions = TestActions(applyRecommendation = { applied++ }),
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
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(guidanceRecommendation()),
                    ),
                    actions = TestActions(
                        applyRecommendation = { applied++ },
                        startGuidance = { guided++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Start").assertIsDisplayed().performClick()
        compose.onNodeWithText("Apply").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, applied)
            assertEquals(1, guided)
        }
    }

    @Test
    fun advisoryKeepsResetOneTapAway() {
        var reset = false
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        decision = LocalDecision.Advisory("Use Reset", "Restore the camera baseline."),
                        resetAvailable = true,
                    ),
                    actions = TestActions(reset = { reset = true }),
                )
            }
        }

        compose.onNodeWithText("Reset").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(reset) }
    }

    @Test
    fun clarificationChipsStayUsableWhileTheModelIsLooking() {
        var selected: ClarificationChip? = null
        var cancelled = false
        val chip = ClarificationChip("Size", "face too big")
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.REQUESTING_VISUAL_INTERPRETATION,
                        decision = LocalDecision.Clarify("Which part feels wrong?", listOf(chip)),
                    ),
                    actions = TestActions(
                        clarificationSelected = { selected = it },
                        cancelCoaching = { cancelled = true },
                    ),
                )
            }
        }

        // Status lives in the mirror bar now, not in a card of its own.
        compose.onNodeWithText("Looking at the scene…").assertIsDisplayed()
        compose.onNodeWithText("Which part feels wrong?").assertIsDisplayed()
        compose.onNodeWithText("Size").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(chip, selected) }
        assertFalse(cancelled)
    }

    @Test
    fun transientMessagesSurfaceInTheMirrorBarWithoutTouchingTheDecision() {
        val message = "AI interpretation unavailable. Using local coaching."
        val state = mutableStateOf(
            readyState(decision = LocalDecision.Advisory("Keep framing", "Still actionable."))
                .copy(transientMessage = message),
        )
        var decisionDismissed = false
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = state.value,
                    actions = TestActions(dismissDecision = { decisionDismissed = true }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.MIRROR_BAR).assertIsDisplayed()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("Keep framing").assertIsDisplayed()

        compose.runOnIdle { state.value = state.value.copy(transientMessage = null) }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithText("Keep framing").assertIsDisplayed()
        compose.runOnIdle { assertFalse(decisionDismissed) }
    }

    // ── Focus ──────────────────────────────────────────────────────

    @Test
    fun focusRecommendationShowsOneTappableMarkerInsteadOfARegularActionButton() {
        var focused = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(tapToFocusRecommendation()),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                    actions = TestActions(focusTarget = { _, _ -> focused++ }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET)
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
            .performClick()
        compose.onNodeWithText("Apply").assertDoesNotExist()
        compose.onNodeWithText("Start").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, focused) }
    }

    @Test
    fun focusRecommendationShowsAUsableTargetWithoutAnyFace() {
        var focusPoint: Pair<Float, Float>? = null
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(tapToFocusRecommendation()),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                    actions = TestActions(focusTarget = { x, y -> focusPoint = x to y }),
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
    fun modelFocusShowsItsPointWithoutAConfirmationCard() {
        var focusPoint: Pair<Float, Float>? = null
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(modelFocusRecommendation()),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                    actions = TestActions(focusTarget = { x, y -> focusPoint = x to y }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET).assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("Choose manually").assertDoesNotExist()
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
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        decision = LocalDecision.Recommend(modelFocusRecommendation()),
                    ).copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
                    isFrontCamera = true,
                    actions = TestActions(focusTarget = { x, y -> focusPoint = x to y }),
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
    fun readyPreviewTapShowsTheFocusMarkerAtThatPoint() {
        var focusPoint: Pair<Float, Float>? = null
        val state = mutableStateOf(
            readyState().copy(capabilities = CameraCapabilities(supportsFocusMetering = true)),
        )
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = state.value,
                    actions = TestActions(
                        focusTarget = { x, y ->
                            focusPoint = x to y
                            state.value = state.value.copy(
                                coachingPhase = CoachingPhase.APPLYING,
                                focusIndicator = FocusPoint(x, y),
                            )
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.FOCUS_AREA).performTouchInput {
            click(percentOffset(.25f, .25f))
        }
        compose.onNodeWithTag(CaptureTestTags.FOCUS_TARGET).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(.25f, focusPoint?.first ?: -1f, .01f)
            assertEquals(.25f, focusPoint?.second ?: -1f, .01f)
        }
    }

    // ── Countdown and guidance ─────────────────────────────────────

    @Test
    fun voiceCountdownIsVisibleAndCancellable() {
        var cancelled = 0
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState().copy(countdownSecondsRemaining = 3),
                    actions = TestActions(cancelCoaching = { cancelled++ }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.COUNTDOWN).assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test
    fun guidancePublishesInstructionAndOneTapCancel() {
        var cancelled = false
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.GUIDING,
                        activeGuidance = ActiveGuidance(
                            instruction = "Aim the phone slightly right",
                            target = VerificationTarget.FacePosition(0.4f..0.6f, 0.35f..0.65f),
                            startedAtMs = 1L,
                        ),
                    ),
                    actions = TestActions(cancelCoaching = { cancelled = true }),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.MIRROR_BAR).assertIsDisplayed()
        compose.onNodeWithText("Aim the phone slightly right").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assert(hasClickAction()).performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    // ── Review ─────────────────────────────────────────────────────

    @Test
    fun captureReviewUsesRetakeWordingAndSavedOriginalMessage() {
        var applied = false
        var retaken = false
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        cameraPhase = CameraPhase.REVIEWING,
                        coachingPhase = CoachingPhase.RECOMMENDATION,
                        review = savedCapture(),
                        decision = LocalDecision.Recommend(exposureRecommendation()),
                    ),
                    actions = TestActions(
                        applyRecommendation = { applied = true },
                        retake = { retaken = true },
                    ),
                )
            }
        }

        compose.onNodeWithText("Original remains saved").assertIsDisplayed()
        compose.onNodeWithText("Apply for retake").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Retake").assertIsDisplayed().performClick()
        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB).assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(applied)
            assertTrue(retaken)
        }
    }

    @Test
    fun captureReviewDisablesNavigationWhileApplying() {
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        cameraPhase = CameraPhase.REVIEWING,
                        coachingPhase = CoachingPhase.APPLYING,
                        review = savedCapture(),
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
                TestCaptureScreen(
                    state = readyState(cameraPhase = CameraPhase.REVIEWING, review = savedCapture())
                        .copy(comment = "make the retake cooler"),
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
                TestCaptureScreen(
                    state = readyState(
                        cameraPhase = CameraPhase.REVIEWING,
                        review = savedCapture(),
                        resetAvailable = true,
                    ),
                    actions = TestActions(reset = { reset = true }),
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
                TestCaptureScreen(
                    state = state.value,
                    preview = {
                        DisposableEffect(Unit) { onDispose { disposals++ } }
                        Box(Modifier.fillMaxSize().testTag("stable_preview"))
                    },
                )
            }
        }

        state.value = readyState(cameraPhase = CameraPhase.REVIEWING, review = savedCapture())
        compose.runOnIdle { assertEquals(0, disposals) }
        state.value = readyState()
        compose.runOnIdle { assertEquals(0, disposals) }
    }

    @Test
    fun modelDerivedAdvisoryKeepsProviderProvenance() {
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        cameraPhase = CameraPhase.REVIEWING,
                        review = savedCapture(),
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
            .performScrollTo()
            .assertIsDisplayed()
    }

    // ── Guide and settings ─────────────────────────────────────────

    @Test
    fun guideExplainsTheControlsAndCloses() {
        compose.setContent {
            PhotoHelperTheme { TestCaptureScreen(state = readyState(settingsOpen = true)) }
        }

        compose.onNodeWithText("How it works").performScrollTo().performClick()
        compose.onNodeWithText("Tap the ring").assertIsDisplayed()
        compose.onNodeWithText("Tap the mic").assertIsDisplayed()
        compose.onNodeWithText("Hold the ring").assertIsDisplayed()
        compose.onNodeWithText("Watch the colour").assertIsDisplayed()
        compose.onNodeWithText("Double tap the ring").assertDoesNotExist()
    }

    @Test
    fun settingsGroupsControlsAndHidesTheKeyBehindAdvanced() {
        compose.setContent {
            PhotoHelperTheme { TestCaptureScreen(state = readyState(settingsOpen = true)) }
        }

        compose.onNodeWithTag(CaptureTestTags.SETTINGS).assertExists()
        compose.onNodeWithText("Interaction").assertIsDisplayed()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Style").performScrollTo().assertExists()

        // Provider plumbing stays collapsed until asked for.
        compose.onNodeWithText("Alibaba Cloud Model Studio (Bailian) API key").assertDoesNotExist()
        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        compose.onNodeWithText("Alibaba Cloud Model Studio (Bailian) API key").performScrollTo().assertExists()
    }

    @Test
    fun settingsMasksAndGatesVisualAiKey() {
        val key = mutableStateOf("")
        var tested = false
        var haptics = true
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(settingsOpen = true),
                    apiKeyInput = key.value,
                    actions = TestActions(
                        apiKeyChanged = { key.value = it },
                        testKey = { tested = true },
                        hapticsChanged = { haptics = it },
                    ),
                )
            }
        }

        compose.onNodeWithText("Vibration").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        compose.onNodeWithText("AI interpretation").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Test, save & enable").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Alibaba Cloud Model Studio (Bailian) API key")
            .performScrollTo()
            .performTextInput("disposable-key")
        compose.onNodeWithText("Test, save & enable").performScrollTo().assertIsEnabled().performClick()
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
                TestCaptureScreen(
                    state = readyState(settingsOpen = true),
                    apiKeyInput = key.value,
                    actions = TestActions(apiKeyChanged = { key.value = it }),
                )
            }
        }

        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        compose.onNodeWithText("Alibaba Cloud Model Studio (Bailian) API key")
            .performScrollTo()
            .performTextInput("x".repeat(513))

        compose.runOnIdle { assertEquals(512, key.value.length) }
    }

    @Test
    fun savedKeyDoesNotPretendBlankInputCanBeRetested() {
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(settingsOpen = true).copy(
                        settings = SettingsUiState(keyConfigured = true, keyStatus = "Key tested and saved"),
                    ),
                )
            }
        }

        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        compose.onNodeWithText("Test, save & enable").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Clear key").performScrollTo().assertIsEnabled()
    }

    @Test
    fun styleProfileIsOptionalAndReportsWhatTheUserTyped() {
        var profile = ""
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(settingsOpen = true),
                    actions = TestActions(styleProfileChanged = { profile = it }),
                )
            }
        }

        compose.onNodeWithText("Describe your photo style (optional)")
            .performScrollTo()
            .performTextInput("moody and cinematic")
        compose.runOnIdle { assertEquals("moody and cinematic", profile) }
    }

    @Test
    fun appearanceOffersAnExplicitLightAndDarkChoice() {
        var chosen: ThemeMode? = null
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(settingsOpen = true),
                    actions = TestActions(themeModeChanged = { chosen = it }),
                )
            }
        }

        compose.onNodeWithText("Match my phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ThemeMode.DARK, chosen) }
    }

    // ── Layout and traversal ───────────────────────────────────────

    @Test
    fun landscapeKeepsControlsInAVerticalRightRail() {
        val landscape = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides landscape) {
                PhotoHelperTheme { TestCaptureScreen(state = readyState()) }
            }
        }

        val root = compose.onNodeWithTag(CaptureTestTags.ROOT).fetchSemanticsNode().boundsInRoot
        val strip = compose.onNodeWithTag(CaptureTestTags.CONTROL_STRIP).fetchSemanticsNode().boundsInRoot
        val mic = compose.onNodeWithTag(CaptureTestTags.MICROPHONE).fetchSemanticsNode().boundsInRoot
        val orb = compose.onNodeWithTag(CaptureTestTags.HELPER_ORB).fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot

        assertTrue("Control strip is not on the right edge", strip.right == root.right)
        assertTrue("Control strip is not vertical", strip.height > strip.width)
        assertTrue("Controls are not ordered vertically", mic.center.y < orb.center.y)
        assertTrue("Controls are not ordered vertically", orb.center.y < settings.center.y)
    }

    @Test
    fun portraitKeepsTheOrbClearOfTheBottomEdge() {
        val portrait = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides portrait) {
                PhotoHelperTheme {
                    TestCaptureScreen(state = readyState().copy(showFirstUseHint = true))
                }
            }
        }

        val root = compose.onNodeWithTag(CaptureTestTags.ROOT).fetchSemanticsNode().boundsInRoot
        val orb = compose.onNodeWithTag(CaptureTestTags.HELPER_ORB).fetchSemanticsNode().boundsInRoot
        val mirror = compose.onNodeWithTag(CaptureTestTags.MIRROR_BAR).fetchSemanticsNode().boundsInRoot

        assertTrue("Orb overlaps the bottom edge", orb.bottom < root.bottom)
        assertTrue("Mirror bar should sit above the Orb", mirror.bottom <= orb.top)
    }

    @Test
    fun portraitLargeTextKeepsTheOrbVisibleWithCoachingContent() {
        val portrait = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalConfiguration provides portrait,
                LocalDensity provides Density(systemDensity.density, fontScale = 2f),
            ) {
                PhotoHelperTheme {
                    TestCaptureScreen(
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
        val orb = compose.onNodeWithTag(CaptureTestTags.HELPER_ORB).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Large text pushed the Orb below the screen", orb.bottom < root.bottom)
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
                    TestCaptureScreen(
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

    @Test
    fun talkBackTraversesAdviceBeforeCameraChrome() {
        compose.setContent {
            PhotoHelperTheme {
                TestCaptureScreen(
                    state = readyState(
                        coachingPhase = CoachingPhase.GUIDING,
                        activeGuidance = ActiveGuidance(
                            instruction = "Aim the phone slightly right",
                            target = VerificationTarget.FacePosition(0.4f..0.6f, 0.35f..0.65f),
                            startedAtMs = 1L,
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(CaptureTestTags.ROOT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
        compose.onNodeWithTag(CaptureTestTags.RESPONSE_CARD)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
        compose.onNodeWithTag(CaptureTestTags.MIRROR_BAR)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 3f))
        compose.onNodeWithTag(CaptureTestTags.HELPER_ORB)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 4f))
        compose.onNodeWithTag(CaptureTestTags.PREVIEW_CHROME)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
    }

    // ── Fixtures ───────────────────────────────────────────────────

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

    private fun savedCapture() = SavedCapture(
        id = "saved-1",
        uri = "content://photo-helper/not-present",
        observation = null,
        telemetry = CameraTelemetry(),
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

    private fun guidanceRecommendation() = Recommendation(
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

    private fun tapToFocusRecommendation() = Recommendation(
        complaintId = "focus-any-subject",
        cameraSessionId = 0,
        headline = "Choose what should be sharp",
        actionText = "Tap the subject in the preview",
        consequence = "The camera will try to lock focus at that point.",
        primaryLabel = null,
        action = RecommendationAction.TapToFocus,
        basis = RecommendationBasis.USER_PREFERENCE,
    )

    private fun modelFocusRecommendation() = Recommendation(
        complaintId = "focus-watch",
        cameraSessionId = 0,
        headline = "Subject located",
        actionText = "Tap the marked point to focus",
        consequence = "The camera will focus at the marked point.",
        primaryLabel = null,
        action = RecommendationAction.FocusAt(
            xFraction = 2.5f / 6f,
            yFraction = 4.5f / 8f,
        ),
        basis = RecommendationBasis.USER_PREFERENCE,
        fromVisualHint = true,
    )
}
