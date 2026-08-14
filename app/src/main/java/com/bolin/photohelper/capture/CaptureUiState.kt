package com.bolin.photohelper.capture

import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.Recommendation
import com.bolin.photohelper.coach.VerificationTarget

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

enum class PermissionState { NOT_REQUESTED, GRANTED, DENIED }

data class SettingsUiState(
    val spokenGuidance: Boolean = true,
    val haptics: Boolean = true,
    val technicalDetail: Boolean = false,
    val visualAiEnabled: Boolean = false,
    val keyConfigured: Boolean = false,
    val keyStatus: String = "No key saved",
    val testingKey: Boolean = false,
)

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
) {
    val recommendation: Recommendation?
        get() = (decision as? LocalDecision.Recommend)?.recommendation

    val shutterEnabled: Boolean
        get() = cameraPermission == PermissionState.GRANTED &&
            cameraPhase == CameraPhase.READY &&
            coachingPhase != CoachingPhase.APPLYING &&
            countdownSecondsRemaining == null &&
            review == null
}
