package com.bolin.photohelper.capture

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp

/**
 * Voice control for the review screen. On the camera screen the Helper Orb owns voice
 * input (long press), so this is the only place a separate mic button survives.
 */
@Composable
fun MicrophoneButton(
    phase: CoachingPhase,
    onMicrophone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val micDescription = when (phase) {
        CoachingPhase.LISTENING -> "Finish voice comment"
        CoachingPhase.INTERPRETING -> "Voice input processing"
        else -> "Describe shot by voice"
    }
    OutlinedButton(
        onClick = onMicrophone,
        enabled = phase !in setOf(CoachingPhase.APPLYING, CoachingPhase.INTERPRETING),
        modifier = modifier
            .size(56.dp)
            .testTag(CaptureTestTags.MICROPHONE)
            .semantics {
                contentDescription = micDescription
                traversalIndex = 4.1f
                stateDescription = when (phase) {
                    CoachingPhase.LISTENING -> "Listening"
                    CoachingPhase.INTERPRETING -> "Processing"
                    CoachingPhase.TRANSIENT_ERROR -> "Error"
                    else -> "Idle"
                }
            },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = if (phase == CoachingPhase.LISTENING) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}
