package com.bolin.photohelper.capture

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

internal const val LIVE_OBSERVATION_FRESH_MS = 750L

data class FaceObservation(
    val trackingId: Int?,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val visibleFraction: Float = 1f,
) {
    val widthFraction: Float get() = (right - left).coerceIn(0f, 1f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class FrameObservation(
    val id: Long,
    val timestampMs: Long,
    val meanLuma: Float,
    val highlightClipFraction: Float,
    val shadowClipFraction: Float,
    val chromaBlueBias: Float? = null,
    val faces: List<FaceObservation> = emptyList(),
    val deviceRollDegrees: Float? = null,
    val motionScore: Float = 0f,
    val sceneLumaSignature: List<Int> = emptyList(),
    val lensId: String? = null,
    val focalLengthMm: Float? = null,
    val zoomRatio: Float? = null,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

internal fun exposureInvariantSceneDifference(previous: List<Int>?, current: List<Int>): Float {
    if (previous == null || previous.isEmpty() || previous.size != current.size) return 0f
    val meanDelta = previous.indices.sumOf { index -> current[index] - previous[index] }.toFloat() / previous.size
    return previous.indices.sumOf { index ->
        abs((current[index] - previous[index]) - meanDelta).toDouble()
    }.toFloat() / previous.size / 255f
}

enum class WhiteBalancePreset { AUTO, WARMER, COOLER }

internal const val MAX_WHITE_BALANCE_LEVEL = 3

internal fun whiteBalanceLevelForPreset(preset: WhiteBalancePreset): Int = when (preset) {
    WhiteBalancePreset.AUTO -> 0
    WhiteBalancePreset.WARMER -> 1
    WhiteBalancePreset.COOLER -> -1
}

internal fun whiteBalancePresetForLevel(level: Int): WhiteBalancePreset = when {
    level > 0 -> WhiteBalancePreset.WARMER
    level < 0 -> WhiteBalancePreset.COOLER
    else -> WhiteBalancePreset.AUTO
}

private fun whiteBalanceLevelsForPresets(presets: Set<WhiteBalancePreset>): Set<Int> = buildSet {
    if (WhiteBalancePreset.AUTO in presets) add(0)
    if (WhiteBalancePreset.WARMER in presets) addAll(1..MAX_WHITE_BALANCE_LEVEL)
    if (WhiteBalancePreset.COOLER in presets) addAll(-MAX_WHITE_BALANCE_LEVEL..-1)
}

enum class FlashMode { OFF, ON, TORCH }

data class CameraCapabilities(
    val exposureCompensationRange: IntRange = IntRange.EMPTY,
    val exposureCompensationStepEv: Float = 0f,
    val zoomRatioRange: ClosedFloatingPointRange<Float> = 1f..1f,
    val supportedWhiteBalancePresets: Set<WhiteBalancePreset> = emptySet(),
    val supportsFocusMetering: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val supportedWhiteBalanceLevels: Set<Int> = whiteBalanceLevelsForPresets(supportedWhiteBalancePresets),
) {
    val supportsExposureCompensation: Boolean
        get() = !exposureCompensationRange.isEmpty() && exposureCompensationStepEv > 0f
}

data class CameraTelemetry(
    val exposureCompensationIndex: Int = 0,
    val zoomRatio: Float = 1f,
    val whiteBalancePreset: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val lensId: String? = null,
    val focalLengthMm: Float? = null,
    val iso: Int? = null,
    val exposureTimeNanos: Long? = null,
    val whiteBalanceLevel: Int = whiteBalanceLevelForPreset(whiteBalancePreset),
)

internal fun capturedTelemetryOrNull(
    exposureCompensationIndex: Int?,
    zoomRatio: Float?,
    whiteBalancePreset: WhiteBalancePreset?,
    whiteBalanceLevel: Int = whiteBalancePreset?.let(::whiteBalanceLevelForPreset) ?: 0,
    lensId: String?,
    focalLengthMm: Float?,
    iso: Int?,
    exposureTimeNanos: Long?,
): CameraTelemetry? {
    if (exposureCompensationIndex == null || zoomRatio == null || !zoomRatio.isFinite() || zoomRatio <= 0f ||
        whiteBalancePreset == null || lensId.isNullOrBlank() || focalLengthMm == null ||
        !focalLengthMm.isFinite() || focalLengthMm <= 0f
    ) return null
    return CameraTelemetry(
        exposureCompensationIndex = exposureCompensationIndex,
        zoomRatio = zoomRatio,
        whiteBalancePreset = whiteBalancePreset,
        whiteBalanceLevel = whiteBalanceLevel,
        lensId = lensId,
        focalLengthMm = focalLengthMm,
        iso = iso?.takeIf { it > 0 },
        exposureTimeNanos = exposureTimeNanos?.takeIf { it > 0 },
    )
}

sealed interface CameraAdjustment {
    data class ExposureCompensation(val targetIndex: Int) : CameraAdjustment
    data class ZoomRatio(val ratio: Float) : CameraAdjustment
    data class WhiteBalance(
        val preset: WhiteBalancePreset,
        val targetLevel: Int = whiteBalanceLevelForPreset(preset),
    ) : CameraAdjustment {
        init {
            require(targetLevel in -MAX_WHITE_BALANCE_LEVEL..MAX_WHITE_BALANCE_LEVEL)
        }
    }
}

enum class CameraPhase { STARTING, READY, CAPTURING, REVIEWING, BLOCKED }

data class CameraState(
    val phase: CameraPhase = CameraPhase.STARTING,
    val message: String? = null,
    val sessionId: Long = 0,
)

data class SavedCapture(
    val id: String,
    val uri: String,
    val observation: FrameObservation?,
    val telemetry: CameraTelemetry?,
)

sealed interface ApplyResult {
    data object Applied : ApplyResult
    data class Failed(val message: String) : ApplyResult
}

sealed interface CaptureResult {
    data class Saved(val capture: SavedCapture) : CaptureResult
    data class Failed(val message: String) : CaptureResult
}

interface CaptureHardware : AutoCloseable {
    val state: StateFlow<CameraState>
    val capabilities: StateFlow<CameraCapabilities>
    val telemetry: StateFlow<CameraTelemetry>
    val observation: StateFlow<FrameObservation?>

    suspend fun apply(adjustment: CameraAdjustment): ApplyResult
    suspend fun applyAtomically(adjustments: List<CameraAdjustment>): ApplyResult =
        if (adjustments.size == 1) apply(adjustments.single())
        else ApplyResult.Failed("This camera cannot apply multiple settings atomically")
    suspend fun focusAt(xFraction: Float, yFraction: Float): ApplyResult =
        ApplyResult.Failed("Tap to focus is unavailable on this camera")
    suspend fun setFlashMode(mode: FlashMode): ApplyResult =
        ApplyResult.Failed("Flash is unavailable on this camera")
    suspend fun reset(): ApplyResult
    suspend fun capture(): CaptureResult
    suspend fun observationImage(capture: SavedCapture? = null): ByteArray?
    fun setAnalysisPaused(paused: Boolean)
    fun setObservationImageEnabled(enabled: Boolean) = Unit
}
