package com.bolin.photohelper.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.ZoomState
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.hardware.camera2.CaptureResult as Camera2CaptureResult

internal fun gravityRollDegrees(x: Float, y: Float, displayRotation: Int): Float? {
    val (screenX, screenY) = when (displayRotation) {
        Surface.ROTATION_90 -> -y to x
        Surface.ROTATION_180 -> -x to -y
        Surface.ROTATION_270 -> y to -x
        else -> x to y
    }
    if (screenX * screenX + screenY * screenY < 1f) return null
    return Math.toDegrees(atan2(-screenX, screenY).toDouble()).toFloat()
}

internal fun physicalCameraChanged(previousId: String?, reportedId: String?): Boolean =
    reportedId != null && previousId != reportedId

internal fun controlBaselineMatchesPhysicalCamera(
    baseline: CameraTelemetry,
    activePhysicalCameraId: String?,
): Boolean = activePhysicalCameraId == null || baseline.lensId == activePhysicalCameraId

private val whiteBalanceModesByLevel = mapOf(
    -3 to CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
    -2 to CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT,
    -1 to CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
    0 to CaptureRequest.CONTROL_AWB_MODE_AUTO,
    1 to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
    2 to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
    3 to CaptureRequest.CONTROL_AWB_MODE_SHADE,
)

internal fun awbModeForLevel(availableModes: Set<Int>, level: Int): Int? =
    whiteBalanceModesByLevel[level]?.takeIf(availableModes::contains)

internal fun whiteBalanceLevelForAwbMode(mode: Int?): Int? =
    whiteBalanceModesByLevel.entries.firstOrNull { it.value == mode }?.key

internal fun whiteBalanceLevelsForModes(availableModes: Set<Int>): Set<Int> =
    whiteBalanceModesByLevel.filterValues(availableModes::contains).keys

internal fun whiteBalancePresetsForModes(availableModes: Set<Int>): Set<WhiteBalancePreset> =
    whiteBalanceLevelsForModes(availableModes).mapTo(mutableSetOf(), ::whiteBalancePresetForLevel)

internal class CameraControlTimeoutException : Exception("Camera control timed out. Try again.")

internal suspend fun <T> awaitCameraControl(block: suspend () -> T): T = try {
    withTimeout(CAMERA_CONTROL_TIMEOUT_MS) { block() }
} catch (_: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    throw CameraControlTimeoutException()
}

internal suspend fun <T> runCameraControlTransaction(
    commands: List<T>,
    isCurrent: () -> Boolean,
    applyCommand: suspend (T) -> Unit,
    rollback: suspend () -> Boolean,
    finishRollback: (Boolean) -> Unit,
    commit: () -> Unit,
): ApplyResult {
    return try {
        commands.forEach { command ->
            check(isCurrent()) { "Camera session changed. Check the shot and try again." }
            applyCommand(command)
        }
        currentCoroutineContext().ensureActive()
        check(isCurrent()) { "Camera session changed. Check the shot and try again." }
        commit()
        ApplyResult.Applied
    } catch (cancelled: CancellationException) {
        val restored = withContext(NonCancellable) { runCatching { rollback() }.getOrDefault(false) }
        finishRollback(restored)
        throw cancelled
    } catch (error: Throwable) {
        val restored = withContext(NonCancellable) { runCatching { rollback() }.getOrDefault(false) }
        finishRollback(restored)
        ApplyResult.Failed(
            if (restored) error.message ?: "Camera rejected the adjustment"
            else "Camera controls could not be restored. Retry the camera before shooting.",
        )
    }
}

internal fun validateAdjustmentBatchStructure(adjustments: List<CameraAdjustment>): String? {
    if (adjustments.size !in 1..3) return "Choose one to three camera settings"
    val axes = adjustments.map { adjustment ->
        when (adjustment) {
            is CameraAdjustment.ExposureCompensation -> "exposure"
            is CameraAdjustment.ZoomRatio -> "zoom"
            is CameraAdjustment.WhiteBalance -> "white-balance"
        }
    }
    return "A grouped change can adjust each camera setting only once"
        .takeIf { axes.distinct().size != axes.size }
}

internal fun captureTerminalState(
    current: CameraState,
    phase: CameraPhase,
    message: String? = null,
    sessionId: Long,
): CameraState = if (current.phase == CameraPhase.BLOCKED) current else CameraState(phase, message, sessionId)

@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
class CameraXSession(context: Context) : CaptureHardware, SensorEventListener {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "photo-helper-analysis")
    }
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build(),
    )
    private val detectorGate = Semaphore(1)
    private val operationMutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val pauseGate = AnalysisPauseGate()
    private val analysisPaused = AtomicBoolean(false)
    private val observationImages = ObservationImageGate()
    private val bindingGeneration = AtomicLong(0)
    private val cameraSessionId = AtomicLong(0)
    private val observationIds = AtomicLong(0)
    private val lastAnalysisMs = AtomicLong(0)
    private val pendingControl = AtomicReference<ListenableFuture<*>?>(null)
    private val pendingStillTelemetry = AtomicReference<CompletableDeferred<CameraTelemetry>?>(null)
    private val previousLumaSignature = AtomicReference<List<Int>?>(null)

    private val _state = MutableStateFlow(CameraState())
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    private val _capabilities = MutableStateFlow(CameraCapabilities())
    override val capabilities: StateFlow<CameraCapabilities> = _capabilities.asStateFlow()

    private val _telemetry = MutableStateFlow(CameraTelemetry())
    override val telemetry: StateFlow<CameraTelemetry> = _telemetry.asStateFlow()

    private val _observation = MutableStateFlow<FrameObservation?>(null)
    override val observation: StateFlow<FrameObservation?> = _observation.asStateFlow()

    @Volatile
    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile
    private var camera: Camera? = null

    @Volatile
    internal var activeCameraId: String? = null
        private set

    @Volatile
    internal var activeLensFacing: Int? = null
        private set

    internal var activeFocalLengthsMm: List<Float> = emptyList()
        private set

    internal var activePhysicalCameraIds: Set<String> = emptySet()
        private set

    @Volatile
    internal var activePhysicalCameraId: String? = null
        private set

    @Volatile
    internal var activeFocalLengthMm: Float? = null
        private set

    private val captureMetadataCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            val physicalCameraId = result.get(Camera2CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
            val focalLengthMm = result.get(Camera2CaptureResult.LENS_FOCAL_LENGTH)
            val exposureCompensationIndex = result.get(Camera2CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)
            val zoomRatio = result.get(Camera2CaptureResult.CONTROL_ZOOM_RATIO)
            val whiteBalanceLevel = whiteBalanceLevelForAwbMode(result.get(Camera2CaptureResult.CONTROL_AWB_MODE))
            val whiteBalancePreset = whiteBalanceLevel?.let(::whiteBalancePresetForLevel)
            val iso = result.get(Camera2CaptureResult.SENSOR_SENSITIVITY)
            val exposureTimeNanos = result.get(Camera2CaptureResult.SENSOR_EXPOSURE_TIME)
            val captureIntent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT)
            if (physicalCameraChanged(activePhysicalCameraId, physicalCameraId)) {
                val invalidatedControls = controlBaseline != null
                controlBaseline = null
                val newSessionId = cameraSessionId.incrementAndGet()
                _state.update {
                    if (invalidatedControls) {
                        CameraState(
                            CameraPhase.BLOCKED,
                            "The active camera lens changed. Retry the camera before shooting.",
                            newSessionId,
                        )
                    } else {
                        it.copy(sessionId = newSessionId)
                    }
                }
            }
            if (physicalCameraId != null) activePhysicalCameraId = physicalCameraId
            val lensId = activePhysicalCameraId ?: activeCameraId
            activeFocalLengthMm = focalLengthMm
            _telemetry.update {
                it.copy(
                    exposureCompensationIndex = exposureCompensationIndex ?: it.exposureCompensationIndex,
                    zoomRatio = zoomRatio ?: it.zoomRatio,
                    whiteBalancePreset = whiteBalancePreset ?: it.whiteBalancePreset,
                    whiteBalanceLevel = whiteBalanceLevel ?: it.whiteBalanceLevel,
                    lensId = lensId,
                    focalLengthMm = focalLengthMm ?: it.focalLengthMm,
                )
            }
            if (captureIntent == CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE ||
                captureIntent == CaptureRequest.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG ||
                captureIntent == CaptureRequest.CONTROL_CAPTURE_INTENT_MANUAL
            ) {
                capturedTelemetryOrNull(
                    exposureCompensationIndex = exposureCompensationIndex,
                    zoomRatio = zoomRatio,
                    whiteBalancePreset = whiteBalancePreset,
                    whiteBalanceLevel = whiteBalanceLevel ?: 0,
                    lensId = lensId,
                    focalLengthMm = focalLengthMm,
                    iso = iso,
                    exposureTimeNanos = exposureTimeNanos,
                )?.let { pendingStillTelemetry.get()?.complete(it) }
            }
        }
    }

    @Volatile
    private var previewUseCase: Preview? = null

    @Volatile
    private var imageCapture: ImageCapture? = null

    @Volatile
    private var imageAnalysis: ImageAnalysis? = null

    @Volatile
    private var focusPointFactory: MeteringPointFactory? = null

    @Volatile
    private var previewWidth = 0

    @Volatile
    private var previewHeight = 0

    @Volatile
    private var controlBaseline: CameraTelemetry? = null

    private var observedCameraInfo: CameraInfo? = null
    private var zoomObserver: Observer<ZoomState>? = null

    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val displayManager = appContext.getSystemService(DisplayManager::class.java)
    private var rollSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val remappedRotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    @Volatile
    private var latestRollDegrees: Float? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        viewPort: ViewPort,
        focusPointFactory: MeteringPointFactory,
        previewWidth: Int,
        previewHeight: Int,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    ) {
        if (closed.get()) {
            _state.value = CameraState(CameraPhase.BLOCKED, "Camera session is closed", cameraSessionId.get())
            return
        }

        val generation = bindingGeneration.incrementAndGet()
        val sessionId = cameraSessionId.incrementAndGet()
        pendingControl.getAndSet(null)?.cancel(true)
        pendingStillTelemetry.getAndSet(null)?.cancel()
        this.focusPointFactory = focusPointFactory
        this.previewWidth = previewWidth
        this.previewHeight = previewHeight
        _state.value = CameraState(CameraPhase.STARTING, sessionId = sessionId)
        _observation.value = null
        observationImages.invalidate()
        lastAnalysisMs.set(0)
        previousLumaSignature.set(null)
        analysisPaused.set(pauseGate.resetSession())
        stopRollSensor()
        controlBaseline = null
        activePhysicalCameraId = null
        activeFocalLengthMm = null

        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            if (closed.get() || bindingGeneration.get() != generation) return@addListener
            try {
                val provider = providerFuture.get()
                removeZoomObserver()
                provider.unbindAll()

                val targetRotation = displayRotation()
                val previewBuilder = Preview.Builder().setTargetRotation(targetRotation)
                val preview = previewBuilder.build().also {
                    it.setSurfaceProvider(surfaceProvider)
                }
                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(targetRotation)
                Camera2Interop.Extender(captureBuilder).setSessionCaptureCallback(captureMetadataCallback)
                val capture = captureBuilder.build()
                capture.flashMode = ImageCapture.FLASH_MODE_OFF
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(targetRotation)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { image -> analyze(image, generation) }
                    }

                val useCases = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .addUseCase(analysis)
                    .setViewPort(viewPort)
                    .build()
                val boundCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    useCases,
                )

                cameraProvider = provider
                camera = boundCamera
                previewUseCase = preview
                val camera2Info = Camera2CameraInfo.from(boundCamera.cameraInfo)
                activeCameraId = camera2Info.cameraId
                activeLensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                activeFocalLengthsMm = camera2Info
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.toList()
                    .orEmpty()
                activePhysicalCameraIds = runCatching {
                    appContext.getSystemService(CameraManager::class.java)
                        .getCameraCharacteristics(camera2Info.cameraId)
                        .physicalCameraIds
                }.getOrDefault(emptySet())
                imageCapture = capture
                imageAnalysis = analysis
                publishCameraInfo(boundCamera.cameraInfo)
                if (!analysisPaused.get()) startRollSensor()
                _state.update { current ->
                    captureTerminalState(current, CameraPhase.READY, sessionId = cameraSessionId.get())
                }
            } catch (error: Throwable) {
                previewUseCase = null
                imageAnalysis?.clearAnalyzer()
                imageAnalysis = null
                imageCapture = null
                camera = null
                activeCameraId = null
                activeLensFacing = null
                activeFocalLengthsMm = emptyList()
                activePhysicalCameraIds = emptySet()
                activePhysicalCameraId = null
                activeFocalLengthMm = null
                _capabilities.value = CameraCapabilities()
                _telemetry.value = CameraTelemetry()
                _state.value = CameraState(
                    CameraPhase.BLOCKED,
                    error.message ?: "Unable to start the selected camera",
                    cameraSessionId.get(),
                )
            }
        }, mainExecutor)
    }

    fun updateTargetRotation(rotation: Int) {
        if (rotation !in setOf(
                Surface.ROTATION_0,
                Surface.ROTATION_90,
                Surface.ROTATION_180,
                Surface.ROTATION_270,
            )
        ) return
        mainExecutor.execute {
            if (closed.get()) return@execute
            previewUseCase?.targetRotation = rotation
            imageCapture?.targetRotation = rotation
            imageAnalysis?.targetRotation = rotation
        }
    }

    fun unbind() {
        if (closed.get()) return
        bindingGeneration.incrementAndGet()
        val sessionId = cameraSessionId.incrementAndGet()
        pendingControl.getAndSet(null)?.cancel(true)
        pendingStillTelemetry.getAndSet(null)?.cancel()
        analysisPaused.set(true)
        stopRollSensor()
        observationImages.invalidate()
        _observation.value = null
        _capabilities.value = CameraCapabilities()
        _telemetry.value = CameraTelemetry()
        controlBaseline = null
        _state.value = CameraState(CameraPhase.STARTING, sessionId = sessionId)
        val releaseCamera = {
            previewUseCase = null
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            imageCapture = null
            camera = null
            activeCameraId = null
            activeLensFacing = null
            activeFocalLengthsMm = emptyList()
            activePhysicalCameraIds = emptySet()
            activePhysicalCameraId = null
            activeFocalLengthMm = null
            focusPointFactory = null
            previewWidth = 0
            previewHeight = 0
            removeZoomObserver()
            cameraProvider?.unbindAll()
            cameraProvider = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) releaseCamera() else mainExecutor.execute(releaseCamera)
    }

    override suspend fun apply(adjustment: CameraAdjustment): ApplyResult =
        applyAtomically(listOf(adjustment))

    override suspend fun applyAtomically(adjustments: List<CameraAdjustment>): ApplyResult = operationMutex.withLock {
        val activeCamera = camera ?: return@withLock ApplyResult.Failed("Camera is not ready")
        if (_state.value.phase !in setOf(CameraPhase.READY, CameraPhase.REVIEWING)) {
            return@withLock ApplyResult.Failed("Camera is busy")
        }

        val generation = bindingGeneration.get()
        val sessionId = cameraSessionId.get()
        val committedState = _telemetry.value
        val previousBaseline = controlBaseline
        val capabilities = _capabilities.value
        validateAdjustments(activeCamera, adjustments, capabilities)?.let {
            return@withLock ApplyResult.Failed(it)
        }
        if (!transactionIsCurrent(activeCamera, generation, sessionId, committedState)) {
            return@withLock ApplyResult.Failed("Camera session changed. Check the shot and try again.")
        }

        runCameraControlTransaction(
            commands = adjustments,
            isCurrent = { transactionIsCurrent(activeCamera, generation, sessionId, committedState) },
            applyCommand = { applyLocked(activeCamera, it) },
            rollback = { rollbackExact(activeCamera, generation, sessionId, committedState) },
            finishRollback = { restored ->
                finishAdjustmentRollback(restored, activeCamera, generation, sessionId, previousBaseline)
            },
            commit = {
                check(transactionIsCurrent(activeCamera, generation, sessionId, committedState))
                controlBaseline = previousBaseline ?: committedState
                if (!transactionIsCurrent(activeCamera, generation, sessionId, committedState)) {
                    controlBaseline = null
                    error("Camera session changed. Check the shot and try again.")
                }
            },
        )
    }

    override suspend fun focusAt(xFraction: Float, yFraction: Float): ApplyResult = operationMutex.withLock {
        val activeCamera = camera ?: return@withLock ApplyResult.Failed("Camera is not ready")
        if (_state.value.phase != CameraPhase.READY) return@withLock ApplyResult.Failed("Camera is busy")
        val action = focusAction(xFraction, yFraction)
            ?: return@withLock ApplyResult.Failed("Tap to focus is unavailable on this camera")
        if (!activeCamera.cameraInfo.isFocusMeteringSupported(action)) {
            return@withLock ApplyResult.Failed("Tap to focus is unavailable on this camera")
        }
        return@withLock try {
            val result = awaitControl(activeCamera.cameraControl.startFocusAndMetering(action))
            if (result.isFocusSuccessful) ApplyResult.Applied
            else ApplyResult.Failed("The camera could not lock focus there")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ApplyResult.Failed(error.message ?: "The camera could not focus there")
        }
    }

    override suspend fun setFlashMode(mode: FlashMode): ApplyResult = operationMutex.withLock {
        val activeCamera = camera ?: return@withLock ApplyResult.Failed("Camera is not ready")
        val capture = imageCapture ?: return@withLock ApplyResult.Failed("Camera is not ready")
        if (_state.value.phase != CameraPhase.READY) return@withLock ApplyResult.Failed("Camera is busy")
        if (!activeCamera.cameraInfo.hasFlashUnit()) {
            return@withLock ApplyResult.Failed("Flash is unavailable on this camera")
        }
        try {
            when (mode) {
                FlashMode.OFF -> {
                    awaitControl(activeCamera.cameraControl.enableTorch(false))
                    capture.flashMode = ImageCapture.FLASH_MODE_OFF
                }
                FlashMode.ON -> {
                    awaitControl(activeCamera.cameraControl.enableTorch(false))
                    capture.flashMode = ImageCapture.FLASH_MODE_ON
                }
                FlashMode.TORCH -> {
                    capture.flashMode = ImageCapture.FLASH_MODE_OFF
                    awaitControl(activeCamera.cameraControl.enableTorch(true))
                }
            }
            ApplyResult.Applied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ApplyResult.Failed(error.message ?: "The camera rejected the flash setting")
        }
    }

    override suspend fun reset(): ApplyResult = operationMutex.withLock {
        val activeCamera = camera ?: return@withLock ApplyResult.Failed("Camera is not ready")
        if (_state.value.phase == CameraPhase.BLOCKED) {
            return@withLock ApplyResult.Failed(
                _state.value.message ?: "Camera controls cannot be reset until the camera is retried",
            )
        }
        val baseline = controlBaseline
        if (baseline != null && !controlBaselineMatchesPhysicalCamera(baseline, activePhysicalCameraId)) {
            controlBaseline = null
            _state.value = CameraState(
                CameraPhase.BLOCKED,
                "The active camera lens changed. Retry the camera before shooting.",
                cameraSessionId.get(),
            )
            return@withLock ApplyResult.Failed("The active camera lens changed. Retry the camera before shooting.")
        }
        try {
            awaitControl(activeCamera.cameraControl.cancelFocusAndMetering())
            if (activeCamera.cameraInfo.hasFlashUnit()) {
                awaitControl(activeCamera.cameraControl.enableTorch(false))
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            if (baseline != null) restoreControlState(activeCamera, baseline)
            controlBaseline = null
            ApplyResult.Applied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            refreshTelemetry(activeCamera.cameraInfo)
            ApplyResult.Failed(error.message ?: "Camera reset failed")
        }
    }

    override suspend fun capture(): CaptureResult = operationMutex.withLock {
        val capture = imageCapture ?: return@withLock CaptureResult.Failed("Camera is not ready")
        if (_state.value.phase != CameraPhase.READY) {
            return@withLock CaptureResult.Failed("Camera is busy")
        }

        val captureRoll = latestRollDegrees
        val stillTelemetry = CompletableDeferred<CameraTelemetry>()
        pendingStillTelemetry.set(stillTelemetry)
        updateAnalysisPause(pauseGate.startCapture())
        _state.value = CameraState(CameraPhase.CAPTURING, sessionId = cameraSessionId.get())
        try {
            val uri = savePhoto(capture)
            val captureTelemetry = withTimeoutOrNull(STILL_TELEMETRY_TIMEOUT_MS) { stillTelemetry.await() }
            val reviewObservation = try {
                createReviewObservation(uri, captureRoll, captureTelemetry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            val saved = SavedCapture(
                id = UUID.randomUUID().toString(),
                uri = uri.toString(),
                observation = reviewObservation,
                telemetry = captureTelemetry,
            )
            updateAnalysisPause(pauseGate.finishCapture(keepPausedForReview = true))
            if (!closed.get()) {
                _state.update { current ->
                    captureTerminalState(current, CameraPhase.REVIEWING, sessionId = cameraSessionId.get())
                }
            }
            CaptureResult.Saved(saved)
        } catch (cancelled: CancellationException) {
            updateAnalysisPause(pauseGate.finishCapture(keepPausedForReview = false))
            if (!closed.get()) {
                _state.update { current ->
                    captureTerminalState(current, CameraPhase.READY, sessionId = cameraSessionId.get())
                }
            }
            throw cancelled
        } catch (error: Throwable) {
            updateAnalysisPause(pauseGate.finishCapture(keepPausedForReview = false))
            if (!closed.get()) {
                _state.update { current ->
                    captureTerminalState(
                        current,
                        CameraPhase.READY,
                        error.message ?: "Photo capture failed",
                        cameraSessionId.get(),
                    )
                }
            }
            CaptureResult.Failed(error.message ?: "Photo capture failed")
        } finally {
            pendingStillTelemetry.compareAndSet(stillTelemetry, null)
            stillTelemetry.cancel()
        }
    }

    override suspend fun observationImage(capture: SavedCapture?): ByteArray? {
        val ticket = observationImages.ticket() ?: return null
        if (capture == null) return observationImages.copyLatest(ticket)
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeBitmap(Uri.parse(capture.uri), OBSERVATION_LONG_EDGE)
                try {
                    encodeObservationJpeg(bitmap)?.let { observationImages.completePrivate(ticket, it) }
                } finally {
                    bitmap.recycle()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
    }

    override fun setAnalysisPaused(paused: Boolean) {
        updateAnalysisPause(pauseGate.setExternal(paused))
    }

    override fun setObservationImageEnabled(enabled: Boolean) {
        observationImages.setEnabled(enabled)
    }

    private fun updateAnalysisPause(paused: Boolean) {
        analysisPaused.set(paused)
        if (paused) {
            observationImages.invalidate()
            previousLumaSignature.set(null)
            stopRollSensor()
        } else if (!closed.get()) {
            if (camera != null) startRollSensor()
            if (_state.value.phase == CameraPhase.REVIEWING) {
                _state.value = CameraState(CameraPhase.READY, sessionId = cameraSessionId.get())
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        bindingGeneration.incrementAndGet()
        val sessionId = cameraSessionId.incrementAndGet()
        analysisPaused.set(true)
        pendingControl.getAndSet(null)?.cancel(true)
        pendingStillTelemetry.getAndSet(null)?.cancel()
        stopRollSensor()
        observationImages.invalidate()
        _observation.value = null
        _capabilities.value = CameraCapabilities()
        _telemetry.value = CameraTelemetry()
        _state.value = CameraState(CameraPhase.STARTING, sessionId = sessionId)

        val releaseCamera = {
            previewUseCase = null
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            imageCapture = null
            camera = null
            activeCameraId = null
            activeLensFacing = null
            activeFocalLengthsMm = emptyList()
            activePhysicalCameraIds = emptySet()
            activePhysicalCameraId = null
            activeFocalLengthMm = null
            focusPointFactory = null
            previewWidth = 0
            previewHeight = 0
            removeZoomObserver()
            cameraProvider?.unbindAll()
            cameraProvider = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) releaseCamera() else mainExecutor.execute(releaseCamera)

        analysisExecutor.execute {
            detectorGate.acquireUninterruptibly()
            try {
                faceDetector.close()
            } finally {
                detectorGate.release()
            }
        }
        analysisExecutor.shutdown()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (closed.get() || analysisPaused.get()) return
        latestRollDegrees = when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> rollFromRotationVector(event.values)
            Sensor.TYPE_GRAVITY -> rollFromGravity(event.values)
            else -> null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun analyze(image: ImageProxy, generation: Long) {
        if (closed.get() || analysisPaused.get() || bindingGeneration.get() != generation) {
            image.close()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalysisMs.get() < ANALYSIS_INTERVAL_MS || !detectorGate.tryAcquire()) {
            image.close()
            return
        }
        lastAnalysisMs.set(now)
        val observationImageTicket = observationImages.ticket()

        var metrics: LumaMetrics? = null
        var uprightBitmap: Bitmap? = null
        var detectorStarted = false
        try {
            val crop = image.cropRect
            val sourceBitmap = image.toBitmap()
            val visibleBitmap = if (
                crop.left >= 0 && crop.top >= 0 &&
                crop.right <= sourceBitmap.width && crop.bottom <= sourceBitmap.height &&
                (crop.left != 0 || crop.top != 0 ||
                    crop.right != sourceBitmap.width || crop.bottom != sourceBitmap.height)
            ) {
                Bitmap.createBitmap(sourceBitmap, crop.left, crop.top, crop.width(), crop.height())
                    .also { sourceBitmap.recycle() }
            } else {
                sourceBitmap
            }
            uprightBitmap = rotateBitmap(visibleBitmap, image.imageInfo.rotationDegrees)
            if (uprightBitmap !== visibleBitmap) visibleBitmap.recycle()
            val frameBitmap = uprightBitmap
            metrics = FrameMetrics.measureBitmap(frameBitmap)
            val frameWidth = frameBitmap.width
            val frameHeight = frameBitmap.height
            val jpeg = if (observationImageTicket != null) encodeObservationJpeg(frameBitmap) else null
            detectorStarted = true
            try {
                val faces = try {
                    Tasks.await(faceDetector.process(InputImage.fromBitmap(frameBitmap, 0)))
                } catch (_: Throwable) {
                    emptyList()
                }
                publishLiveObservation(
                    metrics, faces, frameWidth, frameHeight, jpeg, observationImageTicket, now, generation,
                )
            } finally {
                frameBitmap.recycle()
                detectorGate.release()
            }
        } catch (_: Throwable) {
            if (!detectorStarted) uprightBitmap?.recycle()
            metrics?.let {
                val rotated = image.imageInfo.rotationDegrees % 180 != 0
                val width = if (rotated) image.height else image.width
                val height = if (rotated) image.width else image.height
                publishLiveObservation(it, emptyList(), width, height, null, null, now, generation)
            }
        } finally {
            image.close()
            if (!detectorStarted) detectorGate.release()
        }
    }

    private fun publishLiveObservation(
        metrics: LumaMetrics,
        faces: List<Face>,
        width: Int,
        height: Int,
        jpeg: ByteArray?,
        observationImageTicket: Long?,
        timestampMs: Long,
        generation: Long,
    ) {
        if (closed.get() || analysisPaused.get() || bindingGeneration.get() != generation) {
            jpeg?.fill(0)
            return
        }
        observationImages.publish(observationImageTicket, jpeg)
        val motionScore = FrameMetrics.motionScore(
            previousLumaSignature.getAndSet(metrics.lumaSignature),
            metrics.lumaSignature,
        )
        val telemetry = _telemetry.value
        _observation.value = FrameObservation(
            id = observationIds.incrementAndGet(),
            timestampMs = timestampMs,
            meanLuma = metrics.mean,
            highlightClipFraction = metrics.highlightFraction,
            shadowClipFraction = metrics.shadowFraction,
            chromaBlueBias = metrics.chromaBlueBias,
            faces = normalizeFaces(faces, width, height),
            deviceRollDegrees = latestRollDegrees,
            motionScore = motionScore,
            sceneLumaSignature = metrics.lumaSignature,
            lensId = telemetry.lensId,
            focalLengthMm = telemetry.focalLengthMm,
            zoomRatio = telemetry.zoomRatio,
            sourceWidth = width,
            sourceHeight = height,
        )
    }

    private suspend fun createReviewObservation(
        uri: Uri,
        rollDegrees: Float?,
        captureTelemetry: CameraTelemetry?,
    ): FrameObservation {
        val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri, REVIEW_LONG_EDGE) }
        try {
            val metrics = FrameMetrics.measureBitmap(bitmap)
            val faces = try {
                detectFaces(bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
            return FrameObservation(
                id = observationIds.incrementAndGet(),
                timestampMs = SystemClock.elapsedRealtime(),
                meanLuma = metrics.mean,
                highlightClipFraction = metrics.highlightFraction,
                shadowClipFraction = metrics.shadowFraction,
                chromaBlueBias = metrics.chromaBlueBias,
                faces = normalizeFaces(faces, bitmap.width, bitmap.height),
                deviceRollDegrees = rollDegrees,
                sceneLumaSignature = metrics.lumaSignature,
                lensId = captureTelemetry?.lensId,
                focalLengthMm = captureTelemetry?.focalLengthMm,
                zoomRatio = captureTelemetry?.zoomRatio,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        runInterruptible(Dispatchers.IO) { detectorGate.acquire() }
        if (closed.get()) {
            detectorGate.release()
            return emptyList()
        }
        return try {
            faceDetector.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
        } finally {
            detectorGate.release()
        }
    }

    private fun normalizeFaces(faces: List<Face>, width: Int, height: Int): List<FaceObservation> {
        if (width <= 0 || height <= 0) return emptyList()
        return faces.map { face ->
            val box = face.boundingBox
            val rawArea = box.width().coerceAtLeast(0).toLong() * box.height().coerceAtLeast(0).toLong()
            val visibleWidth = (box.right.coerceAtMost(width) - box.left.coerceAtLeast(0)).coerceAtLeast(0)
            val visibleHeight = (box.bottom.coerceAtMost(height) - box.top.coerceAtLeast(0)).coerceAtLeast(0)
            FaceObservation(
                trackingId = face.trackingId,
                left = (box.left.toFloat() / width).coerceIn(0f, 1f),
                top = (box.top.toFloat() / height).coerceIn(0f, 1f),
                right = (box.right.toFloat() / width).coerceIn(0f, 1f),
                bottom = (box.bottom.toFloat() / height).coerceIn(0f, 1f),
                visibleFraction = if (rawArea > 0) visibleWidth.toLong() * visibleHeight / rawArea.toFloat() else 0f,
            )
        }
    }

    private fun publishCameraInfo(cameraInfo: CameraInfo) {
        val exposure = cameraInfo.exposureState
        val cameraRange = exposure.exposureCompensationRange
        val exposureRange = if (cameraRange.lower < cameraRange.upper) {
            cameraRange.lower..cameraRange.upper
        } else {
            IntRange.EMPTY
        }
        val zoom = cameraInfo.zoomState.value
        val availableWhiteBalanceModes = availableAwbModes(cameraInfo)
        val whiteBalancePresets = whiteBalancePresetsForModes(availableWhiteBalanceModes)
        _capabilities.value = CameraCapabilities(
            exposureCompensationRange = exposureRange,
            exposureCompensationStepEv = if (exposureRange.isEmpty()) 0f else exposure.exposureCompensationStep.toFloat(),
            zoomRatioRange = (zoom?.minZoomRatio ?: 1f)..(zoom?.maxZoomRatio ?: 1f),
            supportedWhiteBalancePresets = whiteBalancePresets,
            supportedWhiteBalanceLevels = whiteBalanceLevelsForModes(availableWhiteBalanceModes),
            supportsFocusMetering = supportsFocusMetering(cameraInfo),
            hasFlashUnit = cameraInfo.hasFlashUnit(),
        )
        _telemetry.value = CameraTelemetry(
            exposureCompensationIndex = exposure.exposureCompensationIndex,
            zoomRatio = zoom?.zoomRatio ?: 1f,
            whiteBalancePreset = WhiteBalancePreset.AUTO,
            lensId = activePhysicalCameraId ?: activeCameraId,
            focalLengthMm = activeFocalLengthMm,
        )

        observedCameraInfo = cameraInfo
        zoomObserver = Observer<ZoomState> { zoomState ->
            if (!closed.get()) {
                _telemetry.value = _telemetry.value.copy(zoomRatio = zoomState.zoomRatio)
            }
        }.also(cameraInfo.zoomState::observeForever)
    }

    private fun supportsFocusMetering(cameraInfo: CameraInfo): Boolean {
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
        return cameraInfo.isFocusMeteringSupported(action)
    }

    private fun refreshTelemetry(
        cameraInfo: CameraInfo,
        whiteBalancePreset: WhiteBalancePreset = _telemetry.value.whiteBalancePreset,
        whiteBalanceLevel: Int = _telemetry.value.whiteBalanceLevel,
    ) {
        _telemetry.update {
            it.copy(
                exposureCompensationIndex = cameraInfo.exposureState.exposureCompensationIndex,
                zoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: it.zoomRatio,
                whiteBalancePreset = whiteBalancePreset,
                whiteBalanceLevel = whiteBalanceLevel,
            )
        }
    }

    private fun validateAdjustments(
        activeCamera: Camera,
        adjustments: List<CameraAdjustment>,
        capabilities: CameraCapabilities,
    ): String? {
        validateAdjustmentBatchStructure(adjustments)?.let { return it }
        return adjustments.firstNotNullOfOrNull { adjustment ->
            when (adjustment) {
                is CameraAdjustment.ExposureCompensation ->
                    "Exposure adjustment is outside this camera's range".takeIf {
                        !capabilities.supportsExposureCompensation ||
                            adjustment.targetIndex !in capabilities.exposureCompensationRange
                    }
                is CameraAdjustment.ZoomRatio ->
                    "Zoom adjustment is outside this camera's range".takeIf {
                        !adjustment.ratio.isFinite() || adjustment.ratio !in capabilities.zoomRatioRange
                    }
                is CameraAdjustment.WhiteBalance ->
                    "White-balance adjustment is unavailable on this camera".takeIf {
                        adjustment.targetLevel !in capabilities.supportedWhiteBalanceLevels ||
                            selectAwbMode(activeCamera.cameraInfo, adjustment.targetLevel) == null
                    }
            }
        }
    }

    private suspend fun applyLocked(activeCamera: Camera, adjustment: CameraAdjustment) {
        when (adjustment) {
            is CameraAdjustment.ExposureCompensation -> {
                val applied = awaitControl(
                    activeCamera.cameraControl.setExposureCompensationIndex(adjustment.targetIndex),
                )
                _telemetry.value = _telemetry.value.copy(exposureCompensationIndex = applied)
            }
            is CameraAdjustment.ZoomRatio -> {
                awaitControl(activeCamera.cameraControl.setZoomRatio(adjustment.ratio))
                _telemetry.value = _telemetry.value.copy(zoomRatio = adjustment.ratio)
            }
            is CameraAdjustment.WhiteBalance -> {
                val awbMode = selectAwbMode(activeCamera.cameraInfo, adjustment.targetLevel)
                    ?: error("White-balance adjustment is unavailable on this camera")
                awaitControl(
                    Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(
                        CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)
                            .build(),
                    ),
                )
                _telemetry.value = _telemetry.value.copy(
                    whiteBalancePreset = whiteBalancePresetForLevel(adjustment.targetLevel),
                    whiteBalanceLevel = adjustment.targetLevel,
                )
            }
        }
    }

    private fun transactionIsCurrent(
        activeCamera: Camera,
        generation: Long,
        sessionId: Long,
        committedState: CameraTelemetry,
    ): Boolean = !closed.get() &&
        camera === activeCamera &&
        bindingGeneration.get() == generation &&
        cameraSessionId.get() == sessionId &&
        _state.value.sessionId == sessionId &&
        _state.value.phase in setOf(CameraPhase.READY, CameraPhase.REVIEWING) &&
        controlBaselineMatchesPhysicalCamera(committedState, activePhysicalCameraId)

    private suspend fun rollbackExact(
        activeCamera: Camera,
        generation: Long,
        sessionId: Long,
        committedState: CameraTelemetry,
    ): Boolean {
        if (!transactionIsCurrent(activeCamera, generation, sessionId, committedState)) return false
        return runCatching {
            restoreControlState(activeCamera, committedState)
            check(transactionIsCurrent(activeCamera, generation, sessionId, committedState))
            check(telemetryMatches(_telemetry.value, committedState))
        }.isSuccess
    }

    private fun telemetryMatches(actual: CameraTelemetry, expected: CameraTelemetry): Boolean {
        val zoomTolerance = (expected.zoomRatio * 0.01f).coerceAtLeast(0.01f)
        return actual.exposureCompensationIndex == expected.exposureCompensationIndex &&
            kotlin.math.abs(actual.zoomRatio - expected.zoomRatio) <= zoomTolerance &&
            actual.whiteBalancePreset == expected.whiteBalancePreset &&
            actual.whiteBalanceLevel == expected.whiteBalanceLevel &&
            actual.lensId == expected.lensId
    }

    private fun finishAdjustmentRollback(
        restored: Boolean,
        activeCamera: Camera,
        generation: Long,
        sessionId: Long,
        previousBaseline: CameraTelemetry?,
    ) {
        val sameBinding = !closed.get() &&
            bindingGeneration.get() == generation &&
            camera === activeCamera
        val sameSession = sameBinding && cameraSessionId.get() == sessionId
        if (restored && sameSession) {
            controlBaseline = previousBaseline
        } else if (sameBinding) {
            controlBaseline = null
            _state.value = CameraState(
                CameraPhase.BLOCKED,
                "Camera controls could not be restored. Retry the camera before shooting.",
                cameraSessionId.get(),
            )
        }
    }

    private suspend fun restoreControlState(activeCamera: Camera, state: CameraTelemetry) {
        val capabilities = _capabilities.value
        if (capabilities.supportsExposureCompensation && state.exposureCompensationIndex in capabilities.exposureCompensationRange) {
            awaitControl(activeCamera.cameraControl.setExposureCompensationIndex(state.exposureCompensationIndex))
        }
        if (state.zoomRatio in capabilities.zoomRatioRange) {
            awaitControl(activeCamera.cameraControl.setZoomRatio(state.zoomRatio))
        }
        val camera2Control = Camera2CameraControl.from(activeCamera.cameraControl)
        if (state.whiteBalanceLevel == 0) {
            awaitControl(camera2Control.clearCaptureRequestOptions())
        } else {
            val awbMode = selectAwbMode(activeCamera.cameraInfo, state.whiteBalanceLevel)
                ?: error("The prior white-balance mode is no longer available")
            awaitControl(
                camera2Control.setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)
                        .build(),
                ),
            )
        }
        refreshTelemetry(activeCamera.cameraInfo, state.whiteBalancePreset, state.whiteBalanceLevel)
    }

    private fun availableAwbModes(cameraInfo: CameraInfo): Set<Int> =
        Camera2CameraInfo.from(cameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?.toSet()
            .orEmpty()

    private fun selectAwbMode(cameraInfo: CameraInfo, level: Int): Int? =
        awbModeForLevel(availableAwbModes(cameraInfo), level)

    private fun focusAction(xFraction: Float, yFraction: Float): FocusMeteringAction? {
        val factory = focusPointFactory ?: return null
        val width = previewWidth.takeIf { it > 0 } ?: return null
        val height = previewHeight.takeIf { it > 0 } ?: return null
        if (!xFraction.isFinite() || !yFraction.isFinite()) return null
        val point = factory.createPoint(
            xFraction.coerceIn(0f, 1f) * width,
            yFraction.coerceIn(0f, 1f) * height,
        )
        return FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
    }

    private fun removeZoomObserver() {
        val observer = zoomObserver
        val info = observedCameraInfo
        if (observer != null && info != null) info.zoomState.removeObserver(observer)
        zoomObserver = null
        observedCameraInfo = null
    }

    private fun startRollSensor() {
        stopRollSensor()
        rollSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        rollSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun stopRollSensor() {
        sensorManager.unregisterListener(this)
        rollSensor = null
        latestRollDegrees = null
    }

    private fun rollFromRotationVector(values: FloatArray): Float {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        val (xAxis, yAxis) = displayAxes()
        SensorManager.remapCoordinateSystem(rotationMatrix, xAxis, yAxis, remappedRotationMatrix)
        SensorManager.getOrientation(remappedRotationMatrix, orientation)
        return Math.toDegrees(orientation[2].toDouble()).toFloat()
    }

    private fun rollFromGravity(values: FloatArray): Float? {
        if (values.size < 2) return null
        return gravityRollDegrees(values[0], values[1], displayRotation())
    }

    private fun displayAxes(): Pair<Int, Int> = when (displayRotation()) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

    private fun displayRotation(): Int =
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun savePhoto(capture: ImageCapture): Uri = suspendCancellableCoroutine { continuation ->
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoHelper_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoHelper")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            appContext.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                val uri = result.savedUri
                if (uri == null) {
                    continuation.resumeWithException(IllegalStateException("Camera returned no saved photo URI"))
                    return
                }
                continuation.resume(uri) {
                    runCatching { appContext.contentResolver.delete(uri, null, null) }
                }
            }

            override fun onError(error: ImageCaptureException) {
                continuation.resumeWithException(error)
            }
        })
    }

    private fun decodeBitmap(uri: Uri, maxLongEdge: Int): Bitmap {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longest = max(width, height)
            if (longest > maxLongEdge) {
                val scale = maxLongEdge.toFloat() / longest
                decoder.setTargetSize(
                    max(1, (width * scale).roundToInt()),
                    max(1, (height * scale).roundToInt()),
                )
            }
        }
    }

    private fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        if (rotation == 0) return source
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true,
        )
    }

    private fun encodeObservationJpeg(source: Bitmap): ByteArray? {
        var bitmap = scaleBitmap(source, OBSERVATION_LONG_EDGE)
        var ownsBitmap = bitmap !== source
        var quality = 70
        try {
            while (true) {
                val bytes = ByteArrayOutputStream().use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
                    output.toByteArray()
                }
                if (bytes.size <= OBSERVATION_MAX_BYTES) return bytes

                if (quality > 30) {
                    quality -= 10
                    continue
                }

                val ratio = sqrt(OBSERVATION_MAX_BYTES.toDouble() / bytes.size) * 0.9
                val width = max(1, (bitmap.width * ratio).roundToInt())
                val height = max(1, (bitmap.height * ratio).roundToInt())
                if (width == bitmap.width && height == bitmap.height) return null
                val smaller = Bitmap.createScaledBitmap(bitmap, width, height, true)
                if (ownsBitmap) bitmap.recycle()
                bitmap = smaller
                ownsBitmap = true
                quality = 70
            }
        } finally {
            if (ownsBitmap) bitmap.recycle()
        }
    }

    private fun scaleBitmap(source: Bitmap, maxLongEdge: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxLongEdge) return source
        val scale = maxLongEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            max(1, (source.width * scale).roundToInt()),
            max(1, (source.height * scale).roundToInt()),
            true,
        )
    }

    private suspend fun <T> awaitControl(future: ListenableFuture<T>): T {
        pendingControl.set(future)
        return try {
            awaitCameraControl { future.awaitFuture() }
        } finally {
            pendingControl.compareAndSet(future, null)
        }
    }

    private suspend fun <T> ListenableFuture<T>.awaitFuture(): T = suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                if (continuation.isActive) continuation.resume(get())
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error.cause ?: error)
            }
        }, mainExecutor)
        continuation.invokeOnCancellation { cancel(true) }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { continuation ->
        addOnSuccessListener(continuation::resume)
        addOnFailureListener(continuation::resumeWithException)
        addOnCanceledListener {
            continuation.resumeWithException(CancellationException("Face analysis was cancelled"))
        }
    }

    private companion object {
        const val ANALYSIS_INTERVAL_MS = 250L
        const val STILL_TELEMETRY_TIMEOUT_MS = 1_000L
        const val OBSERVATION_LONG_EDGE = 768
        const val REVIEW_LONG_EDGE = 720
        const val OBSERVATION_MAX_BYTES = 300 * 1024
    }
}

private const val CAMERA_CONTROL_TIMEOUT_MS = 3_000L

internal data class LumaMetrics(
    val mean: Float,
    val highlightFraction: Float,
    val shadowFraction: Float,
    val chromaBlueBias: Float? = null,
    val lumaSignature: List<Int> = emptyList(),
)

internal class ObservationImageGate {
    private var enabled = false
    private var generation = 0L
    private var latest: ByteArray? = null

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            generation++
        }
        if (!enabled) clearLatest()
    }

    @Synchronized
    fun ticket(): Long? = generation.takeIf { enabled }

    @Synchronized
    fun publish(ticket: Long?, jpeg: ByteArray?) {
        if (ticket != null && ticket == generation && enabled) {
            clearLatest()
            latest = jpeg
        } else {
            jpeg?.fill(0)
        }
    }

    @Synchronized
    fun copyLatest(ticket: Long): ByteArray? =
        latest?.takeIf { enabled && ticket == generation }?.copyOf()

    @Synchronized
    fun completePrivate(ticket: Long, jpeg: ByteArray): ByteArray? {
        if (enabled && ticket == generation) return jpeg
        jpeg.fill(0)
        return null
    }

    @Synchronized
    fun invalidate() {
        generation++
        clearLatest()
    }

    private fun clearLatest() {
        latest?.fill(0)
        latest = null
    }
}

internal class AnalysisPauseGate {
    private var externallyPaused = false
    private var captureInProgress = false
    private var captureHold = false

    @Synchronized
    fun resetSession(): Boolean {
        captureInProgress = false
        captureHold = false
        return externallyPaused
    }

    @Synchronized
    fun setExternal(paused: Boolean): Boolean {
        externallyPaused = paused
        if (!paused && !captureInProgress) captureHold = false
        return isPaused()
    }

    @Synchronized
    fun startCapture(): Boolean {
        captureInProgress = true
        captureHold = true
        return true
    }

    @Synchronized
    fun finishCapture(keepPausedForReview: Boolean): Boolean {
        captureInProgress = false
        captureHold = keepPausedForReview
        return isPaused()
    }

    private fun isPaused(): Boolean = externallyPaused || captureHold
}

internal object FrameMetrics {
    fun measureYPlane(
        buffer: ByteBuffer,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
        rowStride: Int,
        pixelStride: Int,
    ): LumaMetrics {
        if (cropWidth <= 0 || cropHeight <= 0 || rowStride <= 0 || pixelStride <= 0) return EMPTY
        val pixels = buffer.duplicate()
        val bufferStart = pixels.position()
        val step = max(1, min(cropWidth, cropHeight) / 96)
        var count = 0
        var sum = 0L
        var highlights = 0
        var shadows = 0

        var y = 0
        while (y < cropHeight) {
            val row = (cropTop + y) * rowStride
            var x = 0
            while (x < cropWidth) {
                val index = bufferStart + row + (cropLeft + x) * pixelStride
                if (index in 0 until pixels.limit()) {
                    val luma = pixels.get(index).toInt() and 0xff
                    sum += luma
                    count++
                    if (luma >= 235) highlights++
                    if (luma <= 20) shadows++
                }
                x += step
            }
            y += step
        }
        return if (count == 0) EMPTY else LumaMetrics(
            mean = sum.toFloat() / count / 255f,
            highlightFraction = highlights.toFloat() / count,
            shadowFraction = shadows.toFloat() / count,
            lumaSignature = sampleSignature(cropWidth, cropHeight) { sampleX, sampleY ->
                val index = bufferStart + (cropTop + sampleY) * rowStride + (cropLeft + sampleX) * pixelStride
                if (index in 0 until pixels.limit()) pixels.get(index).toInt() and 0xff else 0
            },
        )
    }

    fun measureBitmap(bitmap: Bitmap): LumaMetrics =
        measurePixels(bitmap.width, bitmap.height, bitmap::getPixel)

    fun measureArgb(pixels: IntArray, width: Int, height: Int): LumaMetrics =
        measurePixels(width, height) { x, y -> pixels[y * width + x] }

    private fun measurePixels(width: Int, height: Int, pixelAt: (Int, Int) -> Int): LumaMetrics {
        if (width <= 0 || height <= 0) return EMPTY
        val step = max(1, min(width, height) / 96)
        var count = 0
        var sum = 0.0
        var highlights = 0
        var shadows = 0
        var usableColors = 0
        var blueBiasSum = 0.0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = pixelAt(x, y)
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                val luma = 0.2126 * red + 0.7152 * green + 0.0722 * blue
                sum += luma
                count++
                if (luma >= 235) highlights++
                if (luma <= 20) shadows++
                if (luma in 24.0..231.0 && max(red, max(green, blue)) - min(red, min(green, blue)) <= 128) {
                    blueBiasSum += (blue - red).toDouble() / 255.0
                    usableColors++
                }
                x += step
            }
            y += step
        }
        return LumaMetrics(
            mean = (sum / count / 255.0).toFloat(),
            highlightFraction = highlights.toFloat() / count,
            shadowFraction = shadows.toFloat() / count,
            chromaBlueBias = if (usableColors.toFloat() / count >= 0.3f) {
                (blueBiasSum / usableColors).toFloat()
            } else {
                null
            },
            lumaSignature = sampleSignature(width, height) { sampleX, sampleY ->
                val color = pixelAt(sampleX, sampleY)
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                (0.2126 * red + 0.7152 * green + 0.0722 * blue).roundToInt()
            },
        )
    }

    fun motionScore(previous: List<Int>?, current: List<Int>): Float =
        exposureInvariantSceneDifference(previous, current)

    private fun sampleSignature(width: Int, height: Int, lumaAt: (Int, Int) -> Int): List<Int> {
        val signature = IntArray(12 * 9)
        for (row in 0 until 9) {
            val y = ((row + 0.5f) * height / 9).toInt().coerceIn(0, height - 1)
            for (column in 0 until 12) {
                val x = ((column + 0.5f) * width / 12).toInt().coerceIn(0, width - 1)
                signature[row * 12 + column] = lumaAt(x, y).coerceIn(0, 255)
            }
        }
        return signature.toList()
    }

    private val EMPTY = LumaMetrics(mean = 0.5f, highlightFraction = 0f, shadowFraction = 0f)
}
