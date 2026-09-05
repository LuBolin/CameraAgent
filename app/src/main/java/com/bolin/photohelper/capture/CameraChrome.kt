package com.bolin.photohelper.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import com.bolin.photohelper.coach.RecommendationAction
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.Mango
import com.bolin.photohelper.ui.SoftCream
import com.bolin.photohelper.ui.Charcoal
import com.bolin.photohelper.ui.LocalReducedMotion
import com.bolin.photohelper.ui.QuicksandFontFamily
import com.bolin.photohelper.ui.jarvisColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PreviewPane(
    state: CaptureUiState,
    liveObservation: StateFlow<FrameObservation?>,
    preview: @Composable BoxScope.() -> Unit,
    isFrontCamera: Boolean,
    canFlipCamera: Boolean,
    onFlipCamera: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    onSettingsOpen: () -> Unit,
    onHelpOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showTopChrome: Boolean = true,
    iconRotation: Float = 0f,
) {
    BoxWithConstraints(
        modifier = modifier
            .testTag(CaptureTestTags.PREVIEW_CHROME)
            .semantics {
                isTraversalGroup = true
                traversalIndex = 5f
            },
    ) {
        Box(modifier = Modifier.fillMaxSize(), content = preview)

        if (state.cameraPhase == CameraPhase.STARTING) {
            CameraPhaseStatus()
        }

        val guidance = state.activeGuidance
        if (guidance != null) {
            GuidanceTarget(guidance, Modifier.fillMaxSize())
        }

        ObservationLayers(state, isFrontCamera, onFocusTarget)

        if (showTopChrome) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(12.dp)
                    .semantics { traversalIndex = 1f },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                if (canFlipCamera) OverlayIconAction(
                    icon = Icons.Rounded.Cameraswitch,
                    contentDescription = if (isFrontCamera) "Switch to rear camera" else "Switch to selfie camera",
                    onClick = onFlipCamera,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = iconRotation }
                        .testTag(CaptureTestTags.CAMERA_FLIP),
                )
                else Spacer(Modifier.size(56.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OverlayIconAction(
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = "Open the guide",
                        onClick = onHelpOpen,
                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                    )
                    OverlayIconAction(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Open settings menu",
                        onClick = onSettingsOpen,
                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                    )
                }
            }
        }

        if (state.settings.technicalDetail) {
            val obs by liveObservation.collectAsState()
            obs?.let { TechnicalObservation(it) }
        }

        val countdown = state.countdownSecondsRemaining
        if (countdown != null) {
            val overlays = LocalOverlayColors.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlays.scrimLight)
                    .testTag(CaptureTestTags.COUNTDOWN),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$countdown",
                    style = MaterialTheme.typography.displayLarge,
                    color = overlays.onOverlay,
                )
            }
        }
    }
}

private val CHROME_GAP = 10.dp

@Composable
fun FlashChromeAction(flashMode: FlashMode, onFlashModeCycle: () -> Unit, modifier: Modifier = Modifier) {
    val icon = when (flashMode) {
        FlashMode.OFF -> Icons.Rounded.FlashOff
        FlashMode.ON -> Icons.Rounded.FlashOn
        FlashMode.TORCH -> Icons.Rounded.FlashlightOn
    }
    val label = when (flashMode) {
        FlashMode.OFF -> "Flash off"
        FlashMode.ON -> "Flash on"
        FlashMode.TORCH -> "Torch"
    }
    OverlayIconAction(
        icon = icon,
        contentDescription = "Flash: $label. Tap to cycle.",
        onClick = onFlashModeCycle,
        modifier = modifier.testTag(CaptureTestTags.FLASH_MODE),
    )
}

/**
 * Everything drawn on the frame that comes from looking at it: the model's chosen
 * focus cell, the tappable focus marker, and the confirmation reticle after a focus
 * lands. Tapping anywhere in the preview focuses there whenever the camera can.
 */
@Composable
fun ObservationLayers(
    state: CaptureUiState,
    isFrontCamera: Boolean,
    onFocusTarget: (Float, Float) -> Unit,
) {
    val focusRecommendation = state.recommendation?.takeIf {
        it.action is RecommendationAction.TapToFocus || it.action is RecommendationAction.FocusAt
    }
    val canFocus = state.review == null && state.cameraPhase == CameraPhase.READY &&
        state.capabilities.supportsFocusMetering
    if (!canFocus) return

    // The model reports its cell against the sensor frame; the selfie preview is
    // mirrored, so the marker has to be flipped before it is drawn or tapped.
    val modelTarget = (focusRecommendation?.action as? RecommendationAction.FocusAt)
        ?.forPreview(isFrontCamera)
    val visibleIndicator = state.focusIndicator
    if ((state.coachingPhase != CoachingPhase.APPLYING || visibleIndicator != null) &&
        (modelTarget == null || visibleIndicator != null)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag(CaptureTestTags.FOCUS_AREA)
                .semantics { contentDescription = "Tap anywhere in the preview to focus" }
                .pointerInput(onFocusTarget) {
                    detectTapGestures { point ->
                        onFocusTarget(
                            (point.x / size.width).coerceIn(0f, 1f),
                            (point.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                },
        )
    }

    AnimatedContent(
        targetState = visibleIndicator,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn(tween(durationMillis = 120)) togetherWith fadeOut(tween(durationMillis = 300))
        },
        label = "focus indicator",
    ) { indicator ->
        indicator?.let { FocusTarget(it.xFraction, it.yFraction) }
    }
    when {
        visibleIndicator != null -> Unit
        state.coachingPhase == CoachingPhase.APPLYING -> Unit
        modelTarget != null -> FocusTarget(modelTarget.xFraction, modelTarget.yFraction) {
            onFocusTarget(modelTarget.xFraction, modelTarget.yFraction)
        }
        focusRecommendation != null -> FocusTarget(.5f, .42f) { onFocusTarget(.5f, .42f) }
    }
}

private fun RecommendationAction.FocusAt.forPreview(mirrored: Boolean): RecommendationAction.FocusAt =
    if (!mirrored) this else copy(xFraction = 1f - xFraction)

@Composable
fun FocusTarget(xFraction: Float, yFraction: Float, onTap: (() -> Unit)? = null) {
    val reticleScale = remember(xFraction, yFraction) { Animatable(1.35f) }
    LaunchedEffect(reticleScale) {
        reticleScale.animateTo(1f, tween(durationMillis = 180))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val diameter = 72.dp
        val x = (maxWidth * xFraction - diameter / 2).coerceIn(0.dp, (maxWidth - diameter).coerceAtLeast(0.dp))
        val y = (maxHeight * yFraction - diameter / 2).coerceIn(0.dp, (maxHeight - diameter).coerceAtLeast(0.dp))
        val interaction = if (onTap == null) {
            Modifier.semantics { contentDescription = "Focus point" }
        } else {
            Modifier
                .pointerInput(onTap) { detectTapGestures { onTap() } }
                .semantics {
                    contentDescription = "Tap to focus at the marked point"
                    role = Role.Button
                    traversalIndex = 1f
                    onClick(label = "Focus here") {
                        onTap()
                        true
                    }
                }
        }
        Canvas(
            Modifier
                .absoluteOffset(x, y)
                .size(diameter)
                .testTag(CaptureTestTags.FOCUS_TARGET)
                .then(interaction),
        ) {
            val half = 22.dp.toPx() * reticleScale.value
            val segment = 10.dp.toPx()
            val stroke = 3.dp.toPx()
            val left = center.x - half
            val right = center.x + half
            val top = center.y - half
            val bottom = center.y + half
            listOf(
                Offset(left, top) to Offset(left + segment, top),
                Offset(left, top) to Offset(left, top + segment),
                Offset(right, top) to Offset(right - segment, top),
                Offset(right, top) to Offset(right, top + segment),
                Offset(left, bottom) to Offset(left + segment, bottom),
                Offset(left, bottom) to Offset(left, bottom - segment),
                Offset(right, bottom) to Offset(right - segment, bottom),
                Offset(right, bottom) to Offset(right, bottom - segment),
            ).forEach { (start, end) ->
                drawLine(reticleShadow, start, end, stroke + 2.dp.toPx())
                drawLine(Mango, start, end, stroke)
            }
            drawCircle(reticleShadow, radius = 4.dp.toPx())
            drawCircle(Mango, radius = 2.5.dp.toPx())
        }
    }
}

private val reticleShadow = Charcoal.copy(alpha = 0.55f)

/**
 * Branded splash shown while CameraX is binding. A large Orb ring sweeps through
 * the Jarvis gradient weighted toward Mango, with "Photo Helper" below it. Replaces
 * the generic spinner so the user sees the app's identity, not a void.
 */
@Composable
private fun CameraPhaseStatus() {
    val overlays = LocalOverlayColors.current
    val reducedMotion = LocalReducedMotion.current
    val orbSize = 160.dp
    val glowBlur = 20.dp

    val sweep = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "splash_sweep")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "splash_confidence",
        ).value
    }
    val ringColor = jarvisColor(sweep)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(orbSize + 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Glow aura
                Box(
                    modifier = Modifier
                        .size(orbSize)
                        .blur(glowBlur, BlurredEdgeTreatment.Unbounded)
                        .alpha(0.45f)
                        .background(ringColor, CircleShape),
                )
                // Ring
                Canvas(Modifier.size(orbSize)) {
                    drawCircle(
                        color = ringColor,
                        radius = (size.minDimension - 6.dp.toPx()) / 2f,
                        style = Stroke(width = 6.dp.toPx()),
                    )
                }
                // Center dot
                Canvas(Modifier.size(orbSize / 2.5f)) {
                    drawCircle(color = SoftCream)
                }
            }
            Spacer(Modifier.size(24.dp))
            Text(
                "Photo Helper",
                color = SoftCream,
                style = TextStyle(
                    fontFamily = QuicksandFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ScrimLabel(text: String, modifier: Modifier = Modifier) {
    val overlays = LocalOverlayColors.current
    Surface(color = overlays.scrim, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = overlays.onOverlay,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * How much presence a control has over the viewfinder.
 *
 * Chrome drawn on the preview sits on an arbitrary camera scene - a night sky one
 * second, a white wall the next - so it can never take its colours from
 * [MaterialTheme.colorScheme], which is built for surfaces whose background is known
 * and which flips with a theme setting that has nothing to do with what is behind the
 * lens. Every value here comes from `OverlayColors`, charcoal-based in both themes.
 *
 * There is no scrim under the control row: each button carries its own contrast.
 */
enum class OverlayTier {
    /** Talk and Improve: the two controls the app exists for. Heavy fill, accent ring. */
    PRIMARY,

    /** Flip, Help and Menu: always there, rarely the point. Neutral hairline. */
    SECONDARY,
}

/**
 * A frosted glass chrome button: translucent charcoal, a hairline edge, and a mango
 * ring when it takes keyboard or D-pad focus. [tier] decides how loud it is.
 *
 * Compose on this SDK level cannot blur what is drawn behind a composable, so the
 * "glass" is a translucent fill plus the hairline rather than a true backdrop blur.
 */
@Composable
fun OverlayIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gallery thumbnail: when set it replaces [icon] and fills the button. */
    image: ImageBitmap? = null,
    tier: OverlayTier = OverlayTier.SECONDARY,
    enabled: Boolean = true,
    stateDescription: String? = null,
    traversalIndex: Float? = null,
) {
    val overlays = LocalOverlayColors.current
    var focused by remember { mutableStateOf(false) }
    val primary = tier == OverlayTier.PRIMARY
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
                stateDescription?.let { this.stateDescription = it }
                traversalIndex?.let { this.traversalIndex = it }
            },
        color = if (primary) overlays.frostedGlassStrong else overlays.frostedGlass,
        shape = CircleShape,
        // No resting border: the fill alone separates the button from the scene
        // (3.5:1 on a mid-tone, 4.9:1 on a white wall at the secondary weight, 7.0:1
        // at the primary one). The ring returns only on keyboard or D-pad focus,
        // where WCAG 2.4.7 requires a visible indicator.
        border = if (focused) BorderStroke(2.dp, Mango) else null,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
            if (image == null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when {
                        !enabled -> overlays.onOverlayDisabled
                        primary -> overlays.accentOnOverlay
                        else -> overlays.onOverlay
                    },
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * A chip offered as an answer to a question the agent asked, drawn over the viewfinder.
 *
 * Material's `AssistChip` takes its label from `colorScheme.onSurface`, which is
 * charcoal in light theme - on a charcoal overlay card that is roughly 1.2:1. These are
 * answers to a question, so they sit at [OverlayTier.PRIMARY].
 */
@Composable
fun OverlayChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlays = LocalOverlayColors.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics { this.role = Role.Button },
        color = overlays.frostedGlassStrong,
        shape = CircleShape,
        border = if (focused) BorderStroke(2.dp, Mango) else null,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = overlays.onOverlay,
            )
        }
    }
}

@Composable
fun OverlayAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val overlays = LocalOverlayColors.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 56.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                this.role = Role.Button
                traversalIndex = 5f
            },
        color = overlays.frostedGlass,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) Mango else overlays.frostedGlassBorder,
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = overlays.onOverlay,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Landscape home for the primary controls. A frosted strip pinned to the right edge
 * keeps the viewfinder clean.
 */
@Composable
fun ControlStrip(
    state: CaptureUiState,
    isFrontCamera: Boolean,
    canFlipCamera: Boolean,
    confidence: Float,
    onFlipCamera: () -> Unit,
    onSettingsOpen: () -> Unit,
    onHelpOpen: () -> Unit,
    onOrbTap: () -> Unit,
    onAutoEnhance: () -> Unit,
    onMicrophone: () -> Unit,
    onOpenGallery: () -> Unit,
    galleryThumbnail: ImageBitmap? = null,
    modifier: Modifier = Modifier,
) {
    val overlays = LocalOverlayColors.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .testTag(CaptureTestTags.CONTROL_STRIP)
            .semantics { isTraversalGroup = true }
            .background(overlays.scrimOpaque)
            .safeDrawingPadding()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (canFlipCamera) OverlayIconAction(
            icon = Icons.Rounded.Cameraswitch,
            contentDescription = if (isFrontCamera) "Switch to rear camera" else "Switch to selfie camera",
            onClick = onFlipCamera,
        )
        OverlayIconAction(
            icon = Icons.Rounded.PhotoLibrary,
            image = galleryThumbnail,
            contentDescription = "Open gallery",
            onClick = onOpenGallery,
            modifier = Modifier.testTag(CaptureTestTags.GALLERY),
        )
        MicrophoneButton(phase = state.coachingPhase, onMicrophone = onMicrophone)
        HelperOrb(
            state = orbStateFor(state.coachingPhase),
            confidence = confidence,
            enabled = orbEnabled(state),
            onTap = onOrbTap,
            size = 56.dp,
            autoCaptureFlashKey = state.autoCaptureFlashKey,
        )
        AutoEnhanceButton(
            onClick = onAutoEnhance,
            enabled = orbEnabled(state) && orbStateFor(state.coachingPhase) == OrbState.IDLE,
        )
        OverlayIconAction(
            icon = Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = "Open the guide",
            onClick = onHelpOpen,
        )
        OverlayIconAction(
            icon = Icons.Rounded.Settings,
            contentDescription = "Open settings menu",
            onClick = onSettingsOpen,
        )
    }
}

@Composable
fun BoxScope.TechnicalObservation(observation: FrameObservation) {
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .padding(12.dp)
            .semantics {
                isTraversalGroup = true
                traversalIndex = 5f
            },
    ) {
        ScrimLabel(
            "Luma %.2f · clips %.0f%%/%.0f%% · faces %d".format(
                java.util.Locale.US,
                observation.meanLuma,
                observation.highlightClipFraction * 100,
                observation.shadowClipFraction * 100,
                observation.faces.size,
            ),
        )
    }
}

@Composable
fun BoxScope.DefaultPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal),
        contentAlignment = Alignment.Center,
    ) {
        Text("Camera preview", color = SoftCream)
    }
}
