package com.bolin.photohelper.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.coach.VerificationTarget
import com.bolin.photohelper.ui.Mango

@Composable
fun GuidanceTarget(guidance: ActiveGuidance, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.testTag(CaptureTestTags.GUIDANCE),
    ) {
        val description = when (guidance.target) {
            is VerificationTarget.Level -> "Hold the phone level"
            is VerificationTarget.FaceOccupancy -> "Move to adjust distance"
            is VerificationTarget.FacePosition -> "Frame the subject"
            is VerificationTarget.StepBack -> "Step back from the subject"
            else -> guidance.instruction
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description },
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(size.width, size.height) * 0.15f

            drawCircle(
                color = Mango.copy(alpha = 0.7f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
