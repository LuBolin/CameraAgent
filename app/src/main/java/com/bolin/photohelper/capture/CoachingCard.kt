package com.bolin.photohelper.capture

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.Recommendation
import com.bolin.photohelper.coach.RecommendationAction
import com.bolin.photohelper.ui.Charcoal
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.Mango

/**
 * The card layer on the camera screen. Nothing is drawn unless the agent genuinely
 * needs a decision from the user - status and instructions belong in the mirror bar,
 * and progress is carried by the Orb's colour.
 */
@Composable
fun DecisionSurface(state: CaptureUiState, actions: CaptureScreenActions, modifier: Modifier = Modifier) {
    // A running self-timer must always be stoppable.
    if (state.countdownSecondsRemaining != null) {
        FrostedCard(modifier) {
            CardActions(
                primaryLabel = null,
                onPrimary = {},
                secondaryLabel = "Cancel",
                onSecondary = actions::onCancelCoaching,
            )
        }
        return
    }

    if (state.activeGuidance != null) {
        FrostedCard(modifier) {
            CardActions(
                primaryLabel = null,
                onPrimary = {},
                secondaryLabel = "Cancel",
                onSecondary = actions::onCancelCoaching,
            )
        }
        return
    }

    when (val decision = state.decision) {
        null -> Unit
        is LocalDecision.Recommend -> {
            val recommendation = decision.recommendation
            val primary = primaryActionLabel(recommendation, applying = state.coachingPhase == CoachingPhase.APPLYING)
            if (primary == null) return
            FrostedCard(modifier) {
                CardHeadline(recommendation.actionText)
                CardActions(
                    primaryLabel = primary,
                    onPrimary = { confirmRecommendation(recommendation, actions) },
                    primaryEnabled = state.coachingPhase != CoachingPhase.APPLYING,
                    secondaryLabel = if (state.resetAvailable) "Reset" else "Dismiss",
                    onSecondary = if (state.resetAvailable) actions::onReset else actions::onDismissDecision,
                )
            }
        }
        is LocalDecision.Clarify -> FrostedCard(modifier) {
            CardHeadline(decision.question)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                decision.chips.forEach { chip ->
                    AssistChip(
                        onClick = { actions.onClarificationSelected(chip) },
                        label = { Text(chip.label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            CardActions(
                primaryLabel = null,
                onPrimary = {},
                secondaryLabel = "Dismiss",
                onSecondary = actions::onDismissDecision,
            )
        }
        is LocalDecision.Advisory -> FrostedCard(modifier) {
            CardHeadline(decision.headline)
            CardActions(
                primaryLabel = if (state.resetAvailable) "Reset" else null,
                onPrimary = actions::onReset,
                secondaryLabel = "Dismiss",
                onSecondary = actions::onDismissDecision,
            )
        }
    }
}

/**
 * One visual for every decision type: the same frosted treatment as the mirror bar,
 * a single headline, and one action row.
 */
@Composable
private fun FrostedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val overlays = LocalOverlayColors.current
    Surface(
        color = overlays.mirrorBar,
        shape = CARD_SHAPE,
        border = BorderStroke(1.dp, overlays.mirrorBarBorder),
        modifier = modifier
            .widthIn(max = 420.dp)
            .testTag(CaptureTestTags.RESPONSE_CARD)
            .semantics {
                isTraversalGroup = true
                traversalIndex = 2f
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun CardHeadline(text: String) {
    val overlays = LocalOverlayColors.current
    Text(
        text,
        color = overlays.onOverlay,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun CardActions(
    primaryLabel: String?,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
) {
    val overlays = LocalOverlayColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primaryLabel != null) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mango,
                    contentColor = Charcoal,
                ),
            ) { Text(primaryLabel) }
        }
        TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(secondaryLabel, color = overlays.onOverlay)
        }
    }
}

private val CARD_SHAPE = RoundedCornerShape(20.dp)

private fun primaryActionLabel(recommendation: Recommendation, applying: Boolean): String? = when {
    applying -> "Applying…"
    else -> when (recommendation.action) {
        is RecommendationAction.ApplySettings -> recommendation.primaryLabel ?: "Apply"
        is RecommendationAction.GuidePosition -> recommendation.primaryLabel ?: "Start"
        is RecommendationAction.FocusAt -> "Focus here"
        RecommendationAction.TapToFocus -> null
    }
}

private fun confirmRecommendation(recommendation: Recommendation, actions: CaptureScreenActions) {
    when (val action = recommendation.action) {
        is RecommendationAction.ApplySettings -> actions.onApplyRecommendation()
        is RecommendationAction.GuidePosition -> actions.onStartGuidance()
        is RecommendationAction.FocusAt -> actions.onFocusTarget(action.xFraction, action.yFraction)
        RecommendationAction.TapToFocus -> actions.onDismissDecision()
    }
}

// ── Review screen ──────────────────────────────────────────────────
//
// Review still shows the fuller explanation - the user is looking at a saved photo,
// not composing, so there is room for the reasoning the camera screen suppresses.

@Composable
fun CoachingControls(
    state: CaptureUiState,
    reviewMode: Boolean = false,
    onApplyRecommendation: () -> Unit,
    onStartGuidance: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    onDismissDecision: () -> Unit,
    onDismissTransientMessage: () -> Unit,
    onClarificationSelected: (ClarificationChip) -> Unit,
    onReset: () -> Unit,
) {
    val applying = state.coachingPhase == CoachingPhase.APPLYING

    if (state.coachingPhase == CoachingPhase.REQUESTING_VISUAL_INTERPRETATION) {
        VisualLoading()
    }

    CoachingProgress(state.coachingPhase)

    state.transientMessage?.let { msg ->
        TransientMessage(
            message = msg,
            isError = state.coachingPhase == CoachingPhase.TRANSIENT_ERROR,
            onDismiss = onDismissTransientMessage,
        )
    }

    val decision = state.decision
    if (decision != null) {
        ReviewDecisionCard(
            decision = decision,
            applying = applying,
            reviewMode = reviewMode,
            resetAvailable = state.resetAvailable,
            onApplyRecommendation = onApplyRecommendation,
            onStartGuidance = onStartGuidance,
            onFocusTarget = onFocusTarget,
            onDismiss = onDismissDecision,
            onClarificationSelected = onClarificationSelected,
            onReset = onReset,
        )
    }

    if (state.resetAvailable && decision == null && !applying && state.transientMessage == null) {
        ResetCard(onReset = onReset)
    }
}

@Composable
fun VisualLoading() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CaptureTestTags.VISUAL_LOADING),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("Looking at the scene…")
        }
    }
}

@Composable
private fun ReviewDecisionCard(
    decision: LocalDecision,
    applying: Boolean,
    reviewMode: Boolean,
    resetAvailable: Boolean,
    onApplyRecommendation: () -> Unit,
    onStartGuidance: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
    onClarificationSelected: (ClarificationChip) -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CaptureTestTags.RESPONSE_CARD)
            .semantics {
                isTraversalGroup = true
                traversalIndex = 2f
            },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
                .semantics { isTraversalGroup = false }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (decision) {
                is LocalDecision.Recommend -> RecommendationContent(
                    recommendation = decision.recommendation,
                    applying = applying,
                    reviewMode = reviewMode,
                    resetAvailable = resetAvailable,
                    onApply = onApplyRecommendation,
                    onStartGuidance = onStartGuidance,
                    onFocusTarget = onFocusTarget,
                    onDismiss = onDismiss,
                    onReset = onReset,
                )

                is LocalDecision.Clarify -> {
                    Text(
                        decision.question,
                        modifier = Modifier.semantics { traversalIndex = 0f },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        decision.chips.forEach { chip ->
                            AssistChip(
                                onClick = { onClarificationSelected(chip) },
                                label = { Text(chip.label) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics { traversalIndex = 1f },
                            )
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { traversalIndex = 2f },
                    ) { Text("Dismiss") }
                }

                is LocalDecision.Advisory -> {
                    Text(
                        decision.headline,
                        modifier = Modifier.semantics { traversalIndex = 0f },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(decision.detail, modifier = Modifier.semantics { traversalIndex = 0f })
                    if (decision.fromVisualHint) Provenance()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        if (resetAvailable) {
                            Button(
                                onClick = onReset,
                                modifier = Modifier.heightIn(min = 48.dp).semantics { traversalIndex = 1f },
                            ) { Text("Reset") }
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { traversalIndex = 2f },
                        ) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationContent(
    recommendation: Recommendation,
    applying: Boolean,
    reviewMode: Boolean,
    resetAvailable: Boolean,
    onApply: () -> Unit,
    onStartGuidance: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    val action = recommendation.action
    val context = LocalContext.current
    val touchExploration = context.getSystemService(AccessibilityManager::class.java)
        ?.isTouchExplorationEnabled == true
    val walkingBlocked = action is RecommendationAction.GuidePosition &&
        action.requiresWalkingWarning && touchExploration

    Text(
        recommendation.headline,
        modifier = Modifier.semantics { traversalIndex = 0f },
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        if (walkingBlocked) {
            "Keep the phone stationary. Ask a nearby person to help change the distance, then reframe."
        } else {
            recommendation.actionText
        },
        modifier = Modifier.semantics { traversalIndex = 0f },
        style = MaterialTheme.typography.bodyLarge,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val primaryLabel = when {
            applying -> "Applying…"
            reviewMode && action is RecommendationAction.ApplySettings && action.changes.size == 1 -> "Apply for retake"
            reviewMode && action is RecommendationAction.ApplySettings -> recommendation.primaryLabel ?: "Apply all for retake"
            action is RecommendationAction.ApplySettings -> recommendation.primaryLabel ?: "Apply"
            action is RecommendationAction.GuidePosition -> recommendation.primaryLabel ?: "Start guidance"
            action is RecommendationAction.TapToFocus -> null
            action is RecommendationAction.FocusAt -> "Focus here"
            else -> null
        }
        if (primaryLabel != null && !walkingBlocked) {
            Button(
                onClick = when (action) {
                    is RecommendationAction.ApplySettings -> onApply
                    is RecommendationAction.GuidePosition -> onStartGuidance
                    RecommendationAction.TapToFocus -> onDismiss
                    is RecommendationAction.FocusAt -> {
                        { onFocusTarget(action.xFraction, action.yFraction) }
                    }
                },
                enabled = !applying,
                modifier = Modifier.heightIn(min = 48.dp).semantics { traversalIndex = 1f },
            ) { Text(primaryLabel) }
        }
        Spacer(Modifier.weight(1f))
        if (resetAvailable) {
            TextButton(
                onClick = onReset,
                modifier = Modifier.heightIn(min = 48.dp).semantics { traversalIndex = 2f },
            ) { Text("Reset") }
        } else {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).semantics { traversalIndex = 2f },
            ) { Text("Dismiss") }
        }
    }

    if (!walkingBlocked) {
        Text(recommendation.consequence, modifier = Modifier.semantics { traversalIndex = 0f })
    }
    if (action is RecommendationAction.GuidePosition && action.requiresWalkingWarning && !walkingBlocked) {
        Text(
            "Photo Helper cannot see obstacles. Move only if you can independently verify the path.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (walkingBlocked) {
        Text(
            "Walking guidance is unavailable while touch exploration is on.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (recommendation.fromVisualHint || recommendation.controlIntents.isNotEmpty()) Provenance()
}

/** Where an off-device model was involved, say so. */
@Composable
private fun Provenance() {
    Text(
        "AI-interpreted by Qwen via Alibaba Cloud; camera controls checked on device",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ResetCard(onReset: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reset") }
        }
    }
}

@Composable
fun TransientMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val isAiFallback = message.startsWith("AI interpretation")
    Surface(
        onClick = onDismiss,
        color = when {
            isError -> MaterialTheme.colorScheme.error
            isAiFallback -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.tertiaryContainer
        },
        contentColor = when {
            isError -> MaterialTheme.colorScheme.onError
            isAiFallback -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isError -> {} // no icon for warnings
                isAiFallback -> Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                else -> Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                text = when {
                    isError -> "Warning: $message"
                    else -> message
                },
            )
        }
    }
}

@Composable
fun CoachingProgress(phase: CoachingPhase) {
    val copy = when (phase) {
        CoachingPhase.LISTENING -> "Listening…"
        CoachingPhase.INTERPRETING -> "Checking the shot…"
        CoachingPhase.APPLYING -> "Applying…"
        CoachingPhase.VERIFYING -> "Checking the change…"
        else -> null
    } ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(copy, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun TranscriptOverlay(comment: String, phase: CoachingPhase) {
    if (comment.isBlank() && phase != CoachingPhase.LISTENING) return
    val overlays = LocalOverlayColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .widthIn(max = 480.dp),
        color = overlays.scrim,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = comment.ifBlank { "Speak now…" },
            color = overlays.onOverlay,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(max = 52.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag(CaptureTestTags.COMMENT)
                .semantics {
                    contentDescription = "Voice transcript"
                    traversalIndex = 3f
                },
        )
    }
}
