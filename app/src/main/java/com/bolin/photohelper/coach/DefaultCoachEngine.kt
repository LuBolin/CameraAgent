package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.CameraAdjustment
import com.bolin.photohelper.capture.FaceObservation
import com.bolin.photohelper.capture.FrameObservation
import com.bolin.photohelper.capture.exposureInvariantSceneDifference
import com.bolin.photohelper.capture.WhiteBalancePreset
import kotlin.math.abs
import kotlin.math.roundToInt

private val COMPLAINT_CLAUSE_SEPARATOR =
    Regex("\\s*(?:,|;|[.!?]+(?=\\s+\\S)|\\b(?:and|plus|also|while|but|then)\\b)\\s*")

class DefaultCoachEngine(
    private val thresholds: CoachThresholds = CoachThresholds(),
) : CoachEngine {
    override fun classifyComplaint(complaint: String): IntentClassification =
        com.bolin.photohelper.coach.classifyComplaint(complaint)

    override fun evaluateLocal(input: CoachingInput): LocalDecision {
        val text = input.complaint.trim().lowercase()
        if (text.isBlank()) return clarifyCurrentShot()
        if (text.length > 300) return LocalDecision.Advisory("Comment is too long", "Keep it under 300 characters and describe one problem.")
        if (text == "reset" || text.startsWith("undo")) {
            return LocalDecision.Advisory("Use Reset", "Reset restores the first coached camera setting from this camera session.")
        }
        when (val classification = classifyComplaint(text)) {
            is IntentClassification.Intent -> return planIntents(input, classification.values)
            is IntentClassification.Clarify -> return clarify(classification.reason, text)
            is IntentClassification.Unsupported -> return unsupported(classification.reason)
            IntentClassification.Unknown -> Unit
        }
        if (COMPLAINT_CLAUSE_SEPARATOR.containsMatchIn(text)) {
            return clarify(ClarificationReason.MULTIPLE_COMPLAINTS, text)
        }

        return when {
            text == "person-specific exposure" -> LocalDecision.Advisory(
                "Person-only exposure is unavailable",
                "Photo Helper can change exposure only across the whole photo. Change the lighting or reframe instead.",
            )
            text == "background-specific exposure" -> LocalDecision.Advisory(
                "Background-only exposure is unavailable",
                "Photo Helper can change exposure only across the whole photo. Change the lighting or reframe instead.",
            )
            text == "regional color adjustment" -> LocalDecision.Advisory(
                "Area-only color adjustment is unavailable",
                "Photo Helper can change white balance only across the whole photo. Change the lighting or reframe instead.",
            )
            text == "subject fills too much frame" -> LocalDecision.Advisory(
                "Reframe the subject manually",
                "Move back, zoom out, or aim differently. Generic subject tracking is not available in this version.",
            )
            text == "body is cropped" -> LocalDecision.Advisory(
                "Reframe the full body",
                "Step back or aim to include the missing area. Full-body verification is not available in this version.",
            )
            text == "camera angle makes them look shorter" -> LocalDecision.Advisory(
                "Use a more level viewpoint",
                "Move the phone closer to the subject’s mid-height and keep it level; this version cannot verify body proportions.",
            )
            text == "warmer" -> colorAdjustment(input, WhiteBalancePreset.WARMER, fromVisual = false)
            text == "cooler" -> colorAdjustment(input, WhiteBalancePreset.COOLER, fromVisual = false)
            text == "auto" -> colorAdjustment(input, WhiteBalancePreset.AUTO, fromVisual = false)
            isRegionalExposureComplaint(text) ->
                LocalDecision.Clarify(
                    "Which area do you mean? A whole-photo change affects both person and background.",
                    listOf(
                        ClarificationChip("Whole photo", if (requestsBrighterExposure(text)) "whole photo is too dark" else "whole photo is too bright"),
                        ClarificationChip("Person/face", "person-specific exposure"),
                        ClarificationChip("Background", "background-specific exposure"),
                    ),
                )
            text.containsAny("too bright", "overexposed", "washed out", "highlights gone") -> exposure(input, darker = true)
            text.containsAny("too dark", "too dim", "underexposed", "shadows gone") -> exposure(input, darker = false)
            text.containsAny("face too big", "face looks too big", "face is too big", "too close") -> faceSizeAmbiguity(input)
            text.containsAny("takes up too much frame", "face occupies too much", "make the face smaller") -> faceOccupancy(input, smaller = true)
            text.containsAny("face too small", "make the face bigger") -> faceOccupancy(input, smaller = false)
            text.containsAny("features look distorted", "perspective distortion") -> perspectiveAdvice()
            text.containsAny("too blue", "looks blue", "looks cool", "too cool", "cold") -> color(input, warmer = true)
            text.containsAny("too yellow", "looks yellow", "too warm", "looks warm") -> color(input, warmer = false)
            text.containsAny("crooked", "not straight", "level the phone") -> level(input)
            text.containsAny("subject too high", "person too high", "face too high") -> position(input, vertical = -1)
            text.containsAny("subject too low", "person too low", "face too low") -> position(input, vertical = 1)
            text.containsAny("subject too far left", "person too far left") -> position(input, horizontal = -1)
            text.containsAny("subject too far right", "person too far right") -> position(input, horizontal = 1)
            text == "freeze movement" -> LocalDecision.Advisory(
                "Reduce movement for this shot",
                "Brace the phone and ask the subject to pause. This camera has no tested one-tap shutter-speed control yet.",
            )
            text == "focus missed" -> focus(input)
            text.containsAny("blurry", "blurred", "out of focus") -> LocalDecision.Clarify(
                "Is movement blurred, or did focus miss?",
                listOf(
                    ClarificationChip("Freeze movement", "freeze movement"),
                    ClarificationChip("Focus missed", "focus missed"),
                ),
            )
            text.containsAny("weird", "wrong", "bad") -> LocalDecision.Clarify(
                "Is the problem their size in the frame, the angle, or the color?",
                listOf(
                    ClarificationChip("Size", "face too big"),
                    ClarificationChip("Angle", "crooked"),
                    ClarificationChip("Color", "looks blue"),
                ),
            )
            text.containsAny("person looks short", "looks shorter") -> LocalDecision.Clarify(
                "Do you mean their body is cropped, or the angle makes them look shorter?",
                listOf(
                    ClarificationChip("Body is cropped", "body is cropped"),
                    ClarificationChip("Camera angle", "camera angle makes them look shorter"),
                ),
            )
            else -> clarifyCurrentShot()
        }
    }

    override fun planIntent(input: CoachingInput, intent: ControlIntent): LocalDecision = when (intent) {
        ControlIntent.EXPOSURE_BRIGHTER -> exposure(input, darker = false)
        ControlIntent.EXPOSURE_DARKER -> exposure(input, darker = true)
        ControlIntent.ZOOM_IN -> zoom(input, inward = true)
        ControlIntent.ZOOM_OUT -> zoom(input, inward = false)
        ControlIntent.WHITE_BALANCE_WARMER -> colorAdjustment(input, WhiteBalancePreset.WARMER, fromVisual = false)
        ControlIntent.WHITE_BALANCE_COOLER -> colorAdjustment(input, WhiteBalancePreset.COOLER, fromVisual = false)
        ControlIntent.WHITE_BALANCE_AUTO -> colorAdjustment(input, WhiteBalancePreset.AUTO, fromVisual = false)
        ControlIntent.FOCUS_POINT_REQUIRED -> focus(input)
        ControlIntent.LEVEL_FRAME -> level(input)
    }

    override fun planIntents(input: CoachingInput, intents: List<ControlIntent>): LocalDecision {
        if (intents.isEmpty() || intents.distinct().size != intents.size) return clarifyCurrentShot()
        if (intents.size == 1) return planIntent(input, intents.single())
        val axes = intents.mapNotNull(::directSettingAxis)
        if (axes.size != intents.size || axes.distinct().size != axes.size) {
            return clarify(ClarificationReason.MULTIPLE_COMPLAINTS, input.complaint.lowercase())
        }

        val recommendations = intents.map { planIntent(input, it) }
            .mapNotNull { (it as? LocalDecision.Recommend)?.recommendation }
        val changes = recommendations.flatMap { recommendation ->
            (recommendation.action as? RecommendationAction.ApplySettings)?.changes.orEmpty()
        }
        if (recommendations.size != intents.size || changes.size != intents.size) {
            return LocalDecision.Advisory(
                "Not all requested changes are available",
                "No camera settings were changed. Ask for each unavailable or interactive change separately.",
            )
        }

        val count = changes.size
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = "$count camera changes ready",
                actionText = recommendations.joinToString("\n", transform = ::compoundChangeText),
                consequence = "These changes affect the whole photo and can be reset together.",
                primaryLabel = when {
                    input.origin == ObservationOrigin.CAPTURE_REVIEW && count == 2 -> "Apply both for retake"
                    input.origin == ObservationOrigin.CAPTURE_REVIEW -> "Apply all $count for retake"
                    count == 2 -> "Apply both"
                    else -> "Apply all $count"
                },
                action = RecommendationAction.ApplySettings(changes),
                basis = if (recommendations.any { it.basis == RecommendationBasis.MEASURED_DIAGNOSIS }) {
                    RecommendationBasis.MEASURED_DIAGNOSIS
                } else {
                    RecommendationBasis.USER_PREFERENCE
                },
            ),
        )
    }

    private fun compoundChangeText(recommendation: Recommendation): String {
        val adjustment = (recommendation.action as RecommendationAction.ApplySettings)
            .changes.single().adjustment
        return when (adjustment) {
            is CameraAdjustment.ExposureCompensation ->
                "Brightness · ${recommendation.actionText.removePrefix("Apply ")}"
            is CameraAdjustment.ZoomRatio ->
                "Zoom · ${recommendation.actionText.removePrefix("Apply ")}"
            is CameraAdjustment.WhiteBalance -> "Color · ${when (adjustment.preset) {
                WhiteBalancePreset.WARMER -> "Warmer white balance"
                WhiteBalancePreset.COOLER -> "Cooler white balance"
                WhiteBalancePreset.AUTO -> "Auto white balance"
            }}"
        }
    }

    override fun continueWithVisualHint(
        input: CoachingInput,
        family: VisualFamily,
        hint: VisualHint,
    ): LocalDecision = when (hint) {
        is VisualHint.Clarify -> evaluateLocal(input)
        is VisualHint.FocusCell -> if (family == VisualFamily.OBJECT_FOCUS) {
            objectFocus(input, hint)
        } else {
            evaluateLocal(input)
        }
        is VisualHint.Intent -> when {
            family == VisualFamily.FACE_SIZE_AMBIGUOUS && hint.value == VisualIntent.FACE_OCCUPANCY_LOWER ->
                faceOccupancy(input, smaller = true, fromVisual = true)
            family == VisualFamily.FACE_SIZE_AMBIGUOUS && hint.value == VisualIntent.CLOSE_PERSPECTIVE_ADVISORY ->
                perspectiveAdvice(fromVisual = true)
            family == VisualFamily.COLOR_CAST && hint.value == VisualIntent.WHITE_BALANCE_WARMER &&
                colorHintMatchesComplaintAndFrame(input, hint.value) ->
                colorAdjustment(input, WhiteBalancePreset.WARMER, fromVisual = true)
            family == VisualFamily.COLOR_CAST && hint.value == VisualIntent.WHITE_BALANCE_COOLER &&
                colorHintMatchesComplaintAndFrame(input, hint.value) ->
                colorAdjustment(input, WhiteBalancePreset.COOLER, fromVisual = true)
            else -> evaluateLocal(input)
        }
    }

    private fun objectFocus(input: CoachingInput, hint: VisualHint.FocusCell): LocalDecision {
        if (input.origin == ObservationOrigin.CAPTURE_REVIEW || !input.capabilities.supportsFocusMetering) {
            return focus(input)
        }
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = "Subject located",
                actionText = "Tap the marked point to focus",
                consequence = "The camera will focus at the center of the matching grid area.",
                primaryLabel = null,
                action = RecommendationAction.FocusAt(
                    hint.xFraction,
                    hint.yFraction,
                    hint.leftFraction,
                    hint.topFraction,
                    hint.rightFraction,
                    hint.bottomFraction,
                ),
                basis = RecommendationBasis.USER_PREFERENCE,
                fromVisualHint = true,
            ),
        )
    }

    override fun verify(target: VerificationTarget, current: com.bolin.photohelper.capture.FrameObservation): VerificationResult =
        when (target) {
            is VerificationTarget.Exposure -> {
                if (target.baselineObservation == null) {
                    VerificationResult.Incomparable("Setting applied, but there was no stable baseline to verify the visual result.")
                } else if (!observationsComparable(target.baselineObservation, current)) {
                    VerificationResult.Incomparable("Setting applied, but the scene changed, so I can’t verify the visual result.")
                } else {
                    val lumaDelta = current.meanLuma - target.baselineLuma
                    val clip = if (target.direction < 0) current.highlightClipFraction else current.shadowClipFraction
                    val lumaMoved = lumaDelta * target.direction > 0.03f
                    val clippingSatisfied = target.baselineClipFraction > 0f && clip <= target.baselineClipFraction * 0.7f
                    val preferenceSatisfied = target.baselineClipFraction == 0f && clip == 0f && lumaMoved
                    when {
                        clippingSatisfied || preferenceSatisfied -> VerificationResult.Satisfied
                        lumaMoved || clip < target.baselineClipFraction - 0.02f -> VerificationResult.Progress
                        else -> VerificationResult.Unchanged
                    }
                }
            }
            is VerificationTarget.FaceOccupancy -> singleFace(current)?.widthFraction?.let {
                if (it in target.min..target.max) VerificationResult.Satisfied else VerificationResult.Progress
            } ?: VerificationResult.Incomparable("I lost the face—point back at the person")
            is VerificationTarget.FacePosition -> singleFace(current)?.let {
                if (it.centerX in target.xRange && it.centerY in target.yRange) VerificationResult.Satisfied else VerificationResult.Progress
            } ?: VerificationResult.Incomparable("I lost the face—point back at the person")
            is VerificationTarget.StepBack -> singleFace(current)?.widthFraction?.let {
                if (it <= target.maxFaceWidthFraction) VerificationResult.Satisfied else VerificationResult.Progress
            } ?: VerificationResult.Incomparable("I lost the face—point back at the person")
            is VerificationTarget.Level -> current.deviceRollDegrees?.let {
                if (abs(it) <= target.maxAbsoluteRollDegrees) VerificationResult.Satisfied else VerificationResult.Progress
            } ?: VerificationResult.Incomparable("This phone is not reporting its angle")
            is VerificationTarget.ColorBalance -> {
                if (target.baselineObservation == null) {
                    VerificationResult.Incomparable("Setting applied, but there was no stable baseline to verify the visual result.")
                } else if (!observationsComparable(target.baselineObservation, current)) {
                    VerificationResult.Incomparable("Setting applied, but the scene changed, so I can’t verify the visual result.")
                } else {
                    val baseline = target.baselineBlueBias
                    val currentBias = current.chromaBlueBias
                    if (baseline == null || currentBias == null) {
                        VerificationResult.Incomparable("The setting was applied, but this scene cannot verify its color shift.")
                    } else {
                        val movement = (currentBias - baseline) * target.direction
                        when {
                            movement >= 0.015f -> VerificationResult.Satisfied
                            movement > 0f -> VerificationResult.Progress
                            else -> VerificationResult.Unchanged
                        }
                    }
                }
            }
            is VerificationTarget.Zoom -> current.zoomRatio?.let { ratio ->
                val tolerance = (target.targetRatio * 0.01f).coerceAtLeast(0.01f)
                when {
                    abs(ratio - target.targetRatio) <= tolerance -> VerificationResult.Satisfied
                    (ratio - target.baselineRatio) * target.direction > 0f -> VerificationResult.Progress
                    else -> VerificationResult.Unchanged
                }
            } ?: VerificationResult.Incomparable("Zoom was applied, but the camera did not report its current ratio.")
        }

    private fun exposure(input: CoachingInput, darker: Boolean): LocalDecision {
        if (input.origin == ObservationOrigin.CAPTURE_REVIEW && !input.telemetryKnown) {
            return LocalDecision.Advisory(
                "Capture settings are unavailable",
                "The saved photo is still available, but its camera settings were not reported reliably enough to plan a retake.",
            )
        }
        val caps = input.capabilities
        if (!caps.supportsExposureCompensation) {
            return LocalDecision.Advisory("Exposure control is unavailable", "This camera cannot apply an exposure change here.")
        }
        val direction = if (darker) -1 else 1
        val delta = (direction * 0.7f / caps.exposureCompensationStepEv).roundToInt()
        val target = (input.telemetry.exposureCompensationIndex + delta).coerceIn(caps.exposureCompensationRange)
        if (target == input.telemetry.exposureCompensationIndex) {
            return LocalDecision.Advisory("Exposure limit reached", "This camera cannot move exposure farther in that direction.")
        }
        val observation = input.observation
        val defect = observation != null && if (darker) {
            observation.highlightClipFraction >= thresholds.highlightClip
        } else {
            observation.meanLuma <= thresholds.lowLuma || observation.shadowClipFraction >= thresholds.shadowClip
        }
        val signedEv = (target - input.telemetry.exposureCompensationIndex) * caps.exposureCompensationStepEv
        val amount = String.format(java.util.Locale.US, "%+.1f EV", signedEv).replace("+", "+")
        val basis = if (defect) RecommendationBasis.MEASURED_DIAGNOSIS else RecommendationBasis.USER_PREFERENCE
        val headline = when {
            defect && darker -> "The whole frame is clipping"
            defect -> "The whole frame is underexposed"
            darker -> "Exposure is in the normal range, but I can darken it"
            else -> "Exposure is in the normal range, but I can brighten it"
        }
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = headline,
                actionText = "Apply $amount",
                consequence = if (darker) "This should reduce clipping across the photo." else "This should lift detail across the photo.",
                primaryLabel = if (input.origin == ObservationOrigin.CAPTURE_REVIEW) "Apply for retake" else "Apply",
                action = RecommendationAction.ApplySettings(
                    CameraAdjustment.ExposureCompensation(target),
                    VerificationTarget.Exposure(
                        direction = direction,
                        baselineLuma = observation?.meanLuma ?: 0.5f,
                        baselineClipFraction = if (darker) observation?.highlightClipFraction ?: 0f else observation?.shadowClipFraction ?: 0f,
                        baselineObservation = input.comparisonBaseline,
                    ),
                ),
                basis = basis,
            ),
        )
    }

    private fun zoom(input: CoachingInput, inward: Boolean): LocalDecision {
        if (input.origin == ObservationOrigin.CAPTURE_REVIEW && !input.telemetryKnown) {
            return LocalDecision.Advisory(
                "Capture settings are unavailable",
                "The saved photo is still available, but its zoom ratio was not reported reliably enough to plan a retake.",
            )
        }
        val range = input.capabilities.zoomRatioRange
        if (!range.start.isFinite() || !range.endInclusive.isFinite() || range.endInclusive - range.start < 0.01f) {
            return LocalDecision.Advisory("Zoom control is unavailable", "This camera cannot apply a zoom change here.")
        }
        val current = input.telemetry.zoomRatio
        if (!current.isFinite() || current !in range) {
            return LocalDecision.Advisory("Zoom state is unavailable", "Hold the shot steady while the camera reports its current zoom.")
        }
        val direction = if (inward) 1 else -1
        val target = (if (inward) current * 1.25f else current / 1.25f).coerceIn(range)
        if (abs(target - current) < 0.01f) {
            return LocalDecision.Advisory("Zoom limit reached", "This camera cannot move zoom farther in that direction.")
        }
        val formatted = String.format(java.util.Locale.US, "%.2f", target).trimEnd('0').trimEnd('.') + "×"
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = if (inward) "The framing can be tighter" else "The framing can be wider",
                actionText = "Apply $formatted digital zoom",
                consequence = "This changes framing by cropping the camera image and can be reset.",
                primaryLabel = if (input.origin == ObservationOrigin.CAPTURE_REVIEW) "Apply for retake" else "Apply",
                action = RecommendationAction.ApplySettings(
                    CameraAdjustment.ZoomRatio(target),
                    VerificationTarget.Zoom(direction, current, target),
                ),
                basis = RecommendationBasis.USER_PREFERENCE,
            ),
        )
    }

    private fun faceSizeAmbiguity(input: CoachingInput): LocalDecision {
        val face = coachingFace(input)
        val eligibility = input.observation?.takeIf { observation ->
            face != null && face.visibleFraction >= 0.90f &&
                face.widthFraction * observation.sourceWidth >= 100f &&
                (face.bottom - face.top) * observation.sourceHeight >= 100f &&
                face.widthFraction in 0.25f..0.70f
        }?.let {
            VisualEligibility(input.complaintId, VisualFamily.FACE_SIZE_AMBIGUOUS, input.origin, it.id)
        }
        return LocalDecision.Clarify(
            "Does the face take up too much frame, or do the features look distorted?",
            listOf(
                ClarificationChip("Takes up too much frame", "face takes up too much frame"),
                ClarificationChip("Features look distorted", "features look distorted"),
            ),
            eligibility,
        )
    }

    private fun faceOccupancy(input: CoachingInput, smaller: Boolean, fromVisual: Boolean = false): LocalDecision {
        val face = coachingFace(input)
            ?: return when (input.observation?.faces?.size) {
                0, null -> LocalDecision.Advisory("Point the camera at the person first", "Person-specific coaching needs one visible face.")
                1 -> LocalDecision.Advisory("Hold on one person for a moment", "Person-specific coaching needs a stable face before it can start.")
                else -> LocalDecision.Advisory("I see more than one person", "Frame only the person you want help with.")
            }
        if (!smaller && face.widthFraction >= .9f) {
            return LocalDecision.Advisory("The face already fills nearly all of the frame", "Move only if you can keep the person fully visible.")
        }
        val delta = (face.widthFraction * .1f).coerceAtLeast(.02f)
        val target = if (smaller) {
            VerificationTarget.FaceOccupancy(0f, (face.widthFraction - delta).coerceAtLeast(.05f))
        } else {
            VerificationTarget.FaceOccupancy((face.widthFraction + delta).coerceAtMost(.95f), 1f)
        }
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = "The face fills ${(face.widthFraction * 100).roundToInt()}% of the frame",
                actionText = if (smaller) "Take one small step back" else "Take one small step forward",
                consequence = if (smaller) "I’ll check when the face is visibly smaller." else "I’ll check when the face is visibly larger.",
                primaryLabel = "Start one-step guidance",
                action = RecommendationAction.GuidePosition(
                    instruction = if (smaller) "If the path is clear, take one small step back." else "If the path is clear, take one small step forward.",
                    target = target,
                    requiresWalkingWarning = true,
                ),
                basis = RecommendationBasis.USER_PREFERENCE,
                fromVisualHint = fromVisual,
                subjectTrackingId = face.trackingId,
                subjectFace = face,
            ),
        )
    }

    private fun position(input: CoachingInput, horizontal: Int = 0, vertical: Int = 0): LocalDecision {
        val face = coachingFace(input)
        if (face == null) {
            if (input.complaint.lowercase().contains("subject") &&
                !input.complaint.lowercase().containsAny("person", "face")
            ) {
                return LocalDecision.Advisory(
                    "Reframe the subject manually",
                    "Generic subject tracking is not available yet; use the preview guide to place it where you want.",
                )
            }
            return LocalDecision.Advisory("Hold on one person for a moment", "Position coaching needs exactly one stable visible face.")
        }
        val matchesDirection = when {
            horizontal < 0 -> face.centerX < .35f
            horizontal > 0 -> face.centerX > .65f
            vertical < 0 -> face.centerY < .3f
            else -> face.centerY > .7f
        }
        if (!matchesDirection) {
            return LocalDecision.Advisory(
                "The current frame does not match that direction",
                "The subject is not outside the guide on the side you described. Check the preview and describe the position again.",
            )
        }
        val instruction = when {
            horizontal < 0 -> "Aim the phone slightly left."
            horizontal > 0 -> "Aim the phone slightly right."
            vertical < 0 -> "Tilt the phone slightly up."
            else -> "Tilt the phone slightly down."
        }
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = "The subject is outside the center guide",
                actionText = instruction,
                consequence = "I’ll tell you when the subject enters the guide.",
                primaryLabel = "Start guidance",
                action = RecommendationAction.GuidePosition(
                    instruction,
                    VerificationTarget.FacePosition(0.35f..0.65f, 0.3f..0.7f),
                ),
                basis = RecommendationBasis.USER_PREFERENCE,
                subjectTrackingId = face.trackingId,
                subjectFace = face,
            ),
        )
    }

    private fun level(input: CoachingInput): LocalDecision {
        val roll = input.observation?.deviceRollDegrees
            ?: return LocalDecision.Advisory("Level guidance is unavailable", "This phone is not reporting its angle.")
        if (abs(roll) <= 1.5f) return LocalDecision.Advisory("The phone is already level", "It is within the 1.5° target band.")
        val instruction = if (roll > 0) "Rotate the phone a little counterclockwise." else "Rotate the phone a little clockwise."
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = "The phone is tilted ${abs(roll).roundToInt()}°",
                actionText = instruction,
                consequence = "I’ll check when the phone is level.",
                primaryLabel = "Start guidance",
                action = RecommendationAction.GuidePosition(instruction, VerificationTarget.Level()),
                basis = RecommendationBasis.MEASURED_DIAGNOSIS,
            ),
        )
    }

    private fun focus(input: CoachingInput): LocalDecision {
        if (input.origin == ObservationOrigin.CAPTURE_REVIEW) {
            return LocalDecision.Advisory(
                "Return to the live preview to focus",
                "Tap Retake, then describe the missed focus again and choose the subject in the preview.",
            )
        }
        if (!input.capabilities.supportsFocusMetering) {
            return LocalDecision.Advisory(
                "Focus adjustment is unavailable",
                "This camera does not expose a tested tap-to-focus control.",
            )
        }
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = "Choose what should be sharp",
                actionText = "Tap the subject in the preview",
                consequence = "The camera will try to lock focus at that point.",
                primaryLabel = null,
                action = RecommendationAction.TapToFocus,
                basis = RecommendationBasis.USER_PREFERENCE,
            ),
        )
    }

    private fun color(input: CoachingInput, warmer: Boolean): LocalDecision {
        val observation = input.observation
        val eligible = observation?.takeIf {
            val bias = it.chromaBlueBias
                it.meanLuma in 0.10f..0.90f &&
                it.highlightClipFraction < 0.15f &&
                it.shadowClipFraction < 0.15f &&
                bias != null && abs(bias) in thresholds.blueBiasWeak..thresholds.blueBiasStrong &&
                (if (warmer) bias > 0f else bias < 0f)
        }?.let { VisualEligibility(input.complaintId, VisualFamily.COLOR_CAST, input.origin, it.id) }
        return LocalDecision.Clarify(
            if (warmer) "The image may trend cool. Do you want warmer color or Auto white balance?" else "The image may trend warm. Do you want cooler color or Auto white balance?",
            listOf(
                ClarificationChip(if (warmer) "Warmer" else "Cooler", if (warmer) "warmer" else "cooler"),
                ClarificationChip("Auto", "auto"),
            ),
            eligible,
        )
    }

    private fun perspectiveAdvice(fromVisual: Boolean = false): LocalDecision =
        LocalDecision.Advisory(
            headline = "This may be close-perspective distortion",
            detail = "Step back, then use a longer lens or zoom to restore the framing. " +
                "Photo Helper cannot verify facial proportions in this version.",
            fromVisualHint = fromVisual,
        )

    private fun colorAdjustment(
        input: CoachingInput,
        preset: WhiteBalancePreset,
        fromVisual: Boolean,
    ): LocalDecision {
        if (input.origin == ObservationOrigin.CAPTURE_REVIEW && !input.telemetryKnown) {
            return LocalDecision.Advisory(
                "Capture settings are unavailable",
                "The saved photo is still available, but its camera settings were not reported reliably enough to plan a retake.",
                fromVisualHint = fromVisual,
            )
        }
        if (preset == input.telemetry.whiteBalancePreset) {
            return LocalDecision.Advisory(
                if (preset == WhiteBalancePreset.AUTO) "Auto white balance is already active" else "That color setting is already active",
                "Describe another change if the image still does not look right.",
                fromVisualHint = fromVisual,
            )
        }
        if (preset !in input.capabilities.supportedWhiteBalancePresets) {
            return LocalDecision.Advisory(
                "White-balance adjustment is unavailable",
                "This camera cannot apply that color change in Photo Helper.",
                fromVisualHint = fromVisual,
            )
        }
        val direction = when (preset) {
            WhiteBalancePreset.WARMER -> -1
            WhiteBalancePreset.COOLER -> 1
            WhiteBalancePreset.AUTO -> if (input.telemetry.whiteBalancePreset == WhiteBalancePreset.WARMER) 1 else -1
        }
        val label = if (input.origin == ObservationOrigin.CAPTURE_REVIEW) "Apply for retake" else "Apply"
        return LocalDecision.Recommend(
            Recommendation(
                complaintId = input.complaintId,
                cameraSessionId = input.cameraSessionId,
                headline = when (preset) {
                    WhiteBalancePreset.WARMER -> "The image trends cool"
                    WhiteBalancePreset.COOLER -> "The image trends warm"
                    WhiteBalancePreset.AUTO -> "A fixed white balance is active"
                },
                actionText = when (preset) {
                    WhiteBalancePreset.WARMER -> "Apply a warmer white balance"
                    WhiteBalancePreset.COOLER -> "Apply a cooler white balance"
                    WhiteBalancePreset.AUTO -> "Restore Auto white balance"
                },
                consequence = "This changes color across the whole image and can be reset.",
                primaryLabel = label,
                action = RecommendationAction.ApplySettings(
                    CameraAdjustment.WhiteBalance(preset),
                    VerificationTarget.ColorBalance(
                        direction,
                        input.observation?.chromaBlueBias,
                        input.comparisonBaseline,
                    ),
                ),
                basis = if (fromVisual) RecommendationBasis.MEASURED_DIAGNOSIS else RecommendationBasis.USER_PREFERENCE,
                fromVisualHint = fromVisual,
            ),
        )
    }

    private fun clarify(reason: ClarificationReason, text: String): LocalDecision = when (reason) {
        ClarificationReason.BLUR_TYPE -> LocalDecision.Clarify(
            "Is movement blurred, or did focus miss?",
            listOf(
                ClarificationChip("Freeze movement", "freeze movement"),
                ClarificationChip("Focus missed", "focus missed"),
            ),
        )
        ClarificationReason.ZOOM_OR_DISTANCE -> LocalDecision.Clarify(
            "Is the view zoomed in, is the camera physically too close, or does the subject fill too much frame?",
            listOf(
                ClarificationChip("Zoomed in", "too zoomed in"),
                ClarificationChip("Perspective", "features look distorted"),
                ClarificationChip("Fills frame", "subject fills too much frame"),
            ),
        )
        ClarificationReason.REGIONAL_REQUEST -> if (hasRegionalColorRequest(text)) {
            val wholePhoto = if (text.containsAny("blue", "cold", "cool")) "whole photo is too blue" else "whole photo is too yellow"
            LocalDecision.Clarify(
                "Does that color cast affect the whole photo or only the named area?",
                listOf(
                    ClarificationChip("Whole photo", wholePhoto),
                    ClarificationChip("Named area", "regional color adjustment"),
                ),
            )
        } else {
            LocalDecision.Clarify(
                "Which area do you mean? A whole-photo change affects every area.",
                listOf(
                    ClarificationChip("Whole photo", if (requestsBrighterExposure(text)) "whole photo is too dark" else "whole photo is too bright"),
                    ClarificationChip("Person/face", "person-specific exposure"),
                    ClarificationChip("Background", "background-specific exposure"),
                ),
            )
        }
        ClarificationReason.NEGATED_DIRECTION -> LocalDecision.Clarify(
            "That sounds like a change you do not want. What should change instead?",
            standardChips(),
        )
        ClarificationReason.CONFLICTING_DIRECTIONS -> LocalDecision.Clarify(
            "Those directions conflict. Which one should I use?",
            standardChips(),
        )
        ClarificationReason.MULTIPLE_COMPLAINTS -> LocalDecision.Clarify(
            "Choose one change first; you can ask for the next one after applying it.",
            standardChips(),
        )
        ClarificationReason.AMBIGUOUS -> if (text.contains("cool")) {
            LocalDecision.Clarify(
                "Do you mean the color looks too blue, or that you like the style?",
                listOf(ClarificationChip("Too blue", "too blue")),
            )
        } else {
            LocalDecision.Clarify(
                "Describe the result directly, for example ‘too bright’ or ‘background too bright.’",
                standardChips(),
            )
        }
    }

    private fun unsupported(reason: UnsupportedReason): LocalDecision = when (reason) {
        UnsupportedReason.MANUAL_EXPOSURE -> LocalDecision.Advisory(
            "Manual ISO and shutter are not available yet",
            "This version will not substitute exposure compensation for an explicit ISO or shutter request.",
        )
        UnsupportedReason.NOISE_REDUCTION -> LocalDecision.Advisory(
            "Noise control is not available yet",
            "ISO and shutter must be qualified together before Photo Helper can offer a safe one-tap noise adjustment.",
        )
    }

    private fun clarifyCurrentShot() = LocalDecision.Clarify("Which part feels wrong?", standardChips())

    private fun standardChips() = listOf(
        ClarificationChip("Too bright", "whole photo is too bright"),
        ClarificationChip("Too dark", "whole photo is too dark"),
        ClarificationChip("Face size", "face too big"),
    )

    private fun singleFace(observation: com.bolin.photohelper.capture.FrameObservation?): FaceObservation? =
        observation?.faces?.singleOrNull()

    private fun coachingFace(input: CoachingInput): FaceObservation? =
        input.lockedFace?.takeIf { input.observation?.faces?.size == 1 }

    private fun colorHintMatchesComplaintAndFrame(input: CoachingInput, hint: VisualIntent): Boolean {
        val text = input.complaint.lowercase()
        val expected = when {
            text.containsAny("too blue", "looks blue", "looks cool", "too cool", "cold") -> VisualIntent.WHITE_BALANCE_WARMER
            text.containsAny("too yellow", "looks yellow", "too warm", "looks warm") -> VisualIntent.WHITE_BALANCE_COOLER
            else -> return false
        }
        val bias = input.observation?.chromaBlueBias ?: return false
        val frameMatches = if (expected == VisualIntent.WHITE_BALANCE_WARMER) {
            bias in thresholds.blueBiasWeak..thresholds.blueBiasStrong
        } else {
            bias in -thresholds.blueBiasStrong..-thresholds.blueBiasWeak
        }
        return hint == expected && frameMatches
    }

    private fun isRegionalExposureComplaint(text: String): Boolean =
        text.containsAny("background", "foreground", "sky", "window", "face", "skin", "person", "subject", "left side", "right side") &&
            text.containsAny("bright", "dark", "dim", "overexposed", "underexposed", "washed out", "highlights", "shadows")

    private fun hasRegionalColorRequest(text: String): Boolean =
        text.containsAny("blue", "yellow", "warm", "cool", "cold") &&
            text.containsAny("background", "foreground", "sky", "window", "face", "skin", "person", "subject", "left side", "right side")

    private fun requestsBrighterExposure(text: String): Boolean =
        text.containsAny("dark", "dim", "underexposed", "shadows")

    private fun String.containsAny(vararg values: String) = values.any(::contains)
}

internal fun classifyComplaint(raw: String): IntentClassification {
    val text = raw.trim().lowercase()
    if (text.isBlank()) return IntentClassification.Unknown
    if (hasManualExposureRequest(text)) return IntentClassification.Unsupported(UnsupportedReason.MANUAL_EXPOSURE)
    if (text.containsAnyText("too noisy", "noisy", "grainy", "too much noise", "too much grain")) {
        return IntentClassification.Unsupported(UnsupportedReason.NOISE_REDUCTION)
    }
    if (text in setOf("a little more", "more", "less", "do the opposite", "no, the background")) {
        return IntentClassification.Clarify(ClarificationReason.AMBIGUOUS)
    }
    if (Regex("\\b(?:or|either)\\b").containsMatchIn(text)) {
        return IntentClassification.Clarify(ClarificationReason.AMBIGUOUS)
    }
    if (hasNegatedControlDirection(text)) return IntentClassification.Clarify(ClarificationReason.NEGATED_DIRECTION)
    if (text.containsAnyText("too blur", "blurry", "blurred", "out of focus", "motion blur")) {
        return IntentClassification.Clarify(ClarificationReason.BLUR_TYPE)
    }
    if (text.contains("too close")) return IntentClassification.Clarify(ClarificationReason.ZOOM_OR_DISTANCE)
    if (text.contains("looks cool") && !text.containsAnyText("color", "colour", "tone", "temperature")) {
        return IntentClassification.Clarify(ClarificationReason.AMBIGUOUS)
    }

    val fullTextIntents = buildSet {
        if (text.containsAnyText(
                "too dark", "too dim", "so dark", "so dim", "underexposed", "shadows gone",
                "brighten", "make it brighter", "make brighter", "make the picture brighter",
                "make the photo brighter", "make the image brighter",
            ) || text in setOf("dark", "dim") || Regex("\\bbrighter\\b").containsMatchIn(text)
        ) add(ControlIntent.EXPOSURE_BRIGHTER)
        if (text.containsAnyText(
                "too bright", "overexposed", "washed out", "highlights gone", "darken", "make it darker", "make darker",
                "make the picture darker", "make the photo darker", "make the image darker",
            ) || text == "bright" || Regex("\\bdarker\\b").containsMatchIn(text)
        ) add(ControlIntent.EXPOSURE_DARKER)
        if (text.containsAnyText("too zoomed out", "zoom in", "zoom closer")) add(ControlIntent.ZOOM_IN)
        if (text.containsAnyText("too zoomed in", "zoom out", "zoom wider")) add(ControlIntent.ZOOM_OUT)
        if (text.containsAnyText("too blue", "so blue", "too cold", "looks cold", "cold-toned", "cold toned", "make it warmer") || text == "warmer") {
            add(ControlIntent.WHITE_BALANCE_WARMER)
        }
        if (text.containsAnyText("too yellow", "so yellow", "too warm", "warm-toned", "warm toned", "make it cooler") || text == "cooler") {
            add(ControlIntent.WHITE_BALANCE_COOLER)
        }
        if (text in setOf("auto", "auto white balance", "restore auto white balance")) add(ControlIntent.WHITE_BALANCE_AUTO)
        if (text.containsAnyText("focus missed", "missed focus")) add(ControlIntent.FOCUS_POINT_REQUIRED)
        if (text.containsAnyText("crooked", "not straight", "isn't straight", "is not straight", "level the phone", "level the frame")) {
            add(ControlIntent.LEVEL_FRAME)
        }
    }

    val clauses = text.split(COMPLAINT_CLAUSE_SEPARATOR)
        .filter(String::isNotBlank)
    val clauseClassifications = if (clauses.size > 1) clauses.map(::classifyComplaint) else emptyList()
    if (clauseClassifications.isNotEmpty()) {
        clauseClassifications.filterIsInstance<IntentClassification.Unsupported>().firstOrNull()?.let { return it }
        clauseClassifications.filterIsInstance<IntentClassification.Clarify>().firstOrNull()?.let { return it }
        if (clauseClassifications.any { it == IntentClassification.Unknown }) {
            return if (fullTextIntents.isNotEmpty() || clauseClassifications.any { it is IntentClassification.Intent }) {
                IntentClassification.Clarify(ClarificationReason.MULTIPLE_COMPLAINTS)
            } else {
                IntentClassification.Unknown
            }
        }
    }

    val intents = (fullTextIntents + clauseClassifications
        .filterIsInstance<IntentClassification.Intent>()
        .flatMap(IntentClassification.Intent::values))
        .distinct()
        .sortedBy { directSettingAxis(it) ?: Int.MAX_VALUE }
    val settingAxes = intents.mapNotNull(::directSettingAxis)
    if (settingAxes.distinct().size != settingAxes.size) {
        return IntentClassification.Clarify(ClarificationReason.CONFLICTING_DIRECTIONS)
    }
    if (hasRegionalControlRequest(text)) return IntentClassification.Clarify(ClarificationReason.REGIONAL_REQUEST)
    if (intents.size > 1) {
        return if (intents.all { directSettingAxis(it) != null }) {
            IntentClassification.Intent(intents.toList())
        } else {
            IntentClassification.Clarify(ClarificationReason.MULTIPLE_COMPLAINTS)
        }
    }
    return intents.singleOrNull()?.let(IntentClassification::Intent) ?: IntentClassification.Unknown
}

private fun directSettingAxis(intent: ControlIntent): Int? = when (intent) {
    ControlIntent.EXPOSURE_BRIGHTER, ControlIntent.EXPOSURE_DARKER -> 0
    ControlIntent.ZOOM_IN, ControlIntent.ZOOM_OUT -> 1
    ControlIntent.WHITE_BALANCE_WARMER,
    ControlIntent.WHITE_BALANCE_COOLER,
    ControlIntent.WHITE_BALANCE_AUTO -> 2
    ControlIntent.FOCUS_POINT_REQUIRED, ControlIntent.LEVEL_FRAME -> null
}

private fun hasManualExposureRequest(text: String): Boolean =
    Regex("\\biso\\b").containsMatchIn(text) ||
        text.containsAnyText("shutter", "exposure time", "manual exposure") ||
        Regex("\\b1\\s*/\\s*\\d+\\s*(s|sec|second|seconds)?\\b").containsMatchIn(text)

private fun hasNegatedControlDirection(text: String): Boolean {
    val hasNegation = text.containsAnyText("not too", "no longer", "isn't", "is not", "don't", "dont", "do not") || text.startsWith("not ")
    return hasNegation && text.containsAnyText(
        "bright", "dark", "dim", "overexposed", "underexposed", "blue", "yellow", "warm", "cool", "cold", "zoom", "focus", "blur",
    )
}

private fun hasRegionalControlRequest(text: String): Boolean {
    val region = Regex("\\b(background|foreground|sky|window|face|skin|person|subject)\\b").containsMatchIn(text) ||
        text.containsAnyText("left side", "right side", "top half", "bottom half")
    val wholeFrameControl = text.containsAnyText(
        "bright", "dark", "dim", "overexposed", "underexposed", "washed out", "highlights", "shadows",
        "blue", "yellow", "warm", "cool", "cold",
    )
    return region && wholeFrameControl
}

private fun String.containsAnyText(vararg values: String) = values.any(::contains)

internal fun observationsComparable(baseline: FrameObservation, current: FrameObservation): Boolean {
    if (baseline.sourceWidth <= 0 || baseline.sourceHeight <= 0 || current.sourceWidth <= 0 || current.sourceHeight <= 0) return false
    val baselineAspect = baseline.sourceWidth.toFloat() / baseline.sourceHeight
    val currentAspect = current.sourceWidth.toFloat() / current.sourceHeight
    if (relativeDelta(baselineAspect, currentAspect) >= 0.05f) return false
    if (baseline.motionScore > 0.08f || current.motionScore > 0.08f) return false
    val baselineSignature = baseline.sceneLumaSignature
    val currentSignature = current.sceneLumaSignature
    if (baselineSignature.isEmpty() != currentSignature.isEmpty()) return false
    if (baselineSignature.isNotEmpty() && exposureInvariantSceneDifference(baselineSignature, currentSignature) > 0.08f) return false
    if (baseline.lensId != current.lensId) return false
    if (baseline.focalLengthMm == null != (current.focalLengthMm == null)) return false
    if (baseline.focalLengthMm != null && current.focalLengthMm != null &&
        relativeDelta(baseline.focalLengthMm, current.focalLengthMm) >= 0.02f
    ) return false
    if (baseline.zoomRatio == null != (current.zoomRatio == null)) return false
    if (baseline.zoomRatio != null && current.zoomRatio != null && relativeDelta(baseline.zoomRatio, current.zoomRatio) >= 0.02f) return false
    if (baseline.deviceRollDegrees != null && current.deviceRollDegrees != null &&
        abs(baseline.deviceRollDegrees - current.deviceRollDegrees) >= 2f
    ) return false
    if (baseline.faces.size != current.faces.size) return false
    val baselineFaces = baseline.faces.sortedBy { it.centerX }
    val currentFaces = current.faces.sortedBy { it.centerX }
    return baselineFaces.zip(currentFaces).all { (first, second) ->
        (first.trackingId == null || second.trackingId == null || first.trackingId == second.trackingId) &&
            abs(first.centerX - second.centerX) < 0.05f &&
            abs(first.centerY - second.centerY) < 0.05f &&
            relativeDelta(first.widthFraction, second.widthFraction) < 0.05f &&
            relativeDelta(first.bottom - first.top, second.bottom - second.top) < 0.05f
    }
}

private fun relativeDelta(first: Float, second: Float): Float =
    if (first > 0f) abs(second - first) / first else if (second == 0f) 0f else Float.POSITIVE_INFINITY
