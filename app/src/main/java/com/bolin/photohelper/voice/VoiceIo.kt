package com.bolin.photohelper.voice

import java.util.Locale

sealed interface VoiceResult {
    data class Heard(val text: String) : VoiceResult
    data class Unavailable(val message: String) : VoiceResult
    data class Failed(val message: String) : VoiceResult
}
interface VoiceIo : AutoCloseable {
    fun isOnDeviceRecognitionAvailable(): Boolean
    suspend fun listenOnce(locale: Locale = Locale.getDefault()): VoiceResult
    fun finishListening()
    fun speak(text: String, utteranceId: String)
    fun stop()
}
