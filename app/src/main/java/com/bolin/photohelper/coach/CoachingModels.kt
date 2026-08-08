package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.CameraAdjustment
import com.bolin.photohelper.capture.CameraCapabilities
import com.bolin.photohelper.capture.CameraTelemetry
import com.bolin.photohelper.capture.FaceObservation
import com.bolin.photohelper.capture.FrameObservation

enum class ObservationOrigin { LIVE, CAPTURE_REVIEW }
enum class RecommendationBasis { MEASURED_DIAGNOSIS, USER_PREFERENCE }
enum class VisualFamily { COLOR_CAST, FACE_SIZE_AMBIGUOUS }

data class VisualEligibility(
    val complaintId: String,
    val family: VisualFamily,
    val origin: ObservationOrigin,
    val observationId: Long,
)

enum class VisualIntent {
    WHITE_BALANCE_WARMER,
    WHITE_BALANCE_COOLER,
    FACE_OCCUPANCY_LOWER,
    CLOSE_PERSPECTIVE_ADVISORY,
}

enum class VisualClarificationReason {
    VISUAL_INSUFFICIENT,
    SUBJECT_UNCLEAR,
    SCENE_CONFOUND,
}

sealed interface VisualHint {
    data class Intent(val value: VisualIntent) : VisualHint
    data class Clarify(val reason: VisualClarificationReason) : VisualHint
}

data class CoachingInput(
    val complaintId: String,
    val complaint: String,
    val origin: ObservationOrigin,
    val cameraSessionId: Long,
    val observation: FrameObservation?,
    val lockedFace: FaceObservation?,
    val capabilities: CameraCapabilities,
    val telemetry: CameraTelemetry,
    val telemetryKnown: Boolean = true,
    val comparisonBaseline: FrameObservation? = null,
)

data class ClarificationChip(val label: String, val replacementComplaint: String)

sealed interface VerificationTarget {
    data class Exposure(
        val direction: Int,
        val baselineLuma: Float,
        val baselineClipFraction: Float,
        val baselineObservation: FrameObservation? = null,
    ) : VerificationTarget

    data class FaceOccupancy(val min: Float, val max: Float) : VerificationTarget
    data class FacePosition(val xRange: ClosedFloatingPointRange<Float>, val yRange: ClosedFloatingPointRange<Float>) : VerificationTarget
    data class StepBack(val maxFaceWidthFraction: Float) : VerificationTarget
    data class Level(val maxAbsoluteRollDegrees: Float = 1.5f) : VerificationTarget
    data class ColorBalance(
        val direction: Int,
        val baselineBlueBias: Float?,
        val baselineObservation: FrameObservation? = null,
    ) : VerificationTarget
}

sealed interface RecommendationAction {
    data class ApplySetting(
        val adjustment: CameraAdjustment,
        val target: VerificationTarget,
    ) : RecommendationAction

    data class GuidePosition(
        val instruction: String,
        val target: VerificationTarget,
        val requiresWalkingWarning: Boolean = false,
    ) : RecommendationAction

    data object TapToFocus : RecommendationAction
}

data class Recommendation(
    val complaintId: String,
    val cameraSessionId: Long,
    val headline: String,
    val actionText: String,
    val consequence: String,
    val primaryLabel: String?,
    val action: RecommendationAction,
    val basis: RecommendationBasis,
    val fromVisualHint: Boolean = false,
    val subjectTrackingId: Int? = null,
    val subjectFace: FaceObservation? = null,
    val origin: ObservationOrigin = ObservationOrigin.LIVE,
    val observationId: Long? = null,
    val observationTimestampMs: Long? = null,
    val capabilitiesSnapshot: CameraCapabilities? = null,
    val telemetrySnapshot: CameraTelemetry? = null,
    val createdAtMs: Long? = null,
    val visualFamily: VisualFamily? = null,
    val visualHint: VisualHint? = null,
)

sealed interface LocalDecision {
    data class Recommend(val recommendation: Recommendation) : LocalDecision
    data class Clarify(
        val question: String,
        val chips: List<ClarificationChip>,
        val visualEligibility: VisualEligibility? = null,
    ) : LocalDecision

    data class Advisory(
        val headline: String,
        val detail: String,
        val fromVisualHint: Boolean = false,
    ) : LocalDecision
}

sealed interface VerificationResult {
    data object Satisfied : VerificationResult
    data object Progress : VerificationResult
    data object Unchanged : VerificationResult
    data class Incomparable(val reason: String) : VerificationResult
}

data class CoachThresholds(
    val highlightClip: Float = 0.08f,
    val shadowClip: Float = 0.20f,
    val lowLuma: Float = 0.28f,
    val faceTooLarge: Float = 0.43f,
    val faceTooSmall: Float = 0.18f,
    val blueBiasWeak: Float = 0.03f,
    val blueBiasStrong: Float = 0.12f,
)
