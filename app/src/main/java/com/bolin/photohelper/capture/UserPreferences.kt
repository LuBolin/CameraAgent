package com.bolin.photohelper.capture

import android.content.Context
import com.bolin.photohelper.ui.ThemeMode
import com.bolin.photohelper.visual.VisualProvider

interface PreferenceStore {
    fun onboardingComplete(): Boolean
    fun setOnboardingComplete()
    fun firstUseHintSeen(): Boolean
    fun setFirstUseHintSeen()
    fun settings(keyConfigured: Boolean, defaultProvider: VisualProvider = VisualProvider.QWEN): SettingsUiState
    fun setSpokenGuidance(enabled: Boolean)
    fun setHaptics(enabled: Boolean)
    fun setTechnicalDetail(enabled: Boolean)
    fun setVisualAiEnabled(enabled: Boolean)
    fun setThemeMode(mode: ThemeMode)
    fun setStyleProfile(profile: String)
    fun setVisualProvider(provider: VisualProvider)
    fun autoCaptureEnabled(): Boolean
    fun setAutoCaptureEnabled(enabled: Boolean)
}

class UserPreferences(context: Context) : PreferenceStore {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun onboardingComplete(): Boolean = values.getBoolean(ONBOARDING_COMPLETE, false)
    override fun setOnboardingComplete() = values.edit().putBoolean(ONBOARDING_COMPLETE, true).apply()

    override fun firstUseHintSeen(): Boolean = values.getBoolean(FIRST_USE_HINT_SEEN, false)
    override fun setFirstUseHintSeen() = put(FIRST_USE_HINT_SEEN, true)

    override fun settings(keyConfigured: Boolean, defaultProvider: VisualProvider): SettingsUiState = SettingsUiState(
        spokenGuidance = values.getBoolean(SPOKEN_GUIDANCE, true),
        haptics = values.getBoolean(HAPTICS, true),
        technicalDetail = values.getBoolean(TECHNICAL_DETAIL, false),
        visualAiEnabled = keyConfigured && values.getBoolean(VISUAL_AI_ENABLED, keyConfigured),
        keyConfigured = keyConfigured,
        keyStatus = if (keyConfigured) "Key tested and saved" else "No key saved",
        themeMode = readThemeMode(),
        styleProfile = values.getString(STYLE_PROFILE, "").orEmpty(),
        visualProvider = readVisualProvider(defaultProvider),
        autoCaptureEnabled = values.getBoolean(AUTO_CAPTURE_ENABLED, true),
    )

    override fun setSpokenGuidance(enabled: Boolean) = put(SPOKEN_GUIDANCE, enabled)
    override fun setHaptics(enabled: Boolean) = put(HAPTICS, enabled)
    override fun setTechnicalDetail(enabled: Boolean) = put(TECHNICAL_DETAIL, enabled)
    override fun setVisualAiEnabled(enabled: Boolean) = put(VISUAL_AI_ENABLED, enabled)

    override fun setThemeMode(mode: ThemeMode) = values.edit().putString(THEME_MODE, mode.name).apply()

    override fun setStyleProfile(profile: String) =
        values.edit().putString(STYLE_PROFILE, profile.take(MAX_STYLE_PROFILE_CHARACTERS)).apply()

    override fun autoCaptureEnabled(): Boolean = values.getBoolean(AUTO_CAPTURE_ENABLED, true)
    override fun setAutoCaptureEnabled(enabled: Boolean) = put(AUTO_CAPTURE_ENABLED, enabled)

    override fun setVisualProvider(provider: VisualProvider) =
        values.edit().putString(VISUAL_PROVIDER, provider.name).apply()

    private fun readVisualProvider(default: VisualProvider): VisualProvider {
        val stored = values.getString(VISUAL_PROVIDER, null) ?: return default
        return VisualProvider.entries.firstOrNull { it.name == stored } ?: default
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
        const val VISUAL_PROVIDER = "visual_provider"
        const val AUTO_CAPTURE_ENABLED = "auto_capture_enabled"
    }
}
