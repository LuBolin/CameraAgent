package com.bolin.photohelper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bolin.photohelper.arcore.ArSessionManager
import com.bolin.photohelper.capture.CameraXSession
import com.bolin.photohelper.capture.CaptureViewModel
import com.bolin.photohelper.capture.Feedback
import com.bolin.photohelper.capture.SoundPoolCuePlayer
import com.bolin.photohelper.capture.UserPreferences
import com.bolin.photohelper.coach.DefaultCoachEngine
import com.bolin.photohelper.visual.DemoApiKeyStore
import com.bolin.photohelper.visual.BailianVisualClient
import com.bolin.photohelper.visual.ClaudeVisualClient
import com.bolin.photohelper.visual.VisualProvider
import com.bolin.photohelper.voice.AndroidVoiceIo
import java.io.ByteArrayOutputStream

class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    fun viewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CaptureViewModel::class.java)
            val session = CameraXSession(appContext)
            val keyStore = DemoApiKeyStore(appContext)
            val preferences = UserPreferences(appContext)
            // Both clients are constructed; which one runs is decided per call from the
            // stored preference, so switching providers needs no restart.
            val qwen = BailianVisualClient()
            val claude = ClaudeVisualClient()
            // A key pasted into settings always wins; the .env value is the fallback so
            // a fresh install of a dev build just works for whichever provider is picked.
            fun envKey(provider: VisualProvider): String = when (provider) {
                VisualProvider.QWEN -> BuildConfig.DASHSCOPE_API_KEY
                VisualProvider.CLAUDE -> BuildConfig.ANTHROPIC_API_KEY
            }
            // With exactly one provider keyed in .env, start on that one - otherwise a
            // key in .env looks broken until you also find the radio button.
            val impliedProvider = when {
                BuildConfig.ANTHROPIC_API_KEY.isNotBlank() && BuildConfig.DASHSCOPE_API_KEY.isBlank() ->
                    VisualProvider.CLAUDE
                else -> VisualProvider.QWEN
            }
            fun provider(): VisualProvider = preferences.settings(
                keyConfigured = keyStore.hasKey() ||
                    envKey(VisualProvider.QWEN).isNotBlank() ||
                    envKey(VisualProvider.CLAUDE).isNotBlank(),
                defaultProvider = impliedProvider,
            ).visualProvider
            fun hasKey(): Boolean = keyStore.hasKey() || envKey(provider()).isNotBlank()
            fun loadKey(): CharArray? =
                keyStore.load() ?: envKey(provider()).takeIf { it.isNotBlank() }?.toCharArray()
            val arSession = ArSessionManager(appContext).apply { checkAvailability() }
            val audioCue = SoundPoolCuePlayer(appContext)
            return CaptureViewModel(
                camera = session,
                coach = DefaultCoachEngine(),
                voice = AndroidVoiceIo(appContext),
                preferences = preferences,
                hasApiKey = ::hasKey,
                defaultVisualProvider = impliedProvider,
                loadApiKey = ::loadKey,
                saveApiKey = keyStore::save,
                clearApiKey = keyStore::clear,
                interpretVisual = { request, key ->
                    when (provider()) {
                        VisualProvider.QWEN -> qwen.interpret(request, key)
                        VisualProvider.CLAUDE -> claude.interpret(request, key)
                    }
                },
                interpretCommand = { request, key ->
                    when (provider()) {
                        VisualProvider.QWEN -> qwen.plan(request, key)
                        VisualProvider.CLAUDE -> claude.plan(request, key)
                    }
                },
                createTestImage = ::neutralTestJpeg,
                feedback = ::performFeedback,
                arSession = arSession,
                audioCue = audioCue,
            ) as T
        }
    }

    private fun performFeedback(feedback: Feedback) {
        val vibrator = appContext.getSystemService(VibratorManager::class.java).defaultVibrator
        val effect = when (feedback) {
            Feedback.TICK -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
            Feedback.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 80), -1)
            Feedback.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 70, 40, 70), -1)
        }
        vibrator.vibrate(effect)
    }

    private fun neutralTestJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(128, 128, 128))
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
