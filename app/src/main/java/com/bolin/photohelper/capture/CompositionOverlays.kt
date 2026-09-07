package com.bolin.photohelper.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.bolin.photohelper.coach.VerificationTarget
import com.bolin.photohelper.ui.Mango
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
    if (state.coachingPhase == CoachingPhase.GUIDING && active != null && !active.paused && active.correction.isMovement) {
        OutlinedButton(onClick = actions::onCannotMoveFurther,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftCream),
            border = BorderStroke(1.dp, SoftCream)) { Text("Can't move further") }
    }
    val selection = state.compositionSelection
    if (selection != null) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text("Who are you framing?")
                selection.forEachIndexed { index, face ->
                    val x = if (state.previewMirrored) 1f - face.centerX else face.centerX
                    val location = when { x < .33f -> "left"; x > .67f -> "right"; else -> "centre" }
                    FilterChip(selected = index in state.compositionSelectedIndices,
                        onClick = { actions.onToggleCompositionFace(index) },
                        label = { Text("Person ${index + 1}, $location") })
                }
                Row {
                    TextButton(onClick = actions::onSelectAllCompositionFaces) { Text("Everyone") }
                    TextButton(onClick = actions::onConfirmCompositionSelection, enabled = state.compositionSelectedIndices.isNotEmpty()) { Text("Use selection") }
                    TextButton(onClick = actions::onCancelCoaching) { Text("Cancel") }
                }
            }
        }
    } else if (state.compositionEnabled) {
        Row {
            TextButton(onClick = actions::onChangeCompositionSelection) { Text("Change people") }
            TextButton(onClick = actions::onCancelCoaching) { Text("Stop framing") }
        }
    } else if (state.coachingPhase == CoachingPhase.IDLE && state.review == null) {
        TextButton(onClick = actions::onComposition, enabled = state.shutterEnabled) { Text("Help me frame") }
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
