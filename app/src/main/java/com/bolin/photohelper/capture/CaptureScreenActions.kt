package com.bolin.photohelper.capture

import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.ui.ThemeMode
import com.bolin.photohelper.visual.VisualProvider

interface CaptureScreenActions {
    // Camera controls
    fun onFlipCamera()
    fun onFlashModeCycle()
    fun onShutter()
    fun onAutoEnhance()

    // Voice
    fun onMicrophone()

    // Landing & permissions
    fun onOnboardingContinue()
    fun onRequestCameraPermission()
    fun onOpenAppSettings()
    fun onRetryCamera()
    fun onFirstUseHintSeen()

    // Coaching & decisions
    fun onApplyRecommendation()
    fun onStartGuidance()
    fun onFocusTarget(x: Float, y: Float)
    fun onDismissDecision()
    fun onDismissTransientMessage()
    fun onClarificationSelected(chip: ClarificationChip)
    fun onCancelCoaching()
    fun onReset()

    // Review
    fun onRetake()
    fun onDoneReview()

    // Settings
    fun onSettingsOpen()
    fun onSettingsDismiss()
    fun onSpokenGuidanceChanged(enabled: Boolean)
    fun onHapticsChanged(enabled: Boolean)
    fun onTechnicalDetailChanged(enabled: Boolean)
    fun onVisualAiEnabledChanged(enabled: Boolean)
    fun onThemeModeChanged(mode: ThemeMode)
    fun onStyleProfileChanged(profile: String)
    fun onVisualProviderChanged(provider: VisualProvider)
    fun onApiKeyChanged(key: String)
    fun onTestKey()
    fun onClearKey()
    fun onAutoCaptureEnabledChanged(enabled: Boolean)
    fun onOpenVisualAiPolicy()
    fun onOpenMlKitPolicy()
}
