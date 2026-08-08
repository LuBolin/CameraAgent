package com.bolin.photohelper.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidVoiceIoTest {
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
