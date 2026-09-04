package com.bolin.photohelper.capture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.LocalReducedMotion
import com.bolin.photohelper.ui.Sage
import com.bolin.photohelper.ui.jarvisColor
import kotlinx.coroutines.delay

/** How the Orb can look. Colour is never the only signal - each state has its own glyph too. */
enum class OrbState { IDLE, LISTENING, PROCESSING, DECIDED, ERROR }

/**
 * Maps the coaching phase onto an Orb appearance. WORKING splits into LISTENING
 * (coral, the user is still talking) and PROCESSING (mango, the agent is thinking).
 */
fun orbStateFor(phase: CoachingPhase): OrbState = when (VisibleCoachingState.from(phase)) {
    VisibleCoachingState.IDLE -> OrbState.IDLE
    VisibleCoachingState.WORKING ->
        if (phase == CoachingPhase.LISTENING) OrbState.LISTENING else OrbState.PROCESSING
    VisibleCoachingState.ACTION ->
        if (phase == CoachingPhase.TRANSIENT_ERROR) OrbState.ERROR else OrbState.DECIDED
}

/**
 * Whether the Orb accepts a tap. It stays live while the agent is listening or has
 * decided - those taps finish and confirm - but goes inert mid-thought, where the
 * only sensible escape is the back gesture.
 */
fun orbEnabled(state: CaptureUiState): Boolean = when (orbStateFor(state.coachingPhase)) {
    OrbState.IDLE, OrbState.ERROR -> state.shutterEnabled
    OrbState.PROCESSING -> false
    OrbState.LISTENING, OrbState.DECIDED -> true
}

private const val PULSE_BUDGET = 3
private const val PULSE_MS = 2000

/**
 * The single control that replaces the shutter / mic / enhance bar.
 *
 * A 72dp ring whose colour is the Jarvis gradient sampled at [confidence], with a
 * matching blurred aura behind it. Tap captures (or confirms a decision), long press
 * starts voice input, double tap asks for an automatic enhancement.
 *
 * The glow pulses three times when a new state arrives and then holds still, so the
 * screen is not animating continuously while the user composes a shot.
 */
@Composable
fun HelperOrb(
    state: OrbState,
    confidence: Float,
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    autoCaptureFlashKey: Int = 0,
) {
    val overlays = LocalOverlayColors.current
    val reducedMotion = LocalReducedMotion.current
    // The Orb always sits on a dark ground - the viewfinder in portrait, the scrimmed
    // strip in landscape - so its idle ring follows the overlay palette rather than the
    // app background. Charcoal-on-photo, which the light theme would otherwise give,
    // is close to invisible.
    val idleRing = overlays.onOverlay

    val targetRing = when (state) {
        OrbState.IDLE -> idleRing
        else -> jarvisColor(confidence)
    }
    // The gradient sweep is the progress indicator, so it animates unless the user
    // asked for no motion, in which case colours snap.
    val ringColor by animateColorAsState(
        targetValue = targetRing,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 600),
        label = "orb_ring",
    )

    var glow by remember { mutableFloatStateOf(0f) }
    val glowAlpha by animateFloatAsState(
        targetValue = glow,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else PULSE_MS / 2, easing = LinearEasing),
        label = "orb_glow",
    )
    LaunchedEffect(state, reducedMotion) {
        if (state == OrbState.IDLE) {
            glow = 0f
            return@LaunchedEffect
        }
        if (reducedMotion) {
            glow = 0.55f
            return@LaunchedEffect
        }
        repeat(PULSE_BUDGET) {
            glow = 0.55f
            delay(PULSE_MS / 2L)
            glow = 0.35f
            delay(PULSE_MS / 2L)
        }
        glow = 0.45f
    }

    var flashAlpha by remember { mutableFloatStateOf(0f) }
    val animatedFlash by animateFloatAsState(
        targetValue = flashAlpha,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 300),
        label = "orb_flash",
    )
    var seenFlashKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(autoCaptureFlashKey) {
        if (autoCaptureFlashKey > 0 && autoCaptureFlashKey != seenFlashKey) {
            seenFlashKey = autoCaptureFlashKey
            flashAlpha = 0.9f
            delay(200)
            flashAlpha = 0f
        }
    }

    val description = when (state) {
        OrbState.IDLE -> "Take photo. Hold to auto-enhance."
        OrbState.LISTENING -> "Listening. Tap to finish."
        OrbState.PROCESSING -> "Working on it."
        OrbState.DECIDED -> "Ready. Tap to confirm."
        OrbState.ERROR -> "That did not work. Tap to try again."
    }

    Box(
        modifier = modifier
            .size(size + GLOW_MARGIN * 2)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                traversalIndex = 4f
                stateDescription = when (state) {
                    OrbState.IDLE -> "Idle"
                    OrbState.LISTENING -> "Listening"
                    OrbState.PROCESSING -> "Working"
                    OrbState.DECIDED -> "Decided"
                    OrbState.ERROR -> "Error"
                }
                if (enabled) {
                    onClick(label = "Take photo") { onTap(); true }
                    onLongClick(label = "Auto-enhance") { onLongPress(); true }
                }
            }
            .testTag(CaptureTestTags.HELPER_ORB),
        contentAlignment = Alignment.Center,
    ) {
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .blur(GLOW_BLUR, BlurredEdgeTreatment.Unbounded)
                    .alpha(glowAlpha)
                    .background(ringColor, CircleShape),
            )
        }

        if (animatedFlash > 0f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .blur(GLOW_BLUR * 1.5f, BlurredEdgeTreatment.Unbounded)
                    .alpha(animatedFlash)
                    .background(Sage, CircleShape),
            )
        }

        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { onLongPress() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size)) {
                val stroke = RING_STROKE.toPx()
                drawCircle(
                    color = ringColor.copy(alpha = if (enabled) 1f else 0.4f),
                    radius = (this.size.minDimension - stroke) / 2f,
                    style = Stroke(width = stroke),
                )
            }
            when (state) {
                OrbState.IDLE -> Canvas(Modifier.size(size / 3)) {
                    drawCircle(color = ringColor.copy(alpha = if (enabled) 1f else 0.4f))
                }
                OrbState.LISTENING -> Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = overlays.onOverlay,
                    modifier = Modifier.size(size / 3),
                )
                OrbState.PROCESSING -> CircularProgressIndicator(
                    modifier = Modifier.size(size / 3),
                    color = ringColor,
                    strokeWidth = 2.dp,
                )
                OrbState.DECIDED -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = overlays.onOverlay,
                    modifier = Modifier.size(size / 2.4f),
                )
                OrbState.ERROR -> Icon(
                    imageVector = Icons.Rounded.PriorityHigh,
                    contentDescription = null,
                    tint = overlays.onOverlay,
                    modifier = Modifier.size(size / 2.4f),
                )
            }
        }
    }
}

private val RING_STROKE = 3.5.dp
private val GLOW_BLUR = 14.dp
private val GLOW_MARGIN = 10.dp
