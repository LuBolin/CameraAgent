package com.bolin.photohelper.capture

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.ui.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [CaptureScreenActions] whose members are settable lambdas, all no-ops by default.
 *
 * The production interface deliberately replaced 33 callback parameters on
 * `CaptureScreen`; this keeps the tests' named-callback readability without dragging
 * that surface back into the app.
 */
class TestActions(
    var flipCamera: () -> Unit = {},
    var flashModeCycle: () -> Unit = {},
    var shutter: () -> Unit = {},
    var autoEnhance: () -> Unit = {},
    var microphone: () -> Unit = {},
    var onboardingContinue: () -> Unit = {},
    var requestCameraPermission: () -> Unit = {},
    var openAppSettings: () -> Unit = {},
    var retryCamera: () -> Unit = {},
    var firstUseHintSeen: () -> Unit = {},
    var applyRecommendation: () -> Unit = {},
    var startGuidance: () -> Unit = {},
    var focusTarget: (Float, Float) -> Unit = { _, _ -> },
    var dismissDecision: () -> Unit = {},
    var dismissTransientMessage: () -> Unit = {},
    var clarificationSelected: (ClarificationChip) -> Unit = {},
    var cancelCoaching: () -> Unit = {},
    var reset: () -> Unit = {},
    var retake: () -> Unit = {},
    var doneReview: () -> Unit = {},
    var settingsOpen: () -> Unit = {},
    var settingsDismiss: () -> Unit = {},
    var spokenGuidanceChanged: (Boolean) -> Unit = {},
    var hapticsChanged: (Boolean) -> Unit = {},
    var technicalDetailChanged: (Boolean) -> Unit = {},
    var visualAiEnabledChanged: (Boolean) -> Unit = {},
    var autoCaptureEnabledChanged: (Boolean) -> Unit = {},
    var themeModeChanged: (ThemeMode) -> Unit = {},
    var styleProfileChanged: (String) -> Unit = {},
    var visualProviderChanged: (com.bolin.photohelper.visual.VisualProvider) -> Unit = {},
    var apiKeyChanged: (String) -> Unit = {},
    var testKey: () -> Unit = {},
    var clearKey: () -> Unit = {},
    var openVisualAiPolicy: () -> Unit = {},
    var openMlKitPolicy: () -> Unit = {},
) : CaptureScreenActions {
    override fun onFlipCamera() = flipCamera()
    override fun onFlashModeCycle() = flashModeCycle()
    override fun onShutter() = shutter()
    override fun onAutoEnhance() = autoEnhance()
    override fun onMicrophone() = microphone()
    override fun onOnboardingContinue() = onboardingContinue()
    override fun onRequestCameraPermission() = requestCameraPermission()
    override fun onOpenAppSettings() = openAppSettings()
    override fun onRetryCamera() = retryCamera()
    override fun onFirstUseHintSeen() = firstUseHintSeen()
    override fun onApplyRecommendation() = applyRecommendation()
    override fun onStartGuidance() = startGuidance()
    override fun onFocusTarget(x: Float, y: Float) = focusTarget(x, y)
    override fun onDismissDecision() = dismissDecision()
    override fun onDismissTransientMessage() = dismissTransientMessage()
    override fun onClarificationSelected(chip: ClarificationChip) = clarificationSelected(chip)
    override fun onCancelCoaching() = cancelCoaching()
    override fun onReset() = reset()
    override fun onRetake() = retake()
    override fun onDoneReview() = doneReview()
    override fun onSettingsOpen() = settingsOpen()
    override fun onSettingsDismiss() = settingsDismiss()
    override fun onSpokenGuidanceChanged(enabled: Boolean) = spokenGuidanceChanged(enabled)
    override fun onHapticsChanged(enabled: Boolean) = hapticsChanged(enabled)
    override fun onTechnicalDetailChanged(enabled: Boolean) = technicalDetailChanged(enabled)
    override fun onVisualAiEnabledChanged(enabled: Boolean) = visualAiEnabledChanged(enabled)
    override fun onAutoCaptureEnabledChanged(enabled: Boolean) = autoCaptureEnabledChanged(enabled)
    override fun onThemeModeChanged(mode: ThemeMode) = themeModeChanged(mode)
    override fun onStyleProfileChanged(profile: String) = styleProfileChanged(profile)
    override fun onVisualProviderChanged(provider: com.bolin.photohelper.visual.VisualProvider) = visualProviderChanged(provider)
    override fun onApiKeyChanged(key: String) = apiKeyChanged(key)
    override fun onTestKey() = testKey()
    override fun onClearKey() = clearKey()
    override fun onOpenVisualAiPolicy() = openVisualAiPolicy()
    override fun onOpenMlKitPolicy() = openMlKitPolicy()
}

/** `CaptureScreen` with test defaults for everything the case under test does not care about. */
@Composable
fun TestCaptureScreen(
    state: CaptureUiState,
    actions: CaptureScreenActions = TestActions(),
    liveObservation: StateFlow<FrameObservation?> = MutableStateFlow(null),
    confidence: Float = state.coachingPhase.confidence(),
    apiKeyInput: String = "",
    isFrontCamera: Boolean = false,
    canFlipCamera: Boolean = true,
    preview: @Composable BoxScope.() -> Unit = { DefaultPreview() },
) {
    CaptureScreen(
        state = state,
        liveObservation = liveObservation,
        confidence = confidence,
        apiKeyInput = apiKeyInput,
        preview = preview,
        isFrontCamera = isFrontCamera,
        canFlipCamera = canFlipCamera,
        actions = actions,
    )
}

/** The Orb's accessibility label in its idle state - the shutter's replacement. */
const val ORB_IDLE_DESCRIPTION = "Take photo. Hold to auto-enhance."
