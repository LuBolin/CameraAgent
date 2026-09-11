package com.bolin.photohelper.capture

import com.bolin.photohelper.coach.ClarificationChip
import com.bolin.photohelper.ui.ThemeMode

interface CaptureScreenActions {
    // Camera controls
    fun onFlipCamera()
    fun onFlashModeCycle()
    fun onShutter()
    fun onAutoEnhance()
    fun onZoom(factor: Float) {}
    fun onOpenGallery()
    fun onFocusTap(xFraction: Float, yFraction: Float) = onFocusTarget(xFraction, yFraction)

    // Voice
    fun onMicrophone()
    fun onUpdateComment(text: String) {}
    fun onSubmitComment() {}

    // Landing & permissions
    fun onOnboardingContinue()
    fun onRequestCameraPermission()
    fun onOpenAppSettings()
    fun onRetryCamera()
    fun onFirstUseHintSeen()

    // Coaching & decisions
    fun onApplyRecommendation()
    fun onStartGuidance()
    fun onComposition() {}
    fun onBestShot() {}
    fun onCannotMoveFurther() {}
    fun onChangeCompositionSelection() {}
    fun onToggleCompositionFace(index: Int) {}
    fun onSelectAllCompositionFaces() {}
    fun onConfirmCompositionSelection() {}
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
    fun onApiKeyChanged(key: String)
    fun onTestKey()
    fun onClearKey()
    fun onAutoCaptureEnabledChanged(enabled: Boolean)
    fun onSimplifiedAutoModeChanged(enabled: Boolean) {}
    fun onCaptionConsentChanged(given: Boolean)
    fun onGridOverlayEnabledChanged(enabled: Boolean)
    fun onTiltIndicatorEnabledChanged(enabled: Boolean)
    fun onOpenVisualAiPolicy()
    fun onOpenMlKitPolicy()
}
