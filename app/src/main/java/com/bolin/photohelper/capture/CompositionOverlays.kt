package com.bolin.photohelper.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.bolin.photohelper.coach.VerificationTarget
import com.bolin.photohelper.ui.LocalReducedMotion
import com.bolin.photohelper.ui.Mango
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.SoftCream

/** Upright analysis coordinates to the centre-cropped, optionally mirrored preview. */
internal fun compositionPreviewPoint(x: Float, y: Float, sourceWidth: Int, sourceHeight: Int,
    width: Float, height: Float, mirrored: Boolean): Offset {
    val scale = maxOf(width / sourceWidth.coerceAtLeast(1), height / sourceHeight.coerceAtLeast(1))
    return Offset((if (mirrored) 1f - x else x) * sourceWidth * scale - (sourceWidth * scale - width) / 2,
        y * sourceHeight * scale - (sourceHeight * scale - height) / 2)
}

@Composable
fun GuidanceTarget(guidance: ActiveGuidance, modifier: Modifier = Modifier,
    observation: FrameObservation? = null, mirrored: Boolean = false) {
    val targets = (guidance.target as? VerificationTarget.Composition)?.plan?.targets ?: listOf(guidance.target)
    Canvas(modifier.testTag(CaptureTestTags.GUIDANCE).semantics { contentDescription = guidance.instruction }) {
        fun point(x: Float, y: Float) = if (observation == null) Offset(x * size.width, y * size.height)
            else compositionPreviewPoint(x, y, observation.sourceWidth, observation.sourceHeight, size.width, size.height, mirrored)
        for (target in targets) {
            val ranges = when (target) {
                is VerificationTarget.FacePosition -> target.xRange to target.yRange
                is VerificationTarget.GroupPosition -> target.xRange to target.yRange
                else -> null
            }
            if (ranges != null) {
                val a = point(ranges.first.start, ranges.second.start)
                val b = point(ranges.first.endInclusive, ranges.second.endInclusive)
                drawRect(Mango, Offset(minOf(a.x, b.x), a.y), Size(kotlin.math.abs(b.x - a.x), b.y - a.y), style = Stroke(2.dp.toPx()))
            }
            if (target is VerificationTarget.Level) {
                drawLine(Mango, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .75f, size.height * .5f), 3.dp.toPx())
                val roll = observation?.deviceRollDegrees
                if (roll != null && kotlin.math.abs(roll) > 0.5f) {
                    val rad = Math.toRadians(roll.toDouble().coerceIn(-15.0, 15.0))
                    val halfLen = size.width * .25f
                    val cx = size.width * .5f
                    val cy = size.height * .5f
                    val dx = (halfLen * kotlin.math.cos(rad)).toFloat()
                    val dy = (halfLen * kotlin.math.sin(rad)).toFloat()
                    drawLine(SoftCream.copy(alpha = .5f), Offset(cx - dx, cy + dy), Offset(cx + dx, cy - dy), 2.dp.toPx())
                }
            }
        }
        if (!guidance.paused) guidance.members.forEach { face ->
            val a = point(face.left, face.top)
            val b = point(face.right, face.bottom)
            drawRect(Mango.copy(alpha = .7f), Offset(minOf(a.x, b.x), a.y), Size(kotlin.math.abs(b.x - a.x), b.y - a.y), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
fun CompositionControls(state: CaptureUiState, actions: CaptureScreenActions) {
    val active = state.activeGuidance
    val overlays = LocalOverlayColors.current
    val reducedMotion = LocalReducedMotion.current
    val enterMs = if (reducedMotion) 0 else 250
    val exitMs = if (reducedMotion) 0 else 150
    val selection = state.compositionSelection

    val showSelection = selection != null
    val showStop = !showSelection && (state.compositionEnabled || active != null) && state.decision == null
    AnimatedVisibility(
        visible = showSelection,
        enter = fadeIn(tween(enterMs)) + slideInVertically(
            animationSpec = tween(enterMs, easing = LinearOutSlowInEasing),
            initialOffsetY = { it / 3 },
        ),
        exit = fadeOut(tween(exitMs)),
    ) {
        Surface(shape = MaterialTheme.shapes.medium, color = overlays.scrimOpaque) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Who are you framing?", color = overlays.onOverlay)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = actions::onSelectAllCompositionFaces,
                        modifier = Modifier.heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, overlays.onOverlay),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = overlays.onOverlay),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("All") }
                    Button(
                        onClick = actions::onConfirmCompositionSelection,
                        enabled = state.compositionSelectedIndices.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("Use selection") }
                    OutlinedButton(
                        onClick = actions::onCancelCoaching,
                        modifier = Modifier.heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, overlays.onOverlay),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = overlays.onOverlay),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("Cancel") }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showStop,
        enter = fadeIn(tween(enterMs)) + slideInVertically(
            animationSpec = tween(enterMs, easing = LinearOutSlowInEasing),
            initialOffsetY = { it / 3 },
        ),
        exit = fadeOut(tween(exitMs)),
    ) {
        Row(
            modifier = Modifier
                .testTag(CaptureTestTags.RESPONSE_CARD)
                .semantics { isTraversalGroup = true; traversalIndex = 2f },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.coachingPhase == CoachingPhase.GUIDING && active != null &&
                !active.paused && active.correction.isMovement) {
                Button(
                    onClick = actions::onCannotMoveFurther,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = overlays.scrimOpaque,
                        contentColor = overlays.onOverlay,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text("Can't move further") }
            }
            Button(
                onClick = actions::onCancelCoaching,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = overlays.scrimOpaque,
                    contentColor = overlays.onOverlay,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) { Text("Stop") }
        }
    }

}

@Composable
fun CompositionFaceSelection(faces: List<FaceObservation>, selected: Set<Int>, observation: FrameObservation?,
    mirrored: Boolean, onToggle: (Int) -> Unit) {
    if (observation == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        faces.forEachIndexed { index, face ->
            val a = compositionPreviewPoint(face.left, face.top, observation.sourceWidth, observation.sourceHeight, width, height, mirrored)
            val b = compositionPreviewPoint(face.right, face.bottom, observation.sourceWidth, observation.sourceHeight, width, height, mirrored)
            val x = with(density) { minOf(a.x, b.x).toDp() }.coerceIn(0.dp, (maxWidth - 48.dp).coerceAtLeast(0.dp))
            val y = with(density) { a.y.toDp() }.coerceIn(0.dp, (maxHeight - 48.dp).coerceAtLeast(0.dp))
            OutlinedButton(onClick = { onToggle(index) },
                border = BorderStroke(2.dp, if (index in selected) Mango else SoftCream),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftCream),
                modifier = Modifier.absoluteOffset(x, y)
                .width(with(density) { kotlin.math.abs(b.x - a.x).toDp() }.coerceAtLeast(48.dp))
                .height(with(density) { (b.y - a.y).toDp() }.coerceAtLeast(48.dp))
                .semantics { contentDescription = "Person ${index + 1}, ${if (index in selected) "selected" else "not selected"}" }) {
                Text(if (index in selected) "✓ ${index + 1}" else "${index + 1}")
            }
        }
    }
}
