package com.bolin.photohelper.capture

import android.Manifest
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.filters.RequiresDevice
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.bolin.photohelper.MainActivity
import com.bolin.photohelper.coach.RecommendationAction
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.math.abs

class VoiceAcousticSmokeTest {
    private val permissions = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissions).around(compose)

    @RequiresDevice
    @Test
    fun secondaryDisplaySpeechReachesOneCompoundApplyAndReset() {
        val runId = InstrumentationRegistry.getArguments().getString("voiceRunId")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9-]{1,64}")) }
            ?: error("A bounded voiceRunId is required")
        val expectedTranscript = InstrumentationRegistry.getArguments()
            .getString("voiceExpectedTranscriptBase64")
            ?.let { String(java.util.Base64.getDecoder().decode(it), Charsets.UTF_8) }
            ?.takeIf { it.isNotBlank() && it.length <= 100 }
        val markerDirectory = compose.activity.getExternalFilesDir(null)
            ?: error("External test marker directory is unavailable")
        val playbackMarker = File(markerDirectory, "voice-playback-$runId.ready")
        val playbackCompletedMarker = File(markerDirectory, "voice-playback-$runId.spoken")
        openCameraAndWaitUntilReady()
        val viewModel = ViewModelProvider(compose.activity)[CaptureViewModel::class.java]
        val camera = viewModel.camera
        val capabilities = camera.capabilities.value
        val baseline = camera.telemetry.value
        if (expectedTranscript == null) {
            assertTrue("Stage camera must expose EV compensation", capabilities.supportsExposureCompensation)
            assertTrue(
                "Stage camera must expose a usable zoom-in step",
                capabilities.zoomRatioRange.endInclusive - baseline.zoomRatio >= 0.01f,
            )
        }
        assertTrue("On-device speech recognition is unavailable", viewModel.isVoiceInputAvailable())

        try {
            compose.onNodeWithContentDescription("Describe shot by voice").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                viewModel.uiState.value.coachingPhase == CoachingPhase.LISTENING
            }
            playbackMarker.writeText("ready", Charsets.UTF_8)
            reportVoiceGate("VOICE_GATE playback=REQUESTED source=EDGE_SECONDARY_DISPLAY")

            compose.waitUntil(timeoutMillis = 20_000) { playbackCompletedMarker.exists() }
            compose.onNodeWithContentDescription("Finish voice comment").performClick()
            reportVoiceGate("VOICE_GATE playback=COMPLETED action=FINISH_LISTENING")

            compose.waitUntil(timeoutMillis = 25_000) {
                val phase = viewModel.uiState.value.coachingPhase
                if (expectedTranscript != null) {
                    phase != CoachingPhase.LISTENING
                } else {
                    phase != CoachingPhase.LISTENING &&
                        phase != CoachingPhase.INTERPRETING &&
                        phase != CoachingPhase.REQUESTING_VISUAL_INTERPRETATION
                }
            }
            val state = viewModel.uiState.value
            if (expectedTranscript != null) {
                assertTrue(
                    "Expected-transcript acoustic control did not match: actual='${state.comment}'",
                    normalizeTranscript(expectedTranscript) == normalizeTranscript(state.comment),
                )
                reportVoiceGate("VOICE_GATE transcript=EXPECTED_CONTROL source=EDGE_SECONDARY_DISPLAY")
                return
            }
            assertEquals(
                "Voice did not produce a recommendation: comment='${state.comment}' message='${state.transientMessage}'",
                CoachingPhase.RECOMMENDATION,
                state.coachingPhase,
            )
            val action = state.recommendation?.action as? RecommendationAction.ApplySettings
            assertNotNull("Voice did not produce direct camera changes", action)
            assertEquals("Voice did not produce exactly two camera changes", 2, action!!.changes.size)
            val exposure = action.changes.mapNotNull {
                it.adjustment as? CameraAdjustment.ExposureCompensation
            }.singleOrNull()
            val zoom = action.changes.mapNotNull {
                it.adjustment as? CameraAdjustment.ZoomRatio
            }.singleOrNull()
            assertNotNull("Voice recommendation omitted brightness", exposure)
            assertNotNull("Voice recommendation omitted zoom", zoom)
            assertTrue("Voice recommendation did not brighten", exposure!!.targetIndex > baseline.exposureCompensationIndex)
            assertTrue("Voice recommendation did not zoom in", zoom!!.ratio > baseline.zoomRatio)
            val recognizedComment = state.comment

            compose.onNodeWithText("Apply both").performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                val telemetry = camera.telemetry.value
                viewModel.uiState.value.coachingPhase == CoachingPhase.IDLE &&
                    viewModel.uiState.value.resetAvailable &&
                    telemetry.exposureCompensationIndex == exposure.targetIndex &&
                    abs(telemetry.zoomRatio - zoom.ratio) <= 0.01f
            }

            compose.onNodeWithText("Reset").performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                val telemetry = camera.telemetry.value
                !viewModel.uiState.value.resetAvailable &&
                    telemetry.exposureCompensationIndex == baseline.exposureCompensationIndex &&
                    abs(telemetry.zoomRatio - baseline.zoomRatio) <= 0.01f
            }
            reportVoiceGate(
                "VOICE_GATE chain=EDGE_SPEAKER>PHONE_MIC>APP_PCM_CAPTURE>PFD_ON_DEVICE_STT>COMPOUND>" +
                    "APPLY_BOTH>VERIFY_SETPOINTS>RESET " +
                    "ev=${baseline.exposureCompensationIndex}>${exposure.targetIndex}>${baseline.exposureCompensationIndex} " +
                    "zoom=${baseline.zoomRatio}>${zoom.ratio}>${baseline.zoomRatio}",
            )

            compose.onNodeWithContentDescription("Describe shot by voice").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                viewModel.uiState.value.coachingPhase == CoachingPhase.LISTENING
            }
            SystemClock.sleep(1_500)
            compose.onNodeWithContentDescription("Finish voice comment").performClick()
            compose.waitUntil(timeoutMillis = 25_000) {
                val phase = viewModel.uiState.value.coachingPhase
                phase != CoachingPhase.LISTENING &&
                    phase != CoachingPhase.INTERPRETING &&
                    phase != CoachingPhase.REQUESTING_VISUAL_INTERPRETATION
            }
            val silenceState = viewModel.uiState.value
            assertEquals(CoachingPhase.TRANSIENT_ERROR, silenceState.coachingPhase)
            assertEquals("I didn’t catch that", silenceState.transientMessage)
            assertEquals(recognizedComment, silenceState.comment)
            assertNull(silenceState.recommendation)
            assertFalse(silenceState.resetAvailable)
            assertEquals(baseline.exposureCompensationIndex, camera.telemetry.value.exposureCompensationIndex)
            assertTrue(abs(camera.telemetry.value.zoomRatio - baseline.zoomRatio) <= 0.01f)
            reportVoiceGate("VOICE_GATE silence=NO_TRANSCRIPT stale_buffer=false camera_unchanged=true")
        } finally {
            playbackMarker.delete()
            playbackCompletedMarker.delete()
            val telemetry = camera.telemetry.value
            if (viewModel.uiState.value.resetAvailable ||
                telemetry.exposureCompensationIndex != baseline.exposureCompensationIndex ||
                abs(telemetry.zoomRatio - baseline.zoomRatio) > 0.01f
            ) {
                runBlocking { camera.reset() }
            }
        }
    }

    private fun openCameraAndWaitUntilReady() {
        if (compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Open camera").performClick()
        }
        compose.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                compose.onNodeWithTag(CaptureTestTags.SHUTTER).assertIsEnabled()
            }.isSuccess
        }
    }

    private fun reportVoiceGate(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString(Instrumentation.REPORT_KEY_STREAMRESULT, "$message\n") },
        )
    }

    private fun normalizeTranscript(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\b10\\b"), "ten")
        .replace(Regex("\\s+"), " ")
        .trim()
}
