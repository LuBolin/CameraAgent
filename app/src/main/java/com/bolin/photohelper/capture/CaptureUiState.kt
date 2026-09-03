package com.bolin.photohelper.capture

import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.Recommendation
import com.bolin.photohelper.coach.VerificationTarget
import com.bolin.photohelper.ui.ThemeMode
import com.bolin.photohelper.visual.VisualProvider

enum class CoachingPhase {
    IDLE,
    LISTENING,
    INTERPRETING,
    REQUESTING_VISUAL_INTERPRETATION,
    RECOMMENDATION,
    APPLYING,
    GUIDING,
    VERIFYING,
    TRANSIENT_ERROR,
}

enum class VisibleCoachingState {
    IDLE,
    WORKING,
    ACTION;

    companion object {
        fun from(phase: CoachingPhase): VisibleCoachingState = when (phase) {
            CoachingPhase.IDLE -> IDLE
            CoachingPhase.LISTENING,
            CoachingPhase.INTERPRETING,
            CoachingPhase.REQUESTING_VISUAL_INTERPRETATION,
            CoachingPhase.APPLYING,
            CoachingPhase.VERIFYING -> WORKING
            CoachingPhase.RECOMMENDATION,
            CoachingPhase.GUIDING,
            CoachingPhase.TRANSIENT_ERROR -> ACTION
        }
    }
}

/**
 * How far along the agent is, 0f (uncertain) to 1f (decided). The Helper Orb samples
 * the Jarvis gradient at this point, so a complaint travelling through the coaching
 * phases reads as one continuous coral to sage sweep rather than nine labelled steps.
 */
fun CoachingPhase.confidence(): Float = when (this) {
    CoachingPhase.IDLE -> 0f
    CoachingPhase.TRANSIENT_ERROR -> 0.1f
    CoachingPhase.LISTENING -> 0.2f
    CoachingPhase.INTERPRETING -> 0.4f
    CoachingPhase.REQUESTING_VISUAL_INTERPRETATION -> 0.5f
    CoachingPhase.VERIFYING -> 0.6f
    CoachingPhase.RECOMMENDATION -> 0.8f
    CoachingPhase.APPLYING -> 0.9f
    CoachingPhase.GUIDING -> 1f
}

enum class PermissionState { NOT_REQUESTED, GRANTED, DENIED }

data class SettingsUiState(
    val spokenGuidance: Boolean = true,
    val haptics: Boolean = true,
    val technicalDetail: Boolean = false,
    val visualAiEnabled: Boolean = false,
    val keyConfigured: Boolean = false,
    val keyStatus: String = "No key saved",
    val testingKey: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Optional free text describing the look the user wants; passed to the model as context. */
    val styleProfile: String = "",
    val visualProvider: VisualProvider = VisualProvider.QWEN,
    val autoCaptureEnabled: Boolean = true,
)

const val MAX_STYLE_PROFILE_CHARACTERS = 400

data class ActiveGuidance(
    val instruction: String,
    val target: VerificationTarget,
    val startedAtMs: Long,
    val subjectTrackingId: Int? = null,
    val subjectFace: FaceObservation? = null,
)

data class FocusPoint(val xFraction: Float, val yFraction: Float) {
    init {
        require(xFraction in 0f..1f && yFraction in 0f..1f)
    }
}

data class CaptureUiState(
    val onboardingStep: Int = 0,
    val cameraPermission: PermissionState = PermissionState.NOT_REQUESTED,
    val microphonePermission: PermissionState = PermissionState.NOT_REQUESTED,
    val cameraPhase: CameraPhase = CameraPhase.STARTING,
    val coachingPhase: CoachingPhase = CoachingPhase.IDLE,
    val comment: String = "",
    val decision: LocalDecision? = null,
    val review: SavedCapture? = null,
    val observation: FrameObservation? = null,
    val capabilities: CameraCapabilities = CameraCapabilities(),
    val flashMode: FlashMode = FlashMode.OFF,
    val focusIndicator: FocusPoint? = null,
    val activeGuidance: ActiveGuidance? = null,
    val resetAvailable: Boolean = false,
    val retakeSettingsActive: Boolean = false,
    val transientMessage: String? = null,
    val countdownSecondsRemaining: Int? = null,
    val settingsOpen: Boolean = false,
    val settings: SettingsUiState = SettingsUiState(),
    /** Shown in the mirror bar the first time the camera opens, then never again. */
    val showFirstUseHint: Boolean = false,
    /** Incremented each time auto-capture fires; drives the Orb sage flash animation. */
    val autoCaptureFlashKey: Int = 0,
) {
    val visibleCoachingState: VisibleCoachingState
        get() = VisibleCoachingState.from(coachingPhase)

    val recommendation: Recommendation?
        get() = (decision as? LocalDecision.Recommend)?.recommendation

    val shutterEnabled: Boolean
        get() = cameraPermission == PermissionState.GRANTED &&
            cameraPhase == CameraPhase.READY &&
            coachingPhase != CoachingPhase.APPLYING &&
            countdownSecondsRemaining == null &&
            review == null
}
