package com.bolin.photohelper.capture

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolin.photohelper.coach.CoachEngine
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
import com.bolin.photohelper.visual.VisualProvider
import com.bolin.photohelper.visual.VisualResult
import com.bolin.photohelper.visual.CommandRequest
import com.bolin.photohelper.visual.CommandResult
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
import kotlin.math.abs

enum class Feedback { TICK, SUCCESS, ERROR }
enum class CameraFacingRequest { TOGGLE, FRONT, REAR }

private const val CAPTURE_TIMEOUT_MS = 15_000L
private const val CAPTURE_TIMEOUT_MESSAGE = "Camera did not finish saving the photo. Try again."
private const val VOICE_INPUT_TIMEOUT_MS = 20_000L
private const val VOICE_INPUT_TIMEOUT_MESSAGE = "Voice input timed out. Tap the mic to try again."
private const val TOAST_TIMEOUT_MS = 5_000L
private const val FOCUS_INDICATOR_MS = 5_000L
/** Two visible attempts: the first plan, then one alternative, then an honest concession. */
private const val MAX_SETTING_ATTEMPTS = 2
private const val SETTING_SETTLE_MS = 400L
private const val SETTING_VERIFY_TIMEOUT_MS = 3_000L
private val OBJECT_FOCUS_REQUEST = Regex("\\b(focus|sharp|sharpen|clear)\\b", RegexOption.IGNORE_CASE)
private val TARGET_FOCUS_REQUEST = Regex("\\bfocus\\s+on\\b", RegexOption.IGNORE_CASE)
private val PERSON_FOCUS_REQUEST = Regex(
    "\\bfocus\\s+on\\s+(?:grandma|grandmother|grandpa|grandfather|mom|mother|dad|father|woman|man|person|her|him)\\b",
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
) : ViewModel() {
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
    private var verificationSatisfiedSamples = 0
    /** The settings change awaiting verification, and how many attempts it has had. */
    private var pendingSettingVerification: SettingChange? = null
    private var settingAttempt = 0
    private var settingAttemptComplaint = ""

    private var verificationIncomparableMessage: String? = null
    private var guidanceSatisfiedSinceMs: Long? = null
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
    private var stableFace: FaceObservation? = null
    private var flashChangeInFlight = false
    private var focusInFlight = false

    init {
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
                } else {
                    comparisonSamples.addLast(observation)
                    while (comparisonSamples.size > 3) comparisonSamples.removeFirst()
                }
                stableFace = stableFaceTracker.update(observation, camera.state.value.sessionId)
                if (observation != null) {
                    verifyActiveWork(observation)
                    verifySettingChange(observation)
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
        val comment = (replacement ?: _uiState.value.comment).trim()
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
            requestCommandPlan(comment)
            return
        }
        submitLocalCommand(comment)
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
        if (!canUseVisualAi()) {
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
        guidanceSatisfiedSinceMs = null
        val guidance = ActiveGuidance(action.instruction, action.target, nowMs(), subjectTrackingId, subjectFace)
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
        if (_uiState.value.settings.spokenGuidance) voice.speak(action.instruction, "guidance")
        guidanceTimeoutJob = viewModelScope.launch {
            delay(10_000)
            if (_uiState.value.activeGuidance === guidance) {
                voice.stop()
                _uiState.update {
                    it.copy(
                        coachingPhase = CoachingPhase.TRANSIENT_ERROR,
                        activeGuidance = null,
                        transientMessage = "I couldn’t confirm progress. Try again or stop.",
                    )
                }
            }
        }
    }

    fun cancelCoaching(clearDecision: Boolean = true, preserveCommandPlan: Boolean = false) {
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
        guidanceSatisfiedSinceMs = null
        verificationStartObservationId = null
        verificationStartedAtMs = null
        verificationSatisfiedSamples = 0
        verificationIncomparableMessage = null
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

    fun setVisualProvider(provider: VisualProvider) {
        preferences.setVisualProvider(provider)
        updateSettings { it.copy(visualProvider = provider) }
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
                            decision = if (autoApplyRecommendations) it.decision else null,
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
                when (result) {
                    is CommandResult.Planned -> {
                        startCommandPlan(result.plan, comment)
                    }
                    is CommandResult.Clarified ->
                        useLocalFallback("AI interpretation needs clarification. Using local coaching.")
                    CommandResult.NoChange -> showToast("Looks good already.")
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
            VisualFamily.OBJECT_FOCUS -> lensMatches(
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
        _uiState.update {
            it.copy(
                coachingPhase = CoachingPhase.IDLE,
                transientMessage = message ?: it.transientMessage,
            )
        }
        continueFocusAfterZoomIfPending()
        continueVisualFocusIfPending()
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

    private fun verifyActiveWork(observation: FrameObservation) {
        val active = _uiState.value.activeGuidance ?: return
        if (_uiState.value.coachingPhase == CoachingPhase.VERIFYING &&
            (observation.id == verificationStartObservationId ||
                observation.timestampMs < (verificationStartedAtMs ?: Long.MIN_VALUE) + 500)
        ) return
        val tracksFace = active.target is VerificationTarget.FaceOccupancy ||
            active.target is VerificationTarget.FacePosition ||
            active.target is VerificationTarget.StepBack
        if (_uiState.value.coachingPhase == CoachingPhase.GUIDING && tracksFace) {
            val face = observation.faces.singleOrNull()
            val sameTrackedSubject = face != null && active.subjectFace != null && sameSubject(active.subjectFace, face)
            if (!sameTrackedSubject) {
                guidanceTimeoutJob?.cancel()
                guidanceTimeoutJob = null
                guidanceSatisfiedSinceMs = null
                failWork(
                    when {
                        observation.faces.isEmpty() -> "I lost the person. Point back at them, then start guidance again."
                        observation.faces.size > 1 -> "I can’t isolate the same person. Frame one person, then start guidance again."
                        else -> "The tracked person changed. Start guidance again."
                    },
                )
                return
            }
            face?.let { trackedFace ->
                _uiState.update { state ->
                    state.copy(activeGuidance = state.activeGuidance?.copy(subjectFace = trackedFace, subjectTrackingId = trackedFace.trackingId))
                }
            }
        }
        when (val result = coach.verify(active.target, observation)) {
            VerificationResult.Satisfied -> {
                verificationIncomparableMessage = null
                if (_uiState.value.coachingPhase == CoachingPhase.GUIDING) {
                    val stableSince = guidanceSatisfiedSinceMs
                    if (stableSince == null) guidanceSatisfiedSinceMs = nowMs()
                    else if (nowMs() - stableSince >= 500) {
                        completeWork(
                            if (active.target is VerificationTarget.StepBack) {
                                "The face is smaller after the step. Reframe and decide whether the proportions look better."
                            } else {
                                "That matches your request."
                            },
                            Feedback.SUCCESS,
                        )
                    }
                } else {
                    verificationSatisfiedSamples++
                    if (verificationSatisfiedSamples >= 3) {
                        val recommendation = _uiState.value.recommendation
                        completeWork(successCopy(recommendation), Feedback.SUCCESS)
                    }
                }
            }
            VerificationResult.Progress -> {
                verificationIncomparableMessage = null
                guidanceSatisfiedSinceMs = null
                verificationSatisfiedSamples = 0
                if (_uiState.value.settings.haptics && _uiState.value.coachingPhase == CoachingPhase.GUIDING) feedback(Feedback.TICK)
            }
            VerificationResult.Unchanged -> {
                verificationIncomparableMessage = null
                verificationSatisfiedSamples = 0
            }
            is VerificationResult.Incomparable -> {
                guidanceSatisfiedSinceMs = null
                verificationSatisfiedSamples = 0
                verificationIncomparableMessage = result.reason
                _uiState.update { it.copy(transientMessage = result.reason) }
            }
        }
    }

    private fun successCopy(recommendation: Recommendation?): String =
        when {
            (recommendation?.action as? RecommendationAction.GuidePosition)?.target is VerificationTarget.StepBack ->
                "The face is smaller after the step. Reframe and decide whether the proportions look better."
            (recommendation?.action as? RecommendationAction.ApplySettings)?.target is VerificationTarget.Zoom -> {
                val target = (recommendation.action as RecommendationAction.ApplySettings).target as VerificationTarget.Zoom
                val ratio = String.format(java.util.Locale.US, "%.2f", target.targetRatio).trimEnd('0').trimEnd('.')
                "Zoom changed to $ratio×. Is the framing closer?"
            }
            recommendation?.basis == com.bolin.photohelper.coach.RecommendationBasis.USER_PREFERENCE ->
                "The requested effect is visible. Is this closer?"
            else -> "The measured problem is now in range."
        }

    private fun completeWork(message: String, feedbackType: Feedback) {
        cancelJobsOnly()
        activeComplaintId = null
        guidanceSatisfiedSinceMs = null
        verificationStartObservationId = null
        verificationStartedAtMs = null
        verificationSatisfiedSamples = 0
        verificationIncomparableMessage = null
        voice.stop()
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
        pendingCommandSteps.clear()
        approvedPlanAdjustments.clear()
        pendingVisualFocusText = null
        pendingSubjectZoom = null
        pendingFocusAfterZoom = null
        voice.stop()
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
        val hadCameraWork = _uiState.value.decision != null || _uiState.value.activeGuidance != null || _uiState.value.resetAvailable
        cancelJobsOnly()
        activeComplaintId = null
        recentCameraChanges.clear()
        voice.stop()
        guidanceSatisfiedSinceMs = null
        verificationStartObservationId = null
        verificationStartedAtMs = null
        verificationSatisfiedSamples = 0
        verificationIncomparableMessage = null
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

    private fun cancelJobsOnly() {
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
