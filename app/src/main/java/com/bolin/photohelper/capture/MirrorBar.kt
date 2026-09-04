package com.bolin.photohelper.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.LocalReducedMotion

/**
 * The only place text instructions appear. A frosted pill floating just above the
 * Orb that carries at most a handful of words; the viewfinder itself stays clean.
 */
@Composable
fun MirrorBar(text: String?, modifier: Modifier = Modifier) {
    val overlays = LocalOverlayColors.current
    val reducedMotion = LocalReducedMotion.current
    val enterMs = if (reducedMotion) 0 else 250
    val exitMs = if (reducedMotion) 0 else 150
    // Hold the last line so the pill fades out with its text intact rather than
    // collapsing to an empty capsule.
    var lastText by remember { mutableStateOf("") }
    if (text != null) lastText = text

    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(tween(enterMs)) + slideInVertically(
            animationSpec = tween(enterMs, easing = LinearOutSlowInEasing),
            initialOffsetY = { it / 3 },
        ),
        exit = fadeOut(tween(exitMs)),
    ) {
        val shown = lastText
        Surface(
            color = overlays.mirrorBar,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .border(1.dp, overlays.mirrorBarBorder, RoundedCornerShape(50))
                .testTag(CaptureTestTags.MIRROR_BAR)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = shown
                    traversalIndex = 3f
                },
        ) {
            Text(
                text = shown,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = overlays.onOverlay,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/**
 * The short line the mirror bar should carry right now, or null when it should be
 * hidden. Ordered by urgency: an error the user must read beats a live transcript,
 * which beats a phase status, which beats the one-time first-use hint.
 */
fun mirrorBarText(state: CaptureUiState): String? {
    state.transientMessage?.let { return it }

    state.activeGuidance?.let { return it.instruction }

    return when (state.coachingPhase) {
        CoachingPhase.LISTENING -> state.comment.ifBlank { "Listening…" }
        CoachingPhase.INTERPRETING -> "Working on it…"
        CoachingPhase.REQUESTING_VISUAL_INTERPRETATION -> "Looking at the scene…"
        CoachingPhase.APPLYING -> "Adjusting the camera…"
        CoachingPhase.VERIFYING -> "Checking the change…"
        CoachingPhase.RECOMMENDATION,
        CoachingPhase.GUIDING,
        CoachingPhase.TRANSIENT_ERROR,
        CoachingPhase.IDLE ->
            if (state.showFirstUseHint) FIRST_USE_HINT else null
    }
}

const val FIRST_USE_HINT = "Tap to shoot · mic to talk"
