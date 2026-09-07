package com.bolin.photohelper.visual

import com.bolin.photohelper.coach.VisualFamily
import com.bolin.photohelper.coach.VisualHint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.runBlocking

class VisualContractsTest {
    @Test fun `independent composition rejects contradictory decisions and extra geometry`() {
        val content = JSONObject().put("schemaVersion", 3).put("outcome", "COMPOSITION")
            .put("backgroundCollision", false).put("subjectTooSmall", false)
            .put("problem", "HEADROOM").put("horizontal", "KEEP").put("vertical", "UPPER")
            .put("size", "KEEP").put("movement", "NONE").put("reason", "Reduce the empty space above the face.")
        fun parse() = parseVisualResponse(JSONObject().put("id", "test").put("object", "chat.completion")
            .put("model", QWEN_MODEL).put("choices", JSONArray().put(JSONObject().put("finish_reason", "stop")
                .put("message", JSONObject().put("role", "assistant").put("content", content.toString())))).toString(), VisualFamily.COMPOSITION)
        assertTrue(parse() is VisualHint.CompositionPlan)
        content.put("movement", "WALK")
        assertEquals(null, parse())
        content.put("movement", "NONE").put("vertical", "KEEP")
        assertEquals(null, parse())
        content.put("problem", "NONE")
        assertTrue(parse() is VisualHint.CompositionPlan)
        content.put("x", .3)
        assertEquals(null, parse())
        content.remove("x")
        content.put("size", "HUGE")
        assertEquals(null, parse())
        content.put("size", "KEEP").put("problem", "PERSPECTIVE").put("movement", "NONE")
        val perspective = parse() as VisualHint.CompositionPlan
        assertEquals(com.bolin.photohelper.coach.CompositionSize.SMALLER, perspective.intent.adjustment!!.size)
        content.put("backgroundCollision", true)
        val background = parse() as VisualHint.CompositionPlan
        assertEquals(com.bolin.photohelper.coach.CompositionProblem.BACKGROUND, background.intent.adjustment!!.problem)
        content.put("backgroundCollision", false).put("subjectTooSmall", true).put("problem", "SUBJECT_SIZE")
        val distant = parse() as VisualHint.CompositionPlan
        assertEquals(com.bolin.photohelper.coach.CompositionSize.LARGER, distant.intent.adjustment!!.size)
    }

    @Test
    fun `composition accepts semantics and rejects claimed capabilities`() {
        fun response(content: JSONObject) = JSONObject().put("id", "composition-test")
            .put("object", "chat.completion").put("model", QWEN_MODEL)
            .put("choices", JSONArray().put(JSONObject().put("finish_reason", "stop")
                .put("message", JSONObject().put("role", "assistant").put("content", content.toString())))).toString()
        val content = JSONObject().put("schemaVersion", 1).put("outcome", "COMPOSITION")
            .put("strategy", "SYMMETRY").put("framing", "WIDE").put("placement", "CENTRE")
            .put("reason", "Try the symmetry of the building.")
        assertTrue(parseVisualResponse(response(content), VisualFamily.COMPOSITION) is VisualHint.CompositionPlan)
        for (key in listOf("guidanceMode", "targets", "coordinates", "tolerance")) {
            content.put(key, "CLOSED_LOOP")
            assertEquals(null, parseVisualResponse(response(content), VisualFamily.COMPOSITION))
            content.remove(key)
        }
        content.put("strategy", "INVENTED")
        assertEquals(null, parseVisualResponse(response(content), VisualFamily.COMPOSITION))
        content.put("strategy", "PORTRAIT").put("reason", "x".repeat(241))
        assertEquals(null, parseVisualResponse(response(content), VisualFamily.COMPOSITION))
    }

    @Test
    fun `focus point rejects coordinates outside the preview`() {
        val point = VisualHint.FocusPoint(726 / 999f, 386 / 999f)

        assertEquals(726 / 999f, point.xFraction)
        assertEquals(386 / 999f, point.yFraction)
        assertTrue(runCatching { VisualHint.FocusPoint(1.001f, .5f) }.isFailure)
        assertTrue(runCatching { VisualHint.FocusPoint(Float.NaN, .5f) }.isFailure)
    }

    @Test
    fun `object focus response includes normalized subject bounds`() {
        val content = JSONObject()
            .put("schemaVersion", 3)
            .put("outcome", "TARGET")
            .put("point_2d", JSONArray(listOf(600, 400)))
            .put("box_2d", JSONArray(listOf(400, 200, 800, 600)))
            .toString()
        val response = JSONObject()
            .put("id", "completion-1")
            .put("object", "chat.completion")
            .put("model", QWEN_MODEL)
            .put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("finish_reason", "stop")
                        .put("message", JSONObject().put("role", "assistant").put("content", content)),
                ),
            )
            .toString()

        val hint = parseVisualResponse(response, VisualFamily.OBJECT_FOCUS) as VisualHint.FocusPoint

        assertEquals(400 / 999f, hint.bounds?.left)
        assertEquals(800 / 999f, hint.bounds?.right)
    }

    @Test
    fun `request rejects an oversized Observation Image`() {
        val failure = runCatching {
            VisualRequest(
                family = VisualFamily.COLOR_CAST,
                comment = "looks blue",
                observationJpeg = ByteArray(MAX_OBSERVATION_JPEG_BYTES + 1),
            )
        }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `request description redacts the comment and image`() {
        val request = VisualRequest(
            family = VisualFamily.FACE_SIZE_AMBIGUOUS,
            comment = "private comment",
            observationJpeg = byteArrayOf(0x01, 0x02, 0x03),
        )

        assertFalse(request.toString().contains("private comment"))
        assertFalse(request.toString().contains("1, 2, 3"))
    }

    @Test
    fun `request rejects overlong comments`() {
        val overlong = runCatching {
            VisualRequest(
                family = VisualFamily.COLOR_CAST,
                comment = "x".repeat(MAX_COMMENT_CHARACTERS + 1),
                observationJpeg = byteArrayOf(1),
            )
        }
        assertTrue(overlong.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `visual calls are limited to six in a rolling minute`() {
        val limiter = VisualCallLimiter()

        repeat(6) { assertTrue(limiter.tryAcquire(nowNanos = 0L)) }
        assertFalse(limiter.tryAcquire(nowNanos = 59_999_999_999L))
        assertTrue(limiter.tryAcquire(nowNanos = 60_000_000_000L))
    }

    @Test
    fun `invalid API key is cleared without opening a connection`() = runBlocking {
        val client = BailianVisualClient(connectionFactory = { error("connection must stay closed") })
        val key = charArrayOf('b', 'a', 'd', '\n')
        val request = VisualRequest(
            family = VisualFamily.COLOR_CAST,
            comment = "looks blue",
            observationJpeg = byteArrayOf(1),
        )

        assertEquals(VisualResult.Unavailable, client.interpret(request, key))
        assertTrue(key.all { it == '\u0000' })
    }

    @Test
    fun `overlong API key is cleared without opening a connection`() = runBlocking {
        val client = BailianVisualClient(connectionFactory = { error("connection must stay closed") })
        val key = CharArray(513) { 'x' }
        val request = VisualRequest(
            family = VisualFamily.COLOR_CAST,
            comment = "looks blue",
            observationJpeg = byteArrayOf(1),
        )

        assertEquals(VisualResult.Unavailable, client.interpret(request, key))
        assertTrue(key.all { it == '\u0000' })
    }

    @Test
    fun `HTTP failures retain useful user-facing categories`() {
        assertEquals(VisualResult.CredentialsRejected, visualFailureForHttpStatus(401))
        assertEquals(VisualResult.CredentialsRejected, visualFailureForHttpStatus(403))
        assertEquals(
            VisualResult.Failed("API service is unavailable. Try again later."),
            visualFailureForHttpStatus(503),
        )
        assertEquals(
            VisualResult.Failed("API rate limit reached. Try again later."),
            visualFailureForHttpStatus(429),
        )
    }

}
