package com.bolin.photohelper.capture

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.LocalReducedMotion
import com.bolin.photohelper.ui.SoftCream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CaptureReview(
    state: CaptureUiState,
    capture: SavedCapture,
    onMicrophone: () -> Unit,
    onApplyRecommendation: () -> Unit,
    onStartGuidance: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    onDismissDecision: () -> Unit,
    onDismissTransientMessage: () -> Unit,
    onClarificationSelected: (ClarificationChip) -> Unit,
    onReset: () -> Unit,
    onRetake: () -> Unit,
    onDone: () -> Unit,
) {
    val applying = state.coachingPhase == CoachingPhase.APPLYING
    val overlays = LocalOverlayColors.current
    val reducedMotion = LocalReducedMotion.current

    val photoScale = remember { Animatable(1.02f) }
    LaunchedEffect(Unit) {
        if (reducedMotion) {
            photoScale.snapTo(1f)
        } else {
            photoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { controlsVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(CaptureTestTags.REVIEW),
    ) {
        // Photo: full bleed, fills the whole screen
        SavedCaptureImage(
            capture,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = photoScale.value
                    scaleY = photoScale.value
                },
        )

        // Top scrim gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        // Bottom scrim gradient
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.30f),
                    ),
                ),
        )

        // Top: "Captured" pill
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            ReviewPill(
                text = "Captured",
                modifier = Modifier.semantics {
                    traversalIndex = 1f
                    contentDescription = "Photo captured"
                },
            )
        }

        // Bottom: coaching + "Camera" pill
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Coaching controls (if active)
            val hasCoaching = state.decision != null ||
                state.transientMessage != null ||
                state.activeGuidance != null ||
                state.coachingPhase != CoachingPhase.IDLE

            if (hasCoaching) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        initialOffsetY = { it },
                    ) + fadeIn(),
                ) {
                    Surface(
                        color = overlays.scrimHeavy,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .testTag(CaptureTestTags.REVIEW_CONTROLS)
                            .semantics { isTraversalGroup = false },
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CoachingControls(
                                state = state,
                                reviewMode = true,
                                onApplyRecommendation = onApplyRecommendation,
                                onStartGuidance = onStartGuidance,
                                onFocusTarget = onFocusTarget,
                                onDismissDecision = onDismissDecision,
                                onDismissTransientMessage = onDismissTransientMessage,
                                onClarificationSelected = onClarificationSelected,
                                onReset = onReset,
                            )
                            TranscriptOverlay(state.comment, state.coachingPhase)
                        }
                    }
                }
            }

            // Mic button when coaching is active
            if (hasCoaching) {
                MicrophoneButton(state.coachingPhase, onMicrophone)
            }

            // "Camera" pill → return to camera
            AnimatedVisibility(
                visible = controlsVisible,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialOffsetY = { it / 2 },
                ) + fadeIn(),
            ) {
                ReviewPill(
                    text = "Camera",
                    onClick = onRetake,
                    enabled = !applying,
                    modifier = Modifier.semantics {
                        traversalIndex = 5f
                        role = Role.Button
                        contentDescription = "Return to camera"
                    },
                )
            }
        }
    }
}

/**
 * A frosted pill in the MirrorBar visual language, reused on the review screen
 * for "Captured" (status) and "Camera" (action).
 */
@Composable
private fun ReviewPill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val overlays = LocalOverlayColors.current
    Surface(
        color = overlays.mirrorBar,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .widthIn(min = 120.dp)
            .border(1.dp, overlays.mirrorBarBorder, RoundedCornerShape(50))
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = if (enabled) overlays.onOverlay else overlays.onOverlayDisabled,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SavedCaptureImage(capture: SavedCapture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val displayLongEdge = with(context.resources.displayMetrics) {
        maxOf(widthPixels, heightPixels).coerceIn(1, 1440)
    }
    val bitmapResult by produceState<Result<androidx.compose.ui.graphics.ImageBitmap>?>(
        null,
        capture.uri,
        displayLongEdge,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(capture.uri)
                val source = if (uri.scheme.isNullOrBlank()) {
                    ImageDecoder.createSource(File(capture.uri))
                } else {
                    ImageDecoder.createSource(context.contentResolver, uri)
                }
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val sourceLongEdge = maxOf(width, height)
                    if (sourceLongEdge > displayLongEdge) {
                        decoder.setTargetSize(
                            maxOf(1, (width.toLong() * displayLongEdge / sourceLongEdge).toInt()),
                            maxOf(1, (height.toLong() * displayLongEdge / sourceLongEdge).toInt()),
                        )
                    }
                }.asImageBitmap()
            }
        }
    }
    val bitmap = bitmapResult?.getOrNull()
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Captured photo",
            modifier = modifier.semantics { traversalIndex = 6f },
            contentScale = ContentScale.Crop,
        )
    } else {
        val loading = bitmapResult == null
        Box(
            modifier = modifier.semantics {
                contentDescription = if (loading) "Loading captured photo" else "Captured photo unavailable"
                traversalIndex = 6f
            },
            contentAlignment = Alignment.Center,
        ) {
            if (loading) CircularProgressIndicator(color = SoftCream) else Text("Captured photo unavailable", color = SoftCream)
        }
    }
}
