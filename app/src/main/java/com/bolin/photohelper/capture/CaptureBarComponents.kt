package com.bolin.photohelper.capture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Voice control. On the camera screen the Helper Orb owns the shutter, so this is the
 * separate way in to describing a shot; on the review screen it is the only one.
 *
 * Drawn at [OverlayTier.PRIMARY]. It sits over the live preview or a captured photo,
 * so it takes its colours from `OverlayColors` rather than the theme's colour scheme -
 * see [OverlayTier] for why anything else disappears against a bright scene.
 */
@Composable
fun MicrophoneButton(
    phase: CoachingPhase,
    onMicrophone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayIconAction(
        icon = if (phase == CoachingPhase.LISTENING) Icons.Rounded.Stop else Icons.Rounded.Mic,
        contentDescription = when (phase) {
            CoachingPhase.LISTENING -> "Finish voice comment"
            CoachingPhase.INTERPRETING -> "Voice input processing"
            else -> "Describe shot by voice"
        },
        onClick = onMicrophone,
        modifier = modifier.testTag(CaptureTestTags.MICROPHONE),
        tier = OverlayTier.PRIMARY,
        enabled = phase !in setOf(CoachingPhase.APPLYING, CoachingPhase.INTERPRETING),
        stateDescription = when (phase) {
            CoachingPhase.LISTENING -> "Listening"
            CoachingPhase.INTERPRETING -> "Processing"
            CoachingPhase.TRANSIENT_ERROR -> "Error"
            else -> "Idle"
        },
        traversalIndex = 4.1f,
    )
}

/**
 * One-tap auto adjustment. This was a long-press on the Orb, which nobody discovered;
 * it is its own button at [OverlayTier.PRIMARY] because it is the other half of what
 * the app is for.
 */
@Composable
fun AutoEnhanceButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OverlayIconAction(
        icon = Icons.Rounded.AutoAwesome,
        contentDescription = "Improve this photo automatically",
        onClick = onClick,
        modifier = modifier,
        tier = OverlayTier.PRIMARY,
        enabled = enabled,
        traversalIndex = 4.2f,
    )
}
