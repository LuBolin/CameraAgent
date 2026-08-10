package com.bolin.photohelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bolin.photohelper.capture.CameraXSession
import com.bolin.photohelper.capture.CameraFacingRequest
import com.bolin.photohelper.capture.CameraPhase
import com.bolin.photohelper.capture.CaptureScreen
import com.bolin.photohelper.capture.CaptureViewModel
import com.bolin.photohelper.capture.PermissionState
import com.bolin.photohelper.ui.PhotoHelperTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CaptureViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        viewModel = ViewModelProvider(this, AppGraph(applicationContext).viewModelFactory())[CaptureViewModel::class.java]

        setContent {
            PhotoHelperTheme {
                PhotoHelperApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::viewModel.isInitialized) viewModel.onForeground()
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshPermissions(
                cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
                microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            )
        }
    }

    override fun onStop() {
        if (::viewModel.isInitialized) viewModel.onBackground()
        super.onStop()
    }
}

@Composable
private fun MainActivity.PhotoHelperApp(viewModel: CaptureViewModel) {
    val state by viewModel.uiState.collectAsState()
    var apiKeyInput by remember { mutableStateOf("") }
    var showMicDisclosure by remember { mutableStateOf(false) }
    var cameraBindAttempt by remember { mutableIntStateOf(0) }
    var isFrontCamera by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val canFlipCamera = remember(state.cameraPermission) {
        state.cameraPermission == PermissionState.GRANTED &&
            hasCamera(CameraCharacteristics.LENS_FACING_FRONT)
    }
    val switchCamera: (Boolean, Boolean) -> Unit = { useFrontCamera, preserveCommandPlan ->
        viewModel.cancelCoaching(preserveCommandPlan = preserveCommandPlan)
        isFrontCamera = useFrontCamera
        cameraBindAttempt++
    }
    val latestState by rememberUpdatedState(state)
    val latestCanFlipCamera by rememberUpdatedState(canFlipCamera)
    val latestIsFrontCamera by rememberUpdatedState(isFrontCamera)

    LaunchedEffect(viewModel) {
        viewModel.cameraFacingRequests.collect { request ->
            val requestedFront = when (request) {
                CameraFacingRequest.TOGGLE -> null
                CameraFacingRequest.FRONT -> true
                CameraFacingRequest.REAR -> false
            }
            when {
                requestedFront != null && requestedFront == latestIsFrontCamera -> {
                    viewModel.reportCameraSwitchMessage(
                        if (latestIsFrontCamera) "Selfie camera is already active." else "Rear camera is already active.",
                    )
                    viewModel.cameraFacingRequestCompleted(true)
                }
                !latestState.shutterEnabled -> {
                    viewModel.reportCameraSwitchMessage("Finish the current camera action before switching cameras.")
                    viewModel.cameraFacingRequestCompleted(false)
                }
                !latestCanFlipCamera -> {
                    viewModel.reportCameraSwitchMessage("No front camera is available on this device.")
                    viewModel.cameraFacingRequestCompleted(false)
                }
                else -> {
                    val useFrontCamera = requestedFront ?: !latestIsFrontCamera
                    val previousSessionId = viewModel.camera.state.value.sessionId
                    viewModel.reportCameraSwitchMessage(
                        if (useFrontCamera) "Switching to selfie camera…" else "Switching to rear camera…",
                    )
                    switchCamera(useFrontCamera, true)
                    val result = withTimeoutOrNull(10_000) {
                        viewModel.camera.state.first {
                            it.sessionId > previousSessionId &&
                                it.phase in setOf(CameraPhase.READY, CameraPhase.BLOCKED)
                        }
                    }
                    val succeeded = result?.phase == CameraPhase.READY
                    if (!succeeded) viewModel.reportCameraSwitchMessage("Camera switch did not finish. Try again.")
                    viewModel.cameraFacingRequestCompleted(succeeded)
                }
            }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.setCameraPermission(it)
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.setMicrophonePermission(it)
        if (it) viewModel.startVoiceInput()
    }

    val protectsApiKey = state.settingsOpen || state.onboardingStep == 1
    DisposableEffect(protectsApiKey) {
        if (protectsApiKey) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) apiKeyInput = ""
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CaptureScreen(
        state = state,
        liveObservation = viewModel.camera.observation,
        apiKeyInput = apiKeyInput,
        preview = {
            CameraPreview(
                session = viewModel.camera as CameraXSession,
                enabled = state.cameraPermission == PermissionState.GRANTED,
                bindAttempt = cameraBindAttempt,
                lensFacing = if (isFrontCamera) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                },
            )
        },
        isFrontCamera = isFrontCamera,
        canFlipCamera = canFlipCamera,
        onFlipCamera = {
            switchCamera(!isFrontCamera, false)
        },
        onOnboardingContinue = viewModel::continueOnboarding,
        onOpenCamera = {
            viewModel.finishOnboarding()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                viewModel.setCameraPermission(true)
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        },
        onRequestCameraPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
        onOpenAppSettings = {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
        },
        onRetryCamera = {
            viewModel.retryCamera()
            cameraBindAttempt++
        },
        onSettingsOpen = { viewModel.openSettings(true) },
        onSettingsDismiss = {
            apiKeyInput = ""
            viewModel.openSettings(false)
        },
        onCommentChange = viewModel::updateComment,
        onSubmitComment = { viewModel.submitComment() },
        onMicrophone = {
            when {
                state.coachingPhase == com.bolin.photohelper.capture.CoachingPhase.LISTENING -> viewModel.finishVoiceInput()
                !viewModel.isVoiceInputAvailable() -> viewModel.reportVoiceUnavailable()
                state.microphonePermission == PermissionState.GRANTED -> viewModel.startVoiceInput()
                state.microphonePermission == PermissionState.DENIED -> startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
                )
                else -> showMicDisclosure = true
            }
        },
        onShutter = viewModel::capture,
        onApplyRecommendation = viewModel::applyRecommendation,
        onStartGuidance = viewModel::startGuidance,
        onFocusTarget = viewModel::focusAt,
        onDismissDecision = viewModel::dismissDecision,
        onClarificationSelected = { viewModel.selectClarification(it.replacementComplaint) },
        onCancelCoaching = { viewModel.cancelCoaching() },
        onReset = viewModel::reset,
        onRetake = viewModel::leaveReview,
        onDoneReview = viewModel::leaveReview,
        onSpokenGuidanceChanged = viewModel::setSpokenGuidance,
        onHapticsChanged = viewModel::setHaptics,
        onTechnicalDetailChanged = viewModel::setTechnicalDetail,
        onVisualAiEnabledChanged = viewModel::setVisualAiEnabled,
        onApiKeyChanged = { apiKeyInput = it },
        onTestKey = {
            val key = apiKeyInput.toCharArray()
            apiKeyInput = ""
            viewModel.testAndSaveKey(key)
        },
        onClearKey = {
            apiKeyInput = ""
            viewModel.clearKey()
        },
        onOpenVisualAiPolicy = {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://help.aliyun.com/zh/model-studio/privacy-notice")))
        },
        onOpenMlKitPolicy = {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.google.com/ml-kit/android-data-disclosure")))
        },
    )

    if (showMicDisclosure) {
        AlertDialog(
            onDismissRequest = { showMicDisclosure = false },
            title = { Text("On-device voice input") },
            text = {
                Text(
                    "Android transcribes voice on this device. Photo Helper does not store or send your audio. " +
                        "Android may download the English speech model.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMicDisclosure = false
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showMicDisclosure = false }) { Text("Not now") }
            },
        )
    }
}

private fun MainActivity.hasCamera(lensFacing: Int): Boolean = runCatching {
    val manager = getSystemService(CameraManager::class.java)
    manager.cameraIdList.any { cameraId ->
        manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.LENS_FACING) == lensFacing
    }
}.getOrDefault(false)

@Composable
private fun BoxScope.CameraPreview(
    session: CameraXSession,
    enabled: Boolean,
    bindAttempt: Int,
    lensFacing: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(session, lifecycleOwner, enabled, bindAttempt, lensFacing) {
        var bound = false
        val bindCamera = Runnable {
            val width = previewView.width
            val height = previewView.height
            val viewPort = previewView.viewPort
            if (enabled && !bound && width > 0 && height > 0 && viewPort != null) {
                bound = true
                session.bind(
                    lifecycleOwner,
                    previewView.surfaceProvider,
                    viewPort,
                    previewView.meteringPointFactory,
                    width,
                    height,
                    lensFacing,
                )
            }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            bindCamera.run()
        }
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                val display = previewView.display ?: return
                if (display.displayId == displayId) session.updateTargetRotation(display.rotation)
            }

            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        previewView.addOnLayoutChangeListener(layoutListener)
        if (enabled) displayManager.registerDisplayListener(displayListener, null)
        previewView.post(bindCamera)
        onDispose {
            if (enabled) displayManager.unregisterDisplayListener(displayListener)
            previewView.removeOnLayoutChangeListener(layoutListener)
            previewView.removeCallbacks(bindCamera)
        }
    }
    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}
