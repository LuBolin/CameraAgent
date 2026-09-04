package com.bolin.photohelper.capture

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
                            onDone = actions::onDoneReview,
                        )
                    }
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
            onGuideOpen = {
                actions.onSettingsDismiss()
                showGuide = true
            },
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
            ExerciseType.LONG_PRESS_ORB -> state.coachingPhase == CoachingPhase.APPLYING
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
) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val orbState = orbStateFor(state.coachingPhase)
    val instruction = mirrorBarText(state)
    val chromeVisible = state.review == null

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
    val onOrbLongPress: () -> Unit = {
        if (state.showFirstUseHint) actions.onFirstUseHintSeen()
        actions.onAutoEnhance()
    }

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
                PreviewPane(
                    state = state,
                    liveObservation = liveObservation,
                    preview = preview,
                    isFrontCamera = isFrontCamera,
                    canFlipCamera = canFlipCamera,
                    onFlipCamera = actions::onFlipCamera,
                    onFocusTarget = actions::onFocusTarget,
                    onSettingsOpen = actions::onSettingsOpen,
                    modifier = Modifier.fillMaxSize(),
                    showTopChrome = false,
                )
                if (chromeVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(16.dp)
                        .semantics { isTraversalGroup = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DecisionSurface(state, actions)
                    MirrorBar(instruction)
                }
                }
            }
            if (chromeVisible) {
            ControlStrip(
                state = state,
                isFrontCamera = isFrontCamera,
                canFlipCamera = canFlipCamera,
                confidence = confidence,
                onFlipCamera = actions::onFlipCamera,
                onSettingsOpen = actions::onSettingsOpen,
                onOrbTap = onOrbTap,
                onOrbLongPress = onOrbLongPress,
                onMicrophone = actions::onMicrophone,
                onOpenGallery = actions::onOpenGallery,
                galleryThumbnail = galleryThumbnail,
                modifier = Modifier.width(CONTROL_STRIP_WIDTH),
            )
            }
        }
    } else {
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
                modifier = Modifier.fillMaxSize(),
                showTopChrome = chromeVisible,
            )
            if (chromeVisible) {
            OverlayIconAction(
                icon = Icons.Rounded.PhotoLibrary,
                image = galleryThumbnail,
                contentDescription = "Open gallery",
                onClick = actions::onOpenGallery,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 24.dp)
                    .testTag(CaptureTestTags.GALLERY),
            )
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
                MirrorBar(instruction)
                // The Orb is the brand signature and the primary control, so it sits on
                // the screen axis. The mic is offset beside it rather than sharing a Row,
                // which would push the Orb off-centre by half the mic's width.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HelperOrb(
                        state = orbState,
                        confidence = confidence,
                        enabled = orbEnabled(state),
                        onTap = onOrbTap,
                        onLongPress = onOrbLongPress,
                        autoCaptureFlashKey = state.autoCaptureFlashKey,
                    )
                    MicrophoneButton(
                        phase = state.coachingPhase,
                        onMicrophone = actions::onMicrophone,
                        modifier = Modifier.offset(x = -MIC_OFFSET_FROM_ORB),
                    )
                }
            }
            }
        }
    }
}

private enum class Screen { LANDING, PERMISSION, BLOCKED, CAMERA }

private val CONTROL_STRIP_WIDTH = 72.dp

/** Half the Orb's glow box (46dp) + a 16dp gap + half the mic (28dp): no overlap, Orb on axis. */
private val MIC_OFFSET_FROM_ORB = 90.dp

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
            Text("Camera access needed", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(12.dp))
            Text("Photo Helper needs the camera to take photos and help you frame them.")
            Spacer(Modifier.size(24.dp))
            Button(onClick = onRequest, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Allow camera")
            }
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Open settings")
            }
        }
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
            Text("Camera unavailable", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(12.dp))
            Text("Another app may be using the camera, or the device camera is not available.")
            Spacer(Modifier.size(24.dp))
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Retry")
            }
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Open settings")
            }
        }
    }
}
