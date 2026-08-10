package com.bolin.photohelper.capture

import android.content.res.Configuration
import android.graphics.ImageDecoder
import android.net.Uri
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.Recommendation
import com.bolin.photohelper.coach.RecommendationAction
import com.bolin.photohelper.coach.VerificationTarget
import com.bolin.photohelper.visual.MAX_API_KEY_CHARACTERS
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

object CaptureTestTags {
    const val ROOT = "capture_root"
    const val PREVIEW = "camera_preview"
    const val RESPONSE_CARD = "response_card"
    const val COMMENT = "comment_input"
    const val MICROPHONE = "microphone"
    const val SHUTTER = "shutter"
    const val GUIDANCE = "guidance"
    const val VISUAL_LOADING = "visual_loading"
    const val REVIEW = "capture_review"
    const val SETTINGS = "settings_sheet"
    const val FOCUS_AREA = "focus_area"
    const val FOCUS_TARGET = "focus_target"
    const val FOCUS_CELL = "focus_cell"
    const val FOCUS_CARD = "focus_recommendation_card"
    const val COUNTDOWN = "capture_countdown"
    const val PREVIEW_CHROME = "preview_chrome"
    const val CAMERA_FLIP = "camera_flip"
}

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    modifier: Modifier = Modifier,
    liveObservation: StateFlow<FrameObservation?>? = null,
    apiKeyInput: String = "",
    preview: @Composable BoxScope.() -> Unit = { DefaultPreview() },
    isFrontCamera: Boolean = false,
    canFlipCamera: Boolean = false,
    onFlipCamera: () -> Unit = {},
    onOnboardingContinue: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onRequestCameraPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onRetryCamera: () -> Unit = {},
    onSettingsOpen: () -> Unit = {},
    onSettingsDismiss: () -> Unit = {},
    onCommentChange: (String) -> Unit = {},
    onSubmitComment: () -> Unit = {},
    onMicrophone: () -> Unit = {},
    onShutter: () -> Unit = {},
    onApplyRecommendation: () -> Unit = {},
    onStartGuidance: () -> Unit = {},
    onFocusTarget: (Float, Float) -> Unit = { _, _ -> },
    onDismissDecision: () -> Unit = {},
    onClarificationSelected: (ClarificationChip) -> Unit = {},
    onCancelCoaching: () -> Unit = {},
    onReset: () -> Unit = {},
    onRetake: () -> Unit = {},
    onDoneReview: () -> Unit = {},
    onSpokenGuidanceChanged: (Boolean) -> Unit = {},
    onHapticsChanged: (Boolean) -> Unit = {},
    onTechnicalDetailChanged: (Boolean) -> Unit = {},
    onVisualAiEnabledChanged: (Boolean) -> Unit = {},
    onApiKeyChanged: (String) -> Unit = {},
    onTestKey: () -> Unit = {},
    onClearKey: () -> Unit = {},
    onOpenVisualAiPolicy: () -> Unit = {},
    onOpenMlKitPolicy: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(CaptureTestTags.ROOT)
            .semantics { isTraversalGroup = true },
        color = MaterialTheme.colorScheme.surface,
    ) {
        when {
            state.onboardingStep < 2 -> Onboarding(
                step = state.onboardingStep,
                settings = state.settings,
                apiKeyInput = apiKeyInput,
                onContinue = onOnboardingContinue,
                onOpenCamera = onOpenCamera,
                onApiKeyChanged = onApiKeyChanged,
                onTestKey = onTestKey,
                onClearKey = onClearKey,
                onOpenVisualAiPolicy = onOpenVisualAiPolicy,
            )

            state.cameraPermission != PermissionState.GRANTED -> CameraPermission(
                permission = state.cameraPermission,
                onRequest = onRequestCameraPermission,
                onOpenSettings = onOpenAppSettings,
            )

            state.cameraPhase == CameraPhase.BLOCKED -> CameraUnavailable(onRetryCamera)

            else -> CaptureContent(
                state = state,
                liveObservation = liveObservation,
                preview = preview,
                isFrontCamera = isFrontCamera,
                canFlipCamera = canFlipCamera,
                onFlipCamera = onFlipCamera,
                onSettingsOpen = onSettingsOpen,
                onCommentChange = onCommentChange,
                onSubmitComment = onSubmitComment,
                onMicrophone = onMicrophone,
                onShutter = onShutter,
                onApplyRecommendation = onApplyRecommendation,
                onStartGuidance = onStartGuidance,
                onFocusTarget = onFocusTarget,
                onDismissDecision = onDismissDecision,
                onClarificationSelected = onClarificationSelected,
                onCancelCoaching = onCancelCoaching,
                onReset = onReset,
                onRetake = onRetake,
                onDoneReview = onDoneReview,
            )
        }

        if (state.settingsOpen && state.onboardingStep >= 2) {
            SettingsSheet(
                state = state,
                apiKeyInput = apiKeyInput,
                onDismiss = onSettingsDismiss,
                onSpokenGuidanceChanged = onSpokenGuidanceChanged,
                onHapticsChanged = onHapticsChanged,
                onTechnicalDetailChanged = onTechnicalDetailChanged,
                onVisualAiEnabledChanged = onVisualAiEnabledChanged,
                onApiKeyChanged = onApiKeyChanged,
                onTestKey = onTestKey,
                onClearKey = onClearKey,
                onEnableMicrophone = onOpenAppSettings,
                onOpenVisualAiPolicy = onOpenVisualAiPolicy,
                onOpenMlKitPolicy = onOpenMlKitPolicy,
            )
        }
    }
}

@Composable
private fun Onboarding(
    step: Int,
    settings: SettingsUiState,
    apiKeyInput: String,
    onContinue: () -> Unit,
    onOpenCamera: () -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onTestKey: () -> Unit,
    onClearKey: () -> Unit,
    onOpenVisualAiPolicy: () -> Unit,
) {
    val secondStep = step == 1
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = if (secondStep) "Step 2 of 2" else "Step 1 of 2",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = if (secondStep) "Connect Qwen. You stay in control." else "Tell the camera what looks wrong.",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (secondStep) {
                    "Photo Helper is designed to use an image-capable LLM. Its prompts and strict response contracts " +
                        "are tuned for Qwen3.7 Flash; other models may produce different results and are not supported " +
                        "by this build. Without Qwen, Photo Helper uses a limited local wording fallback. Nothing changes " +
                        "until you tap Apply, a focus target, or Start guidance."
                } else {
                    "Photo Helper can adjust supported settings or guide your position."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (secondStep) {
                Text("Qwen3.7 Flash (2026-07-15) · Alibaba Cloud Model Studio, China (Beijing)")
                QwenKeySetup(
                    settings = settings,
                    apiKeyInput = apiKeyInput,
                    onApiKeyChanged = onApiKeyChanged,
                    onTestKey = onTestKey,
                    onClearKey = onClearKey,
                    onOpenVisualAiPolicy = onOpenVisualAiPolicy,
                )
            }
        }
        Button(
            onClick = if (secondStep) onOpenCamera else onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(if (secondStep) "Open camera" else "Continue")
        }
    }
}

@Composable
private fun CameraPermission(
    permission: PermissionState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val denied = permission == PermissionState.DENIED
    RecoverySurface(
        headline = if (denied) "Camera access is off" else "Camera access is required",
        detail = if (denied) {
            "Photo Helper needs camera access to show the preview and save photos. Turn it on in Android settings."
        } else {
            "Allow camera access to show the preview and save photos."
        },
        action = if (denied) "Open settings" else "Allow camera",
        onAction = if (denied) onOpenSettings else onRequest,
    )
}

@Composable
private fun CameraUnavailable(onRetry: () -> Unit) {
    RecoverySurface(
        headline = "Camera unavailable",
        detail = "Photo Helper could not start the camera.",
        action = "Retry",
        onAction = onRetry,
    )
}

@Composable
private fun RecoverySurface(
    headline: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(12.dp))
        Text(detail, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(24.dp))
        Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
private fun CaptureContent(
    state: CaptureUiState,
    liveObservation: StateFlow<FrameObservation?>?,
    preview: @Composable BoxScope.() -> Unit,
    isFrontCamera: Boolean,
    canFlipCamera: Boolean,
    onFlipCamera: () -> Unit,
    onSettingsOpen: () -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onMicrophone: () -> Unit,
    onShutter: () -> Unit,
    onApplyRecommendation: () -> Unit,
    onStartGuidance: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    onDismissDecision: () -> Unit,
    onClarificationSelected: (ClarificationChip) -> Unit,
    onCancelCoaching: () -> Unit,
    onReset: () -> Unit,
    onRetake: () -> Unit,
    onDoneReview: () -> Unit,
) {
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    val focusRecommendation = state.recommendation?.takeIf {
        state.review == null && it.action is RecommendationAction.FocusAt
    }
    val cancellableWork = state.activeGuidance != null || state.coachingPhase in setOf(
        CoachingPhase.GUIDING,
        CoachingPhase.LISTENING,
        CoachingPhase.REQUESTING_VISUAL_INTERPRETATION,
    )
    BackHandler(enabled = cancellableWork, onBack = onCancelCoaching)

    val controls = @Composable {
        CoachingControls(
            state = state,
            reviewMode = false,
            onCommentChange = onCommentChange,
            onSubmitComment = onSubmitComment,
            onMicrophone = onMicrophone,
            onApplyRecommendation = onApplyRecommendation,
            onStartGuidance = onStartGuidance,
            onFocusTarget = onFocusTarget,
            onDismissDecision = onDismissDecision,
            onClarificationSelected = onClarificationSelected,
            onCancelCoaching = onCancelCoaching,
            onReset = onReset,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (state.review != null) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Box(Modifier.fillMaxSize()) {
                    PreviewPane(
                        state,
                        liveObservation,
                        preview,
                        isFrontCamera,
                        canFlipCamera,
                        onFlipCamera,
                        onSettingsOpen,
                        { guideOpen = true },
                        onCancelCoaching,
                        onFocusTarget,
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, bottom = 16.dp)
                            .widthIn(max = 420.dp)
                            .fillMaxHeight(0.82f),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        controls()
                    }
                    Shutter(
                        enabled = state.shutterEnabled,
                        onClick = onShutter,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .navigationBarsPadding()
                            .padding(20.dp),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    PreviewPane(
                        state,
                        liveObservation,
                        preview,
                        isFrontCamera,
                        canFlipCamera,
                        onFlipCamera,
                        onSettingsOpen,
                        { guideOpen = true },
                        onCancelCoaching,
                        onFocusTarget,
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .navigationBarsPadding()
                            .semantics { isTraversalGroup = false },
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .semantics { isTraversalGroup = false },
                            ) {
                                controls()
                            }
                            Spacer(Modifier.size(8.dp))
                            Shutter(
                                enabled = state.shutterEnabled,
                                onClick = onShutter,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
            }

            focusRecommendation?.let { recommendation ->
                val target = (recommendation.action as RecommendationAction.FocusAt)
                    .forPreview(isFrontCamera)
                val showAtTop = target.yFraction >= .4f
                FocusRecommendationCard(
                    recommendation = recommendation,
                    applying = state.coachingPhase == CoachingPhase.APPLYING,
                    onFocus = { onFocusTarget(target.xFraction, target.yFraction) },
                    onDismiss = onDismissDecision,
                    modifier = Modifier
                        .align(if (showAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(
                            start = 12.dp,
                            top = if (showAtTop) 72.dp else 0.dp,
                            end = 12.dp,
                            bottom = if (showAtTop) 0.dp else 232.dp,
                        )
                        .widthIn(max = 420.dp),
                )
            }
        }

        if (state.review != null) {
            CaptureReview(
                state = state,
                onCommentChange = onCommentChange,
                onSubmitComment = onSubmitComment,
                onMicrophone = onMicrophone,
                onApplyRecommendation = onApplyRecommendation,
                onStartGuidance = onStartGuidance,
                onFocusTarget = onFocusTarget,
                onDismissDecision = onDismissDecision,
                onClarificationSelected = onClarificationSelected,
                onCancelCoaching = onCancelCoaching,
                onReset = onReset,
                onRetake = onRetake,
                onDone = onDoneReview,
            )
        }

        if (guideOpen) GuideSheet(state = state, onDismiss = { guideOpen = false })
    }
}

@Composable
private fun PreviewPane(
    state: CaptureUiState,
    liveObservation: StateFlow<FrameObservation?>?,
    preview: @Composable BoxScope.() -> Unit,
    isFrontCamera: Boolean,
    canFlipCamera: Boolean,
    onFlipCamera: () -> Unit,
    onSettingsOpen: () -> Unit,
    onGuideOpen: () -> Unit,
    onCancelCoaching: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(CaptureTestTags.PREVIEW),
    ) {
        preview()
        ObservationLayers(state, liveObservation, isFrontCamera, onFocusTarget)
        state.activeGuidance?.let { GuidanceTarget(it.target) }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(CaptureTestTags.PREVIEW_CHROME)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 5f
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScrimLabel(
                when {
                    state.retakeSettingsActive -> "LIVE · Retake settings active"
                    isFrontCamera -> "LIVE · SELFIE"
                    else -> "LIVE"
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayAction(
                    text = "↻",
                    onClick = onFlipCamera,
                    modifier = Modifier.testTag(CaptureTestTags.CAMERA_FLIP),
                    enabled = canFlipCamera && state.shutterEnabled,
                    contentDescription = if (isFrontCamera) {
                        "Switch to rear camera"
                    } else {
                        "Switch to front camera"
                    },
                )
                OverlayAction(
                    text = "?",
                    onClick = onGuideOpen,
                    contentDescription = "Open Photo Helper guide",
                )
                OverlayAction(
                    text = "Settings",
                    onClick = onSettingsOpen,
                    contentDescription = "Open settings",
                )
            }
        }

        CameraPhaseStatus(state.cameraPhase)

        state.activeGuidance?.let { guidance ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .testTag(CaptureTestTags.GUIDANCE)
                    .semantics {
                        isTraversalGroup = true
                        liveRegion = LiveRegionMode.Assertive
                        traversalIndex = 0f
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScrimLabel(guidance.instruction)
                OverlayAction("Cancel", onCancelCoaching)
            }
        }

        state.countdownSecondsRemaining?.let { remaining ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag(CaptureTestTags.COUNTDOWN)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    remaining.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
                OverlayAction("Cancel", onCancelCoaching)
            }
        }
    }
}

@Composable
private fun BoxScope.ObservationLayers(
    state: CaptureUiState,
    liveObservation: StateFlow<FrameObservation?>?,
    isFrontCamera: Boolean,
    onFocusTarget: (Float, Float) -> Unit,
) {
    val focusRecommendation = state.recommendation?.takeIf {
        it.action is RecommendationAction.TapToFocus || it.action is RecommendationAction.FocusAt
    }
    if (focusRecommendation == null && !state.settings.technicalDetail) return
    val observation = if (!state.settings.technicalDetail) null else if (liveObservation == null) {
        state.observation
    } else {
        val current by liveObservation.collectAsState()
        current
    }
    if (state.review == null && state.cameraPhase == CameraPhase.READY &&
        state.capabilities.supportsFocusMetering &&
        state.coachingPhase != CoachingPhase.APPLYING
    ) {
        val modelTarget = (focusRecommendation?.action as? RecommendationAction.FocusAt)
            ?.forPreview(isFrontCamera)
        if (modelTarget == null) {
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
            FocusTarget(.5f, .42f) { onFocusTarget(.5f, .42f) }
        } else {
            FocusCellOutline(modelTarget)
            FocusTarget(modelTarget.xFraction, modelTarget.yFraction) {
                onFocusTarget(modelTarget.xFraction, modelTarget.yFraction)
            }
        }
    }
    if (state.settings.technicalDetail) observation?.let { TechnicalObservation(it) }
}

private fun RecommendationAction.FocusAt.forPreview(mirrored: Boolean): RecommendationAction.FocusAt =
    if (!mirrored) this else copy(
        xFraction = 1f - xFraction,
        leftFraction = 1f - rightFraction,
        rightFraction = 1f - leftFraction,
    )

@Composable
private fun FocusCellOutline(target: RecommendationAction.FocusAt) {
    Canvas(Modifier.fillMaxSize().testTag(CaptureTestTags.FOCUS_CELL)) {
        val topLeft = Offset(size.width * target.leftFraction, size.height * target.topFraction)
        val cellSize = Size(
            size.width * (target.rightFraction - target.leftFraction),
            size.height * (target.bottomFraction - target.topFraction),
        )
        drawRect(
            color = Color(0xFFFFD54F).copy(alpha = 0.10f),
            topLeft = topLeft,
            size = cellSize,
        )
        drawRect(
            color = Color(0xFFFFD54F),
            topLeft = topLeft,
            size = cellSize,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun FocusTarget(xFraction: Float, yFraction: Float, onTap: () -> Unit) {
    val reticleScale = remember(xFraction, yFraction) { Animatable(1.35f) }
    LaunchedEffect(reticleScale) {
        reticleScale.animateTo(1f, tween(durationMillis = 180))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val diameter = 72.dp
        val x = (maxWidth * xFraction - diameter / 2).coerceIn(0.dp, (maxWidth - diameter).coerceAtLeast(0.dp))
        val y = (maxHeight * yFraction - diameter / 2).coerceIn(0.dp, (maxHeight - diameter).coerceAtLeast(0.dp))
        Canvas(
            Modifier
                .absoluteOffset(x, y)
                .size(diameter)
                .testTag(CaptureTestTags.FOCUS_TARGET)
                .pointerInput(onTap) { detectTapGestures { onTap() } }
                .semantics {
                    contentDescription = "Tap to focus at the marked point"
                    role = Role.Button
                    traversalIndex = 1f
                    onClick(label = "Focus here") {
                        onTap()
                        true
                    }
                },
        ) {
            val accent = Color(0xFFFFD54F)
            val half = 22.dp.toPx() * reticleScale.value
            val segment = 10.dp.toPx()
            val stroke = 2.5.dp.toPx()
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
                drawLine(Color.Black.copy(alpha = 0.55f), start, end, stroke + 2.dp.toPx())
                drawLine(accent, start, end, stroke)
            }
            drawCircle(Color.Black.copy(alpha = 0.55f), radius = 4.dp.toPx())
            drawCircle(accent, radius = 2.5.dp.toPx())
        }
    }
}

@Composable
private fun BoxScope.CameraPhaseStatus(phase: CameraPhase) {
    val copy = when (phase) {
        CameraPhase.STARTING -> "Starting camera…"
        CameraPhase.CAPTURING -> "Saving photo…"
        CameraPhase.REVIEWING -> "Opening review…"
        CameraPhase.READY, CameraPhase.BLOCKED -> null
    } ?: return
    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = Color.Black.copy(alpha = 0.68f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(copy)
        }
    }
}

@Composable
private fun GuidanceTarget(target: VerificationTarget) {
    Canvas(Modifier.fillMaxSize()) {
        val accent = Color(0xFF8DCDFF)
        when (target) {
            is VerificationTarget.FaceOccupancy -> {
                val width = target.max.coerceIn(0.1f, 0.9f) * size.width
                drawRect(
                    color = accent,
                    topLeft = Offset((size.width - width) / 2f, (size.height - width) / 2f),
                    size = Size(width, width),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }

            is VerificationTarget.FacePosition -> {
                val left = target.xRange.start.coerceIn(0f, 1f) * size.width
                val top = target.yRange.start.coerceIn(0f, 1f) * size.height
                val right = target.xRange.endInclusive.coerceIn(0f, 1f) * size.width
                val bottom = target.yRange.endInclusive.coerceIn(0f, 1f) * size.height
                drawRect(accent, Offset(left, top), Size(right - left, bottom - top), style = Stroke(3.dp.toPx()))
            }

            is VerificationTarget.StepBack -> {
                val width = target.maxFaceWidthFraction.coerceIn(0.1f, 0.9f) * size.width
                drawRect(
                    color = accent,
                    topLeft = Offset((size.width - width) / 2f, (size.height - width) / 2f),
                    size = Size(width, width),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }

            is VerificationTarget.Level -> drawLine(
                color = accent,
                start = Offset(size.width * 0.16f, size.height / 2f),
                end = Offset(size.width * 0.84f, size.height / 2f),
                strokeWidth = 3.dp.toPx(),
            )

            is VerificationTarget.Exposure, is VerificationTarget.ColorBalance, is VerificationTarget.Zoom -> Unit
        }
    }
}

@Composable
private fun ScrimLabel(text: String) {
    Surface(color = Color.Black.copy(alpha = 0.68f), shape = MaterialTheme.shapes.small) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun OverlayAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.68f),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .then(
                    if (contentDescription == null) Modifier else Modifier.semantics {
                        this.contentDescription = contentDescription
                    },
                ),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) { Text(text) }
    }
}

@Composable
private fun CoachingControls(
    state: CaptureUiState,
    reviewMode: Boolean,
    onCommentChange: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onMicrophone: () -> Unit,
    onApplyRecommendation: () -> Unit,
    onStartGuidance: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    onDismissDecision: () -> Unit,
    onClarificationSelected: (ClarificationChip) -> Unit,
    onCancelCoaching: () -> Unit,
    onReset: () -> Unit,
) {
    if (state.coachingPhase == CoachingPhase.REQUESTING_VISUAL_INTERPRETATION) {
        VisualLoading(onCancelCoaching)
        Spacer(Modifier.size(6.dp))
    }

    val inlineDecision = state.decision?.takeUnless {
        !reviewMode && (it as? LocalDecision.Recommend)?.recommendation?.action is RecommendationAction.FocusAt
    }
    if (inlineDecision != null) {
        DecisionCard(
            decision = inlineDecision,
            applying = state.coachingPhase == CoachingPhase.APPLYING,
            reviewMode = reviewMode,
            resetAvailable = state.resetAvailable,
            onApplyRecommendation = onApplyRecommendation,
            onStartGuidance = onStartGuidance,
            onFocusTarget = onFocusTarget,
            onDismiss = onDismissDecision,
            onClarificationSelected = onClarificationSelected,
            onReset = onReset,
        )
        Spacer(Modifier.size(6.dp))
    } else if (state.resetAvailable) {
        ResetCard(onReset)
        Spacer(Modifier.size(6.dp))
    }

    state.transientMessage?.let {
        TransientMessage(it, state.coachingPhase == CoachingPhase.TRANSIENT_ERROR)
        Spacer(Modifier.size(6.dp))
    }

    CoachingProgress(state.coachingPhase)
    CommentComposer(
        comment = state.comment,
        phase = state.coachingPhase,
        onCommentChange = onCommentChange,
        onSubmit = onSubmitComment,
        onMicrophone = onMicrophone,
    )
}

@Composable
private fun FocusRecommendationCard(
    recommendation: Recommendation,
    applying: Boolean,
    onFocus: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .testTag(CaptureTestTags.FOCUS_CARD)
            .semantics {
                isTraversalGroup = false
                liveRegion = LiveRegionMode.Polite
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(recommendation.headline, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(recommendation.actionText, style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onFocus, enabled = !applying, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (applying) "Applying…" else "Focus here")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose manually") }
            }
            Text(
                "Located by Qwen · Camera focus stays on device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VisualLoading(onCancel: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CaptureTestTags.VISUAL_LOADING)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(
                "Looking at the scene with Qwen…",
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}

@Composable
private fun DecisionCard(
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CaptureTestTags.RESPONSE_CARD)
            .semantics {
                isTraversalGroup = false
                liveRegion = LiveRegionMode.Polite
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                        fontWeight = FontWeight.SemiBold,
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
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(decision.detail, modifier = Modifier.semantics { traversalIndex = 0f })
                    if (decision.fromVisualHint) {
                        Text(
                            "AI-interpreted by Qwen via Alibaba Cloud; camera controls checked on device",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        if (walkingBlocked) {
            "Keep the phone stationary. Ask a nearby person to help change the distance, then reframe."
        } else {
            recommendation.actionText
        },
        modifier = Modifier.semantics { traversalIndex = 0f },
        style = MaterialTheme.typography.titleLarge,
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
            color = Color(0xFFFFDDB0),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (walkingBlocked) {
        Text(
            "Walking guidance is unavailable while touch exploration is on.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (recommendation.fromVisualHint || recommendation.controlIntents.isNotEmpty()) {
        Text(
            "AI-interpreted by Qwen via Alibaba Cloud; camera controls checked on device",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResetCard(onReset: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Camera adjustment active", modifier = Modifier.weight(1f))
            TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reset") }
        }
    }
}

@Composable
private fun TransientMessage(message: String, isError: Boolean) {
    val isAiFallback = message.startsWith("AI interpretation")
    Surface(
        color = when {
            isError -> MaterialTheme.colorScheme.error
            isAiFallback -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color(0xFF173D2A)
        },
        contentColor = when {
            isError -> MaterialTheme.colorScheme.onError
            isAiFallback -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> Color(0xFFC4F2D5)
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Text(
            text = when {
                isError -> "Warning: $message"
                isAiFallback -> "ⓘ $message"
                else -> "✓ $message"
            },
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CoachingProgress(phase: CoachingPhase) {
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
private fun CommentComposer(
    comment: String,
    phase: CoachingPhase,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMicrophone: () -> Unit,
) {
    val canSubmit = comment.isNotBlank() && phase != CoachingPhase.APPLYING
    val focusManager = LocalFocusManager.current
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit()
        }
    }
    val micDescription = when (phase) {
        CoachingPhase.LISTENING -> "Finish voice comment"
        CoachingPhase.INTERPRETING -> "Voice input processing"
        else -> "Describe shot by voice"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = comment,
            onValueChange = onCommentChange,
            enabled = phase != CoachingPhase.APPLYING,
            modifier = Modifier
                .weight(1f)
                .testTag(CaptureTestTags.COMMENT)
                .semantics { traversalIndex = 3f },
            placeholder = { Text("Describe the current shot") },
            maxLines = 2,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        OutlinedButton(
            onClick = onMicrophone,
            enabled = phase != CoachingPhase.APPLYING,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag(CaptureTestTags.MICROPHONE)
                .semantics {
                    contentDescription = micDescription
                    traversalIndex = 3.1f
                    stateDescription = when (phase) {
                        CoachingPhase.LISTENING -> "Listening"
                        CoachingPhase.INTERPRETING -> "Processing"
                        CoachingPhase.TRANSIENT_ERROR -> "Error"
                        else -> "Idle"
                    }
                },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { Text(if (phase == CoachingPhase.LISTENING) "Done" else "Mic") }
        Button(
            onClick = submit,
            enabled = canSubmit,
            modifier = Modifier.heightIn(min = 48.dp).semantics { traversalIndex = 3.2f },
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) { Text("Send") }
    }
}

@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(72.dp)
            .testTag(CaptureTestTags.SHUTTER)
            .semantics {
                contentDescription = "Take photo"
                traversalIndex = 4f
            },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.Gray,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .border(2.dp, Color.Black.copy(alpha = 0.75f), CircleShape),
        )
    }
}

@Composable
private fun CaptureReview(
    state: CaptureUiState,
    onCommentChange: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onMicrophone: () -> Unit,
    onApplyRecommendation: () -> Unit,
    onStartGuidance: () -> Unit,
    onFocusTarget: (Float, Float) -> Unit,
    onDismissDecision: () -> Unit,
    onClarificationSelected: (ClarificationChip) -> Unit,
    onCancelCoaching: () -> Unit,
    onReset: () -> Unit,
    onRetake: () -> Unit,
    onDone: () -> Unit,
) {
    val capture = requireNotNull(state.review)
    val applying = state.coachingPhase == CoachingPhase.APPLYING
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 390.dp)
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
                        onCommentChange = onCommentChange,
                        onSubmitComment = onSubmitComment,
                        onMicrophone = onMicrophone,
                        onApplyRecommendation = onApplyRecommendation,
                        onStartGuidance = onStartGuidance,
                        onFocusTarget = onFocusTarget,
                        onDismissDecision = onDismissDecision,
                        onClarificationSelected = onClarificationSelected,
                        onCancelCoaching = onCancelCoaching,
                        onReset = onReset,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 4f
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
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
    }
}

@Composable
private fun SavedCaptureImage(capture: SavedCapture, modifier: Modifier = Modifier) {
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
            if (loading) CircularProgressIndicator() else Text("Captured photo unavailable", color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideSheet(state: CaptureUiState, onDismiss: () -> Unit) {
    var technicalExpanded by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Photo Helper guide",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Describe what you want to change. You can combine brightness, zoom, and color in one request; " +
                    "ask for focus or movement separately.",
            )
            Text(
                "Voice is not always on: tap Mic for one request, then tap Done. You can also say “take a picture” " +
                    "or “take a picture in 5 seconds.” Say “switch camera,” “selfie mode,” or “rear camera” " +
                    "to choose a lens; the same commands work when typed. You can combine up to eight ordered steps, " +
                    "for example: “Make it brighter and warmer, then flip the camera and take a photo in 5 seconds.” " +
                    "The sequence pauses when an adjustment needs your Apply confirmation, then continues.",
            )
            Text(
                "What you can ask",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            GuideTopic("Brightness", "Say “too dark” or “too bright” for a whole-photo exposure change.")
            GuideTopic(
                "Focus",
                "With Qwen enabled, say “focus on the red watch.” Qwen marks one cell in an aspect-aware grid; " +
                    "check the marker, then tap it to focus. Without Qwen, choose the focus point yourself.",
            )
            GuideTopic("Zoom", "Say “too zoomed in” or “too zoomed out” for a wider or tighter digital crop.")
            GuideTopic("Color", "Say “too blue” or “too yellow” for Warmer, Cooler, or Auto white balance.")
            GuideTopic("Level and framing", "Ask to straighten the frame or follow movement guidance. You move the phone yourself.")
            Text(
                "Nothing changes until you tap Apply, tap a focus target, or start guidance. Reset restores supported exposure, zoom, and white-balance settings from before coaching.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                "On this camera",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            CameraCapabilityGuide(state)
            Text(
                "Photo Helper checks the active camera again before every Apply. Controls can differ between phones, lenses, and camera sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { technicalExpanded = !technicalExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = if (technicalExpanded) "Expanded" else "Collapsed" },
            ) {
                Text(if (technicalExpanded) "Camera technical details · Hide" else "Camera technical details · Show")
            }
            if (technicalExpanded) CameraTechnicalDetails()

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text("Close")
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CameraCapabilityGuide(state: CaptureUiState) {
    if (state.cameraPhase == CameraPhase.STARTING) {
        Text("Checking camera controls…")
        return
    }

    val capabilities = state.capabilities
    val zoomRange = capabilities.zoomRatioRange
    val zoomAvailable = zoomRange.start.isFinite() && zoomRange.endInclusive.isFinite() &&
        zoomRange.endInclusive - zoomRange.start >= .01f
    val whiteBalanceModes = listOf(
        WhiteBalancePreset.AUTO to "Auto",
        WhiteBalancePreset.WARMER to "Warmer",
        WhiteBalancePreset.COOLER to "Cooler",
    ).filter { it.first in capabilities.supportedWhiteBalancePresets }
    val colorAvailable = whiteBalanceModes.any { it.first != WhiteBalancePreset.AUTO }

    CapabilityStatus("Brightness", capabilities.supportsExposureCompensation)
    CapabilityStatus("Tap to focus", capabilities.supportsFocusMetering)
    Text(
        if (zoomAvailable) {
            "Digital zoom · Available · %.1f×–%.1f×".format(
                java.util.Locale.US,
                zoomRange.start,
                zoomRange.endInclusive,
            )
        } else {
            "Digital zoom · Unavailable on this camera"
        },
    )
    Text(
        when {
            colorAvailable -> "White balance · Available · ${whiteBalanceModes.joinToString { it.second }}"
            whiteBalanceModes.isNotEmpty() -> "White balance · Auto only · Warmer/Cooler unavailable"
            else -> "White balance · Unavailable on this camera"
        },
    )
    Text("Level and movement · Guidance only; the app cannot move the phone")
    Text("ISO and shutter speed · Not adjustable in this version; phone support varies")
    Text("Exact color temperature (Kelvin) · Not adjustable; available native presets are used instead")
}

@Composable
private fun CapabilityStatus(name: String, available: Boolean) {
    Text("$name · ${if (available) "Available" else "Unavailable on this camera"}")
}

@Composable
private fun CameraTechnicalDetails() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GuideTopic(
            "Exposure compensation (EV)",
            "EV asks Auto exposure for a brighter or darker whole photo. Positive EV is brighter; negative EV is darker. The camera still chooses ISO and shutter speed.",
        )
        GuideTopic(
            "Focus and blur",
            "Focus chooses the distance that should look sharp. Tap-to-focus cannot fix blur caused by subject movement or camera shake.",
        )
        GuideTopic(
            "Digital zoom",
            "Digital zoom crops and enlarges the image. It does not move the camera or change perspective, and higher zoom can reduce detail.",
        )
        GuideTopic(
            "White balance and color temperature",
            "White balance compensates for the color of the light. Warmer reduces a blue cast; Cooler reduces a yellow or orange cast. Photo Helper uses native presets, not an exact Kelvin value.",
        )
        GuideTopic(
            "ISO",
            "At the same shutter speed, higher ISO records a brighter image but usually adds noise and reduces highlight headroom. Photo Helper does not adjust ISO in this version.",
        )
        GuideTopic(
            "Shutter speed",
            "A shorter exposure freezes movement but gathers less light. A longer exposure gathers more light but can blur motion or camera shake. Photo Helper does not adjust shutter speed in this version.",
        )
        Text(
            "Safe manual exposure requires ISO and shutter speed to be controlled together with Auto exposure off, then restored to Auto. Photo Helper will not offer that control until the full transaction is qualified.",
            style = MaterialTheme.typography.bodyMedium,
        )
        GuideTopic(
            "Level, distance, and angle",
            "Level measures camera roll. Moving the phone changes perspective; tilting changes framing. These are guidance, not camera settings, and appear only when the needed sensor or subject is available.",
        )
    }
}

@Composable
private fun GuideTopic(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    state: CaptureUiState,
    apiKeyInput: String,
    onDismiss: () -> Unit,
    onSpokenGuidanceChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onTechnicalDetailChanged: (Boolean) -> Unit,
    onVisualAiEnabledChanged: (Boolean) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onTestKey: () -> Unit,
    onClearKey: () -> Unit,
    onEnableMicrophone: () -> Unit,
    onOpenVisualAiPolicy: () -> Unit,
    onOpenMlKitPolicy: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CaptureTestTags.SETTINGS)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            ToggleRow("Spoken guidance", state.settings.spokenGuidance, onSpokenGuidanceChanged)
            ToggleRow("Haptics", state.settings.haptics, onHapticsChanged)
            ToggleRow("Technical detail", state.settings.technicalDetail, onTechnicalDetailChanged)

            Text("AI interpretation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Qwen3.7 Flash (2026-07-15) · Alibaba Cloud Model Studio, China (Beijing)")
            ToggleRow(
                label = "AI interpretation enabled",
                checked = state.settings.visualAiEnabled,
                onCheckedChange = onVisualAiEnabledChanged,
                enabled = state.settings.keyConfigured && !state.settings.testingKey,
            )
            QwenKeySetup(
                settings = state.settings,
                apiKeyInput = apiKeyInput,
                onApiKeyChanged = onApiKeyChanged,
                onTestKey = onTestKey,
                onClearKey = onClearKey,
                onOpenVisualAiPolicy = onOpenVisualAiPolicy,
            )
            Text(
                "Face detection runs on-device with bundled Google ML Kit. Google documents collection of " +
                    "device/app information and diagnostic usage metrics; camera images and face results are not sent to Google.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenMlKitPolicy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("View ML Kit data disclosure")
            }
            if (state.microphonePermission == PermissionState.DENIED) {
                Text("Microphone access is off. Typed comments remain available.")
                TextButton(onClick = onEnableMicrophone, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Enable microphone")
                }
            }
            Text(
                "Photo Helper is designed around an image-capable LLM. This build implements only Qwen3.7 Flash " +
                    "through Alibaba Cloud Model Studio (Bailian), with prompts and response contracts tuned for that " +
                    "model. Other models may produce different results and have no implemented provider integration. " +
                    "Qwen returns strict allowlisted JSON; camera decisions, validation, and control stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text("Close")
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QwenKeySetup(
    settings: SettingsUiState,
    apiKeyInput: String,
    onApiKeyChanged: (String) -> Unit,
    onTestKey: () -> Unit,
    onClearKey: () -> Unit,
    onOpenVisualAiPolicy: () -> Unit,
) {
    OutlinedTextField(
        value = apiKeyInput,
        onValueChange = { onApiKeyChanged(it.take(MAX_API_KEY_CHARACTERS)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Alibaba Cloud Model Studio (Bailian) API key") },
        placeholder = {
            Text(if (settings.keyConfigured) "Saved key is hidden" else "Enter your API key")
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            keyboardType = KeyboardType.Password,
        ),
        singleLine = true,
        enabled = !settings.testingKey,
    )
    Text(
        "Only an Alibaba Cloud Model Studio (Bailian) API key with access to Qwen3.7 Flash is supported. " +
            "Keys for other providers or models will not work because their API adapters are not implemented.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            settings.keyStatus,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.testingKey) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onTestKey,
            enabled = !settings.testingKey && apiKeyInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Test, save & enable") }
        OutlinedButton(
            onClick = onClearKey,
            enabled = !settings.testingKey && (apiKeyInput.isNotBlank() || settings.keyConfigured),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Clear key") }
    }
    Text(
        "When enabled, Photo Helper sends each typed or transcribed comment to Alibaba Cloud Model Studio (Bailian) " +
            "in China (Beijing). For eligible visual questions it also sends one reduced live frame or one reduced " +
            "copy of the saved photo under review. It never sends audio or streams the preview. Alibaba Cloud may " +
            "retain request data; see its privacy notice.",
        style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(onClick = onOpenVisualAiPolicy, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("View Alibaba Cloud privacy notice")
    }
}

@Composable
private fun BoxScope.TechnicalObservation(observation: FrameObservation) {
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
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun BoxScope.DefaultPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF202326)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Camera preview", color = Color.White)
    }
}
