package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.CameraAdjustment
import com.bolin.photohelper.capture.CameraCapabilities
import com.bolin.photohelper.capture.CameraTelemetry
import com.bolin.photohelper.capture.FaceObservation
import com.bolin.photohelper.capture.FrameObservation

enum class ObservationOrigin { LIVE, CAPTURE_REVIEW }
enum class RecommendationBasis { MEASURED_DIAGNOSIS, USER_PREFERENCE }
enum class VisualFamily { COLOR_CAST, FACE_SIZE_AMBIGUOUS, OBJECT_FOCUS }

enum class ControlIntent {
    EXPOSURE_BRIGHTER,
    EXPOSURE_DARKER,
    ZOOM_IN,
    ZOOM_OUT,
    WHITE_BALANCE_WARMER,
    WHITE_BALANCE_COOLER,
    WHITE_BALANCE_AUTO,
    FOCUS_POINT_REQUIRED,
    LEVEL_FRAME,
}

enum class ClarificationReason {
    AMBIGUOUS,
    NEGATED_DIRECTION,
    CONFLICTING_DIRECTIONS,
    MULTIPLE_COMPLAINTS,
    REGIONAL_REQUEST,
    BLUR_TYPE,
    ZOOM_OR_DISTANCE,
}

enum class UnsupportedReason { MANUAL_EXPOSURE, NOISE_REDUCTION }

sealed interface IntentClassification {
    data class Intent(val values: List<ControlIntent>) : IntentClassification {
        constructor(value: ControlIntent) : this(listOf(value))

        init {
            require(values.isNotEmpty() && values.distinct().size == values.size)
        }
    }
    data class Clarify(val reason: ClarificationReason) : IntentClassification
    data class Unsupported(val reason: UnsupportedReason) : IntentClassification
    data object Unknown : IntentClassification
}

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
    TARGET_NOT_FOUND,
    MULTIPLE_MATCHES,
}

sealed interface VisualHint {
    data class Intent(val value: VisualIntent) : VisualHint
    data class FocusPoint(val xFraction: Float, val yFraction: Float) : VisualHint {
        init {
            require(xFraction in 0f..1f && yFraction in 0f..1f)
        }
    }
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
    val relativeBaseline: CameraTelemetry? = null,
)

data class ClarificationChip(val label: String, val replacementComplaint: String)

sealed interface VerificationTarget {
    sealed interface Setting : VerificationTarget

    data class Exposure(
        val direction: Int,
        val baselineLuma: Float,
        val baselineClipFraction: Float,
        val baselineObservation: FrameObservation? = null,
    ) : Setting

    data class FaceOccupancy(val min: Float, val max: Float) : VerificationTarget
    data class FacePosition(val xRange: ClosedFloatingPointRange<Float>, val yRange: ClosedFloatingPointRange<Float>) : VerificationTarget
    data class StepBack(val maxFaceWidthFraction: Float) : VerificationTarget
    data class Level(val maxAbsoluteRollDegrees: Float = 1.5f) : VerificationTarget
    data class ColorBalance(
        val direction: Int,
        val baselineBlueBias: Float?,
        val baselineObservation: FrameObservation? = null,
    ) : Setting

    data class Zoom(
        val direction: Int,
        val baselineRatio: Float,
        val targetRatio: Float,
    ) : Setting
}

data class SettingChange(
    val adjustment: CameraAdjustment,
    val target: VerificationTarget.Setting,
)

sealed interface RecommendationAction {
    data class ApplySettings(val changes: List<SettingChange>) : RecommendationAction {
        constructor(adjustment: CameraAdjustment, target: VerificationTarget.Setting) :
            this(listOf(SettingChange(adjustment, target)))

        init {
            require(changes.isNotEmpty())
        }

        val adjustment: CameraAdjustment get() = changes.single().adjustment
        val target: VerificationTarget.Setting get() = changes.single().target
    }

    data class GuidePosition(
        val instruction: String,
        val target: VerificationTarget,
        val requiresWalkingWarning: Boolean = false,
    ) : RecommendationAction

    data object TapToFocus : RecommendationAction

    data class FocusAt(
        val xFraction: Float,
        val yFraction: Float,
    ) : RecommendationAction {
        init {
            require(xFraction in 0f..1f && yFraction in 0f..1f)
        }
    }
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
    val controlIntents: List<ControlIntent> = emptyList(),
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
