package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.CameraAdjustment
import com.bolin.photohelper.capture.CameraCapabilities
import com.bolin.photohelper.capture.CameraTelemetry
import com.bolin.photohelper.capture.FaceObservation
import com.bolin.photohelper.capture.FrameObservation
import com.bolin.photohelper.capture.WhiteBalancePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCoachEngineTest {
    private val engine = DefaultCoachEngine()
    private val capabilities = CameraCapabilities(
        exposureCompensationRange = -6..6,
        exposureCompensationStepEv = 1f / 3f,
        supportedWhiteBalancePresets = WhiteBalancePreset.entries.toSet(),
    )

    @Test
    fun `clipped bright frame gets bounded darker EV recommendation`() {
        val decision = engine.evaluateLocal(input("the whole shot is too bright", observation(highlights = .25f)))
        val recommendation = (decision as LocalDecision.Recommend).recommendation
        val action = recommendation.action as RecommendationAction.ApplySetting

        assertEquals(RecommendationBasis.MEASURED_DIAGNOSIS, recommendation.basis)
        assertEquals(CameraAdjustment.ExposureCompensation(-2), action.adjustment)
        assertEquals("Apply", recommendation.primaryLabel)
    }

    @Test
    fun `clear directional request remains a preference when evidence is normal`() {
        val decision = engine.evaluateLocal(input("too bright", observation(highlights = 0f, luma = .5f)))
        val recommendation = (decision as LocalDecision.Recommend).recommendation

        assertEquals(RecommendationBasis.USER_PREFERENCE, recommendation.basis)
        assertTrue(recommendation.headline.contains("normal range"))
    }

    @Test
    fun `literal too dim gets a brighter exposure recommendation`() {
        val decision = engine.evaluateLocal(input("too dim", observation(luma = .5f)))
        val action = ((decision as LocalDecision.Recommend).recommendation.action as RecommendationAction.ApplySetting).adjustment

        assertEquals(CameraAdjustment.ExposureCompensation(2), action)
    }

    @Test
    fun `regional exposure never produces global apply`() {
        val decision = engine.evaluateLocal(input("the background is too bright", observation()))

        assertTrue(decision is LocalDecision.Clarify)
        assertEquals(listOf("Whole photo", "Person/face", "Background"), (decision as LocalDecision.Clarify).chips.map { it.label })
    }

    @Test
    fun `all regional exposure polarities require an area choice`() {
        listOf("background too dark", "background too dim", "face too bright", "person is underexposed", "subject is overexposed").forEach { complaint ->
            assertTrue("$complaint must not change the whole frame", engine.evaluateLocal(input(complaint, observation())) is LocalDecision.Clarify)
        }
    }

    @Test
    fun `regional clarification choices end in an honest limitation`() {
        assertTrue(engine.evaluateLocal(input("person-specific exposure", observation())) is LocalDecision.Advisory)
        assertTrue(engine.evaluateLocal(input("background-specific exposure", observation())) is LocalDecision.Advisory)
    }

    @Test
    fun `natural face too big wording stays ambiguous and visually eligible`() {
        val decisions = listOf("face too big", "the face looks too big", "the face is too big").map {
            engine.evaluateLocal(input(it, observation(faces = listOf(face(.48f))))) as LocalDecision.Clarify
        }

        decisions.forEach {
            assertEquals(VisualFamily.FACE_SIZE_AMBIGUOUS, it.visualEligibility?.family)
            assertEquals(2, it.chips.size)
        }
    }

    @Test
    fun `explicit face occupancy creates one step guidance`() {
        val decision = engine.evaluateLocal(input("face takes up too much frame", observation(faces = listOf(face(.50f)))))
        val recommendation = (decision as LocalDecision.Recommend).recommendation
        val action = recommendation.action as RecommendationAction.GuidePosition

        assertTrue(action.requiresWalkingWarning)
        assertEquals("Start one-step guidance", recommendation.primaryLabel)
    }

    @Test
    fun `multiple faces never select a subject`() {
        val decision = engine.evaluateLocal(
            input("face takes up too much frame", observation(faces = listOf(face(.4f), face(.3f)))),
        )

        assertTrue(decision is LocalDecision.Advisory)
        assertTrue((decision as LocalDecision.Advisory).headline.contains("more than one"))
    }

    @Test
    fun `negation cannot execute`() {
        val decision = engine.evaluateLocal(input("not too bright", observation(highlights = .4f)))

        assertTrue(decision is LocalDecision.Clarify)
        assertFalse(decision is LocalDecision.Recommend)
    }

    @Test
    fun `wrong-family visual label is ignored`() {
        val original = input("face too big", observation(faces = listOf(face(.45f))))
        val decision = engine.continueWithVisualHint(
            original,
            VisualFamily.FACE_SIZE_AMBIGUOUS,
            VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER),
        )

        assertTrue(decision is LocalDecision.Clarify)
    }

    @Test
    fun `visual face occupancy retains provenance`() {
        val original = input("face too big", observation(faces = listOf(face(.5f))))
        val decision = engine.continueWithVisualHint(
            original,
            VisualFamily.FACE_SIZE_AMBIGUOUS,
            VisualHint.Intent(VisualIntent.FACE_OCCUPANCY_LOWER),
        )

        assertTrue((decision as LocalDecision.Recommend).recommendation.fromVisualHint)
    }

    @Test
    fun `exposure verification distinguishes progress from unchanged`() {
        val baseline = observation(luma = .7f, highlights = .3f)
        val target = VerificationTarget.Exposure(-1, .7f, .3f, baseline)

        assertEquals(VerificationResult.Progress, engine.verify(target, observation(luma = .6f, highlights = .24f)))
        assertEquals(VerificationResult.Unchanged, engine.verify(target, observation(luma = .7f, highlights = .3f)))
        assertNotNull(engine.verify(target, observation(luma = .55f, highlights = .1f)))
    }

    @Test
    fun `zero clipping requires directional luma movement for exposure success`() {
        val baseline = observation(luma = .7f, highlights = 0f)
        val target = VerificationTarget.Exposure(-1, .7f, 0f, baseline)

        assertEquals(VerificationResult.Unchanged, engine.verify(target, observation(luma = .7f, highlights = 0f)))
        assertEquals(VerificationResult.Satisfied, engine.verify(target, observation(luma = .6f, highlights = 0f)))
    }

    @Test
    fun `exposure improvement is incomparable when subject framing changes`() {
        val baseline = observation(luma = .7f, highlights = .3f, faces = listOf(face(.3f)))
        val target = VerificationTarget.Exposure(-1, .7f, .3f, baseline)
        val reframed = observation(luma = .5f, highlights = .05f, faces = listOf(face(.5f)))

        assertTrue(engine.verify(target, reframed) is VerificationResult.Incomparable)
    }

    @Test
    fun `same aspect review and live frames remain comparable across resolutions`() {
        val baseline = observation(luma = .7f, highlights = .3f, width = 720, height = 540)
        val target = VerificationTarget.Exposure(-1, .7f, .3f, baseline)

        assertEquals(
            VerificationResult.Satisfied,
            engine.verify(target, observation(luma = .55f, highlights = .1f, width = 640, height = 480)),
        )
    }

    @Test
    fun `settled movement to a different scene is incomparable`() {
        val baseline = observation(
            luma = .7f,
            highlights = .3f,
            sceneLumaSignature = listOf(20, 50, 80, 110),
        )
        val target = VerificationTarget.Exposure(-1, .7f, .3f, baseline)
        val differentScene = observation(
            luma = .55f,
            highlights = .1f,
            sceneLumaSignature = listOf(110, 80, 50, 20),
        )

        assertTrue(engine.verify(target, differentScene) is VerificationResult.Incomparable)
    }

    @Test
    fun `horizontal guidance aims toward the off-center subject`() {
        fun instruction(complaint: String) = (
            (engine.evaluateLocal(
                input(
                    complaint,
                    observation(
                        faces = listOf(face(.3f, centerX = if (complaint.endsWith("left")) .2f else .8f)),
                    ),
                ),
            ) as LocalDecision.Recommend)
                .recommendation.action as RecommendationAction.GuidePosition
            ).instruction

        assertEquals("Aim the phone slightly left.", instruction("subject too far left"))
        assertEquals("Aim the phone slightly right.", instruction("subject too far right"))
    }

    @Test
    fun `position complaint does not guide in a direction contradicted by the frame`() {
        val decision = engine.evaluateLocal(
            input("subject too far left", observation(faces = listOf(face(.3f, centerX = .8f)))),
        )

        assertTrue(decision is LocalDecision.Advisory)
    }

    @Test
    fun `face size guidance verifies the requested direction from its own baseline`() {
        fun target(complaint: String, width: Float) = (
            (engine.evaluateLocal(input(complaint, observation(faces = listOf(face(width))))) as LocalDecision.Recommend)
                .recommendation.action as RecommendationAction.GuidePosition
            ).target as VerificationTarget.FaceOccupancy

        val smaller = target("make the face smaller", .20f)
        val bigger = target("make the face bigger", .50f)

        assertEquals(VerificationResult.Progress, engine.verify(smaller, observation(faces = listOf(face(.20f)))))
        assertEquals(VerificationResult.Satisfied, engine.verify(smaller, observation(faces = listOf(face(.17f)))))
        assertEquals(VerificationResult.Progress, engine.verify(bigger, observation(faces = listOf(face(.50f)))))
        assertEquals(VerificationResult.Satisfied, engine.verify(bigger, observation(faces = listOf(face(.56f)))))
    }

    @Test
    fun `positive roll asks for counterclockwise correction`() {
        val decision = engine.evaluateLocal(input("crooked", observation(rollDegrees = 8f)))
        val recommendation = (decision as LocalDecision.Recommend).recommendation
        val action = recommendation.action as RecommendationAction.GuidePosition

        assertEquals("Rotate the phone a little counterclockwise.", action.instruction)
    }

    @Test
    fun `negative roll asks for clockwise correction`() {
        val decision = engine.evaluateLocal(input("crooked", observation(rollDegrees = -8f)))
        val recommendation = (decision as LocalDecision.Recommend).recommendation
        val action = recommendation.action as RecommendationAction.GuidePosition

        assertEquals("Rotate the phone a little clockwise.", action.instruction)
    }

    @Test
    fun `level verification uses an inclusive plus or minus 1_5 degree band`() {
        val target = VerificationTarget.Level()

        assertEquals(VerificationResult.Satisfied, engine.verify(target, observation(rollDegrees = 1.5f)))
        assertEquals(VerificationResult.Satisfied, engine.verify(target, observation(rollDegrees = -1.5f)))
        assertEquals(VerificationResult.Progress, engine.verify(target, observation(rollDegrees = 1.51f)))
        assertEquals(VerificationResult.Progress, engine.verify(target, observation(rollDegrees = -1.51f)))
    }

    @Test
    fun `level stays non executable when the angle sensor is unavailable`() {
        val decision = engine.evaluateLocal(input("crooked", observation()))
        val verification = engine.verify(VerificationTarget.Level(), observation())

        assertTrue(decision is LocalDecision.Advisory)
        assertEquals("Level guidance is unavailable", (decision as LocalDecision.Advisory).headline)
        assertTrue(verification is VerificationResult.Incomparable)
    }

    @Test
    fun `color clarification chips complete locally`() {
        val chips = listOf("too blue", "too yellow")
            .flatMap { (engine.evaluateLocal(input(it, observation())) as LocalDecision.Clarify).chips }
            .associateBy { it.label }

        assertEquals(setOf("Warmer", "Cooler", "Auto"), chips.keys)
        listOf("Warmer", "Cooler").forEach { label ->
            val decision = engine.evaluateLocal(
                input(chips.getValue(label).replacementComplaint, observation()),
            ) as LocalDecision.Recommend
            val recommendation = decision.recommendation
            assertTrue(recommendation.action is RecommendationAction.ApplySetting)
            assertEquals("Apply", recommendation.primaryLabel)
        }
        assertTrue(engine.evaluateLocal(input(chips.getValue("Auto").replacementComplaint, observation())) is LocalDecision.Advisory)
    }

    @Test
    fun `unsupported white balance never offers a fake apply`() {
        val decision = engine.evaluateLocal(
            input(
                text = "warmer",
                observation = observation(),
                capabilities = capabilities.copy(supportedWhiteBalancePresets = emptySet()),
            ),
        )

        assertTrue(decision is LocalDecision.Advisory)
    }

    @Test
    fun `auto white balance is one tap when a fixed preset is active`() {
        val decision = engine.evaluateLocal(
            input(
                text = "auto",
                observation = observation(),
                telemetry = CameraTelemetry(whiteBalancePreset = WhiteBalancePreset.WARMER),
            ),
        )
        val recommendation = (decision as LocalDecision.Recommend).recommendation
        val action = recommendation.action as RecommendationAction.ApplySetting

        assertEquals(CameraAdjustment.WhiteBalance(WhiteBalancePreset.AUTO), action.adjustment)
        assertEquals("Apply", recommendation.primaryLabel)
    }

    @Test
    fun `explicit perspective concern stays advice only`() {
        val decision = engine.evaluateLocal(
            input("features look distorted", observation(faces = listOf(face(.45f)))),
        )
        val advisory = decision as LocalDecision.Advisory

        assertTrue(advisory.headline.contains("perspective", ignoreCase = true))
        assertTrue(advisory.detail.contains("step back", ignoreCase = true))
    }

    @Test
    fun `visual perspective hint stays attributed advice only`() {
        val original = input("face too big", observation(faces = listOf(face(.45f))))
        val decision = engine.continueWithVisualHint(
            original,
            VisualFamily.FACE_SIZE_AMBIGUOUS,
            VisualHint.Intent(VisualIntent.CLOSE_PERSPECTIVE_ADVISORY),
        )
        val advisory = decision as LocalDecision.Advisory

        assertTrue(advisory.fromVisualHint)
        assertTrue(advisory.detail.contains("cannot verify", ignoreCase = true))
    }

    @Test
    fun `focus miss never requires a detected face`() {
        val decision = engine.evaluateLocal(
            input(
                "focus missed",
                observation(faces = listOf(face(.3f))),
                capabilities.copy(supportsFocusMetering = true),
            ),
        )
        val recommendation = (decision as LocalDecision.Recommend).recommendation

        assertEquals(RecommendationAction.TapToFocus, recommendation.action)
        assertEquals(null, recommendation.primaryLabel)
        assertEquals(null, recommendation.subjectFace)
        assertTrue(recommendation.actionText.contains("Tap the subject"))
    }

    @Test
    fun `focus miss lets the user choose any subject when no face exists`() {
        val decision = engine.evaluateLocal(
            input(
                "focus missed",
                observation(),
                capabilities.copy(supportsFocusMetering = true),
            ),
        )
        val recommendation = (decision as LocalDecision.Recommend).recommendation

        assertEquals(RecommendationAction.TapToFocus, recommendation.action)
        assertEquals(null, recommendation.subjectFace)
        assertTrue(recommendation.actionText.contains("Tap the subject"))
    }

    @Test
    fun `focus miss stays advisory when tap to focus is unsupported`() {
        val decision = engine.evaluateLocal(
            input(
                "focus missed",
                observation(faces = listOf(face(.3f))),
                capabilities.copy(supportsFocusMetering = false),
            ),
        )

        assertTrue(decision is LocalDecision.Advisory)
        assertTrue((decision as LocalDecision.Advisory).headline.contains("unavailable"))
    }

    @Test
    fun `color verification checks the requested direction`() {
        val warmer = VerificationTarget.ColorBalance(
            direction = -1,
            baselineBlueBias = .08f,
            baselineObservation = observation(blueBias = .08f),
        )

        assertEquals(
            VerificationResult.Satisfied,
            engine.verify(warmer, observation(blueBias = .04f)),
        )
        assertEquals(
            VerificationResult.Unchanged,
            engine.verify(warmer, observation(blueBias = .10f)),
        )
    }

    @Test
    fun `weak or opposite color evidence is not uploaded`() {
        val weak = engine.evaluateLocal(input("looks blue", observation(blueBias = .01f))) as LocalDecision.Clarify
        val opposite = engine.evaluateLocal(input("looks blue", observation(blueBias = -.08f))) as LocalDecision.Clarify

        assertEquals(null, weak.visualEligibility)
        assertEquals(null, opposite.visualEligibility)
    }

    @Test
    fun `strong color evidence stays local rather than uploading`() {
        val strongBlue = engine.evaluateLocal(input("looks blue", observation(blueBias = .20f))) as LocalDecision.Clarify
        val strongYellow = engine.evaluateLocal(input("looks yellow", observation(blueBias = -.20f))) as LocalDecision.Clarify

        assertEquals(null, strongBlue.visualEligibility)
        assertEquals(null, strongYellow.visualEligibility)
    }

    @Test
    fun `visual color hint cannot execute after the cast becomes strong`() {
        val decision = engine.continueWithVisualHint(
            input("looks blue", observation(blueBias = .20f)),
            VisualFamily.COLOR_CAST,
            VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER),
        )

        assertTrue(decision is LocalDecision.Clarify)
    }

    @Test
    fun `contradictory visual color label cannot become Apply`() {
        val input = input("looks blue", observation(blueBias = .08f))
        val decision = engine.continueWithVisualHint(
            input,
            VisualFamily.COLOR_CAST,
            VisualHint.Intent(VisualIntent.WHITE_BALANCE_COOLER),
        )

        assertTrue(decision is LocalDecision.Clarify)
    }

    @Test
    fun `unsupported model-suggested white balance retains provenance`() {
        val input = input(
            "looks blue",
            observation(blueBias = .08f),
            capabilities.copy(supportedWhiteBalancePresets = emptySet()),
        )
        val decision = engine.continueWithVisualHint(
            input,
            VisualFamily.COLOR_CAST,
            VisualHint.Intent(VisualIntent.WHITE_BALANCE_WARMER),
        ) as LocalDecision.Advisory

        assertTrue(decision.fromVisualHint)
    }

    private fun input(
        text: String,
        observation: FrameObservation?,
        capabilities: CameraCapabilities = this.capabilities,
        telemetry: CameraTelemetry = CameraTelemetry(),
    ) = CoachingInput(
        complaintId = "complaint-1",
        complaint = text,
        origin = ObservationOrigin.LIVE,
        cameraSessionId = 1,
        observation = observation,
        lockedFace = observation?.faces?.singleOrNull(),
        capabilities = capabilities,
        telemetry = telemetry,
    )

    private fun observation(
        luma: Float = .5f,
        highlights: Float = 0f,
        shadows: Float = 0f,
        blueBias: Float? = 0f,
        faces: List<FaceObservation> = emptyList(),
        width: Int = 640,
        height: Int = 480,
        sceneLumaSignature: List<Int> = emptyList(),
        rollDegrees: Float? = null,
    ) = FrameObservation(
        id = 1,
        timestampMs = 1,
        meanLuma = luma,
        highlightClipFraction = highlights,
        shadowClipFraction = shadows,
        chromaBlueBias = blueBias,
        faces = faces,
        deviceRollDegrees = rollDegrees,
        sceneLumaSignature = sceneLumaSignature,
        sourceWidth = width,
        sourceHeight = height,
    )

    private fun face(width: Float, centerX: Float = .5f, centerY: Float = .45f) = FaceObservation(
        null,
        centerX - width / 2f,
        centerY - .25f,
        centerX + width / 2f,
        centerY + .25f,
    )
}
