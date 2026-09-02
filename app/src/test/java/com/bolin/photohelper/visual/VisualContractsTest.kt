package com.bolin.photohelper.visual

import com.bolin.photohelper.coach.VisualFamily
import com.bolin.photohelper.coach.VisualHint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class VisualContractsTest {
    @Test
    fun `focus point rejects coordinates outside the preview`() {
        val point = VisualHint.FocusPoint(726 / 999f, 386 / 999f)

        assertEquals(726 / 999f, point.xFraction)
        assertEquals(386 / 999f, point.yFraction)
        assertTrue(runCatching { VisualHint.FocusPoint(1.001f, .5f) }.isFailure)
        assertTrue(runCatching { VisualHint.FocusPoint(Float.NaN, .5f) }.isFailure)
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
