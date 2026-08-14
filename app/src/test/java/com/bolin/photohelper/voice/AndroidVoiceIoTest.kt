package com.bolin.photohelper.voice

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidVoiceIoTest {
    @Test
    fun `finish requested before recognizer readiness dispatches once when ready`() {
        var dispatches = 0
        val gate = FinishListeningGate { dispatches++ }

        gate.request()
        assertEquals(0, dispatches)

        gate.onReady()
        gate.request()
        gate.onReady()

        assertEquals(1, dispatches)

        val closedGate = FinishListeningGate { dispatches++ }
        closedGate.close()
        closedGate.request()
        closedGate.onReady()
        assertEquals(1, dispatches)
    }

    @Test
    fun `recognition keeps the device English locale and otherwise falls back to US English`() {
        assertEquals("en-GB", preferredEnglishRecognitionLocale(Locale.UK).toLanguageTag())
        assertEquals("en-SG", preferredEnglishRecognitionLocale(Locale.forLanguageTag("en-SG")).toLanguageTag())
        assertEquals("en-US", preferredEnglishRecognitionLocale(Locale.JAPAN).toLanguageTag())
    }

    @Test
    fun `on-device language state distinguishes ready pending downloadable and unsupported`() {
        assertEquals(OnDeviceLanguageState.INSTALLED, onDeviceLanguageState("en-US", listOf("en-us"), emptyList(), emptyList()))
        assertEquals(OnDeviceLanguageState.PENDING, onDeviceLanguageState("en-US", emptyList(), listOf("en-US"), emptyList()))
        assertEquals(OnDeviceLanguageState.DOWNLOADABLE, onDeviceLanguageState("en-US", emptyList(), emptyList(), listOf("en-US")))
        assertEquals(OnDeviceLanguageState.UNSUPPORTED, onDeviceLanguageState("en-US", listOf("en-GB"), emptyList(), emptyList()))
    }

    @Test
    fun `voice homophones normalize only the two required whole utterances`() {
        assertEquals("too dim", normalizeVoiceComplaint(" two dim "))
        assertEquals("too blue", normalizeVoiceComplaint("Two Blue"))
        assertEquals("two blue lights", normalizeVoiceComplaint("two blue lights"))
        assertEquals("two people", normalizeVoiceComplaint("two people"))
    }

    @Test
    fun `recognition uses the last partial only when the final result is empty`() {
        assertEquals(
            "make the picture brighter and zoom in",
            selectRecognitionText(emptyList(), " make the picture brighter and zoom in "),
        )
        assertEquals(
            "final wording",
            selectRecognitionText(listOf(" final wording "), "older partial wording"),
        )
        assertEquals(null, selectRecognitionText(emptyList(), "  "))
    }

    @Test
    fun `recognition preserves a complete utterance when the final result contains only its ending`() {
        assertEquals(
            "focus on the shoe, make the picture colder, and take a picture",
            selectRecognitionText(
                listOf("take a picture"),
                "focus on the shoe, make the picture colder, and take a picture",
            ),
        )
    }

    @Test
    fun `a shorter trailing partial does not replace the complete utterance`() {
        assertEquals(
            "focus on the shoe, make the picture colder, and take a picture",
            selectPartialRecognitionText(
                "focus on the shoe, make the picture colder, and take a picture",
                listOf("a picture"),
            ),
        )
    }
}
