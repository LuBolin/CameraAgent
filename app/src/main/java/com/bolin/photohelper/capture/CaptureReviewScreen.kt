package com.bolin.photohelper.capture

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.ui.LocalOverlayColors
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
    // The capture is passed in rather than re-read from state: AnimatedContent keeps the
    // outgoing slot composing after the caller's null-check, so re-deriving it here could
    // see a state the guard never approved.
    val applying = state.coachingPhase == CoachingPhase.APPLYING
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalOverlayColors.current.scrimOpaque)
            .testTag(CaptureTestTags.REVIEW),
    ) {
        SavedCaptureImage(capture, Modifier.fillMaxSize())
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(12.dp)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 5f
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScrimLabel("CAPTURED")
            OverlayAction("Done", onDone, enabled = !applying)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp)
                    .testTag(CaptureTestTags.REVIEW_CONTROLS)
                    .semantics { isTraversalGroup = false },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).safeDrawingPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Original remains saved", fontWeight = FontWeight.SemiBold)
                    Column(
                        Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .semantics { isTraversalGroup = false },
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
                    }
                    TranscriptOverlay(state.comment, state.coachingPhase)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                isTraversalGroup = true
                                traversalIndex = 4f
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        MicrophoneButton(state.coachingPhase, onMicrophone)
                        OutlinedButton(
                            onClick = onRetake,
                            enabled = !applying,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Retake")
                        }
                        TextButton(
                            onClick = onDone,
                            enabled = !applying,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.resetAvailable && state.decision == null && !applying,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .offset(y = (-28).dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onReset,
                    modifier = Modifier
                        .testTag(CaptureTestTags.RESET)
                        .semantics { traversalIndex = 3f },
                    icon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
                    text = { Text("Reset") },
                )
            }
        }
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
            contentScale = ContentScale.Fit,
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
            if (loading) CircularProgressIndicator() else Text("Captured photo unavailable", color = LocalOverlayColors.current.onOverlay)
        }
    }
}
