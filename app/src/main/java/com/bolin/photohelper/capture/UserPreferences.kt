package com.bolin.photohelper.capture

import android.content.Context
import com.bolin.photohelper.ui.ThemeMode
import com.bolin.photohelper.visual.VisualProvider

interface PreferenceStore {
    fun onboardingComplete(): Boolean
    fun setOnboardingComplete()
    fun firstUseHintSeen(): Boolean
    fun setFirstUseHintSeen()
    fun settings(keyConfigured: Boolean): SettingsUiState
    fun setSpokenGuidance(enabled: Boolean)
    fun setHaptics(enabled: Boolean)
    fun setTechnicalDetail(enabled: Boolean)
    fun setVisualAiEnabled(enabled: Boolean)
    fun setThemeMode(mode: ThemeMode)
    fun setStyleProfile(profile: String)
    fun setVisualProvider(provider: VisualProvider)
    fun autoCaptureEnabled(): Boolean
    fun setAutoCaptureEnabled(enabled: Boolean)
    fun hasUsedVoice(): Boolean
    fun setHasUsedVoice()
    fun captionConsentGiven(): Boolean
    fun setCaptionConsentGiven(given: Boolean)
    fun galleryBannerDismissed(): Boolean
    fun setGalleryBannerDismissed(dismissed: Boolean)
    fun gridOverlayEnabled(): Boolean
    fun setGridOverlayEnabled(enabled: Boolean)
    fun tiltIndicatorEnabled(): Boolean
    fun setTiltIndicatorEnabled(enabled: Boolean)
}

class UserPreferences(context: Context) : PreferenceStore {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun onboardingComplete(): Boolean = values.getBoolean(ONBOARDING_COMPLETE, false)
    override fun setOnboardingComplete() = values.edit().putBoolean(ONBOARDING_COMPLETE, true).apply()

    override fun firstUseHintSeen(): Boolean = values.getBoolean(FIRST_USE_HINT_SEEN, false)
    override fun setFirstUseHintSeen() = put(FIRST_USE_HINT_SEEN, true)

    override fun settings(keyConfigured: Boolean): SettingsUiState = SettingsUiState(
        spokenGuidance = values.getBoolean(SPOKEN_GUIDANCE, true),
        haptics = values.getBoolean(HAPTICS, true),
        technicalDetail = values.getBoolean(TECHNICAL_DETAIL, false),
        visualAiEnabled = keyConfigured && values.getBoolean(VISUAL_AI_ENABLED, keyConfigured),
        keyConfigured = keyConfigured,
        keyStatus = if (keyConfigured) "Key tested and saved" else "No key saved",
        themeMode = readThemeMode(),
        styleProfile = values.getString(STYLE_PROFILE, "").orEmpty(),
        autoCaptureEnabled = values.getBoolean(AUTO_CAPTURE_ENABLED, true),
        visualProvider = readVisualProvider(),
        captionConsentGiven = values.getBoolean(CAPTION_CONSENT_GIVEN, false),
        gridOverlayEnabled = values.getBoolean(GRID_OVERLAY_ENABLED, false),
        tiltIndicatorEnabled = values.getBoolean(TILT_INDICATOR_ENABLED, false),
    )

    override fun setSpokenGuidance(enabled: Boolean) = put(SPOKEN_GUIDANCE, enabled)
    override fun setHaptics(enabled: Boolean) = put(HAPTICS, enabled)
    override fun setTechnicalDetail(enabled: Boolean) = put(TECHNICAL_DETAIL, enabled)
    override fun setVisualAiEnabled(enabled: Boolean) = put(VISUAL_AI_ENABLED, enabled)

    override fun setThemeMode(mode: ThemeMode) = values.edit().putString(THEME_MODE, mode.name).apply()

    override fun setStyleProfile(profile: String) =
        values.edit().putString(STYLE_PROFILE, profile.take(MAX_STYLE_PROFILE_CHARACTERS)).apply()

    override fun setVisualProvider(provider: VisualProvider) =
        values.edit().putString(VISUAL_PROVIDER, provider.name).apply()

    override fun autoCaptureEnabled(): Boolean = values.getBoolean(AUTO_CAPTURE_ENABLED, true)
    override fun setAutoCaptureEnabled(enabled: Boolean) = put(AUTO_CAPTURE_ENABLED, enabled)

    override fun hasUsedVoice(): Boolean = values.getBoolean(HAS_USED_VOICE, false)
    override fun setHasUsedVoice() = put(HAS_USED_VOICE, true)

    override fun captionConsentGiven(): Boolean = values.getBoolean(CAPTION_CONSENT_GIVEN, false)
    override fun setCaptionConsentGiven(given: Boolean) = put(CAPTION_CONSENT_GIVEN, given)

    override fun galleryBannerDismissed(): Boolean = values.getBoolean(GALLERY_BANNER_DISMISSED, false)
    override fun setGalleryBannerDismissed(dismissed: Boolean) = put(GALLERY_BANNER_DISMISSED, dismissed)

    override fun gridOverlayEnabled(): Boolean = values.getBoolean(GRID_OVERLAY_ENABLED, false)
    override fun setGridOverlayEnabled(enabled: Boolean) = put(GRID_OVERLAY_ENABLED, enabled)

    override fun tiltIndicatorEnabled(): Boolean = values.getBoolean(TILT_INDICATOR_ENABLED, false)
    override fun setTiltIndicatorEnabled(enabled: Boolean) = put(TILT_INDICATOR_ENABLED, enabled)

    private fun readVisualProvider(): VisualProvider {
        val stored = values.getString(VISUAL_PROVIDER, null) ?: return VisualProvider.QWEN
        return VisualProvider.entries.firstOrNull { it.name == stored } ?: VisualProvider.QWEN
    }

    private fun readThemeMode(): ThemeMode {
        val stored = values.getString(THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    private fun put(key: String, value: Boolean) = values.edit().putBoolean(key, value).apply()

    private companion object {
        const val NAME = "photo_helper_preferences"
        const val ONBOARDING_COMPLETE = "onboarding_complete"
        const val FIRST_USE_HINT_SEEN = "first_use_hint_seen"
        const val SPOKEN_GUIDANCE = "spoken_guidance"
        const val HAPTICS = "haptics"
        const val TECHNICAL_DETAIL = "technical_detail"
        const val VISUAL_AI_ENABLED = "visual_ai_enabled"
        const val THEME_MODE = "theme_mode"
        const val STYLE_PROFILE = "style_profile"
        const val AUTO_CAPTURE_ENABLED = "auto_capture_enabled"
        const val VISUAL_PROVIDER = "visual_provider"
        const val HAS_USED_VOICE = "has_used_voice"
        const val CAPTION_CONSENT_GIVEN = "caption_consent_given"
        const val GALLERY_BANNER_DISMISSED = "gallery_banner_dismissed"
        const val GRID_OVERLAY_ENABLED = "grid_overlay_enabled"
        const val TILT_INDICATOR_ENABLED = "tilt_indicator_enabled"
    }
}
