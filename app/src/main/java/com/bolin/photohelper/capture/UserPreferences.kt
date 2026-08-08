package com.bolin.photohelper.capture

import android.content.Context

interface PreferenceStore {
    fun onboardingComplete(): Boolean
    fun setOnboardingComplete()
    fun settings(keyConfigured: Boolean): SettingsUiState
    fun setSpokenGuidance(enabled: Boolean)
    fun setHaptics(enabled: Boolean)
    fun setTechnicalDetail(enabled: Boolean)
    fun setVisualAiEnabled(enabled: Boolean)
}

class UserPreferences(context: Context) : PreferenceStore {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun onboardingComplete(): Boolean = values.getBoolean(ONBOARDING_COMPLETE, false)
    override fun setOnboardingComplete() = values.edit().putBoolean(ONBOARDING_COMPLETE, true).apply()

    override fun settings(keyConfigured: Boolean): SettingsUiState = SettingsUiState(
        spokenGuidance = values.getBoolean(SPOKEN_GUIDANCE, true),
        haptics = values.getBoolean(HAPTICS, true),
        technicalDetail = values.getBoolean(TECHNICAL_DETAIL, false),
        visualAiEnabled = keyConfigured && values.getBoolean(VISUAL_AI_ENABLED, false),
        keyConfigured = keyConfigured,
        keyStatus = if (keyConfigured) "Key tested and saved" else "No key saved",
    )

    override fun setSpokenGuidance(enabled: Boolean) = put(SPOKEN_GUIDANCE, enabled)
    override fun setHaptics(enabled: Boolean) = put(HAPTICS, enabled)
    override fun setTechnicalDetail(enabled: Boolean) = put(TECHNICAL_DETAIL, enabled)
    override fun setVisualAiEnabled(enabled: Boolean) = put(VISUAL_AI_ENABLED, enabled)

    private fun put(key: String, value: Boolean) = values.edit().putBoolean(key, value).apply()

    private companion object {
        const val NAME = "photo_helper_preferences"
        const val ONBOARDING_COMPLETE = "onboarding_complete"
        const val SPOKEN_GUIDANCE = "spoken_guidance"
        const val HAPTICS = "haptics"
        const val TECHNICAL_DETAIL = "technical_detail"
        const val VISUAL_AI_ENABLED = "visual_ai_enabled"
    }
}
