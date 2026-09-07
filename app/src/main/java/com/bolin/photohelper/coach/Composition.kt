package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.FaceObservation
import com.bolin.photohelper.capture.FrameObservation
import kotlin.math.abs

enum class CompositionStrategy { PORTRAIT, SYMMETRY, LEADING_LINES, LOOK_SPACE }
enum class CompositionFraming { CLOSE, BALANCED, WIDE }
enum class CompositionPlacement { CENTRE, LEFT_THIRD, RIGHT_THIRD }
enum class GuidanceMode { CLOSED_LOOP, ADVICE_ONLY }
enum class CompositionProblem { NONE, LOOK_ROOM, HEADROOM, PLACEMENT, SUBJECT_SIZE, PERSPECTIVE, BACKGROUND }
enum class HorizontalPlacement { KEEP, LEFT_THIRD, CENTRE, RIGHT_THIRD }
enum class VerticalPlacement { KEEP, UPPER, MIDDLE, LOWER }
enum class CompositionSize { KEEP, LARGER, SMALLER }
enum class CompositionMovement { NONE, ZOOM, WALK }

data class CompositionAdjustment(
    val problem: CompositionProblem,
    val horizontal: HorizontalPlacement,
    val vertical: VerticalPlacement,
    val size: CompositionSize,
    val movement: CompositionMovement,
) {
    init {
        require((size == CompositionSize.KEEP) == (movement == CompositionMovement.NONE))
        val unchanged = horizontal == HorizontalPlacement.KEEP && vertical == VerticalPlacement.KEEP && size == CompositionSize.KEEP
        require(when (problem) {
            CompositionProblem.NONE, CompositionProblem.BACKGROUND -> unchanged
            CompositionProblem.LOOK_ROOM -> horizontal != HorizontalPlacement.KEEP
            CompositionProblem.HEADROOM -> vertical != VerticalPlacement.KEEP
            CompositionProblem.PLACEMENT -> horizontal != HorizontalPlacement.KEEP || vertical != VerticalPlacement.KEEP
            CompositionProblem.SUBJECT_SIZE -> size != CompositionSize.KEEP
            CompositionProblem.PERSPECTIVE -> horizontal == HorizontalPlacement.KEEP && vertical == VerticalPlacement.KEEP &&
                size == CompositionSize.SMALLER && movement == CompositionMovement.WALK
        })
    }
}

data class CompositionIntent(
    val strategy: CompositionStrategy = CompositionStrategy.PORTRAIT,
    val framing: CompositionFraming = CompositionFraming.BALANCED,
    val placement: CompositionPlacement = CompositionPlacement.CENTRE,
    val reason: String = "Give the faces room in the frame.",
    val adjustment: CompositionAdjustment? = null,
) {
    init { require(reason.isNotBlank() && reason.length <= 240 && reason.none { it.isISOControl() }) }
}

/** Pilot defaults, shared by generic rules and AI-selected strategies. */
data class GuidancePolicy(
    val timeoutMs: Long = 30_000,
    val noProgressMs: Long = 8_000,
    val recoveryMs: Long = 2_000,
    val completionDwellMs: Long = 500,
    val switchDwellMs: Long = 500,
    val cooldownMs: Long = 750,
    val maxReversals: Int = 3,
) {
    init {
        require(timeoutMs > 0 && noProgressMs > 0 && recoveryMs > 0)
        require(completionDwellMs > 0 && switchDwellMs >= 0 && cooldownMs >= 0 && maxReversals >= 0)
    }
}

data class CompositionPlan(
    val intent: CompositionIntent,
    val targets: List<VerificationTarget>,
    val members: List<FaceObservation>,
    val advice: String,
    val policy: GuidancePolicy = GuidancePolicy(),
) {
    val guidanceMode get() = if (targets.isEmpty()) GuidanceMode.ADVICE_ONLY else GuidanceMode.CLOSED_LOOP
    val distanceMovement get() = intent.adjustment?.movement == CompositionMovement.WALK
}

/** No coordinates, capability claims or tolerances come from the model. */
fun compileComposition(intent: CompositionIntent, members: List<FaceObservation>): CompositionPlan {
    intent.adjustment?.let { return compileAdjustment(intent, it, members) }
    val advice = when (intent.strategy) {
        CompositionStrategy.SYMMETRY -> "Try centring the scene to emphasise its symmetry."
        CompositionStrategy.LEADING_LINES -> "Try framing the lines so they lead toward your subject."
        CompositionStrategy.LOOK_SPACE -> "Try leaving space in the direction your subject is looking."
        CompositionStrategy.PORTRAIT -> "Try placing your main subject on a third of the frame."
    }
    if (intent.strategy != CompositionStrategy.PORTRAIT || members.isEmpty() || members.any { !it.validGeometry() }) {
        return CompositionPlan(intent, emptyList(), members, advice)
    }
    val x = when (intent.placement) {
        CompositionPlacement.CENTRE -> .5f
        CompositionPlacement.LEFT_THIRD -> 1f / 3f
        CompositionPlacement.RIGHT_THIRD -> 2f / 3f
    }
    val group = members.size > 1
    val desiredWidth = when (intent.framing) {
        CompositionFraming.CLOSE -> if (group) .65f else .34f
        CompositionFraming.BALANCED -> if (group) .50f else .25f
        CompositionFraming.WIDE -> if (group) .35f else .17f
    }
    // Keep the selected union inside the frame, including non-central placement.
    val maxWidth = minOf(desiredWidth + .04f, 2f * minOf(x - .05f, .95f - x))
    val minWidth = desiredWidth - .04f
    if (minWidth > maxWidth) return CompositionPlan(intent, emptyList(), members, advice)
    val targets = if (group) listOf(
        VerificationTarget.GroupPosition((x - .04f)..(x + .04f), .34f.. .46f),
        VerificationTarget.GroupOccupancy(minWidth, maxWidth),
    ) else listOf(
        VerificationTarget.FacePosition((x - .04f)..(x + .04f), .34f.. .46f),
        VerificationTarget.FaceOccupancy(minWidth, maxWidth),
    )
    return CompositionPlan(intent, targets, members, advice)
}

private fun compileAdjustment(intent: CompositionIntent, choice: CompositionAdjustment, members: List<FaceObservation>): CompositionPlan {
    fun advice(text: String) = CompositionPlan(intent, emptyList(), members, text)
    if (choice.problem == CompositionProblem.NONE) return advice("Keep this framing.")
    if (choice.problem == CompositionProblem.BACKGROUND) return advice("Move sideways to separate the subject from the background.")
    val face = faceUnion(members) ?: return advice("Keep the selected faces visible, then try again.")
    val x = when (choice.horizontal) {
        HorizontalPlacement.KEEP -> face.centerX
        HorizontalPlacement.LEFT_THIRD -> 1f / 3f
        HorizontalPlacement.CENTRE -> .5f
        HorizontalPlacement.RIGHT_THIRD -> 2f / 3f
    }
    val y = when (choice.vertical) {
        VerticalPlacement.KEEP -> face.centerY
        VerticalPlacement.UPPER -> .35f
        VerticalPlacement.MIDDLE -> .5f
        VerticalPlacement.LOWER -> .65f
    }
    // ponytail: one modest size step per plan; later plans can request another step.
    val scale = when (choice.size) {
        CompositionSize.KEEP -> 1f
        CompositionSize.LARGER -> 1.3f
        CompositionSize.SMALLER -> .75f
    }
    val width = face.widthFraction * scale
    val height = (face.bottom - face.top) * scale
    // Reject incompatible targets instead of shrinking or reversing the model's requested movement.
    if (x - width / 2 < 0f || x + width / 2 > 1f || y - height / 2 < 0f || y + height / 2 > 1f)
        return advice("This placement would cut off selected faces. Try a different framing.")
    val tolerance = width * .1f
    val targets = if (members.size == 1) listOf(
        VerificationTarget.FacePosition((x - .03f)..(x + .03f), (y - .03f)..(y + .03f)),
        VerificationTarget.FaceOccupancy(width - tolerance, width + tolerance),
    ) else listOf(
        VerificationTarget.GroupPosition((x - .03f)..(x + .03f), (y - .03f)..(y + .03f)),
        VerificationTarget.GroupOccupancy(width - tolerance, width + tolerance),
    )
    return CompositionPlan(intent, targets, members, "Follow the framing instructions.")
}

internal fun FaceObservation.validGeometry(): Boolean =
    left.isFinite() && right.isFinite() && top.isFinite() && bottom.isFinite() &&
        left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && left < right && top < bottom &&
        visibleFraction in 0f..1f

fun faceUnion(faces: List<FaceObservation>): FaceObservation? =
    faces.takeIf { it.isNotEmpty() && it.all(FaceObservation::validGeometry) }?.let {
        FaceObservation(null, it.minOf { f -> f.left }, it.minOf { f -> f.top },
            it.maxOf { f -> f.right }, it.maxOf { f -> f.bottom }, it.minOf { f -> f.visibleFraction })
    }

/** Unique one-to-one association only. Never choose the first of competing candidates. */
fun matchMembers(previous: List<FaceObservation>, detected: List<FaceObservation>): List<FaceObservation>? {
    if (previous.isEmpty()) return emptyList()
    val matches = previous.map { old ->
        val plausible = detected.filter { next ->
            next.validGeometry() && abs(old.centerX - next.centerX) <= .18f &&
                abs(old.centerY - next.centerY) <= .18f &&
                next.widthFraction / old.widthFraction.coerceAtLeast(.001f) in .5f..2f
        }
        val sameId = plausible.filter { old.trackingId != null && it.trackingId == old.trackingId }
        if (sameId.size == 1) sameId.single() else if (sameId.isEmpty()) {
            plausible.singleOrNull() ?: return null
        } else return null
    }
    return matches.takeIf { it.distinct().size == previous.size }
}

/** A bounded deterministic proposal; ambiguous scenes require a visible selection. */
fun automaticCompositionMembers(samples: List<FrameObservation>): List<FaceObservation>? {
    if (samples.size < 3 || samples.last().timestampMs - samples.first().timestampMs < 500) return null
    val faces = samples.first().faces.filter { it.validGeometry() && it.widthFraction >= .04f }
    if (faces.isEmpty()) return emptyList<FaceObservation>().takeIf { samples.all { it.faces.isEmpty() } }
    var stable = faces
    for (sample in samples.drop(1)) stable = matchMembers(stable, sample.faces) ?: return null
    if (stable.size == 1 && samples.last().faces.size == 1) return stable
    // ponytail: conservative grouping heuristic; tune on selection corrections in the pilot.
    val union = faceUnion(stable) ?: return null
    val heightSpread = stable.maxOf { it.centerY } - stable.minOf { it.centerY }
    val ordered = stable.sortedBy { it.centerX }
    val grouped = ordered.zipWithNext().all { (a, b) -> b.left - a.right <= .18f }
    return stable.takeIf { grouped && heightSpread <= .25f && union.widthFraction <= .9f && stable.size == samples.last().faces.size }
}

enum class Correction(val instruction: String, val axis: Int, val sign: Int) {
    HOLD("Hold this framing", 0, 0),
    // Directions name the required image motion; instructions name the opposite phone motion.
    LEFT("Aim the phone slightly right", 1, -1), RIGHT("Aim the phone slightly left", 1, 1),
    UP("Tilt the phone slightly down", 2, -1), DOWN("Tilt the phone slightly up", 2, 1),
    SMALLER("Zoom out slightly", 3, -1), LARGER("Zoom in slightly", 3, 1),
    STEP_BACK("Move the phone farther away", 3, -1),
    STEP_FORWARD("Move the phone closer", 3, 1),
    LEVEL_LEFT("Rotate the phone counterclockwise", 4, -1), LEVEL_RIGHT("Rotate the phone clockwise", 4, 1),
    RECOVER("Hold the camera while I find the same people", 0, 0),
    BLOCKED("Keep this position", 0, 0),
    REFRAME("Include every selected face in the frame", 0, 0);

    fun phoneInstruction(mirrored: Boolean = false): String = when {
        mirrored && this == LEFT -> RIGHT.instruction
        mirrored && this == RIGHT -> LEFT.instruction
        else -> instruction
    }

    val isMovement: Boolean get() = axis in 1..2 || axis == 4 || this == STEP_BACK || this == STEP_FORWARD
}

data class GuidanceMeasurement(val correction: Correction, val error: Float, val satisfied: Boolean)

fun measureGuidance(targets: List<VerificationTarget>, observation: FrameObservation,
    blocked: Set<Correction> = emptySet(), distanceMovement: Boolean = false): GuidanceMeasurement? {
    val face = faceUnion(observation.faces)
    val errors = mutableListOf<Pair<Correction, Float>>()
    fun range(value: Float, bounds: ClosedFloatingPointRange<Float>, low: Correction, high: Correction) {
        val tolerance = ((bounds.endInclusive - bounds.start) / 2f).coerceAtLeast(.02f)
        if (value < bounds.start) errors += low to (bounds.start - value) / tolerance
        if (value > bounds.endInclusive) errors += high to (value - bounds.endInclusive) / tolerance
    }
    for (target in targets) when (target) {
        is VerificationTarget.Composition -> return measureGuidance(target.plan.targets, observation, blocked, distanceMovement)
        is VerificationTarget.FacePosition -> {
            if (observation.faces.size != 1 || face == null) return null
            range(face.centerX, target.xRange, Correction.RIGHT, Correction.LEFT)
            range(face.centerY, target.yRange, Correction.DOWN, Correction.UP)
        }
        is VerificationTarget.GroupPosition -> {
            if (face == null || observation.faces.size < 2) return null
            range(face.centerX, target.xRange, Correction.RIGHT, Correction.LEFT)
            range(face.centerY, target.yRange, Correction.DOWN, Correction.UP)
        }
        is VerificationTarget.FaceOccupancy -> {
            if (face == null || observation.faces.size != 1) return null
            range(face.widthFraction, target.min..target.max, Correction.LARGER, Correction.SMALLER)
        }
        is VerificationTarget.GroupOccupancy -> {
            if (face == null || observation.faces.size < 2) return null
            range(face.widthFraction, target.min..target.max, Correction.LARGER, Correction.SMALLER)
        }
        is VerificationTarget.StepBack -> {
            if (face == null || observation.faces.size != 1) return null
            range(face.widthFraction, 0f..target.maxFaceWidthFraction, Correction.LARGER, Correction.STEP_BACK)
        }
        is VerificationTarget.Level -> range(observation.deviceRollDegrees ?: return null,
            -target.maxAbsoluteRollDegrees..target.maxAbsoluteRollDegrees, Correction.LEVEL_RIGHT, Correction.LEVEL_LEFT)
        else -> return null
    }
    if (targets.isEmpty()) return null
    val faceTarget = targets.any { it !is VerificationTarget.Level }
    if (faceTarget && observation.faces.any { it.visibleFraction < .90f }) {
        val correction = when {
            face == null -> return null
            face.left <= .001f && face.right >= .999f -> Correction.SMALLER
            face.left <= .001f -> Correction.RIGHT
            face.right >= .999f -> Correction.LEFT
            face.top <= .001f && face.bottom >= .999f -> Correction.SMALLER
            face.top <= .001f -> Correction.DOWN
            face.bottom >= .999f -> Correction.UP
            else -> Correction.REFRAME
        }
        return GuidanceMeasurement(if (correction in blocked) Correction.BLOCKED else correction,
            1f + errors.sumOf { it.second.toDouble() }.toFloat(), false)
    }
    val available = errors.map { (direction, error) ->
        (if (distanceMovement) when (direction) {
            Correction.SMALLER -> Correction.STEP_BACK
            Correction.LARGER -> Correction.STEP_FORWARD
            else -> direction
        } else direction) to error
    }.filter { it.first !in blocked }
    val correction = available.filter { it.first.axis == 3 }.maxByOrNull { it.second }
        ?: available.maxByOrNull { it.second }
    return GuidanceMeasurement(correction?.first ?: if (errors.isEmpty()) Correction.HOLD else Correction.BLOCKED,
        errors.sumOf { it.second.toDouble() }.toFloat(), errors.isEmpty())
}

data class GuidanceMetrics(
    val elapsedMs: Long, val reversals: Int, val overshoots: Int, val trackingLosses: Int,
    val framesWithinTolerance: Int, val frames: Int, val outcome: String,
    val sessionId: String,
    val timeToTargetMs: Long?,
)

data class GovernedGuidance(val correction: Correction, val complete: Boolean = false, val failure: String? = null)

class GuidanceGovernor(val startedAtMs: Long, val policy: GuidancePolicy = GuidancePolicy()) {
    val sessionId: String = java.util.UUID.randomUUID().toString()
    private var displayed = Correction.HOLD
    private var candidate = Correction.HOLD
    private var candidateSince = startedAtMs
    private var switchedAt = startedAtMs - policy.cooldownMs
    private var satisfiedSince: Long? = null
    private var lostSince: Long? = null
    private var lastAt = startedAtMs
    private var bestError = Float.POSITIVE_INFINITY
    private var progressAt = startedAtMs
    private var lastMovement: Correction? = null
    private var withinLastFrame = false
    private var reversals = 0
    private var overshoots = 0
    private var losses = 0
    private var within = 0
    private var frames = 0
    private var ended = false
    private var lastSatisfiedAt: Long? = null
    private var filteredError: Float? = null

    fun suppressMovement() {
        displayed = Correction.HOLD
        candidate = Correction.HOLD
    }

    fun update(measurement: GuidanceMeasurement?, nowMs: Long): GovernedGuidance {
        if (ended) return GovernedGuidance(Correction.HOLD, failure = "Guidance ended")
        frames++
        if (nowMs - startedAtMs >= policy.timeoutMs) return GovernedGuidance(Correction.HOLD, failure = "timeout")
        if (nowMs - lastAt > 750) {
            satisfiedSince = null
            withinLastFrame = false
            if (lostSince == null) { lostSince = lastAt + 750; losses++ }
        }
        if (lostSince?.let { nowMs - it >= policy.recoveryMs } == true)
            return GovernedGuidance(Correction.RECOVER, failure = "tracking_loss")
        if (measurement == null) {
            if (lostSince == null) { lostSince = nowMs; losses++ }
            progressAt += (nowMs - lastAt).coerceAtLeast(0)
            lastAt = nowMs
            satisfiedSince = null
            withinLastFrame = false
            displayed = Correction.RECOVER
            candidate = Correction.RECOVER
            return GovernedGuidance(Correction.RECOVER,
                failure = if (nowMs - lostSince!! >= policy.recoveryMs) "tracking_loss" else null)
        }
        if (lostSince != null) { progressAt += (nowMs - lastAt).coerceAtLeast(0); lostSince = null }
        if (measurement.correction == Correction.BLOCKED) return GovernedGuidance(Correction.BLOCKED, failure = "movement_blocked")
        lastAt = nowMs
        val filtered = filteredError?.let { .6f * it + .4f * measurement.error } ?: measurement.error
        filteredError = filtered
        if (filtered < bestError - .1f) { bestError = filtered; progressAt = nowMs }
        if (measurement.satisfied) {
            within++
            lastSatisfiedAt = nowMs
            withinLastFrame = true
            if (satisfiedSince == null) satisfiedSince = nowMs
            displayed = Correction.HOLD
            candidate = Correction.HOLD
            return GovernedGuidance(Correction.HOLD, complete = nowMs - satisfiedSince!! >= policy.completionDwellMs)
        }
        if (withinLastFrame) overshoots++
        withinLastFrame = false
        satisfiedSince = null
        if (nowMs - progressAt >= policy.noProgressMs) return GovernedGuidance(Correction.HOLD, failure = "no_progress")
        // A bounded Schmitt band prevents chatter without holding a small error forever.
        val desired = if (displayed == Correction.HOLD && measurement.error < .25f &&
            lastSatisfiedAt?.let { nowMs - it < policy.cooldownMs } == true) Correction.HOLD else measurement.correction
        if (desired != candidate) { candidate = desired; candidateSince = nowMs }
        if (desired != displayed && nowMs - candidateSince >= policy.switchDwellMs && nowMs - switchedAt >= policy.cooldownMs) {
            val old = lastMovement
            if (old != null && old.axis == desired.axis && old.sign * desired.sign < 0) reversals++
            if (reversals > policy.maxReversals) return GovernedGuidance(Correction.HOLD, failure = "reversals")
            displayed = desired
            switchedAt = nowMs
            if (desired.axis != 0) lastMovement = desired
        }
        return GovernedGuidance(displayed)
    }

    fun finish(nowMs: Long, outcome: String): GuidanceMetrics? {
        if (ended) return null
        ended = true
        val elapsed = (nowMs - startedAtMs).coerceAtLeast(0)
        return GuidanceMetrics(elapsed, reversals, overshoots, losses, within, frames, outcome, sessionId,
            elapsed.takeIf { outcome == "completion" })
    }
}
