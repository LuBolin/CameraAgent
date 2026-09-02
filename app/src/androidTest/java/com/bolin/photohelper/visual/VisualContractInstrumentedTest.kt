package com.bolin.photohelper.visual

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bolin.photohelper.coach.ClarificationReason
import com.bolin.photohelper.coach.ControlIntent
import com.bolin.photohelper.capture.FlashMode
import com.bolin.photohelper.capture.CameraCapabilities
import com.bolin.photohelper.capture.CameraTelemetry
import com.bolin.photohelper.capture.FrameObservation
import com.bolin.photohelper.capture.WhiteBalancePreset
import com.bolin.photohelper.coach.IntentClassification
import com.bolin.photohelper.coach.VisualFamily
import com.bolin.photohelper.coach.VisualHint
import com.bolin.photohelper.coach.VisualIntent
import com.bolin.photohelper.voice.CameraFacing
import com.bolin.photohelper.voice.CommandPlan
import com.bolin.photohelper.voice.CommandPlanStep
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.cert.Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualContractInstrumentedTest {
    @Test
    fun objectFocusSendsOneCleanFrameAndRequestsANormalizedPoint() {
        val clean = testJpeg(width = 120, height = 160)
        val request = VisualRequest(
            family = VisualFamily.OBJECT_FOCUS,
            comment = "focus on the headphones",
            observationJpeg = clean,
        )

        val body = JSONObject(buildVisualRequestBody(request).toString(StandardCharsets.UTF_8))
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")

        assertEquals(2, content.length())
        assertEquals(
            "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(clean)}",
            content.getJSONObject(0).getJSONObject("image_url").getString("url"),
        )
        val prompt = content.getJSONObject(1).getString("text")
        assertTrue(prompt.contains("point_2d"))
        assertTrue(prompt.contains("normalized to 0..999"))
        assertTrue(prompt.contains("solid, visible, high-contrast or textured"))
        assertTrue(prompt.contains("never the empty geometric center"))
        clean.fill(0)
    }

    @Test
    fun commandRequestSendsOneCleanImageWhileSeparatingUntrustedText() {
        val clean = testJpeg()
        val bytes = buildCommandRequestBody(
            CommandRequest("Focus on the watch, then take a photo", clean),
        )
        val body = JSONObject(bytes.toString(StandardCharsets.UTF_8))
        val messages = body.getJSONArray("messages")
        val content = messages.getJSONObject(1).getJSONArray("content")

        assertEquals(QWEN_MODEL, body.getString("model"))
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val systemPrompt = messages.getJSONObject(0).getString("content")
        assertTrue(systemPrompt.contains("JSON"))
        assertTrue(systemPrompt.contains("FOCUS_POINT"))
        assertTrue(systemPrompt.contains("point_2d"))
        assertTrue(systemPrompt.contains("Emit CAPTURE only when the user explicitly asks"))
        assertTrue(systemPrompt.contains("Never infer CAPTURE from a focus or parameter request"))
        assertTrue(systemPrompt.contains("never volunteer flash for a brightness complaint"))
        assertTrue(systemPrompt.contains("Use SET_FLASH only when the user explicitly mentions"))
        assertTrue(systemPrompt.contains("'Take a picture with the focus on the keyboard' => FOCUS_POINT, CAPTURE"))
        assertTrue(systemPrompt.contains("RESET restores exposure, zoom, white balance, flash off, and continuous autofocus"))
        assertTrue(systemPrompt.contains("must not change the selected front or rear camera"))
        assertFalse(systemPrompt.contains("Focus on the watch"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals(2, content.length())
        assertEquals("Focus on the watch, then take a photo", content.getJSONObject(1).getString("text"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals(false, body.getBoolean("enable_thinking"))
        assertEquals(0, body.getInt("temperature"))
        assertEquals(false, body.getBoolean("stream"))
        assertEquals(256, body.getInt("max_completion_tokens"))
        clean.fill(0)
    }

    @Test
    fun autoEnhanceSendsTrustedSettingsAndRejectsUnsafeActions() {
        val clean = testJpeg()
        val request = CommandRequest(
            comment = "Make this shot look nicer.",
            observationJpeg = clean,
            telemetry = CameraTelemetry(2, 1.5f, WhiteBalancePreset.COOLER, "rear-wide", 4.5f, 400, 8_000_000),
            capabilities = CameraCapabilities(
                exposureCompensationRange = -6..6,
                exposureCompensationStepEv = 1f / 3f,
                zoomRatioRange = 1f..8f,
                supportedWhiteBalancePresets = WhiteBalancePreset.entries.toSet(),
                supportsFocusMetering = true,
                hasFlashUnit = true,
            ),
            flashMode = FlashMode.ON,
            autoEnhance = true,
            frameObservation = FrameObservation(
                id = 1,
                timestampMs = 1_000,
                meanLuma = .42f,
                highlightClipFraction = .03f,
                shadowClipFraction = .12f,
                chromaBlueBias = .04f,
                motionScore = .01f,
                sourceWidth = 120,
                sourceHeight = 160,
            ),
        )
        val body = JSONObject(buildCommandRequestBody(request).toString(StandardCharsets.UTF_8))
        val prompt = body.getJSONArray("messages").getJSONObject(0).getString("content")

        assertFalse(body.getBoolean("enable_thinking"))
        assertEquals(256, body.getInt("max_completion_tokens"))
        assertTrue(prompt.contains("Independently decide all four axes"))
        assertTrue(prompt.contains("confidence\":\"MEDIUM|HIGH"))
        assertTrue(prompt.contains("LOW should be rare"))
        assertTrue(prompt.contains("already sharp or no identifiable subject=NONE"))
        assertTrue(prompt.contains("Exposure controls brightness"))
        assertTrue(prompt.contains("prefer exposure"))
        assertTrue(prompt.contains("first identify one clear primary capture subject"))
        assertTrue(prompt.contains("clear subject so large that it is clipped, cramped, or leaves too little context=ZOOM_OUT"))
        assertTrue(prompt.contains("Do not return actions"))
        assertFalse(prompt.contains("CAPTURE only when"))
        assertTrue(prompt.contains("\"exposureCompensationIndex\":2"))
        assertTrue(prompt.contains("\"zoomRatio\":1.5"))
        assertTrue(prompt.contains("\"whiteBalancePreset\":\"COOLER\""))
        assertTrue(prompt.contains("\"flashMode\":\"ON\""))
        assertTrue(prompt.contains("\"meanLuma\":0.42"))
        assertTrue(prompt.contains("\"highlightClipFraction\":0.03"))
        assertTrue(prompt.contains("positive chromaBlueBias supports WARMER"))
        assertNull(parseCommandResponse(response("""{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"CAPTURE","countdownSeconds":0}]}"""), true))
        assertEquals(
            CommandResult.NoChange,
            parseCommandResponse(response(autoAssessment()), true),
        )
        assertEquals(
            CommandResult.Unsure,
            parseCommandResponse(response("""{"schemaVersion":4,"outcome":"UNSURE","confidence":"LOW"}"""), true),
        )
        val planned = parseCommandResponse(
            response(autoAssessment(exposure = "BRIGHTER", framing = "ZOOM_IN", focus = "\"decision\":\"FOCUS_POINT\",\"point_2d\":[417,438]")),
            true,
        ) as CommandResult.Planned
        assertEquals(3, planned.plan.steps.size)
        assertEquals(CommandPlanStep.FocusPoint(417 / 999f, 438 / 999f), planned.plan.steps.last())
        assertNull(parseCommandResponse(response(autoAssessment(confidence = "LOW")), true))
        assertNull(parseCommandResponse(response("""{"schemaVersion":4,"outcome":"UNSURE","confidence":"MEDIUM"}"""), true))
        clean.fill(0)
    }

    @Test
    fun commandParserAcceptsAnOrderedPlanIncludingPointFocus() {
        assertEquals(
            CommandResult.Planned(
                CommandPlan(
                    listOf(
                        CommandPlanStep.Adjust(
                            listOf(ControlIntent.EXPOSURE_BRIGHTER, ControlIntent.WHITE_BALANCE_WARMER),
                        ),
                        CommandPlanStep.FocusPoint(417 / 999f, 688 / 999f),
                        CommandPlanStep.Capture(5),
                    ),
                ),
            ),
            parseCommandResponse(
                response(
                    """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"ADJUST","intents":["WHITE_BALANCE_WARMER","EXPOSURE_BRIGHTER"]},{"type":"FOCUS_POINT","point_2d":[417,688]},{"type":"CAPTURE","countdownSeconds":5}]}""",
                ),
            ),
        )
        assertEquals(
            CommandResult.Planned(
                CommandPlan(
                    listOf(CommandPlanStep.SetCamera(CameraFacing.FRONT), CommandPlanStep.Capture(null)),
                ),
            ),
            parseCommandResponse(
                response(
                    """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"SET_CAMERA","facing":"FRONT"},{"type":"CAPTURE","countdownSeconds":0}]}""",
                ),
            ),
        )
        assertEquals(
            CommandResult.Planned(CommandPlan(listOf(CommandPlanStep.Reset))),
            parseCommandResponse(
                response("""{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"RESET"}]}"""),
            ),
        )
        assertEquals(
            CommandResult.Planned(CommandPlan(listOf(CommandPlanStep.SetFlash(FlashMode.TORCH)))),
            parseCommandResponse(
                response("""{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"SET_FLASH","mode":"TORCH"}]}"""),
            ),
        )

        listOf(
            """{"schemaVersion":3,"outcome":"PLAN","actions":[]}""",
            """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"CAPTURE","countdownSeconds":0},{"type":"SET_CAMERA","facing":"FRONT"}]}""",
            """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"FOCUS_POINT","point_2d":[1000,500]}]}""",
            """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"SET_CAMERA","facing":"FRONT"},{"type":"FOCUS_POINT","point_2d":[417,688]}]}""",
            """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"ADJUST","intents":["ZOOM_IN","ZOOM_OUT"]}]}""",
            """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"CAPTURE","countdownSeconds":31}]}""",
            """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"UNKNOWN"}]}""",
            """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"CAPTURE","countdownSeconds":0,"extra":true}]}""",
            """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"RESET"},{"type":"SET_CAMERA","facing":"FRONT"}]}""",
        ).forEach { assertNull(parseCommandResponse(response(it))) }
    }

    @Test
    fun complaintClientUsesOneBoundedRedactedHttpsRequest() = runBlocking {
        val clean = testJpeg()
        val fakeConnection = FakeHttpsConnection(
            response = response(
                """{"schemaVersion":3,"outcome":"PLAN","actions":[{"type":"ADJUST","intents":["ZOOM_OUT","WHITE_BALANCE_COOLER"]}]}""",
            ),
        )
        var connections = 0
        val client = BailianVisualClient(connectionFactory = {
            connections++
            fakeConnection
        })
        val key = "disposable-api-key".toCharArray()

        val result = client.plan(CommandRequest("The crop is tight and the light is amber", clean), key)

        assertEquals(
            CommandResult.Planned(
                CommandPlan(
                    listOf(CommandPlanStep.Adjust(listOf(ControlIntent.ZOOM_OUT, ControlIntent.WHITE_BALANCE_COOLER))),
                ),
            ),
            result,
        )
        assertEquals(1, connections)
        assertEquals(30_000, fakeConnection.connectTimeout)
        assertEquals(30_000, fakeConnection.readTimeout)
        assertTrue(key.all { it == '\u0000' })
        assertFalse(String(fakeConnection.sentBody.toByteArray(), StandardCharsets.UTF_8).contains("disposable-api-key"))
        assertTrue(fakeConnection.disconnected)
        clean.fill(0)
    }

    @Test
    fun clientUsesOneBoundedRedactedHttpsRequest() = runBlocking {
        val request = VisualRequest(
            family = VisualFamily.FACE_SIZE_AMBIGUOUS,
            comment = "face looks too big",
            observationJpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()),
        )
        val fakeConnection = FakeHttpsConnection(
            response = response(
                """{"schemaVersion":3,"outcome":"INTENT","distortionVisible":false}""",
            ),
        )
        var requestedUrl: URL? = null
        val client = BailianVisualClient(connectionFactory = {
            requestedUrl = it
            fakeConnection
        })
        val apiKey = "disposable-api-key".toCharArray()

        val result = client.interpret(request, apiKey)

        assertEquals(
            VisualResult.Available(VisualHint.Intent(VisualIntent.FACE_OCCUPANCY_LOWER)),
            result,
        )
        assertEquals(BAILIAN_ENDPOINT, requestedUrl.toString())
        assertEquals("POST", fakeConnection.requestMethod)
        assertEquals(5_000, fakeConnection.connectTimeout)
        assertEquals(5_000, fakeConnection.readTimeout)
        assertEquals("Bearer disposable-api-key", fakeConnection.headers["Authorization"])
        assertTrue(apiKey.all { it == '\u0000' })
        assertFalse(String(fakeConnection.sentBody.toByteArray(), StandardCharsets.UTF_8).contains("disposable-api-key"))
        assertTrue(fakeConnection.disconnected)
    }

    @Test
    fun providerFailureIsNotRetried() = runBlocking {
        var connections = 0
        val client = BailianVisualClient(connectionFactory = {
            connections++
            FakeHttpsConnection(status = 503, response = "unavailable")
        })
        val request = VisualRequest(
            family = VisualFamily.COLOR_CAST,
            comment = "looks blue",
            observationJpeg = byteArrayOf(1),
        )

        assertEquals(VisualResult.Failed("API service is unavailable. Try again later."), client.interpret(request, "demo-key".toCharArray()))
        assertEquals(1, connections)
    }

    @Test
    fun requestMatchesTheFixedProviderContractWithoutCredentials() {
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        val request = VisualRequest(
            family = VisualFamily.FACE_SIZE_AMBIGUOUS,
            comment = "face looks too big",
            observationJpeg = jpeg,
        )

        val bytes = buildVisualRequestBody(request)
        val body = JSONObject(bytes.toString(StandardCharsets.UTF_8))
        val message = body.getJSONArray("messages").getJSONObject(0)
        val content = message.getJSONArray("content")

        assertEquals(QWEN_MODEL, body.getString("model"))
        assertEquals("user", message.getString("role"))
        assertEquals(
            "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(jpeg)}",
            content.getJSONObject(0).getJSONObject("image_url").getString("url"),
        )
        assertEquals(
            "Prompt v3: family=FACE_SIZE_AMBIGUOUS; comment=face looks too big; inspect facial proportions only. " +
                "Is there visible close or wide-angle perspective distortion, such as central features or the nose " +
                "enlarged relative to the ears and sides of the face? Do not infer distortion from a large face, tight " +
                "crop, or proximity alone. Return JSON only in exactly one shape: " +
                "{\"schemaVersion\":3,\"outcome\":\"INTENT\",\"distortionVisible\":true} or " +
                "{\"schemaVersion\":3,\"outcome\":\"INTENT\",\"distortionVisible\":false} or " +
                "{\"schemaVersion\":3,\"outcome\":\"CLARIFY\",\"reason\":\"<REASON>\"}; " +
                "outcome must be the literal value INTENT or CLARIFY; distortionVisible must be a JSON boolean; " +
                "allowed REASON labels=VISUAL_INSUFFICIENT|SUBJECT_UNCLEAR|SCENE_CONFOUND; no other keys or prose",
            content.getJSONObject(1).getString("text"),
        )
        assertEquals(false, body.getBoolean("enable_thinking"))
        assertEquals(0, body.getInt("temperature"))
        assertEquals(false, body.getBoolean("stream"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertTrue(bytes.size <= MAX_REQUEST_BODY_BYTES)
        assertFalse(body.has("request_id"))
        assertFalse(body.has("thinking"))
        assertFalse(body.has("do_sample"))
        assertFalse(body.has("max_tokens"))
        assertFalse(body.has("max_completion_tokens"))
        assertTrue(!body.has("tools"))
        assertTrue(!bytes.toString(StandardCharsets.UTF_8).contains("api-key"))
    }

    @Test
    fun maximumObservationAndCommentStayWithinTheRequestLimit() {
        val body = buildVisualRequestBody(
            VisualRequest(
                family = VisualFamily.COLOR_CAST,
                comment = "x".repeat(MAX_COMMENT_CHARACTERS),
                observationJpeg = ByteArray(MAX_OBSERVATION_JPEG_BYTES),
            ),
        )

        assertTrue(body.size <= MAX_REQUEST_BODY_BYTES)
    }

    @Test
    fun strictEnvelopeAndFamilyAllowlistAcceptOnlyTheBoundedHint() {
        val occupancy = """{"schemaVersion":3,"outcome":"INTENT","distortionVisible":false}"""
        val perspective = """{"schemaVersion":3,"outcome":"INTENT","distortionVisible":true}"""
        val warmer = """{"schemaVersion":2,"outcome":"INTENT","intent":"WHITE_BALANCE_WARMER"}"""

        assertEquals(
            VisualHint.Intent(VisualIntent.FACE_OCCUPANCY_LOWER),
            parseVisualResponse(response(occupancy), VisualFamily.FACE_SIZE_AMBIGUOUS),
        )
        assertEquals(
            VisualHint.Intent(VisualIntent.CLOSE_PERSPECTIVE_ADVISORY),
            parseVisualResponse(response(perspective), VisualFamily.FACE_SIZE_AMBIGUOUS),
        )
        assertEquals(
            VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER),
            parseVisualResponse(response(warmer), VisualFamily.COLOR_CAST),
        )
        assertNull(parseVisualResponse(response(occupancy), VisualFamily.COLOR_CAST))
        assertNull(parseVisualResponse(response(warmer), VisualFamily.FACE_SIZE_AMBIGUOUS))
    }

    @Test
    fun objectFocusParserUsesNormalizedQwenCoordinates() {
        val target = response("""{"schemaVersion":2,"outcome":"TARGET","point_2d":[726,386]}""")
        val outsideFrame = response("""{"schemaVersion":2,"outcome":"TARGET","point_2d":[1000,386]}""")

        assertEquals(VisualHint.FocusPoint(726 / 999f, 386 / 999f), parseVisualResponse(target, VisualFamily.OBJECT_FOCUS))
        assertNull(parseVisualResponse(outsideFrame, VisualFamily.OBJECT_FOCUS))
        assertNull(parseVisualResponse(target, VisualFamily.COLOR_CAST))
        assertNull(
            parseVisualResponse(
                response("""{"schemaVersion":2,"outcome":"CLARIFY","reason":"SCENE_CONFOUND"}"""),
                VisualFamily.OBJECT_FOCUS,
            ),
        )
        assertNull(
            parseVisualResponse(
                response("""{"schemaVersion":2,"outcome":"CLARIFY","reason":"TARGET_NOT_FOUND"}"""),
                VisualFamily.COLOR_CAST,
            ),
        )
    }

    @Test
    fun strictParserRejectsEveryEnvelopeAndUnionDrift() {
        val valid = """{"schemaVersion":3,"outcome":"CLARIFY","reason":"SUBJECT_UNCLEAR"}"""
        val invalidResponses = listOf(
            response(valid, id = ""),
            response(valid, objectType = "chat.completion.chunk"),
            response(valid, model = "another-model"),
            response(valid, finishReason = "length"),
            response(valid, role = "user"),
            response(valid, choices = 2),
            response(valid, toolCalls = JSONArray().put(JSONObject().put("id", "tool"))),
            response(valid, reasoningContent = "reasoning"),
            response(valid, refusal = "refused"),
            response("x".repeat(MAX_RESPONSE_CONTENT_BYTES + 1)),
            response("""{"schemaVersion":1,"outcome":"CLARIFY","reason":"SUBJECT_UNCLEAR"}"""),
            response("""{"schemaVersion":3,"outcome":"INTENT","distortionVisible":"true"}"""),
            response("""{"schemaVersion":3,"outcome":"CLARIFY","reason":"SUBJECT_UNCLEAR","distortionVisible":false}"""),
            response("""{"schemaVersion":3,"outcome":"CLARIFY","reason":"SUBJECT_UNCLEAR","extra":true}"""),
            response("$valid trailing prose"),
        )

        invalidResponses.forEach {
            assertNull(parseVisualResponse(it, VisualFamily.FACE_SIZE_AMBIGUOUS))
        }
    }

    private fun testJpeg(width: Int = 120, height: Int = 160): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun autoAssessment(
        confidence: String = "HIGH",
        exposure: String = "NONE",
        whiteBalance: String = "NONE",
        framing: String = "NONE",
        focus: String = "\"decision\":\"NONE\"",
    ): String =
        """{"schemaVersion":4,"outcome":"ASSESSMENT","confidence":"$confidence","exposure":{"decision":"$exposure","strength":"SMALL"},"whiteBalance":{"decision":"$whiteBalance","strength":"SMALL"},"framing":{"decision":"$framing","strength":"SMALL"},"focus":{$focus}}"""

    private fun response(
        content: String,
        id: String = "chatcmpl-test",
        objectType: String = "chat.completion",
        model: String = QWEN_MODEL,
        finishReason: String = "stop",
        choices: Int = 1,
        role: String = "assistant",
        toolCalls: JSONArray = JSONArray(),
        reasoningContent: String = "",
        refusal: String = "",
    ): String {
        val values = JSONArray()
        repeat(choices) {
            values.put(
                JSONObject()
                    .put("finish_reason", finishReason)
                    .put(
                        "message",
                        JSONObject()
                            .put("role", role)
                            .put("content", content)
                            .put("tool_calls", toolCalls)
                            .put("reasoning_content", reasoningContent)
                            .put("refusal", refusal),
                    ),
            )
        }
        return JSONObject()
            .put("id", id)
            .put("object", objectType)
            .put("model", model)
            .put("choices", values)
            .toString()
    }

    private class FakeHttpsConnection(
        url: URL = URL(BAILIAN_ENDPOINT),
        private val status: Int = 200,
        response: String,
    ) : HttpsURLConnection(url) {
        val headers = mutableMapOf<String, String>()
        val sentBody = ByteArrayOutputStream()
        var disconnected = false
        private val responseBytes = response.toByteArray(StandardCharsets.UTF_8)

        override fun setRequestProperty(key: String, value: String) {
            headers[key] = value
        }

        override fun getOutputStream() = sentBody
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBytes)
        override fun getErrorStream(): InputStream = ByteArrayInputStream(responseBytes)
        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun getCipherSuite(): String = "TLS_FAKE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
}
