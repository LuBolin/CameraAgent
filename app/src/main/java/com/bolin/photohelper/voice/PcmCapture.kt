package com.bolin.photohelper.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class CapturedPcm(
    val bytes: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int,
)

internal sealed interface PcmCaptureResult {
    data class Ready(val pcm: CapturedPcm) : PcmCaptureResult
    data object Unsupported : PcmCaptureResult
    data object NoSpeech : PcmCaptureResult
    data object Failed : PcmCaptureResult
}

internal class PcmCapture : AutoCloseable {
    private val lock = Any()
    private var activeRecord: AudioRecord? = null
    private var cancelled = false
    private var closed = false

    @SuppressLint("MissingPermission")
    suspend fun capture(onReady: () -> Unit): PcmCaptureResult {
        currentCoroutineContext().ensureActive()
        val audioRecord = createAudioRecord() ?: return PcmCaptureResult.Unsupported
        synchronized(lock) {
            if (closed || activeRecord != null) {
                audioRecord.release()
                return PcmCaptureResult.Failed
            }
            cancelled = false
            activeRecord = audioRecord
        }

        val maxBytes = audioRecord.sampleRate * audioRecord.channelCount * BYTES_PER_SAMPLE * MAX_CAPTURE_SECONDS
        val capturedBytes = ByteArray(maxBytes)
        val readChunkBytes = max(MIN_READ_BUFFER_BYTES, audioRecord.bufferSizeInFrames * BYTES_PER_SAMPLE)
        var capturedSize = 0
        var rawPcm: ByteArray? = null

        return try {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                PcmCaptureResult.Failed
            } else {
                onReady()
                val readFailed = withContext(Dispatchers.IO) {
                    var failed = false
                    while (
                        currentCoroutineContext().isActive &&
                        !isCancelled() &&
                        audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING &&
                        capturedSize < maxBytes
                    ) {
                        val count = audioRecord.read(
                            capturedBytes,
                            capturedSize,
                            min(readChunkBytes, maxBytes - capturedSize),
                            AudioRecord.READ_NON_BLOCKING,
                        )
                        when {
                            count > 0 -> {
                                capturedSize += count - count % BYTES_PER_SAMPLE
                            }
                            count == 0 -> delay(5)
                            audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING || isCancelled() -> Unit
                            else -> {
                                failed = true
                                break
                            }
                        }
                    }
                    failed
                }
                when {
                    readFailed -> PcmCaptureResult.Failed
                    isCancelled() -> PcmCaptureResult.Failed
                    else -> {
                        val raw = capturedBytes.copyOf(capturedSize)
                        rawPcm = raw
                        val trimmed = trimPcm16Mono(raw, audioRecord.sampleRate)
                        if (trimmed.isEmpty()) {
                            PcmCaptureResult.NoSpeech
                        } else {
                            PcmCaptureResult.Ready(
                                CapturedPcm(
                                    bytes = trimmed,
                                    sampleRate = audioRecord.sampleRate,
                                    channelCount = audioRecord.channelCount,
                                    encoding = audioRecord.audioFormat,
                                ),
                            )
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            PcmCaptureResult.Failed
        } catch (_: IllegalStateException) {
            PcmCaptureResult.Failed
        } catch (_: SecurityException) {
            PcmCaptureResult.Failed
        } finally {
            stopSafely(audioRecord)
            audioRecord.release()
            synchronized(lock) {
                if (activeRecord === audioRecord) activeRecord = null
            }
            capturedBytes.fill(0)
            rawPcm?.fill(0)
        }
    }

    fun finish() {
        val record = synchronized(lock) { activeRecord }
        if (record != null) stopSafely(record)
    }

    fun cancel() {
        val record = synchronized(lock) {
            cancelled = true
            activeRecord
        }
        if (record != null) stopSafely(record)
    }

    override fun close() {
        val record = synchronized(lock) {
            closed = true
            cancelled = true
            activeRecord
        }
        if (record != null) stopSafely(record)
    }

    private fun isCancelled(): Boolean = synchronized(lock) { cancelled }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        for (sampleRate in SAMPLE_RATE_CANDIDATES) {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) continue
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(max(minBuffer * 2, MIN_READ_BUFFER_BYTES))
                    .setPrivacySensitive(true)
                    .build()
            }.getOrNull() ?: continue
            if (
                record.state == AudioRecord.STATE_INITIALIZED &&
                record.channelCount == 1 &&
                record.audioFormat == AudioFormat.ENCODING_PCM_16BIT
            ) {
                return record
            }
            record.release()
        }
        return null
    }

    private fun stopSafely(record: AudioRecord) {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { record.stop() }
        }
    }

    private companion object {
        val SAMPLE_RATE_CANDIDATES = intArrayOf(16_000, 44_100, 48_000)
        const val BYTES_PER_SAMPLE = 2
        const val MAX_CAPTURE_SECONDS = 15
        const val MIN_READ_BUFFER_BYTES = 3_200
    }
}

internal fun trimPcm16Mono(pcm: ByteArray, sampleRate: Int): ByteArray {
    if (sampleRate <= 0 || pcm.size < 2) return ByteArray(0)
    val sampleCount = pcm.size / 2
    val frameSamples = max(1, sampleRate / 50)
    val frameCount = ceil(sampleCount.toDouble() / frameSamples).toInt()
    if (frameCount < SUSTAINED_WINDOW_FRAMES) return ByteArray(0)

    val rms = DoubleArray(frameCount)
    for (frame in 0 until frameCount) {
        val startSample = frame * frameSamples
        val endSample = min(sampleCount, startSample + frameSamples)
        var sumSquares = 0.0
        for (sampleIndex in startSample until endSample) {
            val byteIndex = sampleIndex * 2
            val value = (
                (pcm[byteIndex].toInt() and 0xFF) or
                    (pcm[byteIndex + 1].toInt() shl 8)
                ).toShort().toInt()
            sumSquares += value.toDouble() * value.toDouble()
        }
        rms[frame] = sqrt(sumSquares / max(1, endSample - startSample))
    }

    val sorted = rms.sortedArray()
    val noiseFloor = sorted[(sorted.lastIndex * NOISE_PERCENTILE).toInt()]
    val peak = sorted.last()
    if (peak < MIN_SPEECH_RMS) return ByteArray(0)
    if (peak < noiseFloor * MIN_PEAK_TO_NOISE_RATIO) return pcm.copyOf()
    val activeThreshold = max(
        MIN_ACTIVE_RMS,
        max(noiseFloor * NOISE_MULTIPLIER, peak * PEAK_FRACTION),
    )

    fun sustained(windowStart: Int): Boolean {
        var active = 0
        for (frame in windowStart until windowStart + SUSTAINED_WINDOW_FRAMES) {
            if (rms[frame] >= activeThreshold) active++
        }
        return active >= REQUIRED_ACTIVE_FRAMES
    }

    val lastWindowStart = frameCount - SUSTAINED_WINDOW_FRAMES
    val first = (0..lastWindowStart).firstOrNull(::sustained) ?: return pcm.copyOf()
    val last = (lastWindowStart downTo first).firstOrNull(::sustained) ?: return pcm.copyOf()
    val leadingPaddingFrames = ceil(LEADING_PADDING_SECONDS * 50).toInt()
    val trailingPaddingFrames = ceil(TRAILING_PADDING_SECONDS * 50).toInt()
    val startFrame = max(0, first - leadingPaddingFrames)
    val endFrame = min(frameCount, last + SUSTAINED_WINDOW_FRAMES + trailingPaddingFrames)
    val startByte = startFrame * frameSamples * 2
    val endByte = min(sampleCount, endFrame * frameSamples) * 2
    return pcm.copyOfRange(startByte, endByte)
}

private const val SUSTAINED_WINDOW_FRAMES = 5
private const val REQUIRED_ACTIVE_FRAMES = 3
private const val NOISE_PERCENTILE = 0.20
private const val MIN_PEAK_TO_NOISE_RATIO = 2.0
private const val NOISE_MULTIPLIER = 2.0
private const val PEAK_FRACTION = 0.05
private const val MIN_SPEECH_RMS = 30.0
private const val MIN_ACTIVE_RMS = 20.0
private const val LEADING_PADDING_SECONDS = 0.30
private const val TRAILING_PADDING_SECONDS = 0.45
