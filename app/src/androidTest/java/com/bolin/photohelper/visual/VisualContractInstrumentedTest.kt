package com.bolin.photohelper.visual

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bolin.photohelper.coach.ClarificationReason
import com.bolin.photohelper.coach.ControlIntent
import com.bolin.photohelper.coach.IntentClassification
import com.bolin.photohelper.coach.VisualFamily
import com.bolin.photohelper.coach.VisualHint
import com.bolin.photohelper.coach.VisualIntent
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
    fun complaintRequestSeparatesUntrustedTextFromTheStrictContract() {
        val bytes = buildComplaintRequestBody(ComplaintRequest("Could you open the framing a little?"))
        val body = JSONObject(bytes.toString(StandardCharsets.UTF_8))
        val messages = body.getJSONArray("messages")

        assertEquals(QWEN_MODEL, body.getString("model"))
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertTrue(messages.getJSONObject(0).getString("content").contains("JSON"))
        assertTrue(messages.getJSONObject(0).getString("content").contains("ZOOM_OUT"))
        assertFalse(messages.getJSONObject(0).getString("content").contains("Could you open"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("Could you open the framing a little?", messages.getJSONObject(1).getString("content"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals(false, body.getBoolean("enable_thinking"))
        assertEquals(0, body.getInt("temperature"))
        assertEquals(false, body.getBoolean("stream"))
        assertEquals(64, body.getInt("max_completion_tokens"))
    }

    @Test
    fun complaintParserAcceptsOnlyTheSemanticUnion() {
        assertEquals(
            IntentClassification.Intent(ControlIntent.ZOOM_OUT),
            parseComplaintResponse(response("""{"schemaVersion":2,"outcome":"INTENT","intent":"ZOOM_OUT"}""")),
        )
        assertEquals(
            IntentClassification.Clarify(ClarificationReason.BLUR_TYPE),
            parseComplaintResponse(response("""{"schemaVersion":2,"outcome":"CLARIFY","reason":"BLUR_TYPE"}""")),
        )

        listOf(
            """{"schemaVersion":2,"outcome":"INTENT","intent":"WHITE_BALANCE_AUTO"}""",
            """{"schemaVersion":2,"outcome":"INTENT","intent":"ZOOM_OUT","ratio":1.5}""",
            """{"schemaVersion":2,"outcome":"INTENT","intent":"FOCUS_POINT_REQUIRED","x":0.5}""",
            """{"schemaVersion":2,"outcome":"CLARIFY","reason":"UNKNOWN"}""",
            """{"schemaVersion":1,"outcome":"INTENT","intent":"ZOOM_OUT"}""",
            """{"schemaVersion":2,"outcome":"UNSUPPORTED","reason":"MANUAL_EXPOSURE"}""",
            """{"schemaVersion":2,"outcome":"INTENT","intent":"ZOOM_OUT"} trailing""",
        ).forEach { assertNull(parseComplaintResponse(response(it))) }
    }

    @Test
    fun complaintParserAcceptsOnlyBoundedDirectSettingCompounds() {
        assertEquals(
            IntentClassification.Intent(
                listOf(
                    ControlIntent.EXPOSURE_BRIGHTER,
                    ControlIntent.ZOOM_OUT,
                    ControlIntent.WHITE_BALANCE_COOLER,
                ),
            ),
            parseComplaintResponse(
                response(
                    """{"schemaVersion":2,"outcome":"INTENTS","intents":["WHITE_BALANCE_COOLER","ZOOM_OUT","EXPOSURE_BRIGHTER"]}""",
                ),
            ),
        )

        listOf(
            """{"schemaVersion":2,"outcome":"INTENTS","intents":[]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["ZOOM_OUT"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["ZOOM_OUT","ZOOM_OUT"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["ZOOM_IN","ZOOM_OUT"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["FOCUS_POINT_REQUIRED","ZOOM_OUT"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["LEVEL_FRAME","ZOOM_OUT"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["WHITE_BALANCE_AUTO","ZOOM_OUT"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["UNKNOWN","ZOOM_OUT"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["EXPOSURE_BRIGHTER","ZOOM_OUT","WHITE_BALANCE_COOLER","ZOOM_IN"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["EXPOSURE_BRIGHTER",7]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["EXPOSURE_BRIGHTER",null]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["EXPOSURE_BRIGHTER",{}]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intent":"ZOOM_OUT"}""",
            """{"schemaVersion":2,"outcome":"INTENT","intents":["ZOOM_OUT","WHITE_BALANCE_COOLER"]}""",
            """{"schemaVersion":2,"outcome":"INTENTS","intents":["EXPOSURE_BRIGHTER","ZOOM_OUT"],"extra":true}""",
        ).forEach { assertNull(parseComplaintResponse(response(it))) }
    }

    @Test
    fun complaintClientUsesOneBoundedRedactedHttpsRequest() = runBlocking {
        val fakeConnection = FakeHttpsConnection(
            response = response(
                """{"schemaVersion":2,"outcome":"INTENTS","intents":["ZOOM_OUT","WHITE_BALANCE_COOLER"]}""",
            ),
        )
        var connections = 0
        val client = BailianVisualClient(connectionFactory = {
            connections++
            fakeConnection
        })
        val key = "disposable-api-key".toCharArray()

        val result = client.classify(ComplaintRequest("The crop is tight and the light is amber"), key)

        assertEquals(
            ComplaintResult.Available(
                IntentClassification.Intent(
                    listOf(ControlIntent.ZOOM_OUT, ControlIntent.WHITE_BALANCE_COOLER),
                ),
            ),
            result,
        )
        assertEquals(1, connections)
        assertEquals(10_000, fakeConnection.connectTimeout)
        assertEquals(10_000, fakeConnection.readTimeout)
        assertTrue(key.all { it == '\u0000' })
        assertFalse(String(fakeConnection.sentBody.toByteArray(), StandardCharsets.UTF_8).contains("disposable-api-key"))
        assertTrue(fakeConnection.disconnected)
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

        assertEquals(VisualResult.Unavailable, client.interpret(request, "demo-key".toCharArray()))
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
