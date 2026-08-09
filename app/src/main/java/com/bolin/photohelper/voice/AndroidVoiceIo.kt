package com.bolin.photohelper.voice

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class AndroidVoiceIo(context: Context) : VoiceIo {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var finishListeningGate: FinishListeningGate? = null
    private val pcmCapture = PcmCapture()
    private var ttsReady = false
    private val tts = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            selectOfflineVoice()
        }
    }

    override fun isOnDeviceRecognitionAvailable(): Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    override suspend fun listenOnce(locale: Locale): VoiceResult = withContext(Dispatchers.Main.immediate) {
        if (!isOnDeviceRecognitionAvailable()) {
            return@withContext VoiceResult.Unavailable("On-device speech recognition is unavailable. Type your comment instead.")
        }
        val languageTag = preferredEnglishRecognitionLocale(locale).toLanguageTag()
        val supportIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        val captureResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            captureForRecognition()
        } else {
            PcmCaptureResult.Unsupported
        }
        val capturedPcm = when (captureResult) {
            is PcmCaptureResult.Ready -> captureResult.pcm
            PcmCaptureResult.Unsupported -> null
            PcmCaptureResult.NoSpeech -> return@withContext VoiceResult.Failed("I didn’t catch that")
            PcmCaptureResult.Failed -> return@withContext VoiceResult.Failed(
                "Voice input is unavailable. Type your comment instead.",
            )
        }
        var pcmPipe: PcmPipe? = null
        try {
            if (capturedPcm != null) {
                pcmPipe = try {
                    PcmPipe(capturedPcm.bytes)
                } catch (_: IOException) {
                    return@withContext VoiceResult.Failed(
                        "Voice input is unavailable. Type your comment instead.",
                    )
                }
            }
            val activePipe = pcmPipe
            val recognitionIntent = Intent(supportIntent).apply {
                if (capturedPcm != null && activePipe != null) {
                    putCapturedAudioSource(capturedPcm, activePipe)
                }
            }
            suspendCancellableCoroutine<VoiceResult> { continuation ->
            val speechRecognizer = try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } catch (_: RuntimeException) {
                continuation.resume(VoiceResult.Unavailable("On-device speech recognition could not start. Type your comment instead."))
                return@suspendCancellableCoroutine
            }
            finishListeningGate?.close()
            finishListeningGate = null
            recognizer?.destroy()
            recognizer = speechRecognizer
            var finished = false
            var maxRmsDb = Float.NEGATIVE_INFINITY
            var lastPartialText: String? = null
            val sessionFinishGate = if (capturedPcm == null) {
                FinishListeningGate {
                    if (!finished && recognizer === speechRecognizer) {
                        try {
                            traceVoice("finish_requested")
                            speechRecognizer.stopListening()
                        } catch (_: RuntimeException) {
                            speechRecognizer.cancel()
                        }
                    }
                }
            } else {
                null
            }
            if (sessionFinishGate != null) finishListeningGate = sessionFinishGate

            fun finish(result: VoiceResult) {
                if (finished) return
                finished = true
                sessionFinishGate?.close()
                if (sessionFinishGate != null && finishListeningGate === sessionFinishGate) {
                    finishListeningGate = null
                }
                speechRecognizer.destroy()
                if (recognizer === speechRecognizer) recognizer = null
                if (continuation.isActive) continuation.resume(result)
            }

            fun startListening() {
                if (finished) return
                try {
                    traceVoice("start_requested")
                    speechRecognizer.startListening(recognitionIntent)
                    pcmPipe?.startWriting()
                } catch (_: RuntimeException) {
                    finish(VoiceResult.Failed("On-device speech recognition could not start. Type your comment instead."))
                }
            }

            fun requestModelDownload() {
                if (finished) return
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    finish(VoiceResult.Unavailable("The on-device English model is unavailable. Type your comment instead."))
                    return
                }
                try {
                    speechRecognizer.triggerModelDownload(supportIntent)
                    Handler(Looper.getMainLooper()).post {
                        finish(VoiceResult.Unavailable("Android is preparing the on-device English model. Try the microphone again after the download finishes."))
                    }
                } catch (_: RuntimeException) {
                    finish(VoiceResult.Unavailable("The on-device English model could not be downloaded. Type your comment instead."))
                }
            }

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val candidates = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = selectRecognitionText(candidates, lastPartialText)
                    traceVoice(
                        "results count=${candidates?.size ?: 0} nonEmpty=${!text.isNullOrEmpty()} " +
                            "partialFallback=${candidates.isNullOrEmpty() && !lastPartialText.isNullOrEmpty()} maxRmsDb=$maxRmsDb",
                    )
                    finish(
                        if (text.isNullOrEmpty()) {
                            VoiceResult.Failed("I didn’t catch that")
                        } else {
                            VoiceResult.Heard(normalizeVoiceComplaint(text))
                        }
                    )
                }

                override fun onError(error: Int) {
                    traceVoice("error code=$error maxRmsDb=$maxRmsDb")
                    when (error) {
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> requestModelDownload()
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> finish(
                            VoiceResult.Unavailable("This device’s on-device recognizer does not support English. Type your comment instead."),
                        )
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            finish(VoiceResult.Failed("I didn’t catch that"))
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            finish(VoiceResult.Failed("Speech recognition is busy. Try again or type your comment."))
                        else -> finish(VoiceResult.Failed("Voice input is unavailable. Type your comment instead."))
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    traceVoice(if (capturedPcm == null) "ready source=ANDROID_RECOGNIZER" else "decode_ready source=APP_PCM_CAPTURE")
                    sessionFinishGate?.onReady()
                }
                override fun onBeginningOfSpeech() {
                    traceVoice("speech_begin")
                }
                override fun onRmsChanged(rmsdB: Float) {
                    if (rmsdB.isFinite() && rmsdB > maxRmsDb) maxRmsDb = rmsdB
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    traceVoice("speech_end maxRmsDb=$maxRmsDb")
                    sessionFinishGate?.close()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { lastPartialText = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            continuation.invokeOnCancellation {
                runOnMain {
                    if (!finished) {
                        finished = true
                        sessionFinishGate?.close()
                        if (sessionFinishGate != null && finishListeningGate === sessionFinishGate) {
                            finishListeningGate = null
                        }
                        speechRecognizer.cancel()
                        speechRecognizer.destroy()
                        if (recognizer === speechRecognizer) recognizer = null
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    speechRecognizer.checkRecognitionSupport(
                        supportIntent,
                        appContext.mainExecutor,
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                                when (
                                    onDeviceLanguageState(
                                        languageTag,
                                        recognitionSupport.installedOnDeviceLanguages,
                                        recognitionSupport.pendingOnDeviceLanguages,
                                        recognitionSupport.supportedOnDeviceLanguages,
                                    )
                                ) {
                                    OnDeviceLanguageState.INSTALLED -> startListening()
                                    OnDeviceLanguageState.PENDING -> finish(
                                        VoiceResult.Unavailable("Android is preparing the on-device English model. Try the microphone again after the download finishes."),
                                    )
                                    OnDeviceLanguageState.DOWNLOADABLE -> requestModelDownload()
                                    OnDeviceLanguageState.UNSUPPORTED -> finish(
                                        VoiceResult.Unavailable("This device’s on-device recognizer does not support English. Type your comment instead."),
                                    )
                                }
                            }

                            override fun onError(error: Int) {
                                when (error) {
                                    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> startListening()
                                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> requestModelDownload()
                                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> finish(
                                        VoiceResult.Unavailable("This device’s on-device recognizer does not support English. Type your comment instead."),
                                    )
                                    else -> finish(VoiceResult.Failed("On-device speech support could not be checked. Type your comment instead."))
                                }
                            }
                        },
                    )
                } catch (_: RuntimeException) {
                    startListening()
                }
            } else {
                startListening()
            }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (pcmPipe?.closeAndJoin() == false) traceVoice("pcm_writer_shutdown_timeout")
            }
            capturedPcm?.bytes?.fill(0)
        }
    }

    private suspend fun captureForRecognition(): PcmCaptureResult {
        val captureGate = FinishListeningGate {
            traceVoice("finish_requested source=APP_PCM_CAPTURE")
            pcmCapture.finish()
        }
        finishListeningGate?.close()
        finishListeningGate = captureGate
        return try {
            pcmCapture.capture {
                traceVoice("ready source=APP_PCM_CAPTURE")
                captureGate.onReady()
            }.also { result ->
                traceVoice(
                    when (result) {
                        is PcmCaptureResult.Ready -> "capture_complete speech=true"
                        PcmCaptureResult.NoSpeech -> "capture_complete speech=false"
                        PcmCaptureResult.Unsupported -> "capture_unavailable"
                        PcmCaptureResult.Failed -> "capture_failed"
                    },
                )
            }
        } finally {
            captureGate.close()
            if (finishListeningGate === captureGate) finishListeningGate = null
        }
    }

    override fun speak(text: String, utteranceId: String) {
        if (ttsReady && tts.voice?.isNetworkConnectionRequired == false) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    override fun finishListening() {
        runOnMain {
            finishListeningGate?.request()
        }
    }

    override fun stop() {
        runOnMain {
            finishListeningGate?.close()
            finishListeningGate = null
            pcmCapture.cancel()
            recognizer?.cancel()
            tts.stop()
        }
    }

    override fun close() {
        runOnMain {
            finishListeningGate?.close()
            finishListeningGate = null
            pcmCapture.close()
            recognizer?.destroy()
            recognizer = null
            tts.shutdown()
        }
    }

    private fun selectOfflineVoice() {
        val voice: Voice? = tts.voices
            ?.filter { !it.isNetworkConnectionRequired && it.locale.language == Locale.ENGLISH.language }
            ?.maxByOrNull { it.quality }
        if (voice != null) tts.voice = voice
    }

    private fun traceVoice(message: String) {
        if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.i("PhotoHelperVoice", message)
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else Handler(Looper.getMainLooper()).post { action() }
    }
}

@TargetApi(Build.VERSION_CODES.TIRAMISU)
private fun Intent.putCapturedAudioSource(pcm: CapturedPcm, pipe: PcmPipe) {
    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe.readDescriptor)
    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, pcm.channelCount)
    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, pcm.encoding)
    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.sampleRate)
}

private class PcmPipe(private val bytes: ByteArray) {
    private val descriptors = ParcelFileDescriptor.createPipe()
    val readDescriptor: ParcelFileDescriptor = descriptors[0]
    private val writeDescriptor: ParcelFileDescriptor = descriptors[1]
    private var writer: Thread? = null
    @Volatile private var closing = false

    @Synchronized
    fun startWriting() {
        if (writer != null) return
        writer = Thread({
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeDescriptor).use { output ->
                    var offset = 0
                    while (!closing && offset < bytes.size) {
                        val count = minOf(PIPE_WRITE_CHUNK_BYTES, bytes.size - offset)
                        output.write(bytes, offset, count)
                        offset += count
                    }
                }
            }
        }, "photo-helper-pcm-writer").apply {
            isDaemon = true
            start()
        }
    }

    fun closeAndJoin(): Boolean {
        closing = true
        runCatching { readDescriptor.close() }
        runCatching { writeDescriptor.close() }
        writer?.join(3_000)
        return writer?.isAlive != true
    }

    private companion object {
        const val PIPE_WRITE_CHUNK_BYTES = 4_096
    }
}

internal class FinishListeningGate(private val dispatch: () -> Unit) {
    private var requested = false
    private var ready = false
    private var dispatched = false
    private var closed = false

    fun request() {
        if (closed) return
        requested = true
        dispatchIfReady()
    }

    fun onReady() {
        if (closed) return
        ready = true
        dispatchIfReady()
    }

    fun close() {
        closed = true
    }

    private fun dispatchIfReady() {
        if (!requested || !ready || dispatched || closed) return
        dispatched = true
        dispatch()
    }
}

internal enum class OnDeviceLanguageState { INSTALLED, PENDING, DOWNLOADABLE, UNSUPPORTED }

internal fun preferredEnglishRecognitionLocale(deviceLocale: Locale): Locale =
    deviceLocale.takeIf { it.language.equals(Locale.ENGLISH.language, ignoreCase = true) } ?: Locale.US

internal fun normalizeVoiceComplaint(text: String): String {
    val trimmed = text.trim()
    return when (trimmed.lowercase(Locale.US)) {
        "two dim" -> "too dim"
        "two blue" -> "too blue"
        else -> trimmed
    }
}

internal fun selectRecognitionText(finalCandidates: List<String>?, lastPartialText: String?): String? =
    finalCandidates
        ?.firstOrNull()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: lastPartialText?.trim()?.takeIf(String::isNotEmpty)

internal fun onDeviceLanguageState(
    requestedLanguageTag: String,
    installed: List<String>,
    pending: List<String>,
    supported: List<String>,
): OnDeviceLanguageState {
    fun List<String>.containsRequested() = any { it.equals(requestedLanguageTag, ignoreCase = true) }
    return when {
        installed.containsRequested() -> OnDeviceLanguageState.INSTALLED
        pending.containsRequested() -> OnDeviceLanguageState.PENDING
        supported.containsRequested() -> OnDeviceLanguageState.DOWNLOADABLE
        else -> OnDeviceLanguageState.UNSUPPORTED
    }
}
