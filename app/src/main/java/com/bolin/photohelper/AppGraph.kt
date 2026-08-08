package com.bolin.photohelper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bolin.photohelper.capture.CameraXSession
import com.bolin.photohelper.capture.CaptureViewModel
import com.bolin.photohelper.capture.Feedback
import com.bolin.photohelper.capture.UserPreferences
import com.bolin.photohelper.coach.DefaultCoachEngine
import com.bolin.photohelper.visual.DemoApiKeyStore
import com.bolin.photohelper.visual.BailianVisualClient
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
            val visualClient = BailianVisualClient()
            return CaptureViewModel(
                camera = session,
                coach = DefaultCoachEngine(),
                voice = AndroidVoiceIo(appContext),
                preferences = UserPreferences(appContext),
                hasApiKey = keyStore::hasKey,
                loadApiKey = keyStore::load,
                saveApiKey = keyStore::save,
                clearApiKey = keyStore::clear,
                interpretVisual = visualClient::interpret,
                interpretComplaint = visualClient::classify,
                createTestImage = ::neutralTestJpeg,
                feedback = ::performFeedback,
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
