package com.bolin.photohelper.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandTest {
    @Test
    fun `recognizes immediate shutter equivalents`() {
        listOf(
            "take picture", "capture a photo", "shoot", "snap the shot", "cheese",
            "press the shutter", "take one", "get a shot",
        ).forEach {
            assertEquals(it, VoiceCommand.TakePicture, parseVoiceCommand(it))
        }
    }

    @Test
    fun `recognizes countdown digits and number words`() {
        assertEquals(VoiceCommand.CountdownAndTakePicture(5), parseVoiceCommand("countdown 5 seconds and take picture"))
        assertEquals(VoiceCommand.CountdownAndTakePicture(3), parseVoiceCommand("take a photo in three seconds"))
        assertEquals(VoiceCommand.CountdownAndTakePicture(10), parseVoiceCommand("set timer for ten seconds then capture photo"))
        assertEquals(VoiceCommand.CountdownAndTakePicture(21), parseVoiceCommand("take one in twenty one seconds"))
    }

    @Test
    fun `recognizes camera switching and explicit lens requests`() {
        listOf("switch camera", "flip the camera", "change my camera").forEach {
            assertEquals(it, VoiceCommand.SwitchCamera, parseVoiceCommand(it))
        }
        listOf("use the front camera", "selfie mode", "camera facing me").forEach {
            assertEquals(it, VoiceCommand.UseFrontCamera, parseVoiceCommand(it))
        }
        listOf("switch to the back camera", "rear-facing camera", "camera pointing away").forEach {
            assertEquals(it, VoiceCommand.UseRearCamera, parseVoiceCommand(it))
        }
    }

    @Test
    fun `does not turn coaching requests into shutter commands`() {
        assertNull(parseVoiceCommand("make the picture brighter"))
        assertNull(parseVoiceCommand("focus on the red watch"))
        assertNull(parseVoiceCommand("do not take a picture"))
        assertNull(parseVoiceCommand("dont press the shutter"))
        assertNull(parseVoiceCommand("take a picture in 90 seconds"))
        assertNull(parseVoiceCommand("take a picture in a bit"))
        assertNull(parseVoiceCommand("do not switch the camera"))
    }

    @Test
    fun `plans selfie countdown as front camera then capture`() {
        assertEquals(
            CommandPlan(
                listOf(
                    CommandPlanStep.SetCamera(CameraFacing.FRONT),
                    CommandPlanStep.Capture(5),
                ),
            ),
            parseCommandPlan("take a selfie in 5 seconds"),
        )
    }

    @Test
    fun `camera setters consume their command wording`() {
        assertEquals(
            CommandPlan(listOf(CommandPlanStep.SetCamera(CameraFacing.FRONT))),
            parseCommandPlan("switch to the front camera"),
        )
        assertEquals(
            CommandPlan(listOf(CommandPlanStep.SetCamera(CameraFacing.REAR))),
            parseCommandPlan("use the back camera"),
        )
    }

    @Test
    fun `countdown before capture remains one capture action`() {
        assertEquals(
            CommandPlan(listOf(CommandPlanStep.Capture(5))),
            parseCommandPlan("countdown 5 seconds and take picture"),
        )
    }

    @Test
    fun `plans settings camera toggle and delayed capture in order`() {
        assertEquals(
            CommandPlan(
                listOf(
                    CommandPlanStep.Coach("make it brighter and more warm"),
                    CommandPlanStep.SetCamera(CameraFacing.TOGGLE),
                    CommandPlanStep.Capture(5),
                ),
            ),
            parseCommandPlan("Make it brighter and more warm, then flip the camera and take a photo in 5 seconds"),
        )
    }

    @Test
    fun `keeps capture terminal and bounds unsafe plans`() {
        assertEquals(
            CommandPlan(listOf(CommandPlanStep.Coach("take a photo then switch camera"))),
            parseCommandPlan("take a photo then switch camera"),
        )
        assertEquals(
            CommandPlan(listOf(CommandPlanStep.Coach("do not switch the camera and take a photo"))),
            parseCommandPlan("do not switch the camera and take a photo"),
        )
    }
}
