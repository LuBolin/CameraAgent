package com.bolin.photohelper.capture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.RecommendationAction
import com.bolin.photohelper.guide.ActiveExercise
import com.bolin.photohelper.guide.ExerciseOverlay
import com.bolin.photohelper.guide.ExerciseType
import com.bolin.photohelper.guide.GuideProgress
import com.bolin.photohelper.guide.GuideScreen
import kotlinx.coroutines.flow.StateFlow

object CaptureTestTags {
    const val ROOT = "capture_root"
    const val PREVIEW = "camera_preview"
    const val RESPONSE_CARD = "response_card"
    const val COMMENT = "comment_input"
    const val MICROPHONE = "microphone"
    const val LANDING = "landing_screen"
    const val HELPER_ORB = "helper_orb"
    const val MIRROR_BAR = "mirror_bar"
    const val CONTROL_STRIP = "control_strip"
    const val GUIDANCE = "guidance"
    const val VISUAL_LOADING = "visual_loading"
    const val REVIEW = "capture_review"
    const val REVIEW_CONTROLS = "review_controls"
    const val RESET = "reset"
    const val SETTINGS = "settings_sheet"
    const val FOCUS_AREA = "focus_area"
    const val FOCUS_TARGET = "focus_target"
    const val COUNTDOWN = "capture_countdown"
    const val PREVIEW_CHROME = "preview_chrome"
    const val CAMERA_FLIP = "camera_flip"
    const val FLASH_MODE = "flash_mode"
    const val GALLERY = "gallery"
}

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    liveObservation: StateFlow<FrameObservation?>,
    confidence: Float,
    apiKeyInput: String,
    preview: @Composable BoxScope.() -> Unit,
    isFrontCamera: Boolean,
    canFlipCamera: Boolean,
    actions: CaptureScreenActions,
    guideProgress: GuideProgress? = null,
    galleryThumbnail: ImageBitmap? = null,
) {
    var showGuide by remember { mutableStateOf(false) }
    var activeExercise by remember { mutableStateOf<ActiveExercise?>(null) }
    var exerciseCompleted by remember { mutableStateOf(false) }

    val cameraReady = state.onboardingStep > 0 &&
        state.cameraPermission == PermissionState.GRANTED &&
        state.cameraPhase != CameraPhase.BLOCKED
    LaunchedEffect(cameraReady) {
        if (cameraReady && guideProgress != null && !guideProgress.hasSeenGuide()) {
            guideProgress.markGuideSeen()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CaptureTestTags.ROOT),
        color = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = when {
                state.onboardingStep == 0 -> Screen.LANDING
                state.cameraPermission == PermissionState.DENIED -> Screen.PERMISSION
                state.cameraPhase == CameraPhase.BLOCKED -> Screen.BLOCKED
                else -> Screen.CAMERA
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "capture_state",
        ) { screen ->
            when (screen) {
                // Only hand the landing a live feed once the camera is actually
                // permitted; otherwise it would bind a second preview alongside the
                // camera screen during the crossfade, for a feed that cannot render.
                Screen.LANDING -> LandingScreen(
                    preview = if (state.cameraPermission == PermissionState.GRANTED) preview else ({}),
                    onStart = actions::onOnboardingContinue,
                    onGuideOpen = { showGuide = true },
                )
                Screen.PERMISSION -> CameraPermission(
                    onRequest = actions::onRequestCameraPermission,
                    onSettings = actions::onOpenAppSettings,
                )
                Screen.BLOCKED -> RecoverySurface(
                    onRetry = actions::onRetryCamera,
                    onSettings = actions::onOpenAppSettings,
                )
                // Review draws over the camera rather than replacing it, so the
                // CameraX session stays bound and a retake is instant.
                Screen.CAMERA -> Box(Modifier.fillMaxSize()) {
                    CaptureContent(
                        state = state,
                        liveObservation = liveObservation,
                        confidence = confidence,
                        preview = preview,
                        isFrontCamera = isFrontCamera,
                        canFlipCamera = canFlipCamera,
                        galleryThumbnail = galleryThumbnail,
                        actions = actions,
                        onHelpOpen = { showGuide = true },
                    )
                    val review = state.review
                    if (review != null) {
                        CaptureReview(
                            state = state,
                            capture = review,
                            onMicrophone = actions::onMicrophone,
                            onApplyRecommendation = actions::onApplyRecommendation,
                            onStartGuidance = actions::onStartGuidance,
                            onFocusTarget = actions::onFocusTarget,
                            onDismissDecision = actions::onDismissDecision,
                            onDismissTransientMessage = actions::onDismissTransientMessage,
                            onClarificationSelected = actions::onClarificationSelected,
                            onReset = actions::onReset,
                            onRetake = actions::onRetake,
                            onOpenGallery = actions::onOpenGallery,
                            onDone = actions::onDoneReview,
                        )
                    }
                    ShutterFlash(visible = review != null)
                }
            }
        }
    }

    if (state.settingsOpen) {
        SettingsSheet(
            state = state,
            apiKeyInput = apiKeyInput,
            onDismiss = actions::onSettingsDismiss,
            onSpokenGuidanceChanged = actions::onSpokenGuidanceChanged,
            onHapticsChanged = actions::onHapticsChanged,
            onTechnicalDetailChanged = actions::onTechnicalDetailChanged,
            onVisualAiEnabledChanged = actions::onVisualAiEnabledChanged,
            onThemeModeChanged = actions::onThemeModeChanged,
            onStyleProfileChanged = actions::onStyleProfileChanged,
            onVisualProviderChanged = actions::onVisualProviderChanged,
            onApiKeyChanged = actions::onApiKeyChanged,
            onTestKey = actions::onTestKey,
            onClearKey = actions::onClearKey,
            onEnableMicrophone = actions::onOpenAppSettings,
            onAutoCaptureEnabledChanged = actions::onAutoCaptureEnabledChanged,
            onOpenVisualAiPolicy = actions::onOpenVisualAiPolicy,
            onOpenMlKitPolicy = actions::onOpenMlKitPolicy,
        )
    }

    if (showGuide && guideProgress != null) {
        GuideScreen(
            progress = guideProgress,
            onDismiss = { showGuide = false },
            onStartExercise = { exercise, lessonId ->
                showGuide = false
                activeExercise = ActiveExercise(lessonId, exercise)
            },
        )
    }

    activeExercise?.let { exercise ->
        val autoComplete = when (exercise.exercise.type) {
            ExerciseType.TAKE_PHOTO -> state.review != null
            ExerciseType.VOICE_COMMAND -> state.coachingPhase == CoachingPhase.LISTENING
            ExerciseType.TAP_ENHANCE -> state.coachingPhase == CoachingPhase.APPLYING
            else -> false
        }
        if (autoComplete && !exerciseCompleted) exerciseCompleted = true

        ExerciseOverlay(
            active = exercise,
            completed = exerciseCompleted,
            onCancel = {
                activeExercise = null
                exerciseCompleted = false
            },
            onComplete = {
                guideProgress?.markLessonComplete(exercise.lessonId)
                activeExercise = null
                exerciseCompleted = false
            },
        )
    }

    BackHandler(enabled = showGuide) {
        showGuide = false
    }
    BackHandler(enabled = !showGuide && state.coachingPhase != CoachingPhase.IDLE) {
        actions.onCancelCoaching()
    }
}

@Composable
private fun CaptureContent(
    state: CaptureUiState,
    liveObservation: StateFlow<FrameObservation?>,
    confidence: Float,
    preview: @Composable BoxScope.() -> Unit,
    isFrontCamera: Boolean,
    canFlipCamera: Boolean,
    galleryThumbnail: ImageBitmap?,
    actions: CaptureScreenActions,
    onHelpOpen: () -> Unit,
) {
    val deviceOrientation = rememberDeviceOrientation()
    val iconRotation = deviceOrientation.iconRotation
    val isLandscape = deviceOrientation.devicePosture == 90 || deviceOrientation.devicePosture == 270
    val orbState = orbStateFor(state.coachingPhase)
    val baseInstruction = mirrorBarText(state)
    val chromeVisible = state.review == null

    var voiceHintIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.showVoiceHints) {
        if (!state.showVoiceHints) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5000)
            voiceHintIndex = (voiceHintIndex + 1) % VOICE_HINTS.size
        }
    }
    val instruction = baseInstruction ?: if (
        state.showVoiceHints && orbState == OrbState.IDLE && state.review == null
    ) VOICE_HINTS[voiceHintIndex] else null

    val onOrbTap: () -> Unit = {
        if (state.showFirstUseHint) actions.onFirstUseHintSeen()
        when (orbState) {
            OrbState.IDLE -> actions.onShutter()
            OrbState.LISTENING -> actions.onMicrophone()
            OrbState.PROCESSING -> Unit
            OrbState.DECIDED -> confirmDecision(state, actions)
            OrbState.ERROR -> actions.onDismissTransientMessage()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        PreviewPane(
            state = state,
            liveObservation = liveObservation,
            preview = preview,
            isFrontCamera = isFrontCamera,
            canFlipCamera = canFlipCamera,
            onFlipCamera = actions::onFlipCamera,
            onFocusTarget = actions::onFocusTarget,
            onSettingsOpen = actions::onSettingsOpen,
            onHelpOpen = onHelpOpen,
            modifier = Modifier.fillMaxSize(),
            showTopChrome = chromeVisible,
            iconRotation = iconRotation,
        )
        if (chromeVisible) {
        if (isLandscape) {
            val mirrorAlign = if (deviceOrientation.devicePosture == 90) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            MirrorBar(
                instruction,
                modifier = Modifier
                    .align(mirrorAlign)
                    .padding(24.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = 0,
                                maxWidth = constraints.maxHeight,
                                minHeight = 0,
                                maxHeight = constraints.maxWidth,
                            ),
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                x = -(placeable.width - placeable.height) / 2,
                                y = -(placeable.height - placeable.width) / 2,
                            )
                        }
                    }
                    .graphicsLayer { rotationZ = iconRotation },
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .semantics { isTraversalGroup = true },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DecisionSurface(state, actions)
            if (!isLandscape) {
                MirrorBar(instruction)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Gallery
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    OverlayIconAction(
                        icon = Icons.Rounded.PhotoLibrary,
                        image = galleryThumbnail,
                        contentDescription = "Open gallery",
                        onClick = actions::onOpenGallery,
                        modifier = Modifier
                            .graphicsLayer { rotationZ = iconRotation }
                            .testTag(CaptureTestTags.GALLERY),
                    )
                }
                // Center: Orb
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    HelperOrb(
                        state = orbState,
                        confidence = confidence,
                        enabled = orbEnabled(state),
                        onTap = onOrbTap,
                        autoCaptureFlashKey = state.autoCaptureFlashKey,
                    )
                }
                // Right: AI button (starts voice)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    OverlayIconAction(
                        icon = if (state.coachingPhase == CoachingPhase.LISTENING) Icons.Rounded.Stop else Icons.Rounded.AutoAwesome,
                        contentDescription = if (state.coachingPhase == CoachingPhase.LISTENING) "Finish voice comment" else "Describe what to improve",
                        onClick = actions::onMicrophone,
                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                        tier = OverlayTier.PRIMARY,
                        enabled = state.coachingPhase !in setOf(CoachingPhase.APPLYING, CoachingPhase.INTERPRETING),
                        traversalIndex = 4.2f,
                    )
                }
            }
        }
        }
    }
}

private enum class Screen { LANDING, PERMISSION, BLOCKED, CAMERA }

/**
 * What a tap on a decided Orb means. The Orb is the confirm button for whatever the
 * agent proposed, so it routes to the same handler the decision card's primary action
 * would have used.
 */

private fun confirmDecision(state: CaptureUiState, actions: CaptureScreenActions) {
    if (state.activeGuidance != null) {
        actions.onCancelCoaching()
        return
    }
    when (val action = (state.decision as? LocalDecision.Recommend)?.recommendation?.action) {
        is RecommendationAction.ApplySettings -> actions.onApplyRecommendation()
        is RecommendationAction.GuidePosition -> actions.onStartGuidance()
        is RecommendationAction.FocusAt -> actions.onFocusTarget(action.xFraction, action.yFraction)
        RecommendationAction.TapToFocus, null -> actions.onDismissDecision()
    }
}

@Composable
private fun CameraPermission(onRequest: () -> Unit, onSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Camera access needed",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(12.dp))
            Text("Photo Helper needs the camera to take photos and help you frame them.")
            Spacer(Modifier.size(24.dp))
            Button(onClick = onRequest, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Allow camera")
            }
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Open settings")
            }
        }
    }
}

@Composable
private fun ShutterFlash(visible: Boolean) {
    val reducedMotion = com.bolin.photohelper.ui.LocalReducedMotion.current
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (!visible || reducedMotion) return@LaunchedEffect
        flashAlpha.snapTo(0.85f)
        flashAlpha.animateTo(0f, tween(durationMillis = 200))
    }
    if (flashAlpha.value > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .alpha(flashAlpha.value)
                .background(Color.White),
        )
    }
}

@Composable
private fun RecoverySurface(onRetry: () -> Unit, onSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Camera unavailable",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(12.dp))
            Text("Another app may be using the camera, or the device camera is not available.")
            Spacer(Modifier.size(24.dp))
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Retry")
            }
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Open settings")
            }
        }
    }
}

class DeviceOrientation(val iconRotation: Float, val devicePosture: Int)

@Composable
private fun rememberDeviceOrientation(): DeviceOrientation {
    val context = LocalContext.current
    var targetRotation by remember { mutableStateOf(0f) }
    var devicePosture by remember { mutableIntStateOf(0) }
    val animatedRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "iconRotation",
    )

    androidx.compose.runtime.DisposableEffect(context) {
        val listener = object : android.view.OrientationEventListener(context) {
            private var lastSnapped = 0
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val deviceSnap = when {
                    orientation in 45..134 -> 90
                    orientation in 135..224 -> 180
                    orientation in 225..314 -> 270
                    else -> 0
                }
                val iconSnap = when (deviceSnap) {
                    90 -> 270
                    270 -> 90
                    else -> deviceSnap
                }
                if (iconSnap != lastSnapped) {
                    lastSnapped = iconSnap
                    devicePosture = deviceSnap
                    val current = targetRotation % 360f
                    var delta = iconSnap - current
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    targetRotation += delta
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    return DeviceOrientation(animatedRotation, devicePosture)
}
