package com.bolin.photohelper.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.bolin.photohelper.BuildConfig
import com.bolin.photohelper.capture.CameraCapabilities
import com.bolin.photohelper.capture.CameraTelemetry
import com.bolin.photohelper.coach.VisualFamily
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live checks against the real Claude API through the production client.
 *
 * Unlike `scripts/claude-live-smoke.py`, which replicates the request in Python, this
 * exercises [ClaudeVisualClient] itself - the real prompts, the real grid guide, the
 * real parsers - so a pass here is evidence about the app rather than about a replica.
 *
 * Each test is one billable call, so these are opt-in twice over: they need
 * `ANTHROPIC_API_KEY` in `.env` (which reaches them through BuildConfig) *and* an
 * explicit `liveApi=true` argument. Without both they skip, so the default
 * `connectedDebugAndroidTest` run never spends money.
 *
 *     ./gradlew.bat connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.bolin.photohelper.visual.ClaudeLiveSmokeTest \
 *       -Pandroid.testInstrumentationRunnerArguments.liveApi=true
 */
class ClaudeLiveSmokeTest {
    private val client = ClaudeVisualClient()

    private fun key(): CharArray {
        // Opt-in only: every test here spends real money, so the default
        // connectedDebugAndroidTest run must not pick them up just because a key exists.
        assumeTrue(
            "live API tests are opt-in: pass -Pandroid.testInstrumentationRunnerArguments.liveApi=true",
            InstrumentationRegistry.getArguments().getString("liveApi") == "true",
        )
        assumeTrue("No ANTHROPIC_API_KEY in .env", BuildConfig.ANTHROPIC_API_KEY.isNotBlank())
        return BuildConfig.ANTHROPIC_API_KEY.toCharArray()
    }

    /**
     * A dark frame with one bright square, so "the bright square" has exactly one
     * correct answer and the assertion does not depend on scene interpretation.
     */
    private fun sceneJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(24, 26, 30))
        canvas.drawRect(
            300f, 120f, 420f, 240f,
            Paint().apply { color = Color.rgb(250, 240, 210) },
        )
        return try {
            ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out))
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun commandRequest(comment: String, autoEnhance: Boolean = false): CommandRequest {
        val jpeg = sceneJpeg()
        return CommandRequest(
            comment = comment,
            observationJpeg = jpeg,
            focusGrid = FocusGrid.forImage(480, 640),
            telemetry = CameraTelemetry(),
            // An EV range and step is what makes supportsExposureCompensation true, which
            // the prompt needs before it will plan a brightness change.
            capabilities = CameraCapabilities(
                exposureCompensationRange = -6..6,
                exposureCompensationStepEv = 1f / 3f,
                supportsFocusMetering = true,
            ),
            autoEnhance = autoEnhance,
        )
    }

    @Test
    fun objectFocusReturnsAGridCell() {
        val jpeg = sceneJpeg()
        val grid = FocusGrid.forImage(480, 640)
        val result = runBlocking {
            client.interpret(
                VisualRequest(VisualFamily.OBJECT_FOCUS, "the bright square", jpeg, grid),
                key(),
            )
        }
        println("PHOTOHELPER_CLAUDE objectFocus -> $result")
        assertTrue("expected a usable hint, got $result", result is VisualResult.Available)
    }

    @Test
    fun commandPlanReturnsAPlan() {
        val result = runBlocking { client.plan(commandRequest("make it brighter"), key()) }
        println("PHOTOHELPER_CLAUDE commandPlan -> $result")
        assertTrue(
            "expected a plan or a clarification, got $result",
            result is CommandResult.Planned || result is CommandResult.Clarified,
        )
    }

    @Test
    fun autoEnhanceReturnsAnAssessment() {
        val result = runBlocking {
            client.plan(commandRequest("Make this shot look nicer.", autoEnhance = true), key())
        }
        println("PHOTOHELPER_CLAUDE autoEnhance -> $result")
        assertTrue(
            "expected an assessment outcome, got $result",
            result is CommandResult.Planned || result is CommandResult.NoChange ||
                result is CommandResult.Unsure,
        )
    }
}
