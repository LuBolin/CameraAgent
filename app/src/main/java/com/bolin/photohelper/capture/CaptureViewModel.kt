package com.bolin.photohelper.capture

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolin.photohelper.coach.CoachEngine
import com.bolin.photohelper.coach.CompositionIntent
import com.bolin.photohelper.coach.CompositionMovement
import com.bolin.photohelper.coach.CompositionPlan
import com.bolin.photohelper.coach.CompositionSize
import com.bolin.photohelper.coach.GuidanceGovernor
import com.bolin.photohelper.coach.GuidanceMetrics
import com.bolin.photohelper.coach.GuidanceMode
import com.bolin.photohelper.coach.automaticCompositionMembers
import com.bolin.photohelper.coach.compileComposition
import com.bolin.photohelper.coach.faceUnion
import com.bolin.photohelper.coach.matchMembers
import com.bolin.photohelper.coach.measureGuidance
import com.bolin.photohelper.coach.CoachingInput
import com.bolin.photohelper.coach.ControlIntent
import com.bolin.photohelper.coach.IntentClassification
import com.bolin.photohelper.coach.LocalDecision
import com.bolin.photohelper.coach.ObservationOrigin
import com.bolin.photohelper.coach.Recommendation
import com.bolin.photohelper.coach.SettingChange
import com.bolin.photohelper.coach.SubjectBounds
import com.bolin.photohelper.coach.RecommendationAction
import com.bolin.photohelper.coach.VerificationResult
import com.bolin.photohelper.coach.VerificationTarget
import com.bolin.photohelper.coach.VisualEligibility
import com.bolin.photohelper.coach.VisualFamily
import com.bolin.photohelper.coach.VisualHint
import com.bolin.photohelper.coach.observationsComparable
import com.bolin.photohelper.visual.VisualRequest
import com.bolin.photohelper.visual.VisualResult
import com.bolin.photohelper.visual.markCompositionMembers
import com.bolin.photohelper.visual.CommandRequest
import com.bolin.photohelper.visual.CommandResult
import com.bolin.photohelper.visual.WbVerdict
import com.bolin.photohelper.visual.CameraChangeSnapshot
import com.bolin.photohelper.arcore.ArSessionManager
import com.bolin.photohelper.arcore.SpatialState
import com.bolin.photohelper.arcore.SpatialTracker
import com.bolin.photohelper.ui.ThemeMode
import com.bolin.photohelper.voice.VoiceIo
import com.bolin.photohelper.voice.VoiceResult
import com.bolin.photohelper.voice.CameraFacing
import com.bolin.photohelper.voice.CommandPlan
import com.bolin.photohelper.voice.CommandPlanStep
import com.bolin.photohelper.voice.parseCommandPlan
import com.bolin.photohelper.voice.parseVoiceCommand
import com.bolin.photohelper.BuildConfig
import java.util.UUID
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs

enum class Feedback { TICK, SUCCESS, ERROR }
enum class CameraFacingRequest { TOGGLE, FRONT, REAR }

private const val CAPTURE_TIMEOUT_MS = 15_000L
private const val CAPTURE_TIMEOUT_MESSAGE = "Camera did not finish saving the photo. Try again."
private const val VOICE_INPUT_TIMEOUT_MS = 20_000L
private const val VOICE_INPUT_TIMEOUT_MESSAGE = "Voice input timed out. Tap the mic to try again."
private const val TOAST_TIMEOUT_MS = 5_000L
private const val HINT_VISIBLE_MS = 3_000L
private const val FOCUS_INDICATOR_MS = 3_000L
/** Two visible attempts: the first plan, then one alternative, then an honest concession. */
private const val MAX_SETTING_ATTEMPTS = 2
private const val MAX_REANALYSIS_ROUNDS = 2
private const val SETTING_SETTLE_MS = 400L
private const val SETTING_VERIFY_TIMEOUT_MS = 3_000L
private val OBJECT_FOCUS_REQUEST = Regex("\\b(focus|sharp|sharpen|clear)\\b", RegexOption.IGNORE_CASE)
private val TARGET_FOCUS_REQUEST = Regex("\\bfocus\\s+on\\b", RegexOption.IGNORE_CASE)
private val PERSON_FOCUS_REQUEST = Regex(
    "\\bfocus\\s+on\\s+(?:grandma|grandmother|grandpa|grandfather|mom|mother|dad|father|woman|man|person|her|him)\\b",
    RegexOption.IGNORE_CASE,
)
private val GENERAL_IMPROVEMENT_REQUEST = Regex(
    "^\\s*(?:make\\s+(?:it|this|the\\s+(?:shot|photo|picture))\\s+(?:look\\s+)?(?:nicer|better|good|great|prettier|nice)|" +
        "(?:improve|enhance|fix|help|polish|clean\\s*up)\\s+(?:it|this|the\\s+(?:shot|photo|picture))|" +
        "(?:improve|enhance|fix|help|polish|clean\\s*up)\\s+(?:this|it)|" +
        "(?:looks?\\s+(?:bad|wrong|off|weird|ugly))|" +
        "(?:what(?:'s|\\s+is)\\s+wrong)|" +
        "(?:make\\s+(?:it|this)\\s+(?:look\\s+)?nice))\\s*[.!?]*\\s*$",
    RegexOption.IGNORE_CASE,
)
private val SMALL_ADJUSTMENT_REQUEST = Regex(
    "\\b(?:slightly|a little|little bit|a bit|a touch|gently)\\b",
    RegexOption.IGNORE_CASE,
)
private val COMMAND_CLAUSE_SEPARATOR = Regex(
    "\\s*(?:,|;|\\b(?:and|then|plus|also)\\b)\\s*",
    RegexOption.IGNORE_CASE,
)
private val IMMEDIATE_SETTING_INTENTS = setOf(
    ControlIntent.EXPOSURE_BRIGHTER,
    ControlIntent.EXPOSURE_DARKER,
    ControlIntent.ZOOM_IN,
    ControlIntent.ZOOM_OUT,
    ControlIntent.WHITE_BALANCE_WARMER,
    ControlIntent.WHITE_BALANCE_COOLER,
    ControlIntent.WHITE_BALANCE_AUTO,
)
private fun countdownMessage(seconds: Int) = "Photo in $seconds ${if (seconds == 1) "second" else "seconds"}…"

private data class SettingAndFocus(
    val intents: List<ControlIntent>,
    val focusText: String,
    val small: Boolean,
)

private data class PendingSubjectZoom(val sourceText: String, val small: Boolean)
private data class PendingFocusAfterZoom(
    val sourceText: String,
    val point: VisualHint.FocusPoint,
    val sourceZoomRatio: Float,
)

class CaptureViewModel(
    internal val camera: CaptureHardware,
    private val coach: CoachEngine,
    private val voice: VoiceIo,
    private val preferences: PreferenceStore,
    private val hasApiKey: () -> Boolean,
    private val loadApiKey: () -> CharArray?,
    private val saveApiKey: (CharArray) -> Unit,
    private val clearApiKey: () -> Unit,
    private val interpretVisual: suspend (VisualRequest, CharArray) -> VisualResult,
    private val interpretCommand: suspend (CommandRequest, CharArray) -> CommandResult,
    private val createTestImage: () -> ByteArray?,
    private val feedback: (Feedback) -> Unit = {},
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
    private val autoApplyRecommendations: Boolean = true,
    internal val arSession: ArSessionManager? = null,
    private val audioCue: AudioCuePlayer? = null,
    private val recordGuidance: (GuidanceMetrics) -> Unit = {
        java.util.logging.Logger.getLogger("CompositionGuidance").info(it.toString())
    },
) : ViewModel() {
    private val log = java.util.logging.Logger.getLogger("CaptureVM")
    private val initialSettings = preferences.settings(hasApiKey())
    private val _uiState = MutableStateFlow(
        CaptureUiState(
            onboardingStep = if (preferences.onboardingComplete()) 2 else 0,
            settings = initialSettings,
            capabilities = camera.capabilities.value,
            showFirstUseHint = !preferences.firstUseHintSeen(),
            showVoiceHints = preferences.firstUseHintSeen() && !preferences.hasUsedVoice(),
        ),
    )
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    /**
     * How far the agent has got with the current complaint, 0f to 1f. The Helper Orb
     * samples the Jarvis gradient at this point, which is why it is a float and not
     * the phase enum: the ring sweeps rather than steps.
     */
    val confidence: StateFlow<Float> = uiState
        .map { it.coachingPhase.confidence() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CoachingPhase.IDLE.confidence())

    /**
     * True while the agent is mid-session. Spatial guidance is phrased against the
     * orientation the session started in - "step left" flips meaning if the phone
     * rotates underneath it - so the activity pins rotation until the work is done.
     */
    val shouldLockOrientation: StateFlow<Boolean> = uiState
        .map { it.coachingPhase != CoachingPhase.IDLE }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val spatialTracker = SpatialTracker()
    private val _spatialState = MutableStateFlow(SpatialState(0f, null, isStill = false, stillnessDuration = 0))
    val spatialState: StateFlow<SpatialState> = _spatialState.asStateFlow()
    private var readyForAutoCapture = false

    private val _cameraFacingRequests = MutableSharedFlow<CameraFacingRequest>(extraBufferCapacity = 1)
    val cameraFacingRequests: SharedFlow<CameraFacingRequest> = _cameraFacingRequests.asSharedFlow()

    private var activeComplaintId: String? = null
    private var visualJob: Job? = null
    private var operationJob: Job? = null
    private var countdownJob: Job? = null
    private var keyTestJob: Job? = null
    private var guidanceTimeoutJob: Job? = null
    private var verificationTimeoutJob: Job? = null
    private var focusIndicatorJob: Job? = null
    private var verificationStartObservationId: Long? = null
    private var verificationStartedAtMs: Long? = null
    /** The settings change awaiting verification, and how many attempts it has had. */
    private var pendingSettingVerification: SettingChange? = null
    private var settingAttempt = 0
    private var settingAttemptComplaint = ""
    private var reanalysisRound = 0
    private var preWbJpeg: ByteArray? = null

    private var observedSessionId = camera.state.value.sessionId
    private var captureInFlight = false
    private var voiceFinishRequested = false
    private var settingApplyInFlight = false
    private var resetInFlight = false
    private var restoreSettingAfterApply = false
    private var isBackgrounded = false
    private var visualCredentialsRejected = false
    private var latestLiveObservation: FrameObservation? = camera.observation.value
    private var liveObservationBarrierId: Long? = null
    private val stableFaceTracker = StableFaceTracker()
    private val comparisonSamples = ArrayDeque<FrameObservation>(3)
    private val pendingCommandSteps = ArrayDeque<CommandPlanStep>()
    private val approvedPlanAdjustments = mutableListOf<CameraAdjustment>()
    private var pendingVisualFocusText: String? = null
    private var pendingSubjectZoom: PendingSubjectZoom? = null
    private var pendingFocusAfterZoom: PendingFocusAfterZoom? = null
    private val recentCameraChanges = ArrayDeque<CameraChangeSnapshot>(3)
    private var activeCommandText = ""
    private var compositionAfterEnhance = false
    private var stableFace: FaceObservation? = null
    private var flashChangeInFlight = false
    private var focusInFlight = false
    private var compositionWatching: Boolean
        get() = _uiState.value.compositionEnabled
        set(value) { _uiState.update { it.copy(compositionEnabled = value) } }
    private var compositionScene: FrameObservation? = null
    private var previewMirrored = false
    private var stationaryComposition = false
    private var guidanceSceneAnchor: FrameObservation? = null
    private var compositionZoomInFlight = false
    private var compositionZoomAttempts = 0

    private fun logComposition(event: String) {
        java.util.logging.Logger.getLogger("CompositionGuidance").info("composition event=$event timeMs=${nowMs()}")
    }

    private fun logAgent(kind: AgentLogKind, message: String) {
        val entry = AgentLogEntry(kind, message.take(300), nowMs())
        _uiState.update { it.copy(agentLog = (it.agentLog + entry).takeLast(50)) }
    }

    fun setCompositionPreview(mirrored: Boolean, stationaryOnly: Boolean) {
        previewMirrored = mirrored
        stationaryComposition = stationaryOnly
        _uiState.update { it.copy(previewMirrored = mirrored) }
    }

    init {
        hideHintsAfterDelay()
        camera.setObservationImageEnabled(initialSettings.visualAiEnabled && initialSettings.keyConfigured)
        viewModelScope.launch {
            camera.state.collect { cameraState ->
                if (cameraState.sessionId != observedSessionId) {
                    observedSessionId = cameraState.sessionId
                    invalidateCameraSession()
                }
                _uiState.update {
                    it.copy(
                        cameraPhase = if (it.review != null) CameraPhase.REVIEWING else cameraState.phase,
                        transientMessage = cameraState.message ?: it.transientMessage,
                    )
                }
            }
        }
        viewModelScope.launch {
            camera.capabilities.collect { capabilities ->
                _uiState.update { it.copy(capabilities = capabilities) }
            }
        }
        viewModelScope.launch {
            camera.observation.collect { observation ->
                if (isBackgrounded) return@collect
                val barrierId = liveObservationBarrierId
                if (observation != null && barrierId != null && observation.id <= barrierId) return@collect
                if (observation != null) liveObservationBarrierId = null
                latestLiveObservation = observation
                if (observation == null) {
                    comparisonSamples.clear()
                    verifyActiveWork(null)
                } else {
                    comparisonSamples.addLast(observation)
                    while (comparisonSamples.size > 3) comparisonSamples.removeFirst()
                }
                stableFace = stableFaceTracker.update(observation, camera.state.value.sessionId)
                _uiState.value.compositionSelection?.let { previous ->
                    val matched = observation?.takeIf { previous.isNotEmpty() }?.let { matchMembers(previous, it.faces) }
                    if (matched != null) _uiState.update { it.copy(compositionSelection = matched) }
                }
                if (observation != null) {
                    verifyActiveWork(observation)
                    verifySettingChange(observation)
                    maybeRefreshComposition(observation)
                }
            }
        }
        viewModelScope.launch {
            uiState.map { it.transientMessage }.distinctUntilChanged().collectLatest { message ->
                if (message == null) return@collectLatest
                delay(TOAST_TIMEOUT_MS)
                _uiState.update {
                    if (it.transientMessage == message) {
                        it.copy(
                            coachingPhase = if (it.coachingPhase == CoachingPhase.TRANSIENT_ERROR) {
                                CoachingPhase.IDLE
                            } else {
                                it.coachingPhase
                            },
                            transientMessage = null,
                        )
                    } else {
                        it
                    }
                }
            }
        }
        if (arSession != null) {
            viewModelScope.launch {
                arSession.latestFrame.collect { frame ->
                    if (frame != null) {
                        _spatialState.value = spatialTracker.update(frame)
                    }
                }
            }
            viewModelScope.launch {
                spatialState.collect { spatial ->
                    if (spatial.isStill && readyForAutoCapture && _uiState.value.settings.autoCaptureEnabled && _uiState.value.shutterEnabled) {
                        readyForAutoCapture = false
                        _uiState.update { it.copy(autoCaptureFlashKey = it.autoCaptureFlashKey + 1) }
                        capture()
                    }
                }
            }
        }
    }

    fun finishOnboarding() {
        preferences.setOnboardingComplete()
        _uiState.update { it.copy(onboardingStep = 2) }
    }

    /** The Orb hint is shown once ever; any interaction with the Orb retires it. */
    fun markFirstUseHintSeen() {
        if (!_uiState.value.showFirstUseHint) return
        preferences.setFirstUseHintSeen()
        _uiState.update {
            it.copy(
                showFirstUseHint = false,
                showVoiceHints = !preferences.hasUsedVoice(),
            )
        }
        hideHintsAfterDelay()
    }

    private fun hideHintsAfterDelay() {
        viewModelScope.launch {
            delay(HINT_VISIBLE_MS)
            _uiState.update { it.copy(showFirstUseHint = false, showVoiceHints = false) }
        }
    }

    fun setCameraPermission(granted: Boolean) = _uiState.update {
        it.copy(cameraPermission = if (granted) PermissionState.GRANTED else PermissionState.DENIED)
    }

    fun retryCamera() {
        if (_uiState.value.cameraPhase != CameraPhase.BLOCKED) return
        _uiState.update { it.copy(cameraPhase = CameraPhase.STARTING, transientMessage = null) }
    }

    fun setMicrophonePermission(granted: Boolean) = _uiState.update {
        it.copy(
            microphonePermission = if (granted) PermissionState.GRANTED else PermissionState.DENIED,
            transientMessage = if (granted) it.transientMessage else "Microphone unavailable.",
        )
    }

    fun refreshPermissions(cameraGranted: Boolean, microphoneGranted: Boolean) = _uiState.update {
        it.copy(
            cameraPermission = if (cameraGranted) PermissionState.GRANTED else PermissionState.DENIED,
            microphonePermission = when {
                microphoneGranted -> PermissionState.GRANTED
                it.microphonePermission == PermissionState.GRANTED -> PermissionState.DENIED
                else -> it.microphonePermission
            },
        )
    }

    fun updateComment(comment: String) {
        if (_uiState.value.coachingPhase == CoachingPhase.APPLYING) return
        val next = comment.take(300)
        if (next != _uiState.value.comment) {
            pendingCommandSteps.clear()
            approvedPlanAdjustments.clear()
        }
        if (activeComplaintId != null && next != _uiState.value.comment) cancelCoaching()
        _uiState.update { it.copy(comment = next) }
    }

    fun submitComment(replacement: String? = null) {
        if (_uiState.value.coachingPhase == CoachingPhase.APPLYING) return
        reanalysisRound = 0
        preWbJpeg = null
        val comment = (replacement ?: _uiState.value.comment).trim()
        if (comment.isNotBlank()) logAgent(AgentLogKind.USER, comment)
        if (comment.lowercase() in setOf("help me frame", "help me frame this", "composition", "help with composition", "frame the group", "frame us")) {
            requestComposition()
            return
        }
        if (BuildConfig.DEBUG) {
            debugTestCommand(comment)?.let { plan ->
                logAgent(AgentLogKind.AI, "Debug plan: ${plan.steps.joinToString()}")
                startCommandPlan(plan, comment)
                return
            }
        }
        if (comment.isBlank()) {
            _uiState.update { it.copy(transientMessage = "Describe the current shot first.") }
            return
        }
        if (comment.lowercase() in setOf("reset", "reset settings", "reset all settings", "reset camera settings") ||
            (_uiState.value.resetAvailable && comment.equals("undo last camera adjustment", ignoreCase = true))
        ) {
            reset()
            return
        }
        if (canUseVisualAi()) {
            val parsedPlan = parseCommandPlan(comment)
            if (parsedPlan.steps.none { it is CommandPlanStep.Coach }) {
                startCommandPlan(parsedPlan, comment)
                return
            }
            if (parsedPlan.steps.size != 1 || parsedPlan.steps.single() !is CommandPlanStep.Coach) {
                requestCommandPlan(comment)
                return
            }
            if (parseVoiceCommand(comment) != null) {
                requestCommandPlan(comment)
                return
            }
            splitSettingAndFocus(comment)?.let { request ->
                if (request.intents == listOf(ControlIntent.ZOOM_IN)) {
                    cancelCoaching()
                    pendingSubjectZoom = PendingSubjectZoom(comment, request.small)
                    resolveVisualFocus(request.focusText)
                    return
                }
                startCommandPlan(
                    CommandPlan(listOf(CommandPlanStep.Adjust(request.intents, small = request.small))),
                    comment,
                    visualFocusAfterSettings = request.focusText,
                )
                return
            }
            immediateSettingIntents(comment)?.let { intents ->
                startCommandPlan(
                    CommandPlan(listOf(CommandPlanStep.Adjust(intents, small = SMALL_ADJUSTMENT_REQUEST.containsMatchIn(comment)))),
                    comment,
                )
                return
            }
            if (TARGET_FOCUS_REQUEST.containsMatchIn(comment)) {
                resolveVisualFocus(comment)
                return
            }
            if (GENERAL_IMPROVEMENT_REQUEST.containsMatchIn(comment)) {
                compositionAfterEnhance = true
                requestCommandPlan(comment, autoEnhance = true)
                return
            }
            requestCommandPlan(comment)
            return
        }
        submitLocalCommand(comment)
    }

    private fun debugTestCommand(comment: String): CommandPlan? {
        val cmd = comment.lowercase().trim()
        if (!cmd.startsWith("test ")) return null
        val steps: List<CommandPlanStep> = when (cmd.removePrefix("test ").trim()) {
            "brighter" -> listOf(CommandPlanStep.Adjust(listOf(ControlIntent.EXPOSURE_BRIGHTER)))
            "darker" -> listOf(CommandPlanStep.Adjust(listOf(ControlIntent.EXPOSURE_DARKER)))
            "zoom" -> listOf(CommandPlanStep.Adjust(listOf(ControlIntent.ZOOM_IN)))
            "zoom out" -> listOf(CommandPlanStep.Adjust(listOf(ControlIntent.ZOOM_OUT)))
            "warmer" -> listOf(CommandPlanStep.Adjust(listOf(ControlIntent.WHITE_BALANCE_WARMER)))
            "cooler" -> listOf(CommandPlanStep.Adjust(listOf(ControlIntent.WHITE_BALANCE_COOLER)))
            "focus" -> listOf(CommandPlanStep.FocusPoint(0.5f, 0.5f))
            "capture" -> listOf(CommandPlanStep.Capture(3))
            "flash" -> listOf(CommandPlanStep.SetFlash(FlashMode.ON))
            "reset" -> listOf(CommandPlanStep.Reset)
            "combo" -> listOf(
                CommandPlanStep.Adjust(listOf(ControlIntent.EXPOSURE_BRIGHTER)),
                CommandPlanStep.Adjust(listOf(ControlIntent.ZOOM_IN)),
                CommandPlanStep.Capture(3),
            )
            else -> return null
        }
        return CommandPlan(steps)
    }

    private fun immediateSettingIntents(text: String): List<ControlIntent>? =
        (coach.classifyComplaint(text) as? IntentClassification.Intent)
            ?.values
            ?.takeIf { intents -> intents.isNotEmpty() && intents.all(IMMEDIATE_SETTING_INTENTS::contains) }

    private fun splitSettingAndFocus(text: String): SettingAndFocus? {
        val clauses = text.split(COMMAND_CLAUSE_SEPARATOR).filter(String::isNotBlank)
        if (clauses.size != 2) return null
        val focus = clauses.singleOrNull(TARGET_FOCUS_REQUEST::containsMatchIn) ?: return null
        val setting = clauses.singleOrNull { it != focus } ?: return null
        return SettingAndFocus(
            immediateSettingIntents(setting) ?: return null,
            focus,
            SMALL_ADJUSTMENT_REQUEST.containsMatchIn(setting),
        )
    }

    private fun resolveVisualFocus(comment: String) {
        val face = latestLiveObservation
            ?.takeIf { nowMs() - it.timestampMs <= LIVE_OBSERVATION_FRESH_MS }
            ?.faces
            ?.singleOrNull()
        if (face != null && PERSON_FOCUS_REQUEST.containsMatchIn(comment)) {
            val bounds = runCatching {
                SubjectBounds(
                    face.left.coerceIn(0f, 1f),
                    face.top.coerceIn(0f, 1f),
                    face.right.coerceIn(0f, 1f),
                    face.bottom.coerceIn(0f, 1f),
                )
            }.getOrNull()
            val hint = VisualHint.FocusPoint(
                face.centerX.coerceIn(0f, 1f),
                face.centerY.coerceIn(0f, 1f),
                bounds,
            )
            if (pendingSubjectZoom != null) planSubjectZoom(hint) else startCommandPlan(
                CommandPlan(listOf(CommandPlanStep.FocusPoint(hint.xFraction, hint.yFraction))),
                comment,
            )
        } else {
            submitCoaching(comment, allowRemote = true)
        }
    }

    fun makeItNicer() {
        if (!_uiState.value.shutterEnabled || _uiState.value.coachingPhase != CoachingPhase.IDLE) return
        reanalysisRound = 0
        preWbJpeg = null
        compositionAfterEnhance = true
        if (!canUseVisualAi()) {
            compositionAfterEnhance = false
            _uiState.update {
                it.copy(
                    coachingPhase = CoachingPhase.TRANSIENT_ERROR,
                    transientMessage = "Automatic improvements are not set up yet. See Settings.",
                )
            }
            return
        }
        requestCommandPlan("Make this shot look nicer.", autoEnhance = true)
    }

    fun bestShot() {
        compositionAfterEnhance = true
        if (!canUseVisualAi()) {
            compositionAfterEnhance = false
            requestComposition()
            return
        }
        makeItNicer()
    }

    private fun submitLocalCommand(comment: String, fallbackMessage: String? = null) {
        immediateSettingIntents(comment)?.let { intents ->
            startCommandPlan(
                CommandPlan(listOf(CommandPlanStep.Adjust(intents, small = SMALL_ADJUSTMENT_REQUEST.containsMatchIn(comment)))),
                comment,
            )
            fallbackMessage?.let { message -> _uiState.update { it.copy(transientMessage = message) } }
            return
        }
        val plan = parseCommandPlan(comment)
        if (plan.steps.size > 1 || plan.steps.single() !is CommandPlanStep.Coach) {
            startCommandPlan(plan, comment)
            return
        }
        submitCoaching(comment, allowRemote = false)
        fallbackMessage?.let { message -> _uiState.update { it.copy(transientMessage = message) } }
    }

    private fun submitCoaching(comment: String, allowRemote: Boolean = true) {
        cancelCoaching(clearDecision = false, preserveCommandPlan = true)
        val complaintId = UUID.randomUUID().toString()
        activeComplaintId = complaintId
        _uiState.update {
            it.copy(
                comment = comment,
                coachingPhase = CoachingPhase.INTERPRETING,
                decision = null,
                transientMessage = null,
            )
        }
        operationJob = viewModelScope.launch {
            val input = coachingInput(complaintId, comment)
            val decision = coach.evaluateLocal(input).withProvenance(input)
            if (activeComplaintId != complaintId) return@launch
            val eligibility = (decision as? LocalDecision.Clarify)?.visualEligibility
            val objectFocusRequested = OBJECT_FOCUS_REQUEST.containsMatchIn(comment)
            val focusFallback = if (objectFocusRequested) {
                coach.planIntent(input, ControlIntent.FOCUS_POINT_REQUIRED).withProvenance(input)
            } else null
            when {
                objectFocusRequested && allowRemote && canUseVisualAi() &&
                    input.origin == ObservationOrigin.LIVE && input.observation != null ->
                    requestVisualHint(
                        input,
                        VisualEligibility(input.complaintId, VisualFamily.OBJECT_FOCUS, input.origin, input.observation.id),
                        focusFallback ?: decision,
                    )
                eligibility != null && allowRemote && canUseVisualAi() -> requestVisualHint(input, eligibility, decision)
                else -> publishLocalDecision(focusFallback ?: decision)
            }
        }
    }

    fun selectClarification(replacementComplaint: String) = submitComment(replacementComplaint)

    fun dismissDecision() {
        if (_uiState.value.coachingPhase == CoachingPhase.APPLYING) return
        cancelCoaching()
        _uiState.update { it.copy(decision = null, coachingPhase = CoachingPhase.IDLE, transientMessage = null) }
    }

    fun dismissTransientMessage() {
        _uiState.update {
            it.copy(
                coachingPhase = if (it.coachingPhase == CoachingPhase.TRANSIENT_ERROR) {
                    CoachingPhase.IDLE
                } else {
                    it.coachingPhase
                },
                transientMessage = null,
            )
        }
    }

    fun applyRecommendation() {
        val recommendation = currentRecommendation() ?: return
        applyResolvedRecommendation(recommendation)
    }

    fun zoomBy(factor: Float) {
        if (!factor.isFinite() || factor <= 0f || _uiState.value.cameraPhase != CameraPhase.READY) return
        val range = _uiState.value.capabilities.zoomRatioRange
        val target = (camera.telemetry.value.zoomRatio * factor).coerceIn(range.start, range.endInclusive)
        if (target == camera.telemetry.value.zoomRatio) return
        viewModelScope.launch {
            when (val result = camera.apply(CameraAdjustment.ZoomRatio(target))) {
                ApplyResult.Applied -> markResetAvailable()
                is ApplyResult.Failed -> showIdleMessage(result.message)
            }
        }
    }

    private fun applyResolvedRecommendation(recommendation: Recommendation) {
        if (_uiState.value.coachingPhase == CoachingPhase.APPLYING) return
        if (recommendation.cameraSessionId != camera.state.value.sessionId) {
            invalidateCameraSession()
            failWork("The camera session changed. Describe the shot again before applying a change.")
            return
        }
        val action = recommendation.action as? RecommendationAction.ApplySettings ?: return
        val beforeTelemetry = camera.telemetry.value
        val requestText = activeCommandText.ifBlank { _uiState.value.comment }
        logAgent(AgentLogKind.ACTION, "Apply ${action.changes.joinToString { it.adjustment.toString() }}")
        cancelJobsOnly()
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.APPLYING,
                decision = if (autoApplyRecommendations) null else it.decision,
                transientMessage = null,
            )
        }
        settingApplyInFlight = true
        operationJob = viewModelScope.launch {
            try {
                if (action.changes.any { it.adjustment is CameraAdjustment.WhiteBalance }) {
                    preWbJpeg = camera.observationImage(null)
                }
                val result = camera.applyAtomically(action.changes.map { it.adjustment })
                if (restoreSettingAfterApply) {
                    restoreAfterBackground()
                    return@launch
                }
                when (result) {
                    ApplyResult.Applied -> {
                        rememberCameraChange(requestText, beforeTelemetry, camera.telemetry.value)
                        val wasReview = _uiState.value.review != null
                        if (wasReview) camera.setAnalysisPaused(false)
                        if (pendingCommandSteps.isNotEmpty()) {
                            action.changes.map { it.adjustment }.forEach { adjustment ->
                                approvedPlanAdjustments.removeAll { it::class == adjustment::class }
                                approvedPlanAdjustments += adjustment
                            }
                            _uiState.update {
                                it.copy(
                                    review = null,
                                    cameraPhase = CameraPhase.READY,
                                    coachingPhase = CoachingPhase.IDLE,
                                    retakeSettingsActive = wasReview,
                                    transientMessage = null,
                                )
                            }
                            markResetAvailable()
                            if (_uiState.value.settings.haptics) feedback(Feedback.TICK)
                            advanceCommandPlan()
                            return@launch
                        }
                        audioCue?.play(AudioCue.CHIME)
                        readyForAutoCapture = arSession != null
                        markResetAvailable()
                        if (_uiState.value.settings.haptics) feedback(Feedback.TICK)
                        // Applying is not succeeding. Hold the frame still for a moment and
                        // check the measurement actually moved the way the plan intended;
                        // startSettingVerification decides whether to finish or try again.
                        val verifiable = action.changes.singleOrNull()
                        if (verifiable != null && _uiState.value.review == null) {
                            startSettingVerification(verifiable, recommendation, wasReview)
                            return@launch
                        }
                        activeComplaintId = null
                        if (maybeStartReanalysis()) return@launch
                        if (maybeStartCompositionAfterEnhance()) return@launch
                        _uiState.update {
                            it.copy(
                                review = null,
                                cameraPhase = CameraPhase.READY,
                                coachingPhase = CoachingPhase.IDLE,
                                retakeSettingsActive = wasReview,
                                activeGuidance = null,
                                transientMessage = recommendation.actionText,
                            )
                        }
                        continueFocusAfterZoomIfPending()
                        continueVisualFocusIfPending()
                    }
                    is ApplyResult.Failed -> failWork(result.message)
                }
            } finally {
                settingApplyInFlight = false
                restoreSettingAfterApply = false
                resumeAnalysisAfterControl()
            }
        }
    }

    fun startGuidance() {
        if (_uiState.value.activeGuidance != null || _uiState.value.coachingPhase == CoachingPhase.APPLYING) return
        val recommendation = currentRecommendation() ?: return
        val action = recommendation.action as? RecommendationAction.GuidePosition ?: return
        (action.target as? VerificationTarget.Composition)?.let { composition ->
            val observation = latestLiveObservation ?: return
            val members = matchMembers(composition.plan.members, observation.faces) ?: return
            beginComposition(compileComposition(composition.plan.intent, members, observation.deviceRollDegrees))
            return
        }
        cancelJobsOnly()
        val tracksFace = action.target is VerificationTarget.FaceOccupancy ||
            action.target is VerificationTarget.FacePosition ||
            action.target is VerificationTarget.StepBack
        val subjectFace = if (tracksFace) stableFace else null
        val subjectTrackingId = subjectFace?.trackingId
        if (tracksFace && (recommendation.subjectFace == null || subjectFace == null || !sameSubject(recommendation.subjectFace, subjectFace))) {
            failWork("The person or camera session changed. Hold the frame on the same person, then ask again.")
            return
        }
        val wasReview = _uiState.value.review != null
        if (wasReview) camera.setAnalysisPaused(false)
        val guidance = ActiveGuidance("Hold while I check the framing", action.target, nowMs(), subjectTrackingId, subjectFace,
            distanceMovement = action.requiresWalkingWarning)
        _uiState.update {
            it.copy(
                review = if (wasReview) null else it.review,
                cameraPhase = if (wasReview) CameraPhase.READY else it.cameraPhase,
                coachingPhase = CoachingPhase.GUIDING,
                activeGuidance = guidance,
                transientMessage = if (action.requiresWalkingWarning) {
                    "Photo Helper cannot see obstacles. Move only if you can independently verify the path."
                } else null,
            )
        }
        if (_uiState.value.settings.spokenGuidance) voice.speak(guidance.instruction, "guidance")
        watchGuidance(guidance)
    }

    fun requestComposition() {
        if (!_uiState.value.shutterEnabled || isBackgrounded) return
        cancelCoaching()
        val observation = latestLiveObservation
        if (observation == null || nowMs() - observation.timestampMs !in 0..LIVE_OBSERVATION_FRESH_MS) {
            showToast("Hold the camera steady, then try framing again.")
            return
        }
        compositionWatching = true
        logAgent(AgentLogKind.ACTION, "Check composition")
        val members = automaticCompositionMembers(comparisonSamples.toList())
        logComposition(if (members == null) "selection_prompt" else "automatic_selection_count_${members.size}")
        if (members == null && observation.faces.isNotEmpty()) {
            changeCompositionSelection()
            return
        }
        chooseComposition(members.orEmpty())
    }

    fun changeCompositionSelection() {
        logComposition("selection_opened")
        cancelCoaching()
        compositionWatching = true
        val observation = latestLiveObservation ?: return
        if (nowMs() - observation.timestampMs !in 0..LIVE_OBSERVATION_FRESH_MS) return
        _uiState.update { it.copy(compositionSelection = observation.faces, compositionSelectedIndices = emptySet()) }
    }

    fun toggleCompositionFace(index: Int) {
        _uiState.update {
            if (index !in it.compositionSelection.orEmpty().indices) it else it.copy(
                compositionSelectedIndices = if (index in it.compositionSelectedIndices)
                    it.compositionSelectedIndices - index else it.compositionSelectedIndices + index,
            )
        }
    }

    fun selectAllCompositionFaces() {
        _uiState.update { it.copy(compositionSelectedIndices = it.compositionSelection.orEmpty().indices.toSet()) }
    }

    fun confirmCompositionSelection() {
        val state = _uiState.value
        val chosen = state.compositionSelection?.filterIndexed { index, _ -> index in state.compositionSelectedIndices } ?: return
        if (chosen.isEmpty()) return
        val observation = latestLiveObservation ?: return
        val members = if (nowMs() - observation.timestampMs in 0..LIVE_OBSERVATION_FRESH_MS)
            matchMembers(chosen, observation.faces) else null
        if (members == null) {
            changeCompositionSelection()
            _uiState.update { it.copy(transientMessage = "The faces moved. Choose them again.") }
            return
        }
        _uiState.update { it.copy(compositionSelection = null, compositionSelectedIndices = emptySet()) }
        logComposition("manual_selection_count_${members.size}")
        chooseComposition(members)
    }

    private fun chooseComposition(members: List<FaceObservation>) {
        compositionScene = latestLiveObservation?.copy(faces = members)
        if (!canUseVisualAi()) {
            logComposition("generic_strategy")
            beginComposition(compileComposition(CompositionIntent(), members, latestLiveObservation?.deviceRollDegrees))
            return
        }
        val original = latestLiveObservation ?: return
        val session = camera.state.value.sessionId
        val requestId = UUID.randomUUID().toString()
        activeComplaintId = requestId
        _uiState.update { it.copy(coachingPhase = CoachingPhase.REQUESTING_VISUAL_INTERPRETATION, decision = null) }
        visualJob = viewModelScope.launch {
            var key: CharArray? = null
            var jpeg: ByteArray? = null
            try {
                jpeg = camera.observationImage(expectedObservationId = original.id)
                key = loadApiKey()
                val result = withTimeoutOrNull(6_000) {
                    val selectedSubset = members.isNotEmpty() && members.size < original.faces.size
                    if (selectedSubset) {
                        val originalJpeg = jpeg
                        jpeg = if (originalJpeg == null) null else withContext(Dispatchers.Default) {
                            try { markCompositionMembers(originalJpeg, members) } finally { originalJpeg.fill(0) }
                        }
                    }
                    val image = jpeg
                    val credential = key
                    val selectedWidth = faceUnion(members)?.widthFraction
                    if (image == null || credential == null) null else interpretVisual(
                        VisualRequest(VisualFamily.COMPOSITION,
                            "Suggest a composition for ${members.size} selected people, or scene advice if none. " +
                                (selectedWidth?.let { "Selected people width is ${(it * 100).toInt()}% of the image. " } ?: "") +
                                (if (selectedSubset) "Only people outlined in yellow are selected. " else "All detected people are selected. ") +
                                "Do not suggest selecting other people.", image), credential)
                }
                if (activeComplaintId != requestId || session != camera.state.value.sessionId || isBackgrounded) return@launch
                val current = latestLiveObservation
                val matched = current?.let { matchMembers(members, it.faces) }
                if (current == null || matched == null || nowMs() - current.timestampMs !in 0..LIVE_OBSERVATION_FRESH_MS ||
                    !lensMatches(original.lensId, original.focalLengthMm, current.lensId, current.focalLengthMm) ||
                    exposureInvariantSceneDifference(original.sceneLumaSignature, current.sceneLumaSignature) > .08f) {
                    showToast("The scene changed. Tap Best shot to try again.")
                    return@launch
                }
                if (result == VisualResult.CredentialsRejected) markSavedKeyRejected()
                val intent = ((result as? VisualResult.Available)?.hint as? VisualHint.CompositionPlan)?.intent
                if (intent != null) logAgent(AgentLogKind.AI, "Composition: ${intent.reason}")
                logComposition(if (intent == null) "ai_generic_fallback" else "ai_strategy_${intent.strategy}")
                visualJob = null
                val chosen = intent ?: CompositionIntent()
                val decision = coach.continueWithVisualHint(
                    coachingInput(requestId, "composition").copy(compositionMembers = matched),
                    VisualFamily.COMPOSITION, VisualHint.CompositionPlan(chosen),
                )
                val target = ((decision as? LocalDecision.Recommend)?.recommendation?.action as? RecommendationAction.GuidePosition)
                    ?.target as? VerificationTarget.Composition
                if (target != null) beginComposition(target.plan)
                else publishLocalDecision(decision)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                logComposition("ai_exception_fallback")
                if (activeComplaintId == requestId && session == camera.state.value.sessionId && !isBackgrounded) {
                    val current = latestLiveObservation
                    val matched = current?.let { matchMembers(members, it.faces) }
                    visualJob = null
                    if (matched != null && nowMs() - current.timestampMs in 0..LIVE_OBSERVATION_FRESH_MS &&
                        lensMatches(original.lensId, original.focalLengthMm, current.lensId, current.focalLengthMm) &&
                        exposureInvariantSceneDifference(original.sceneLumaSignature, current.sceneLumaSignature) <= .08f)
                        beginComposition(compileComposition(CompositionIntent(), matched, current.deviceRollDegrees))
                    else showToast("The scene changed. Tap Best shot to try again.")
                }
            } finally {
                key?.fill('\u0000')
                jpeg?.fill(0)
            }
        }
    }

    private fun beginComposition(plan: CompositionPlan) {
        guidanceSceneAnchor = latestLiveObservation
        if (stationaryComposition && plan.guidanceMode == GuidanceMode.CLOSED_LOOP) {
            publishLocalDecision(LocalDecision.Advisory("Composition idea",
                "Keep the phone stationary. Adjust zoom or ask someone to help frame the selected people."))
            return
        }
        if (plan.guidanceMode == GuidanceMode.ADVICE_ONLY) {
            logComposition("advice_only_${plan.intent.strategy}")
            publishLocalDecision(LocalDecision.Advisory("Composition idea", plan.advice))
            return
        }
        cancelJobsOnly()
        val adjustment = plan.intent.adjustment
        if (adjustment != null && adjustment.movement == CompositionMovement.ZOOM && adjustment.size != CompositionSize.KEEP) {
            val scale = if (adjustment.size == CompositionSize.LARGER) 1.3f else 0.75f
            val currentZoom = camera.telemetry.value.zoomRatio
            val range = _uiState.value.capabilities.zoomRatioRange
            val targetZoom = (currentZoom * scale).coerceIn(range.start, range.endInclusive)
            logComposition("auto_zoom_${adjustment.size}_from_${currentZoom}_to_$targetZoom")
            viewModelScope.launch {
                camera.applyAtomically(listOf(CameraAdjustment.ZoomRatio(targetZoom)))
                delay(400)
                guidanceSceneAnchor = latestLiveObservation
                startGuidanceLoop(plan)
            }
            return
        }
        startGuidanceLoop(plan)
    }

    private fun startGuidanceLoop(plan: CompositionPlan) {
        val started = nowMs()
        val guidance = ActiveGuidance(
            "Hold the camera while I check the framing", VerificationTarget.Composition(plan), started,
            members = plan.members, governor = GuidanceGovernor(started, plan.policy),
            distanceMovement = plan.distanceMovement,
        )
        logComposition("closed_loop_${guidance.governor.sessionId}_members_${plan.members.size}")
        _uiState.update { it.copy(activeGuidance = guidance, coachingPhase = CoachingPhase.GUIDING,
            decision = null, transientMessage = null, compositionSelection = null) }
        watchGuidance(guidance)
    }

    fun cannotMoveFurther() {
        val active = _uiState.value.activeGuidance ?: return
        if (_uiState.value.coachingPhase != CoachingPhase.GUIDING || active.paused || !active.correction.isMovement) return
        active.governor.suppressMovement()
        _uiState.update { it.copy(activeGuidance = active.copy(
            blockedDirections = active.blockedDirections + active.correction,
            correction = com.bolin.photohelper.coach.Correction.HOLD,
            instruction = "Hold while I check another adjustment", nearTarget = false)) }
        verifyActiveWork(latestLiveObservation)
    }

    private fun watchGuidance(guidance: ActiveGuidance) {
        guidanceTimeoutJob = viewModelScope.launch {
            var waited = 0L
            while (waited < guidance.governor.policy.timeoutMs) {
                delay(250)
                waited += 250
                if (_uiState.value.activeGuidance?.governor !== guidance.governor) return@launch
                val current = latestLiveObservation
                if (current == null || nowMs() - current.timestampMs > LIVE_OBSERVATION_FRESH_MS) verifyActiveWork(null)
            }
            if (_uiState.value.activeGuidance?.governor === guidance.governor) {
                finishGuidance("timeout")
                failWork("I couldn’t confirm the framing. Try again or change the selection.")
            }
        }
    }

    private fun maybeRefreshComposition(observation: FrameObservation) {
        val state = _uiState.value
        if (!compositionWatching || state.coachingPhase != CoachingPhase.IDLE || state.review != null ||
            state.compositionSelection != null || !state.shutterEnabled) return
        val previous = compositionScene ?: return
        val samples = comparisonSamples.toList()
        if (samples.size < 3 || samples.last().timestampMs - samples.first().timestampMs < 500 ||
            samples.any { it.motionScore > .02f } ||
            samples.any { exposureInvariantSceneDifference(it.sceneLumaSignature, observation.sceneLumaSignature) > .02f }) return
        val changed = exposureInvariantSceneDifference(previous.sceneLumaSignature, observation.sceneLumaSignature) > .08f ||
            (previous.faces.isEmpty() && observation.faces.isNotEmpty()) ||
            matchMembers(previous.faces, observation.faces) == null
        if (!changed) return
        compositionScene = observation
        val members = automaticCompositionMembers(samples)
        if (members == null && observation.faces.isNotEmpty()) changeCompositionSelection()
        else chooseComposition(members.orEmpty())
    }

    fun cancelCoaching(clearDecision: Boolean = true, preserveCommandPlan: Boolean = false) {
        compositionWatching = false
        reanalysisRound = 0
        preWbJpeg = null
        compositionAfterEnhance = false
        if (settingApplyInFlight || resetInFlight) return
        if (!preserveCommandPlan) {
            pendingCommandSteps.clear()
            approvedPlanAdjustments.clear()
            activeCommandText = ""
            pendingVisualFocusText = null
            pendingSubjectZoom = null
            pendingFocusAfterZoom = null
        }
        cancelJobsOnly()
        voiceFinishRequested = false
        readyForAutoCapture = false
        activeComplaintId = null
        voice.stop()
        verificationStartObservationId = null
        verificationStartedAtMs = null
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.IDLE,
                decision = if (clearDecision) null else it.decision,
                activeGuidance = null,
                countdownSecondsRemaining = null,
            )
        }
    }

    fun capture() {
        if (captureInFlight || !_uiState.value.shutterEnabled) return
        logAgent(AgentLogKind.ACTION, "Take photo")
        captureInFlight = true
        readyForAutoCapture = false
        audioCue?.play(AudioCue.SHUTTER)
        cancelCoaching()
        _uiState.update { it.copy(cameraPhase = CameraPhase.CAPTURING) }
        operationJob = viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { camera.capture() }
                    ?: CaptureResult.Failed(CAPTURE_TIMEOUT_MESSAGE)
                when (result) {
                    is CaptureResult.Saved -> _uiState.update {
                        it.copy(
                            cameraPhase = CameraPhase.REVIEWING,
                            review = result.capture,
                            comment = "",
                            transientMessage = null,
                        )
                    }
                    is CaptureResult.Failed -> {
                        _uiState.update { it.copy(cameraPhase = camera.state.value.phase) }
                        failWork(result.message)
                    }
                }
            } finally {
                captureInFlight = false
            }
        }
    }

    private fun startCaptureCountdown(seconds: Int) {
        if (!_uiState.value.shutterEnabled) return
        cancelCoaching()
        _uiState.update {
            it.copy(
                countdownSecondsRemaining = seconds,
                transientMessage = countdownMessage(seconds),
            )
        }
        countdownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _uiState.update {
                    it.copy(countdownSecondsRemaining = remaining, transientMessage = countdownMessage(remaining))
                }
                delay(1_000)
            }
            countdownJob = null
            _uiState.update { it.copy(countdownSecondsRemaining = null) }
            capture()
        }
    }

    fun leaveReview() {
        if (_uiState.value.coachingPhase == CoachingPhase.APPLYING) return
        cancelCoaching()
        camera.setAnalysisPaused(false)
        _uiState.update {
            it.copy(
                review = null,
                cameraPhase = camera.state.value.phase,
                comment = "",
                decision = null,
                retakeSettingsActive = false,
            )
        }
    }

    fun reset() {
        if (_uiState.value.coachingPhase == CoachingPhase.APPLYING) return
        cancelCoaching()
        _uiState.update { it.copy(coachingPhase = CoachingPhase.APPLYING, transientMessage = null) }
        resetInFlight = true
        operationJob = viewModelScope.launch {
            try {
                when (val result = camera.reset()) {
                    ApplyResult.Applied -> {
                        recentCameraChanges.clear()
                        _uiState.update { it.copy(
                            coachingPhase = CoachingPhase.IDLE,
                            decision = null,
                            resetAvailable = false,
                            retakeSettingsActive = false,
                            flashMode = FlashMode.OFF,
                            transientMessage = "Automatic camera settings restored.",
                        ) }
                    }
                    is ApplyResult.Failed -> failWork(result.message)
                }
            } finally {
                resetInFlight = false
                resumeAnalysisAfterControl()
            }
        }
    }

    fun startVoiceInput() {
        if (_uiState.value.microphonePermission != PermissionState.GRANTED ||
            _uiState.value.coachingPhase == CoachingPhase.APPLYING
        ) return
        if (_uiState.value.showVoiceHints) {
            preferences.setHasUsedVoice()
            _uiState.update { it.copy(showVoiceHints = false) }
        }
        cancelCoaching(clearDecision = false)
        voiceFinishRequested = false
        _uiState.update { it.copy(coachingPhase = CoachingPhase.LISTENING, comment = "", transientMessage = null) }
        operationJob = viewModelScope.launch {
            val result = withTimeoutOrNull(VOICE_INPUT_TIMEOUT_MS) { voice.listenOnce() }
                ?: VoiceResult.Failed(VOICE_INPUT_TIMEOUT_MESSAGE)
            operationJob = null
            voiceFinishRequested = false
            when (result) {
                is VoiceResult.Heard -> {
                    updateComment(result.text)
                    submitComment(result.text)
                }
                is VoiceResult.Unavailable -> {
                    voice.stop()
                    showToast(result.message)
                }
                is VoiceResult.Failed -> {
                    voice.stop()
                    showToast(result.message)
                }
            }
        }
    }

    private fun startCommandPlan(
        plan: CommandPlan,
        sourceText: String,
        visualFocusAfterSettings: String? = null,
    ) {
        cancelCoaching()
        activeCommandText = sourceText
        pendingVisualFocusText = visualFocusAfterSettings
        pendingCommandSteps.addAll(plan.steps.sortedBy { step ->
            when (step) {
                is CommandPlanStep.FocusPoint -> 1
                is CommandPlanStep.Capture -> 2
                else -> 0
            }
        })
        _uiState.update { it.copy(comment = "", decision = null, coachingPhase = CoachingPhase.IDLE) }
        advanceCommandPlan()
    }

    private fun advanceCommandPlan() {
        when (val step = pendingCommandSteps.pollFirst()) {
            is CommandPlanStep.Coach -> {
                _uiState.update { it.copy(comment = step.text) }
                submitCoaching(step.text, allowRemote = false)
            }
            is CommandPlanStep.Adjust -> {
                val complaintId = UUID.randomUUID().toString()
                activeComplaintId = complaintId
                val currentInput = coachingInput(complaintId, activeCommandText)
                val input = currentInput.copy(
                    relativeBaseline = if (step.small) {
                        recentCameraChanges.peekLast()?.before ?: currentInput.telemetry
                    } else null,
                )
                val decision = coach.planIntents(input, step.intents).withProvenance(
                    input,
                    controlIntents = step.intents,
                )
                _uiState.update { it.copy(comment = activeCommandText) }
                if (decision !is LocalDecision.Recommend) {
                    pendingCommandSteps.clear()
                    approvedPlanAdjustments.clear()
                    pendingVisualFocusText = null
                }
                publishLocalDecision(decision)
            }
            is CommandPlanStep.SetCamera -> requestCameraFacing(
                when (step.facing) {
                    CameraFacing.TOGGLE -> CameraFacingRequest.TOGGLE
                    CameraFacing.FRONT -> CameraFacingRequest.FRONT
                    CameraFacing.REAR -> CameraFacingRequest.REAR
                },
            )
            is CommandPlanStep.SetFlash -> setFlashMode(step.mode, continuePlan = true)
            is CommandPlanStep.FocusPoint -> {
                val complaintId = UUID.randomUUID().toString()
                activeComplaintId = complaintId
                val input = coachingInput(complaintId, activeCommandText)
                val decision = coach.continueWithVisualHint(
                    input,
                    VisualFamily.OBJECT_FOCUS,
                    VisualHint.FocusPoint(step.xFraction, step.yFraction),
                ).withProvenance(
                    input,
                    visualFamily = VisualFamily.OBJECT_FOCUS,
                    visualHint = VisualHint.FocusPoint(step.xFraction, step.yFraction),
                )
                _uiState.update { it.copy(comment = activeCommandText) }
                if (decision !is LocalDecision.Recommend) {
                    pendingCommandSteps.clear()
                    approvedPlanAdjustments.clear()
                    pendingVisualFocusText = null
                }
                publishLocalDecision(decision)
            }
            CommandPlanStep.Reset -> reset()
            is CommandPlanStep.Capture -> {
                if (step.countdownSeconds == null) capture() else startCaptureCountdown(step.countdownSeconds)
            }
            null -> {
                activeCommandText = ""
                approvedPlanAdjustments.clear()
            }
        }
    }

    private fun requestCameraFacing(request: CameraFacingRequest) {
        cancelCoaching(preserveCommandPlan = true)
        _uiState.update { it.copy(comment = "", decision = null, coachingPhase = CoachingPhase.IDLE) }
        _cameraFacingRequests.tryEmit(request)
    }

    fun cameraFacingRequestCompleted(success: Boolean) {
        if (!success) {
            pendingCommandSteps.clear()
            approvedPlanAdjustments.clear()
            return
        }
        if (approvedPlanAdjustments.isEmpty()) {
            advanceCommandPlan()
            return
        }
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.APPLYING,
                transientMessage = "Putting your changes back…",
            )
        }
        settingApplyInFlight = true
        operationJob = viewModelScope.launch {
            try {
                val result = camera.applyAtomically(approvedPlanAdjustments.toList())
                if (restoreSettingAfterApply) {
                    restoreAfterBackground()
                    return@launch
                }
                when (result) {
                    ApplyResult.Applied -> {
                        _uiState.update {
                            it.copy(
                                cameraPhase = CameraPhase.READY,
                                coachingPhase = CoachingPhase.IDLE,
                                transientMessage = null,
                            )
                        }
                        markResetAvailable()
                        advanceCommandPlan()
                    }
                    is ApplyResult.Failed -> failWork(
                        "The other camera could not make those changes.",
                    )
                }
            } finally {
                settingApplyInFlight = false
                restoreSettingAfterApply = false
                resumeAnalysisAfterControl()
            }
        }
    }

    fun reportCameraSwitchMessage(message: String) = _uiState.update {
        it.copy(transientMessage = message)
    }

    fun cycleFlashMode() {
        val state = _uiState.value
        if (!state.shutterEnabled || !state.capabilities.hasFlashUnit || flashChangeInFlight) return
        val next = when (state.flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
        setFlashMode(next, continuePlan = false)
    }

    private fun setFlashMode(mode: FlashMode, continuePlan: Boolean) {
        if (flashChangeInFlight) return
        flashChangeInFlight = true
        operationJob = viewModelScope.launch {
            try {
                when (val result = camera.setFlashMode(mode)) {
                    ApplyResult.Applied -> {
                        _uiState.update { it.copy(flashMode = mode, transientMessage = null) }
                        if (continuePlan) markResetAvailable()
                        if (continuePlan) advanceCommandPlan()
                    }
                    is ApplyResult.Failed -> {
                        if (continuePlan) {
                            pendingCommandSteps.clear()
                            approvedPlanAdjustments.clear()
                        }
                        failWork(result.message)
                    }
                }
            } finally {
                flashChangeInFlight = false
                operationJob = null
            }
        }
    }

    fun finishVoiceInput() {
        if (_uiState.value.coachingPhase != CoachingPhase.LISTENING || voiceFinishRequested) return
        voiceFinishRequested = true
        voice.finishListening()
        _uiState.update { it.copy(transientMessage = "Finishing voice input…") }
    }

    fun isVoiceInputAvailable(): Boolean = voice.isOnDeviceRecognitionAvailable()

    fun reportVoiceUnavailable() = _uiState.update {
        it.copy(
            coachingPhase = CoachingPhase.TRANSIENT_ERROR,
            transientMessage = "On-device speech recognition is unavailable.",
        )
    }

    fun openSettings(open: Boolean) = _uiState.update { it.copy(settingsOpen = open) }

    fun setSpokenGuidance(enabled: Boolean) {
        preferences.setSpokenGuidance(enabled)
        if (!enabled) {
            if (_uiState.value.coachingPhase == CoachingPhase.LISTENING) cancelCoaching() else voice.stop()
        }
        updateSettings { it.copy(spokenGuidance = enabled) }
    }

    fun setHaptics(enabled: Boolean) {
        preferences.setHaptics(enabled)
        updateSettings { it.copy(haptics = enabled) }
    }

    fun setTechnicalDetail(enabled: Boolean) {
        preferences.setTechnicalDetail(enabled)
        updateSettings { it.copy(technicalDetail = enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
        updateSettings { it.copy(themeMode = mode) }
    }

    fun setStyleProfile(profile: String) {
        val trimmed = profile.take(MAX_STYLE_PROFILE_CHARACTERS)
        preferences.setStyleProfile(trimmed)
        updateSettings { it.copy(styleProfile = trimmed) }
    }

    fun setAutoCaptureEnabled(enabled: Boolean) {
        preferences.setAutoCaptureEnabled(enabled)
        if (!enabled) readyForAutoCapture = false
        updateSettings { it.copy(autoCaptureEnabled = enabled) }
    }

    fun setCaptionConsentGiven(given: Boolean) {
        preferences.setCaptionConsentGiven(given)
        updateSettings { it.copy(captionConsentGiven = given) }
    }

    fun setGridOverlayEnabled(enabled: Boolean) {
        preferences.setGridOverlayEnabled(enabled)
        updateSettings { it.copy(gridOverlayEnabled = enabled) }
        if (enabled && _uiState.value.settings.spokenGuidance) {
            voice.speak("Grid overlay is on. You can turn it off in Settings.", "setting")
        }
    }

    fun setTiltIndicatorEnabled(enabled: Boolean) {
        preferences.setTiltIndicatorEnabled(enabled)
        updateSettings { it.copy(tiltIndicatorEnabled = enabled) }
        if (enabled && _uiState.value.settings.spokenGuidance) {
            voice.speak("Tilt indicator is on. You can turn it off in Settings.", "setting")
        }
    }

    fun setVisualAiEnabled(enabled: Boolean) {
        if (enabled && visualCredentialsRejected) {
            camera.setObservationImageEnabled(false)
            updateSettings {
                it.copy(
                    visualAiEnabled = false,
                    keyStatus = "Saved key rejected. Test it again",
                )
            }
            _uiState.update { it.copy(transientMessage = "Still off. Test the key again to turn it on.") }
            return
        }
        val allowed = enabled && _uiState.value.settings.keyConfigured
        if (!allowed) {
            visualJob?.cancel()
            visualJob = null
            if (_uiState.value.coachingPhase == CoachingPhase.REQUESTING_VISUAL_INTERPRETATION) {
                val fallback = activeComplaintId?.let { complaintId ->
                    val input = coachingInput(complaintId, _uiState.value.comment)
                    coach.evaluateLocal(input).withProvenance(input)
                }
                _uiState.update {
                    it.copy(
                        decision = fallback,
                        coachingPhase = if (fallback is LocalDecision.Recommend) {
                            CoachingPhase.RECOMMENDATION
                        } else {
                            CoachingPhase.IDLE
                        },
                        transientMessage = "Turned off. Using on-device coaching now.",
                    )
                }
            }
        }
        preferences.setVisualAiEnabled(allowed)
        camera.setObservationImageEnabled(allowed)
        updateSettings { it.copy(visualAiEnabled = allowed) }
    }

    fun focusAt(xFraction: Float, yFraction: Float) = focusAt(xFraction, yFraction, keepRecommendation = false)

    private fun focusAt(xFraction: Float, yFraction: Float, keepRecommendation: Boolean) {
        if (_uiState.value.coachingPhase == CoachingPhase.APPLYING && !focusInFlight) return
        if (!xFraction.isFinite() || !yFraction.isFinite() || xFraction !in 0f..1f || yFraction !in 0f..1f) {
            failWork("Choose a focus point inside the preview.")
            return
        }
        val recommendation = _uiState.value.recommendation
        if (recommendation != null) {
            val current = currentRecommendation() ?: return
            if (current.action !is RecommendationAction.TapToFocus &&
                current.action !is RecommendationAction.FocusAt
            ) return
        } else if (_uiState.value.review != null || _uiState.value.cameraPhase != CameraPhase.READY) {
            return
        }
        if (!camera.capabilities.value.supportsFocusMetering) {
            cancelCoaching()
            failWork("Tap to focus is unavailable on this camera.")
            return
        }
        val focusPoint = FocusPoint(xFraction, yFraction)
        logAgent(AgentLogKind.ACTION, "Focus at ${"%.2f".format(xFraction)}, ${"%.2f".format(yFraction)}")
        val indicatorDecision = if (keepRecommendation) _uiState.value.decision else null
        focusIndicatorJob?.cancel()
        operationJob?.cancel()
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.APPLYING,
                focusIndicator = focusPoint,
                transientMessage = null,
            )
        }
        focusIndicatorJob = viewModelScope.launch {
            delay(FOCUS_INDICATOR_MS)
            _uiState.update {
                it.copy(
                    focusIndicator = if (it.focusIndicator == focusPoint) null else it.focusIndicator,
                    decision = if (indicatorDecision != null && it.decision === indicatorDecision) null else it.decision,
                )
            }
            focusIndicatorJob = null
        }
        focusInFlight = true
        operationJob = viewModelScope.launch {
            when (val result = camera.focusAt(xFraction, yFraction)) {
                ApplyResult.Applied -> {
                    focusInFlight = false
                    activeComplaintId = null
                    if (_uiState.value.settings.haptics) feedback(Feedback.SUCCESS)
                    _uiState.update {
                        it.copy(
                            coachingPhase = CoachingPhase.IDLE,
                            decision = null,
                            transientMessage = null,
                        )
                    }
                    markResetAvailable()
                    if (pendingCommandSteps.isNotEmpty()) advanceCommandPlan()
                }
                is ApplyResult.Failed -> {
                    focusInFlight = false
                    _uiState.update {
                        it.copy(
                            decision = if (autoApplyRecommendations) null else it.decision,
                            focusIndicator = null,
                        )
                    }
                    failWork(result.message)
                }
            }
        }
    }

    fun testAndSaveKey(apiKey: CharArray) {
        val requestKey = apiKey.copyOf()
        apiKey.fill('\u0000')
        val testImage = try {
            createTestImage()
        } catch (_: Exception) {
            null
        }
        if (testImage == null || requestKey.isEmpty()) {
            requestKey.fill('\u0000')
            testImage?.fill(0)
            updateSettings { it.copy(testingKey = false, keyStatus = "Enter a key first") }
            return
        }
        cancelKeyTest()
        updateSettings { it.copy(testingKey = true, keyStatus = "Testing key…") }
        keyTestJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var keyForStorage: CharArray? = null
            try {
                val storageKey = requestKey.copyOf()
                keyForStorage = storageKey
                val request = VisualRequest(VisualFamily.COLOR_CAST, "neutral test pattern", testImage)
                when (val result = interpretVisual(request, requestKey)) {
                    is VisualResult.Available -> {
                        runCatching { saveApiKey(storageKey) }
                            .onSuccess {
                                visualCredentialsRejected = false
                                preferences.setVisualAiEnabled(true)
                                camera.setObservationImageEnabled(true)
                                updateSettings {
                                    it.copy(
                                        visualAiEnabled = true,
                                        keyConfigured = true,
                                        keyStatus = "Key tested, saved, and enabled",
                                    )
                                }
                            }
                            .onFailure { updateSettings { it.copy(keyStatus = "Could not save key") } }
                    }
                    VisualResult.CredentialsRejected -> updateSettings { it.copy(keyStatus = "Key rejected") }
                    is VisualResult.Failed -> {
                        updateSettings { it.copy(keyStatus = result.message) }
                        showToast(result.message)
                    }
                    VisualResult.Unavailable -> updateSettings { it.copy(keyStatus = "Key test failed") }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateSettings { it.copy(keyStatus = "Key test failed") }
            } finally {
                requestKey.fill('\u0000')
                keyForStorage?.fill('\u0000')
                testImage.fill(0)
                updateSettings {
                    it.copy(
                        testingKey = false,
                        keyStatus = if (it.keyStatus == "Testing key…") "Key test cancelled" else it.keyStatus,
                    )
                }
            }
        }
    }

    fun clearKey() {
        cancelKeyTest()
        visualCredentialsRejected = false
        setVisualAiEnabled(false)
        if (runCatching { clearApiKey() }.isSuccess) {
            updateSettings {
                it.copy(
                    keyConfigured = false,
                    keyStatus = "No key saved",
                    testingKey = false,
                )
            }
        } else {
            val configured = runCatching { hasApiKey() }.getOrDefault(false)
            updateSettings {
                it.copy(
                    visualAiEnabled = false,
                    keyConfigured = configured,
                    keyStatus = if (configured) "Could not clear key" else "Key removed; cleanup can be retried",
                    testingKey = false,
                )
            }
        }
    }

    fun onBackground() {
        isBackgrounded = true
        advanceLiveObservationBarrier()
        clearLiveObservationProvenance()
        cancelKeyTest()
        pendingCommandSteps.clear()
        approvedPlanAdjustments.clear()
        if (settingApplyInFlight) restoreSettingAfterApply = true
        cancelCoaching()
        camera.setAnalysisPaused(true)
        if (_uiState.value.resetAvailable && !settingApplyInFlight && !resetInFlight) {
            resetInFlight = true
            _uiState.update { it.copy(coachingPhase = CoachingPhase.APPLYING, transientMessage = null) }
            operationJob = viewModelScope.launch {
                try {
                    restoreAfterBackground()
                } finally {
                    resetInFlight = false
                    resumeAnalysisAfterControl()
                }
            }
        }
    }

    fun onForeground() {
        advanceLiveObservationBarrier()
        isBackgrounded = false
        resumeAnalysisAfterControl()
    }

    override fun onCleared() {
        cancelKeyTest()
        cancelJobsOnly()
        voice.close()
        camera.close()
        audioCue?.release()
    }

    private fun requestVisualHint(
        originalInput: CoachingInput,
        eligibility: VisualEligibility,
        fallback: LocalDecision,
    ) {
        visualJob?.cancel()
        visualJob = viewModelScope.launch {
            _uiState.update { it.copy(coachingPhase = CoachingPhase.REQUESTING_VISUAL_INTERPRETATION) }
            var jpeg: ByteArray? = null
            var key: CharArray? = null
            try {
                val capture = _uiState.value.review
                jpeg = camera.observationImage(capture)
                key = runCatching { loadApiKey() }.getOrNull()
                val ownedJpeg = jpeg
                val ownedKey = key
                if (ownedKey == null) {
                    preferences.setVisualAiEnabled(false)
                    camera.setObservationImageEnabled(false)
                    updateSettings {
                        it.copy(
                            visualAiEnabled = false,
                            keyConfigured = false,
                            keyStatus = "Saved key unavailable. Enter it again",
                        )
                    }
                    if (activeComplaintId == originalInput.complaintId) keepVisualFallback(fallback)
                    return@launch
                }
                if (ownedJpeg == null || !visualProvenanceMatches(originalInput, eligibility)) {
                    if (activeComplaintId == originalInput.complaintId) keepVisualFallback(fallback)
                    return@launch
                }
                val result = interpretVisual(
                    VisualRequest(
                        eligibility.family,
                        originalInput.complaint,
                        ownedJpeg,
                    ),
                    ownedKey,
                )
                if (result == VisualResult.CredentialsRejected) {
                    visualCredentialsRejected = true
                    camera.setObservationImageEnabled(false)
                    updateSettings {
                        it.copy(
                            visualAiEnabled = false,
                            keyConfigured = true,
                            keyStatus = "Saved key rejected. Test it again",
                        )
                    }
                    if (activeComplaintId == originalInput.complaintId) {
                        keepVisualFallback(fallback, "AI interpretation disabled. The saved key was rejected.")
                    }
                    return@launch
                }
                if (activeComplaintId != originalInput.complaintId) return@launch
                if (!visualProvenanceMatches(originalInput, eligibility)) {
                    keepVisualFallback(fallback)
                    return@launch
                }
                when (result) {
                    is VisualResult.Available -> {
                        val freshInput = coachingInput(originalInput.complaintId, originalInput.complaint)
                        if (result.hint is VisualHint.FocusPoint && pendingSubjectZoom != null) {
                            planSubjectZoom(freshInput, result.hint)
                            return@launch
                        }
                        val decision = coach.continueWithVisualHint(freshInput, eligibility.family, result.hint)
                            .withProvenance(freshInput, eligibility.family, result.hint)
                        publishLocalDecision(decision)
                    }
                    VisualResult.CredentialsRejected ->
                        keepVisualFallback(fallback, "AI interpretation disabled. The saved key was rejected.")
                    is VisualResult.Failed -> {
                        pendingSubjectZoom = null
                        showToast(result.message)
                    }
                    VisualResult.Unavailable -> keepVisualFallback(fallback)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (activeComplaintId == originalInput.complaintId) keepVisualFallback(fallback)
            } finally {
                jpeg?.fill(0)
                key?.fill('\u0000')
            }
        }
    }

    private fun requestCommandPlan(comment: String, autoEnhance: Boolean = false) {
        cancelCoaching()
        val complaintId = UUID.randomUUID().toString()
        activeComplaintId = complaintId
        val originalInput = coachingInput(complaintId, comment)
        val originalFlashMode = _uiState.value.flashMode
        fun useLocalFallback(message: String) {
            visualJob = null
            if (autoEnhance) {
                activeComplaintId = null
                _uiState.update {
                    it.copy(
                        comment = "",
                        coachingPhase = CoachingPhase.TRANSIENT_ERROR,
                        decision = null,
                        transientMessage = message,
                    )
                }
            } else {
                submitLocalCommand(comment, message)
            }
        }
        visualJob?.cancel()
        visualJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    comment = comment,
                    coachingPhase = CoachingPhase.REQUESTING_VISUAL_INTERPRETATION,
                    decision = null,
                    transientMessage = null,
                )
            }
            var key: CharArray? = null
            var jpeg: ByteArray? = null
            try {
                key = runCatching { loadApiKey() }.getOrNull()
                val ownedKey = key
                if (ownedKey == null) {
                    markSavedKeyUnavailable()
                    if (activeComplaintId == complaintId) {
                        useLocalFallback("AI interpretation unavailable. Using local coaching.")
                    }
                    return@launch
                }
                if (!complaintProvenanceMatches(originalInput)) {
                    if (activeComplaintId == complaintId) {
                        useLocalFallback("Camera frame changed. Using local coaching.")
                    }
                    return@launch
                }
                val observation = originalInput.observation
                if (observation == null) {
                    useLocalFallback("No camera frame was available. Using local coaching.")
                    return@launch
                }
                val requestJpeg = camera.observationImage(null) ?: run {
                    useLocalFallback("Camera image unavailable. Using local coaching.")
                    return@launch
                }
                jpeg = requestJpeg
                val result = interpretCommand(
                    CommandRequest(
                        comment = comment,
                        observationJpeg = requestJpeg,
                        telemetry = originalInput.telemetry,
                        capabilities = originalInput.capabilities,
                        flashMode = originalFlashMode,
                        autoEnhance = autoEnhance,
                        frameObservation = observation,
                        recentChanges = recentCameraChanges.toList(),
                        styleProfile = _uiState.value.settings.styleProfile,
                    ),
                    ownedKey,
                )
                if (result == CommandResult.CredentialsRejected) {
                    markSavedKeyRejected()
                    if (activeComplaintId == complaintId) {
                        useLocalFallback("AI interpretation disabled. The saved key was rejected.")
                    }
                    return@launch
                }
                if (!complaintProvenanceMatches(originalInput)) {
                    if (activeComplaintId == complaintId) {
                        useLocalFallback("Camera frame changed. Using local coaching.")
                    }
                    return@launch
                }
                log.info("command result=$result comment=${comment.take(40)} autoEnhance=$autoEnhance reanalysis=$reanalysisRound")
                when (result) {
                    is CommandResult.Planned -> {
                        if (result.compositionSuggested) compositionAfterEnhance = true
                        logAgent(AgentLogKind.AI, "Plan: ${result.plan.steps.joinToString()}")
                        startCommandPlan(result.plan, comment)
                    }
                    is CommandResult.Clarified ->
                        useLocalFallback("AI interpretation needs clarification. Using local coaching.")
                    CommandResult.CompositionOnly -> {
                        compositionAfterEnhance = true
                        maybeStartCompositionAfterEnhance()
                    }
                    CommandResult.NoChange -> {
                        if (reanalysisRound > 0) {
                            reanalysisRound = 0
                            showIdleMessage("Looking good!")
                        } else if (!compositionAfterEnhance) {
                            showIdleMessage("Looks good already.")
                        }
                        maybeStartCompositionAfterEnhance()
                    }
                    is CommandResult.WbComparison -> handleWbVerdict(result.verdict)
                    CommandResult.Unsure -> showToast("The model isn’t sure what to do. Please try again.")
                    is CommandResult.Failed -> showToast(result.message)
                    CommandResult.CredentialsRejected ->
                        useLocalFallback("AI interpretation disabled. The saved key was rejected.")
                    CommandResult.Unavailable ->
                        useLocalFallback("AI interpretation unavailable. Using local coaching.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (activeComplaintId == complaintId) {
                    useLocalFallback("AI interpretation unavailable. Using local coaching.")
                }
            } finally {
                jpeg?.fill(0)
                key?.fill('\u0000')
            }
        }
    }

    private fun keepVisualFallback(
        fallback: LocalDecision,
        message: String = "AI interpretation unavailable. Using local coaching.",
    ) {
        pendingSubjectZoom = null
        _uiState.update { it.copy(transientMessage = message) }
        publishLocalDecision(fallback)
    }

    private fun planSubjectZoom(hint: VisualHint.FocusPoint) {
        val complaintId = activeComplaintId ?: UUID.randomUUID().toString()
        planSubjectZoom(coachingInput(complaintId, pendingSubjectZoom?.sourceText.orEmpty()), hint)
    }

    private fun planSubjectZoom(input: CoachingInput, hint: VisualHint.FocusPoint) {
        val pending = pendingSubjectZoom ?: return
        pendingSubjectZoom = null
        activeCommandText = pending.sourceText
        activeComplaintId = input.complaintId
        val decision = coach.planSubjectZoom(input, hint.bounds, pending.small).withProvenance(input)
        val zoom = (decision as? LocalDecision.Recommend)
            ?.recommendation
            ?.action
            ?.let { it as? RecommendationAction.ApplySettings }
            ?.adjustment as? CameraAdjustment.ZoomRatio
        if (zoom == null) {
            startCommandPlan(
                CommandPlan(listOf(CommandPlanStep.FocusPoint(hint.xFraction, hint.yFraction))),
                pending.sourceText,
            )
            return
        }
        pendingFocusAfterZoom = PendingFocusAfterZoom(
            pending.sourceText,
            hint,
            input.telemetry.zoomRatio,
        )
        _uiState.update { it.copy(comment = pending.sourceText) }
        publishLocalDecision(decision)
    }

    private fun publishLocalDecision(decision: LocalDecision) {
        if (decision is LocalDecision.Advisory && decision.headline.endsWith("limit reached")) {
            pendingCommandSteps.clear()
            approvedPlanAdjustments.clear()
            showToast(decision.detail)
            return
        }
        val recommendation = (decision as? LocalDecision.Recommend)?.recommendation
        val autoApplied = autoApplyRecommendations &&
            recommendation?.action is RecommendationAction.ApplySettings
        _uiState.update {
            it.copy(
                decision = if (autoApplied) null else decision,
                coachingPhase = if (decision is LocalDecision.Recommend) CoachingPhase.RECOMMENDATION else CoachingPhase.IDLE,
            )
        }
        if (!autoApplyRecommendations) return
        when (val action = recommendation?.action) {
            is RecommendationAction.ApplySettings -> applyResolvedRecommendation(recommendation)
            is RecommendationAction.FocusAt -> focusAt(action.xFraction, action.yFraction, keepRecommendation = true)
            else -> Unit
        }
    }

    private fun markResetAvailable() {
        _uiState.update { it.copy(resetAvailable = true) }
    }

    private fun showToast(message: String) {
        _uiState.update {
            it.copy(coachingPhase = CoachingPhase.TRANSIENT_ERROR, decision = null, transientMessage = message)
        }
    }

    private fun showIdleMessage(message: String) {
        _uiState.update {
            it.copy(coachingPhase = CoachingPhase.IDLE, decision = null, transientMessage = message)
        }
    }

    private fun rememberCameraChange(request: String, before: CameraTelemetry, after: CameraTelemetry) {
        if (before.exposureCompensationIndex == after.exposureCompensationIndex &&
            abs(before.zoomRatio - after.zoomRatio) < 0.01f &&
            before.whiteBalancePreset == after.whiteBalancePreset &&
            before.whiteBalanceLevel == after.whiteBalanceLevel
        ) return
        if (recentCameraChanges.size == 3) recentCameraChanges.removeFirst()
        recentCameraChanges.addLast(CameraChangeSnapshot(request, before, after))
    }

    private fun visualProvenanceMatches(input: CoachingInput, eligibility: VisualEligibility): Boolean {
        if (!canUseVisualAi()) return false
        if (activeComplaintId != input.complaintId) return false
        if (camera.state.value.sessionId != input.cameraSessionId) return false
        val review = _uiState.value.review
        if (input.origin == ObservationOrigin.CAPTURE_REVIEW) return review?.observation?.id == eligibility.observationId
        val initial = input.observation ?: return false
        val current = latestLiveObservation ?: return false
        if (nowMs() - current.timestampMs > LIVE_OBSERVATION_FRESH_MS) return false
        return when (eligibility.family) {
            VisualFamily.COLOR_CAST -> {
                val initialBias = initial.chromaBlueBias ?: return false
                val currentBias = current.chromaBlueBias ?: return false
                abs(initialBias - currentBias) <= 0.05f
            }
            VisualFamily.FACE_SIZE_AMBIGUOUS -> {
                val first = input.lockedFace ?: return false
                val latest = stableFace ?: return false
                sameSubject(first, latest)
            }
            VisualFamily.OBJECT_FOCUS, VisualFamily.COMPOSITION -> lensMatches(
                initial.lensId,
                initial.focalLengthMm,
                current.lensId,
                current.focalLengthMm,
            )
        }
    }

    private fun complaintProvenanceMatches(input: CoachingInput): Boolean {
        if (!canUseVisualAi() || activeComplaintId != input.complaintId || camera.state.value.sessionId != input.cameraSessionId) return false
        val review = _uiState.value.review
        if (input.origin == ObservationOrigin.CAPTURE_REVIEW) return review?.observation?.id == input.observation?.id
        val current = latestLiveObservation ?: return false
        if (nowMs() - current.timestampMs > LIVE_OBSERVATION_FRESH_MS) return false
        return lensMatches(input.observation?.lensId, input.observation?.focalLengthMm, current.lensId, current.focalLengthMm)
    }

    private fun markSavedKeyUnavailable() {
        preferences.setVisualAiEnabled(false)
        camera.setObservationImageEnabled(false)
        updateSettings {
            it.copy(
                visualAiEnabled = false,
                keyConfigured = false,
                keyStatus = "Saved key unavailable. Enter it again",
            )
        }
    }

    private fun markSavedKeyRejected() {
        visualCredentialsRejected = true
        camera.setObservationImageEnabled(false)
        updateSettings {
            it.copy(
                visualAiEnabled = false,
                keyConfigured = true,
                keyStatus = "Saved key rejected. Test it again",
            )
        }
    }

    private fun coachingInput(complaintId: String, complaint: String): CoachingInput {
        val review = _uiState.value.review
        return CoachingInput(
            complaintId = complaintId,
            complaint = complaint,
            origin = if (review == null) ObservationOrigin.LIVE else ObservationOrigin.CAPTURE_REVIEW,
            cameraSessionId = camera.state.value.sessionId,
            observation = review?.observation ?: latestLiveObservation,
            lockedFace = review?.observation?.faces?.singleOrNull() ?: stableFace,
            capabilities = camera.capabilities.value,
            telemetry = if (review == null) camera.telemetry.value else review.telemetry ?: CameraTelemetry(),
            telemetryKnown = review == null || review.telemetry != null,
            comparisonBaseline = review?.observation ?: stableComparisonBaseline(),
        )
    }

    private fun stableComparisonBaseline(): FrameObservation? {
        if (comparisonSamples.size < 3) return null
        val samples = comparisonSamples.toList()
        if (samples.last().timestampMs - samples.first().timestampMs < 500) return null
        return samples.last().takeIf { latest ->
            samples.dropLast(1).all { observationsComparable(it, latest) }
        }
    }


    // ── Settings verification and bounded retry ────────────────────
    //
    // A settings change is not finished when the camera accepts it - it is finished
    // when the frame actually moved the way the plan intended. The coach engine could
    // always answer that question; until now nothing asked it for settings, so a change
    // that silently did nothing still reported success.
    //
    // On a miss the agent gets one more attempt, and only if it has a genuinely
    // different thing to try. Two visible attempts, then an honest concession.

    private fun startSettingVerification(
        change: SettingChange,
        recommendation: Recommendation,
        wasReview: Boolean,
    ) {
        pendingSettingVerification = change
        if (settingAttempt == 0) settingAttemptComplaint = activeCommandText.ifBlank { _uiState.value.comment }
        verificationStartObservationId = latestLiveObservation?.id
        verificationStartedAtMs = nowMs()
        _uiState.update {
            it.copy(
                review = null,
                cameraPhase = CameraPhase.READY,
                coachingPhase = CoachingPhase.VERIFYING,
                retakeSettingsActive = wasReview,
                activeGuidance = null,
                transientMessage = recommendation.actionText,
            )
        }
        verificationTimeoutJob?.cancel()
        verificationTimeoutJob = viewModelScope.launch {
            delay(SETTING_VERIFY_TIMEOUT_MS)
            // No comparable frame arrived in time. Treat that as unverifiable rather
            // than as failure - the change was applied, we simply cannot prove it.
            if (_uiState.value.coachingPhase == CoachingPhase.VERIFYING) {
                finishSettingWork(recommendation.actionText)
            }
        }
    }

    /** Called for every new frame while a settings change is awaiting verification. */
    private fun verifySettingChange(observation: FrameObservation) {
        val change = pendingSettingVerification ?: return
        if (_uiState.value.coachingPhase != CoachingPhase.VERIFYING) return
        // Ignore the frame the change was applied on, and give the sensor a moment to settle.
        if (observation.id == verificationStartObservationId) return
        if (observation.timestampMs < (verificationStartedAtMs ?: Long.MIN_VALUE) + SETTING_SETTLE_MS) return

        when (val result = coach.verify(change.target, observation)) {
            VerificationResult.Satisfied, VerificationResult.Progress -> finishSettingWork(null)
            // Applied, but unprovable. Say which, rather than implying success.
            is VerificationResult.Incomparable -> finishSettingWork(result.reason)
            VerificationResult.Unchanged -> retryOrConcede(change)
        }
    }

    private fun retryOrConcede(change: SettingChange) {
        verificationTimeoutJob?.cancel()
        verificationTimeoutJob = null
        pendingSettingVerification = null

        val reason = exhaustedReason(change.adjustment)
        if (pendingVisualFocusText != null || pendingFocusAfterZoom != null) {
            finishSettingWork(reason ?: "That setting did not visibly change; continuing with focus.")
            return
        }
        // Retrying only makes sense when the planner can reach a different answer. The
        // local engine is deterministic - a second run returns the same plan - so on the
        // local path a miss concedes immediately rather than burning a visible attempt.
        val canReplan = canUseVisualAi()
        if (!canReplan || settingAttempt >= MAX_SETTING_ATTEMPTS - 1 ||
            reason != null || settingAttemptComplaint.isBlank()
        ) {
            settingAttempt = 0
            // Say what stopped it, not just that something did.
            failWork(reason ?: "That did not change the shot. Try moving or changing the angle.")
            return
        }

        settingAttempt++
        // The retry is a normal planning request; what makes it a second attempt is the
        // failed change sitting in recentChanges, which the prompt already describes as
        // prior actions. The model can read that the last step did nothing.
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.INTERPRETING,
                transientMessage = "That did not take. Trying something else…",
            )
        }
        requestCommandPlan(settingAttemptComplaint)
    }

    /**
     * Why another attempt on this axis would be pointless. Checking the camera's own
     * limits first avoids spending a call to be told what the capabilities already say.
     */
    private fun exhaustedReason(adjustment: CameraAdjustment): String? {
        val capabilities = _uiState.value.capabilities
        val telemetry = camera.telemetry.value
        return when (adjustment) {
            is CameraAdjustment.ExposureCompensation -> {
                val range = capabilities.exposureCompensationRange
                when {
                    range.isEmpty() -> "This camera cannot change brightness."
                    telemetry.exposureCompensationIndex >= range.last ->
                        "Brightness is already as high as this camera goes."
                    telemetry.exposureCompensationIndex <= range.first ->
                        "Brightness is already as low as this camera goes."
                    else -> null
                }
            }
            is CameraAdjustment.ZoomRatio -> {
                val range = capabilities.zoomRatioRange
                when {
                    telemetry.zoomRatio >= range.endInclusive -> "Zoom is already at its maximum."
                    telemetry.zoomRatio <= range.start -> "Zoom is already at its minimum."
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun finishSettingWork(message: String?) {
        verificationTimeoutJob?.cancel()
        verificationTimeoutJob = null
        pendingSettingVerification = null
        settingAttempt = 0
        activeComplaintId = null
        verificationStartObservationId = null
        verificationStartedAtMs = null
        if (pendingVisualFocusText == null && pendingFocusAfterZoom == null && maybeStartReanalysis()) {
            return
        }
        if (maybeStartCompositionAfterEnhance()) return
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.IDLE,
                transientMessage = message ?: it.transientMessage,
            )
        }
        continueFocusAfterZoomIfPending()
        continueVisualFocusIfPending()
    }

    private fun maybeStartReanalysis(): Boolean {
        if (reanalysisRound >= MAX_REANALYSIS_ROUNDS || !canUseVisualAi()) return false
        reanalysisRound++
        val wbChanged = recentCameraChanges.any { change ->
            change.before.whiteBalancePreset != change.after.whiteBalancePreset ||
                change.before.whiteBalanceLevel != change.after.whiteBalanceLevel
        }
        if (wbChanged && preWbJpeg != null) {
            log.info("reanalysis round=$reanalysisRound wb-comparison")
            requestWbComparison()
            return true
        }
        val axes = recentCameraChanges.flatMap { change ->
            listOfNotNull(
                "exposure".takeIf { change.before.exposureCompensationIndex != change.after.exposureCompensationIndex },
                "white balance".takeIf { change.before.whiteBalancePreset != change.after.whiteBalancePreset || change.before.whiteBalanceLevel != change.after.whiteBalanceLevel },
                "zoom".takeIf { change.before.zoomRatio != change.after.zoomRatio },
            )
        }.distinct()
        val comment = if (axes.isNotEmpty()) {
            "Verify only the ${axes.joinToString()} adjustment just made. If it looks good, return no changes. Do not suggest changes to any other axis."
        } else {
            "Make this shot look nicer."
        }
        log.info("reanalysis round=$reanalysisRound axes=$axes")
        requestCommandPlan(comment, autoEnhance = true)
        return true
    }

    private fun requestWbComparison() {
        cancelCoaching()
        val complaintId = UUID.randomUUID().toString()
        activeComplaintId = complaintId
        val originalInput = coachingInput(complaintId, "Compare white balance.")
        visualJob?.cancel()
        visualJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    coachingPhase = CoachingPhase.REQUESTING_VISUAL_INTERPRETATION,
                    decision = null,
                    transientMessage = null,
                )
            }
            var key: CharArray? = null
            var afterJpeg: ByteArray? = null
            try {
                key = runCatching { loadApiKey() }.getOrNull()
                val ownedKey = key ?: run {
                    preWbJpeg = null
                    reanalysisRound = 0
                    showIdleMessage("Looking good!")
                    return@launch
                }
                afterJpeg = camera.observationImage(null) ?: run {
                    preWbJpeg = null
                    reanalysisRound = 0
                    showIdleMessage("Looking good!")
                    return@launch
                }
                val beforeJpeg = preWbJpeg ?: run {
                    reanalysisRound = 0
                    showIdleMessage("Looking good!")
                    return@launch
                }
                val result = interpretCommand(
                    CommandRequest(
                        comment = "Compare white balance.",
                        observationJpeg = afterJpeg,
                        telemetry = originalInput.telemetry,
                        capabilities = originalInput.capabilities,
                        flashMode = _uiState.value.flashMode,
                        autoEnhance = true,
                        frameObservation = originalInput.observation,
                        recentChanges = recentCameraChanges.toList(),
                        wbComparisonJpeg = beforeJpeg,
                    ),
                    ownedKey,
                )
                log.info("wb comparison result=$result reanalysis=$reanalysisRound")
                when (result) {
                    is CommandResult.WbComparison -> handleWbVerdict(result.verdict)
                    CommandResult.NoChange -> {
                        preWbJpeg = null
                        reanalysisRound = 0
                        showIdleMessage("Looking good!")
                    }
                    else -> {
                        preWbJpeg = null
                        reanalysisRound = 0
                        showIdleMessage("Looking good!")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                preWbJpeg = null
                reanalysisRound = 0
                showIdleMessage("Looking good!")
            } finally {
                afterJpeg?.fill(0)
                key?.fill(' ')
            }
        }
    }

    private fun handleWbVerdict(verdict: WbVerdict) {
        when (verdict) {
            WbVerdict.KEEP -> {
                log.info("wb verdict=KEEP, stopping")
                preWbJpeg = null
                reanalysisRound = 0
                showIdleMessage("Looking good!")
            }
            WbVerdict.MORE -> {
                log.info("wb verdict=MORE, applying one more step")
                val lastWbChange = recentCameraChanges.lastOrNull { change ->
                    change.before.whiteBalanceLevel != change.after.whiteBalanceLevel
                }
                if (lastWbChange == null || reanalysisRound >= MAX_REANALYSIS_ROUNDS) {
                    preWbJpeg = null
                    reanalysisRound = 0
                    showIdleMessage("Looking good!")
                    return
                }
                val direction = lastWbChange.after.whiteBalanceLevel - lastWbChange.before.whiteBalanceLevel
                val currentLevel = camera.telemetry.value.whiteBalanceLevel
                val targetLevel = currentLevel + direction
                val supported = camera.capabilities.value.supportedWhiteBalanceLevels
                if (targetLevel !in supported) {
                    preWbJpeg = null
                    reanalysisRound = 0
                    showIdleMessage("White balance at limit.")
                    return
                }
                val preset = if (direction > 0) WhiteBalancePreset.WARMER else WhiteBalancePreset.COOLER
                activeComplaintId = null
                operationJob = viewModelScope.launch {
                    val beforeTelemetry = camera.telemetry.value
                    val applyResult = camera.applyAtomically(
                        listOf(CameraAdjustment.WhiteBalance(preset, targetLevel)),
                    )
                    if (applyResult == ApplyResult.Applied) {
                        rememberCameraChange("WB comparison MORE", beforeTelemetry, camera.telemetry.value)
                        if (maybeStartReanalysis()) return@launch
                    }
                    preWbJpeg = null
                    reanalysisRound = 0
                    showIdleMessage("Looking good!")
                }
            }
            WbVerdict.REVERT -> {
                log.info("wb verdict=REVERT, undoing WB change")
                val lastWbChange = recentCameraChanges.lastOrNull { change ->
                    change.before.whiteBalanceLevel != change.after.whiteBalanceLevel
                }
                if (lastWbChange == null) {
                    preWbJpeg = null
                    reanalysisRound = 0
                    showIdleMessage("Looking good!")
                    return
                }
                val revertLevel = lastWbChange.before.whiteBalanceLevel
                val revertPreset = when {
                    revertLevel > 0 -> WhiteBalancePreset.WARMER
                    revertLevel < 0 -> WhiteBalancePreset.COOLER
                    else -> WhiteBalancePreset.AUTO
                }
                activeComplaintId = null
                operationJob = viewModelScope.launch {
                    camera.applyAtomically(
                        listOf(CameraAdjustment.WhiteBalance(revertPreset, revertLevel)),
                    )
                    preWbJpeg = null
                    reanalysisRound = 0
                    recentCameraChanges.pollLast()
                    showIdleMessage("Reverted white balance.")
                }
            }
        }
    }

    private fun maybeStartCompositionAfterEnhance(): Boolean {
        if (!compositionAfterEnhance) return false
        compositionAfterEnhance = false
        requestComposition()
        return true
    }

    private fun continueFocusAfterZoomIfPending() {
        val pending = pendingFocusAfterZoom ?: return
        pendingFocusAfterZoom = null
        val currentZoom = latestLiveObservation?.zoomRatio ?: camera.telemetry.value.zoomRatio
        val scale = (currentZoom / pending.sourceZoomRatio).takeIf(Float::isFinite) ?: 1f
        val x = (0.5f + (pending.point.xFraction - 0.5f) * scale).coerceIn(0f, 1f)
        val y = (0.5f + (pending.point.yFraction - 0.5f) * scale).coerceIn(0f, 1f)
        startCommandPlan(CommandPlan(listOf(CommandPlanStep.FocusPoint(x, y))), pending.sourceText)
    }

    private fun continueVisualFocusIfPending() {
        val focusText = pendingVisualFocusText ?: return
        pendingVisualFocusText = null
        resolveVisualFocus(focusText)
    }

    private fun verifyActiveWork(observation: FrameObservation?) {
        val active = _uiState.value.activeGuidance ?: return
        if (_uiState.value.coachingPhase == CoachingPhase.GUIDING) {
            val anchor = guidanceSceneAnchor
            val sceneJump = active.target is VerificationTarget.Composition && observation != null && anchor != null &&
                exposureInvariantSceneDifference(anchor.sceneLumaSignature, observation.sceneLumaSignature) > .20f
            if (sceneJump) {
                val samples = comparisonSamples.toList()
                if (samples.size == 3 && samples.last().timestampMs - samples.first().timestampMs >= 500 &&
                    samples.all { it.motionScore <= .02f &&
                        exposureInvariantSceneDifference(anchor?.sceneLumaSignature, it.sceneLumaSignature) > .20f &&
                        exposureInvariantSceneDifference(observation?.sceneLumaSignature, it.sceneLumaSignature) <= .02f }) {
                    finishGuidance("scene_changed")
                    cancelJobsOnly()
                    _uiState.update { it.copy(activeGuidance = null, coachingPhase = CoachingPhase.IDLE) }
                    return
                }
            } else if (observation != null) guidanceSceneAnchor = observation
            val members = observation?.let { matchMembers(active.members, it.faces) }
            val fresh = observation != null && nowMs() - observation.timestampMs in 0..LIVE_OBSERVATION_FRESH_MS
            val selected = if (members != null && fresh && !sceneJump) observation.copy(faces = members) else null
            val measurement = selected?.let { measureGuidance(listOf(active.target), it, active.blockedDirections, active.distanceMovement) }
            if (selected != null && measurement?.correction in setOf(
                    com.bolin.photohelper.coach.Correction.LARGER,
                    com.bolin.photohelper.coach.Correction.SMALLER,
                ) && !active.distanceMovement && applyCompositionZoom(active, selected)) return
            val result = active.governor.update(measurement, nowMs())
            if (result.failure != null) {
                finishGuidance(result.failure)
                guidanceTimeoutJob?.cancel()
                guidanceTimeoutJob = null
                failWork(if (result.failure == "movement_blocked") "Keep this position. This framing needs movement you've ruled out."
                    else "I couldn’t confirm this framing. Reframe freely or change the selection.")
                return
            }
            if (result.complete) {
                if (active.target is VerificationTarget.Composition) compositionScene = observation?.copy(faces = members.orEmpty())
                compositionWatching = false
                finishGuidance("completion")
                val message = if (active.target is VerificationTarget.Composition) "Framing done." else if (active.target is VerificationTarget.StepBack)
                    "The face is smaller. Decide whether the proportions look better."
                    else "Stop. Hold there."
                completeWork(message, Feedback.SUCCESS)
                if (active.target is VerificationTarget.Composition &&
                    _uiState.value.settings.autoCaptureEnabled && observation?.motionScore?.let { it <= .02f } == true) capture()
                return
            }
            val near = measurement != null && result.correction == measurement.correction &&
                measurement.error < if (active.nearTarget) 1f else .5f
            val instruction = when {
                result.correction == com.bolin.photohelper.coach.Correction.HOLD ->
                    if (measurement?.satisfied == true) "Stop. Hold there." else "Hold while I check the framing"
                near -> "A little more. " + result.correction.phoneInstruction(previewMirrored)
                else -> result.correction.phoneInstruction(previewMirrored)
            }
            _uiState.update { state -> state.copy(activeGuidance = active.copy(
                instruction = instruction, members = members ?: active.members,
                subjectFace = members?.singleOrNull() ?: active.subjectFace,
                subjectTrackingId = members?.singleOrNull()?.trackingId ?: active.subjectTrackingId,
                paused = selected == null, correction = result.correction, nearTarget = near,
            )) }
            if (instruction != active.instruction) {
                logAgent(AgentLogKind.ACTION, instruction)
                if (_uiState.value.settings.spokenGuidance) voice.speak(instruction, "guidance")
                if (_uiState.value.settings.haptics) feedback(Feedback.TICK)
            }
            return
        }
    }

    private fun applyCompositionZoom(active: ActiveGuidance, observation: FrameObservation): Boolean {
        if (compositionZoomInFlight) return true
        val plan = (active.target as? VerificationTarget.Composition)?.plan ?: return false
        if (plan.intent.adjustment?.movement != CompositionMovement.ZOOM) return false
        if (compositionZoomAttempts >= 3) {
            failWork("I couldn't confirm the zoom. Move closer or farther, then try framing again.")
            return true
        }
        val occupancy = plan.targets.firstNotNullOfOrNull { target -> when (target) {
            is VerificationTarget.FaceOccupancy -> target.min..target.max
            is VerificationTarget.GroupOccupancy -> target.min..target.max
            else -> null
        } } ?: return false
        val width = faceUnion(observation.faces)?.widthFraction?.takeIf { it > 0f } ?: return false
        val current = camera.telemetry.value.zoomRatio
        val range = camera.capabilities.value.zoomRatioRange
        val desired = (occupancy.start + occupancy.endInclusive) / 2f
        val target = (current * desired / width).coerceIn(range.start, range.endInclusive)
        if (abs(target - current) < .01f) {
            failWork("Zoom limit reached. Move closer or farther to finish framing.")
            return true
        }
        compositionZoomInFlight = true
        compositionZoomAttempts++
        logAgent(AgentLogKind.ACTION, "Zoom ${"%.2f".format(current)}× → ${"%.2f".format(target)}×")
        _uiState.update { state -> state.copy(activeGuidance = state.activeGuidance?.copy(
            instruction = "Adjusting zoom…", correction = com.bolin.photohelper.coach.Correction.HOLD,
        )) }
        operationJob = viewModelScope.launch {
            try {
                when (val result = camera.apply(CameraAdjustment.ZoomRatio(target))) {
                    ApplyResult.Applied -> {
                        markResetAvailable()
                    }
                    is ApplyResult.Failed -> failWork(result.message)
                }
            } finally {
                compositionZoomInFlight = false
            }
        }
        return true
    }

    private fun completeWork(message: String, feedbackType: Feedback) {
        cancelJobsOnly()
        activeComplaintId = null
        verificationStartObservationId = null
        verificationStartedAtMs = null
        voice.stop()
        logAgent(AgentLogKind.RESULT, message)
        audioCue?.play(AudioCue.CHIME)
        readyForAutoCapture = arSession != null
        if (_uiState.value.settings.haptics) feedback(feedbackType)
        if (_uiState.value.settings.spokenGuidance) voice.speak(message, "result")
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.IDLE,
                decision = null,
                activeGuidance = null,
                transientMessage = message,
            )
        }
    }

    private fun failWork(message: String) {
        if (_uiState.value.activeGuidance?.target is VerificationTarget.Composition) compositionWatching = false
        finishGuidance("failure")
        pendingCommandSteps.clear()
        approvedPlanAdjustments.clear()
        pendingVisualFocusText = null
        pendingSubjectZoom = null
        pendingFocusAfterZoom = null
        voice.stop()
        logAgent(AgentLogKind.RESULT, message)
        if (_uiState.value.settings.haptics) feedback(Feedback.ERROR)
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.TRANSIENT_ERROR,
                activeGuidance = null,
                transientMessage = message,
            )
        }
    }

    private fun canUseVisualAi(): Boolean = _uiState.value.settings.let {
        it.visualAiEnabled && it.keyConfigured
    }

    private fun updateSettings(transform: (SettingsUiState) -> SettingsUiState) =
        _uiState.update { it.copy(settings = transform(it.settings)) }

    private suspend fun restoreAfterBackground() {
        when (val result = camera.reset()) {
            ApplyResult.Applied -> _uiState.update {
                it.copy(
                    coachingPhase = CoachingPhase.IDLE,
                    decision = null,
                    activeGuidance = null,
                    resetAvailable = false,
                    retakeSettingsActive = false,
                    flashMode = FlashMode.OFF,
                )
            }
            is ApplyResult.Failed -> _uiState.update {
                it.copy(
                    coachingPhase = CoachingPhase.TRANSIENT_ERROR,
                    decision = null,
                    activeGuidance = null,
                    resetAvailable = true,
                    transientMessage = result.message,
                )
            }
        }
    }

    private fun resumeAnalysisAfterControl() {
        if (!isBackgrounded && !settingApplyInFlight && !resetInFlight && _uiState.value.review == null) {
            camera.setAnalysisPaused(false)
        }
    }

    private fun currentRecommendation(): Recommendation? {
        val recommendation = _uiState.value.recommendation ?: return null
        if (recommendation.cameraSessionId != camera.state.value.sessionId) {
            invalidateCameraSession()
            failWork("The camera session changed. Describe the shot again before applying a change.")
            return null
        }
        if (activeComplaintId != recommendation.complaintId) return null

        val review = _uiState.value.review
        if (recommendation.origin == ObservationOrigin.CAPTURE_REVIEW) {
            if (review == null || (recommendation.observationId != null && review.observation?.id != recommendation.observationId)) {
                failWork("The saved photo changed. Review the current photo before applying a change.")
                return null
            }
            if (!lensMatches(
                    review.telemetry?.lensId ?: review.observation?.lensId,
                    review.telemetry?.focalLengthMm ?: review.observation?.focalLengthMm,
                    camera.telemetry.value.lensId,
                    camera.telemetry.value.focalLengthMm,
                )
            ) {
                failWork("The camera lens changed. Return to the live view and check the shot again.")
                return null
            }
        } else {
            val current = latestLiveObservation
            if (current == null || nowMs() - current.timestampMs > LIVE_OBSERVATION_FRESH_MS) {
                cancelCoaching()
                failWork("The camera view changed or became stale. Hold the shot steady, then ask again.")
                return null
            }
            if (!lensMatches(
                    current.lensId,
                    current.focalLengthMm,
                    camera.telemetry.value.lensId,
                    camera.telemetry.value.focalLengthMm,
                )
            ) {
                cancelCoaching()
                failWork("The camera lens changed. Hold the shot steady while I check the new view.")
                return null
            }
            if (recommendation.basis == com.bolin.photohelper.coach.RecommendationBasis.USER_PREFERENCE &&
                recommendation.createdAtMs?.let { nowMs() - it > 30_000 } == true
            ) {
                cancelCoaching()
                failWork("That recommendation expired. Describe the shot again.")
                return null
            }
        }

        val currentInput = coachingInput(recommendation.complaintId, _uiState.value.comment)
        val changed = recommendation.observationId != currentInput.observation?.id ||
            recommendation.capabilitiesSnapshot != currentInput.capabilities ||
            (recommendation.origin == ObservationOrigin.LIVE && recommendation.telemetrySnapshot != currentInput.telemetry)
        if (!changed) return recommendation

        val decision = when {
            recommendation.controlIntents.isNotEmpty() -> coach.planIntents(currentInput, recommendation.controlIntents)
            recommendation.fromVisualHint && recommendation.visualFamily != null && recommendation.visualHint != null ->
                coach.continueWithVisualHint(currentInput, recommendation.visualFamily, recommendation.visualHint)
            else -> coach.evaluateLocal(currentInput)
        }.withProvenance(
            currentInput,
            recommendation.visualFamily,
            recommendation.visualHint,
            recommendation.controlIntents,
        )
        _uiState.update {
            it.copy(
                decision = decision,
                coachingPhase = if (decision is LocalDecision.Recommend) CoachingPhase.RECOMMENDATION else CoachingPhase.IDLE,
                transientMessage = if (decision is LocalDecision.Recommend) it.transientMessage
                else "The scene changed, so I checked the recommendation again.",
            )
        }
        return (decision as? LocalDecision.Recommend)?.recommendation
    }

    private fun lensMatches(
        firstId: String?,
        firstFocalLengthMm: Float?,
        secondId: String?,
        secondFocalLengthMm: Float?,
    ): Boolean {
        if (firstId != secondId) return false
        if (firstFocalLengthMm == null || secondFocalLengthMm == null) {
            return firstFocalLengthMm == secondFocalLengthMm
        }
        if (firstFocalLengthMm <= 0f) return secondFocalLengthMm <= 0f
        return abs(firstFocalLengthMm - secondFocalLengthMm) / firstFocalLengthMm < 0.02f
    }

    private fun LocalDecision.withProvenance(
        input: CoachingInput,
        visualFamily: VisualFamily? = null,
        visualHint: com.bolin.photohelper.coach.VisualHint? = null,
        controlIntents: List<ControlIntent> = emptyList(),
    ): LocalDecision = if (this is LocalDecision.Recommend) {
        copy(
            recommendation = recommendation.copy(
                origin = input.origin,
                observationId = input.observation?.id,
                observationTimestampMs = input.observation?.timestampMs,
                capabilitiesSnapshot = input.capabilities,
                telemetrySnapshot = input.telemetry,
                createdAtMs = nowMs(),
                controlIntents = controlIntents,
                visualFamily = visualFamily,
                visualHint = visualHint,
            ),
        )
    } else {
        this
    }

    private fun invalidateCameraSession() {
        compositionWatching = false
        compositionScene = null
        val hadCameraWork = _uiState.value.decision != null || _uiState.value.activeGuidance != null || _uiState.value.resetAvailable
        cancelJobsOnly()
        activeComplaintId = null
        recentCameraChanges.clear()
        voice.stop()
        verificationStartObservationId = null
        verificationStartedAtMs = null
        pendingVisualFocusText = null
        pendingSubjectZoom = null
        pendingFocusAfterZoom = null
        settingApplyInFlight = false
        resetInFlight = false
        restoreSettingAfterApply = false
        flashChangeInFlight = false
        advanceLiveObservationBarrier()
        clearLiveObservationProvenance()
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.IDLE,
                decision = null,
                activeGuidance = null,
                resetAvailable = false,
                retakeSettingsActive = false,
                flashMode = FlashMode.OFF,
                transientMessage = if (hadCameraWork) "Camera session changed. Check the shot again." else it.transientMessage,
            )
        }
    }

    private fun finishGuidance(outcome: String) {
        _uiState.value.activeGuidance?.governor?.finish(nowMs(), outcome)?.let(recordGuidance)
    }

    private fun cancelJobsOnly() {
        finishGuidance("abandonment")
        _uiState.update { it.copy(compositionSelection = null, compositionSelectedIndices = emptySet()) }
        operationJob?.cancel()
        countdownJob?.cancel()
        visualJob?.cancel()
        guidanceTimeoutJob?.cancel()
        verificationTimeoutJob?.cancel()
        focusIndicatorJob?.cancel()
        operationJob = null
        countdownJob = null
        visualJob = null
        guidanceTimeoutJob = null
        verificationTimeoutJob = null
        focusIndicatorJob = null
        focusInFlight = false
        compositionZoomInFlight = false
        compositionZoomAttempts = 0
        _uiState.update { it.copy(focusIndicator = null) }
    }

    private fun cancelKeyTest() {
        keyTestJob?.cancel()
        keyTestJob = null
    }

    private fun advanceLiveObservationBarrier() {
        camera.observation.value?.id?.let { currentId ->
            liveObservationBarrierId = maxOf(liveObservationBarrierId ?: Long.MIN_VALUE, currentId)
        }
    }

    private fun clearLiveObservationProvenance() {
        latestLiveObservation = null
        stableFaceTracker.reset()
        stableFace = null
        comparisonSamples.clear()
    }
}

internal class StableFaceTracker {
    private data class Sample(val sessionId: Long, val observation: FrameObservation, val face: FaceObservation)

    private val samples = ArrayDeque<Sample>(3)

    fun update(observation: FrameObservation?, sessionId: Long): FaceObservation? {
        val face = observation?.faces?.singleOrNull()
        val qualified = observation != null && face != null &&
            face.visibleFraction >= 0.90f &&
            face.widthFraction * observation.sourceWidth >= 100f &&
            (face.bottom - face.top) * observation.sourceHeight >= 100f
        val lastSessionId = samples.peekLast()?.sessionId
        if (!qualified || (lastSessionId != null && lastSessionId != sessionId)) {
            reset()
        }
        if (!qualified || observation == null || face == null) return null
        val previous = samples.peekLast()
        if (previous != null && !sameSubject(previous.face, face)) reset()
        samples.addLast(Sample(sessionId, observation, face))
        while (samples.size > 3) samples.removeFirst()
        if (samples.size < 3) return null
        val firstSample = samples.peekFirst() ?: return null
        val lastSample = samples.peekLast() ?: return null
        if (lastSample.observation.timestampMs - firstSample.observation.timestampMs < 500) return null
        return lastSample.face
    }

    fun reset() = samples.clear()
}

internal fun sameSubject(first: FaceObservation, second: FaceObservation): Boolean {
    if (first.trackingId != null && second.trackingId != null && first.trackingId != second.trackingId) return false
    val intersectionLeft = maxOf(first.left, second.left)
    val intersectionTop = maxOf(first.top, second.top)
    val intersectionRight = minOf(first.right, second.right)
    val intersectionBottom = minOf(first.bottom, second.bottom)
    val intersection = (intersectionRight - intersectionLeft).coerceAtLeast(0f) *
        (intersectionBottom - intersectionTop).coerceAtLeast(0f)
    val firstArea = (first.right - first.left).coerceAtLeast(0f) * (first.bottom - first.top).coerceAtLeast(0f)
    val secondArea = (second.right - second.left).coerceAtLeast(0f) * (second.bottom - second.top).coerceAtLeast(0f)
    val union = firstArea + secondArea - intersection
    val iou = if (union > 0f) intersection / union else 0f
    val scaleDelta = if (first.widthFraction > 0f) abs(second.widthFraction - first.widthFraction) / first.widthFraction else 1f
    return iou >= 0.70f &&
        abs(first.centerX - second.centerX) <= 0.08f &&
        abs(first.centerY - second.centerY) <= 0.08f &&
        scaleDelta <= 0.10f
}
