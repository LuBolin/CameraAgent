package com.bolin.photohelper.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmCaptureTest {
    @Test
    fun `quiet leading and trailing audio is removed with speech padding retained`() {
        val pcm = pcm16(
            segment(1_000, 90),
            segment(1_300, 1_200),
            segment(2_000, 90),
        )

        val trimmed = trimPcm16Mono(pcm, SAMPLE_RATE)

        assertTrue(trimmed.size < pcm.size)
        assertTrue(trimmed.size > 1_300 * BYTES_PER_MILLISECOND)
        assertEquals(0, trimmed.size % 2)
    }

    @Test
    fun `near silence does not become a transcript source`() {
        val pcm = pcm16(segment(4_000, 10))

        assertTrue(trimPcm16Mono(pcm, SAMPLE_RATE).isEmpty())
    }

    @Test
    fun `continuous ambiguous audio is preserved for the platform recognizer`() {
        val pcm = pcm16(segment(4_000, 500))

        assertArrayEquals(pcm, trimPcm16Mono(pcm, SAMPLE_RATE))
    }

    @Test
    fun `an isolated tap after speech does not extend the retained tail`() {
        val pcm = pcm16(
            segment(800, 80),
            segment(1_000, 1_100),
            segment(1_800, 80),
            segment(20, 6_000),
            segment(800, 80),
        )

        val trimmed = trimPcm16Mono(pcm, SAMPLE_RATE)

        assertTrue(trimmed.size < 2_500 * BYTES_PER_MILLISECOND)
    }

    @Test
    fun `a pause between two spoken clauses is preserved`() {
        val pcm = pcm16(
            segment(700, 70),
            segment(500, 900),
            segment(800, 70),
            segment(500, 1_000),
            segment(1_200, 70),
        )

        val trimmed = trimPcm16Mono(pcm, SAMPLE_RATE)

        assertTrue(trimmed.size >= 2_300 * BYTES_PER_MILLISECOND)
        assertTrue(trimmed.size < pcm.size)
    }

    private fun segment(durationMs: Int, amplitude: Int): ShortArray =
        ShortArray(durationMs * SAMPLE_RATE / 1_000) { index ->
            if (index % 2 == 0) amplitude.toShort() else (-amplitude).toShort()
        }

    private fun pcm16(vararg segments: ShortArray): ByteArray {
        val samples = segments.sumOf(ShortArray::size)
        val result = ByteArray(samples * 2)
        var offset = 0
        segments.forEach { segment ->
            segment.forEach { sample ->
                val value = sample.toInt()
                result[offset++] = value.toByte()
                result[offset++] = (value shr 8).toByte()
            }
        }
        return result
    }

    private companion object {
        const val SAMPLE_RATE = 1_000
        const val BYTES_PER_MILLISECOND = SAMPLE_RATE * 2 / 1_000
    }
}
