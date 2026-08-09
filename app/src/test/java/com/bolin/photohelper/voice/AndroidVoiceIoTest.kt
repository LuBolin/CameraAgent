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
}
