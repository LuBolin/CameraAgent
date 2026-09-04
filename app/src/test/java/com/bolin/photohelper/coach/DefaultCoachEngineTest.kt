package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.CameraAdjustment
import com.bolin.photohelper.capture.CameraCapabilities
import com.bolin.photohelper.capture.CameraTelemetry
import com.bolin.photohelper.capture.FaceObservation
import com.bolin.photohelper.capture.FrameObservation
import com.bolin.photohelper.capture.MAX_WHITE_BALANCE_LEVEL
import com.bolin.photohelper.capture.WhiteBalancePreset
import com.bolin.photohelper.capture.whiteBalancePresetForLevel
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
        val action = recommendation.action as RecommendationAction.ApplySettings

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
        val action = ((decision as LocalDecision.Recommend).recommendation.action as RecommendationAction.ApplySettings).adjustment

        assertEquals(CameraAdjustment.ExposureCompensation(2), action)
    }

    @Test
    fun `subject bounds choose a bounded zoom target`() {
        val decision = engine.planSubjectZoom(
            input(
                "zoom in on grandma",
                observation(),
                capabilities.copy(zoomRatioRange = 1f..10f),
                CameraTelemetry(zoomRatio = 1f),
            ),
            SubjectBounds(.4f, .35f, .6f, .55f),
            small = false,
        )
        val action = ((decision as LocalDecision.Recommend).recommendation.action as RecommendationAction.ApplySettings)

        assertEquals(CameraAdjustment.ZoomRatio(2f), action.adjustment)
    }

    @Test
    fun `core complaints classify to allowlisted control intents`() {
        mapOf(
            "so dim" to ControlIntent.EXPOSURE_BRIGHTER,
            "dark" to ControlIntent.EXPOSURE_BRIGHTER,
            "too bright" to ControlIntent.EXPOSURE_DARKER,
            "too zoomed out" to ControlIntent.ZOOM_IN,
            "too zoomed in" to ControlIntent.ZOOM_OUT,
            "too blue" to ControlIntent.WHITE_BALANCE_WARMER,
            "too yellow" to ControlIntent.WHITE_BALANCE_COOLER,
            "focus missed" to ControlIntent.FOCUS_POINT_REQUIRED,
            "crooked" to ControlIntent.LEVEL_FRAME,
        ).forEach { (complaint, intent) ->
            assertEquals(IntentClassification.Intent(intent), classifyComplaint(complaint))
        }
    }

    @Test
    fun `compatible direct setting complaints classify together`() {
        assertEquals(
            IntentClassification.Intent(
                listOf(
                    ControlIntent.ZOOM_OUT,
                    ControlIntent.WHITE_BALANCE_COOLER,
                ),
            ),
            classifyComplaint("It's too warm, and too zoomed in!"),
        )
        assertEquals(
            IntentClassification.Intent(
                listOf(
                    ControlIntent.EXPOSURE_BRIGHTER,
                    ControlIntent.ZOOM_IN,
                ),
            ),
            classifyComplaint("Make the picture brighter and zoom in"),
        )
        assertEquals(
            IntentClassification.Intent(
                listOf(
                    ControlIntent.EXPOSURE_BRIGHTER,
                    ControlIntent.ZOOM_IN,
                ),
            ),
            classifyComplaint("Naked picture brighter and zoom in"),
        )
    }

    @Test
    fun `short aliases in compound complaints are all retained in canonical order`() {
        mapOf(
            "warmer and zoom out" to listOf(
                ControlIntent.ZOOM_OUT,
                ControlIntent.WHITE_BALANCE_WARMER,
            ),
            "warmer but zoom out" to listOf(
                ControlIntent.ZOOM_OUT,
                ControlIntent.WHITE_BALANCE_WARMER,
            ),
            "warmer. zoom out." to listOf(
                ControlIntent.ZOOM_OUT,
                ControlIntent.WHITE_BALANCE_WARMER,
            ),
            "dark and zoom in" to listOf(
                ControlIntent.EXPOSURE_BRIGHTER,
                ControlIntent.ZOOM_IN,
            ),
        ).forEach { (complaint, intents) ->
            assertEquals(IntentClassification.Intent(intents), classifyComplaint(complaint))
        }
    }

    @Test
    fun `compatible direct settings produce one compound recommendation`() {
        val decision = engine.evaluateLocal(
            input(
                "It's too warm, and too zoomed in!",
                observation(),
                capabilities.copy(zoomRatioRange = 1f..10f),
                CameraTelemetry(zoomRatio = 2f),
            ),
        ) as LocalDecision.Recommend
        val action = decision.recommendation.action as RecommendationAction.ApplySettings

        assertEquals(
            listOf(
                CameraAdjustment.ZoomRatio(1.6f),
                CameraAdjustment.WhiteBalance(WhiteBalancePreset.COOLER),
            ),
            action.changes.map { it.adjustment },
        )
        assertEquals("Apply both", decision.recommendation.primaryLabel)
        assertEquals(
            "Zoom · 1.6× digital zoom\nColor · Cooler white balance",
            decision.recommendation.actionText,
        )
    }

    @Test
    fun `unsafe complaint boundaries fail closed before planning`() {
        mapOf(
            "too blur" to ClarificationReason.BLUR_TYPE,
            "too warm or too zoomed in" to ClarificationReason.AMBIGUOUS,
            "no longer too dark" to ClarificationReason.NEGATED_DIRECTION,
            "isn't overexposed" to ClarificationReason.NEGATED_DIRECTION,
            "too dark and too bright" to ClarificationReason.CONFLICTING_DIRECTIONS,
            "too blue and too yellow" to ClarificationReason.CONFLICTING_DIRECTIONS,
            "warmer and auto white balance" to ClarificationReason.CONFLICTING_DIRECTIONS,
            "too zoomed in and too zoomed out" to ClarificationReason.CONFLICTING_DIRECTIONS,
            "too dark and focus missed" to ClarificationReason.MULTIPLE_COMPLAINTS,
            "the sky is too bright" to ClarificationReason.REGIONAL_REQUEST,
            "this looks cool" to ClarificationReason.AMBIGUOUS,
            "too close" to ClarificationReason.ZOOM_OR_DISTANCE,
        ).forEach { (complaint, reason) ->
            assertEquals(IntentClassification.Clarify(reason), classifyComplaint(complaint))
            assertFalse(engine.evaluateLocal(input(complaint, observation())) is LocalDecision.Recommend)
        }
    }

    @Test
    fun `compound recommendation is all or nothing when one setting is unavailable`() {
        val decision = engine.evaluateLocal(
            input(
                "too warm and too zoomed in",
                observation(),
                capabilities.copy(
                    zoomRatioRange = 1f..10f,
                    supportedWhiteBalancePresets = setOf(WhiteBalancePreset.AUTO),
                    supportedWhiteBalanceLevels = setOf(0),
                ),
                CameraTelemetry(zoomRatio = 2f),
            ),
        )

        assertTrue(decision is LocalDecision.Advisory)
        assertEquals("Not all requested changes are available", (decision as LocalDecision.Advisory).headline)
    }

    @Test
    fun `compound planner rejects conflicting or interactive axes at its authority boundary`() {
        listOf(
            listOf(ControlIntent.EXPOSURE_BRIGHTER, ControlIntent.EXPOSURE_DARKER),
            listOf(ControlIntent.ZOOM_IN, ControlIntent.ZOOM_OUT),
            listOf(ControlIntent.WHITE_BALANCE_WARMER, ControlIntent.WHITE_BALANCE_COOLER),
            listOf(ControlIntent.EXPOSURE_BRIGHTER, ControlIntent.FOCUS_POINT_REQUIRED),
            listOf(ControlIntent.ZOOM_IN, ControlIntent.LEVEL_FRAME),
        ).forEach { intents ->
            assertFalse(
                "$intents must not become executable",
                engine.planIntents(
                    input(
                        "model wording",
                        observation(),
                        capabilities.copy(zoomRatioRange = 1f..10f),
                        CameraTelemetry(zoomRatio = 2f),
                    ),
                    intents,
                ) is LocalDecision.Recommend,
            )
        }
    }

    @Test
    fun `recognized setting plus an unrecognized clause never silently drops the clause`() {
        listOf(
            "too warm and move the camera higher",
            "too warm but move the camera higher",
            "too warm. Move the camera higher.",
            "too warm, and the framing feels tight",
            "too warm and more",
            "too warm and do the opposite",
        ).forEach { complaint ->
            assertTrue(classifyComplaint(complaint) is IntentClassification.Clarify)
            assertFalse(engine.evaluateLocal(input(complaint, observation())) is LocalDecision.Recommend)
        }
    }

    @Test
    fun `compound legacy guidance never executes only its first movement`() {
        val decision = engine.evaluateLocal(
            input(
                "person too high and person too far left",
                observation(faces = listOf(face(.3f))),
            ),
        )

        assertTrue(decision is LocalDecision.Clarify)
    }

    @Test
    fun `manual exposure and noise requests never substitute another setting`() {
        listOf("too dark, raise ISO", "set ISO 100", "use 1/500 s", "use a faster shutter", "too noisy").forEach { complaint ->
            assertTrue("$complaint must remain advisory", engine.evaluateLocal(input(complaint, observation())) is LocalDecision.Advisory)
        }
    }

    @Test
    fun `an allowlisted intent is still planned locally`() {
        val decision = engine.planIntent(input("model wording", observation()), ControlIntent.EXPOSURE_BRIGHTER)
        val action = ((decision as LocalDecision.Recommend).recommendation.action as RecommendationAction.ApplySettings).adjustment

        assertEquals(CameraAdjustment.ExposureCompensation(2), action)
    }

    @Test
    fun `zoom complaints plan one bounded digital zoom step`() {
        val zoomCapabilities = capabilities.copy(zoomRatioRange = 1f..10f)

        fun adjustment(complaint: String, currentRatio: Float): CameraAdjustment.ZoomRatio {
            val decision = engine.evaluateLocal(
                input(complaint, observation(), zoomCapabilities, CameraTelemetry(zoomRatio = currentRatio)),
            ) as LocalDecision.Recommend
            return (decision.recommendation.action as RecommendationAction.ApplySettings).adjustment as CameraAdjustment.ZoomRatio
        }

        assertEquals(1.6f, adjustment("too zoomed in", 2f).ratio, .001f)
        assertEquals(1.25f, adjustment("too zoomed out", 1f).ratio, .001f)
        assertTrue(
            engine.evaluateLocal(input("too zoomed in", observation(), zoomCapabilities, CameraTelemetry(zoomRatio = 1f))) is LocalDecision.Advisory,
        )
        assertTrue(
            engine.evaluateLocal(input("too zoomed out", observation(), zoomCapabilities, CameraTelemetry(zoomRatio = 10f))) is LocalDecision.Advisory,
        )
    }

    @Test
    fun `small corrections move halfway toward the previous setting`() {
        val exposureInput = input(
            "a little darker",
            observation(),
            telemetry = CameraTelemetry(exposureCompensationIndex = 4),
        ).copy(relativeBaseline = CameraTelemetry(exposureCompensationIndex = 0))
        val exposure = engine.planIntent(exposureInput, ControlIntent.EXPOSURE_DARKER) as LocalDecision.Recommend
        val exposureAdjustment = (exposure.recommendation.action as RecommendationAction.ApplySettings).adjustment

        val zoomInput = input(
            "zoom out a little",
            observation(),
            capabilities.copy(zoomRatioRange = 1f..10f),
            CameraTelemetry(zoomRatio = 2f),
        ).copy(relativeBaseline = CameraTelemetry(zoomRatio = 1f))
        val zoom = engine.planIntent(zoomInput, ControlIntent.ZOOM_OUT) as LocalDecision.Recommend
        val zoomAdjustment = (zoom.recommendation.action as RecommendationAction.ApplySettings).adjustment

        assertEquals(CameraAdjustment.ExposureCompensation(2), exposureAdjustment)
        assertEquals(1.5f, (zoomAdjustment as CameraAdjustment.ZoomRatio).ratio, .001f)
    }

    @Test
    fun `zoom limits explain which boundary was reached`() {
        val zoomCapabilities = capabilities.copy(zoomRatioRange = 1f..10f)
        val maximum = engine.planIntent(
            input("zoom in", observation(), zoomCapabilities, CameraTelemetry(zoomRatio = 10f)),
            ControlIntent.ZOOM_IN,
        ) as LocalDecision.Advisory
        val minimum = engine.planIntent(
            input("zoom out", observation(), zoomCapabilities, CameraTelemetry(zoomRatio = 1f)),
            ControlIntent.ZOOM_OUT,
        ) as LocalDecision.Advisory

        assertEquals("Maximum zoom reached.", maximum.detail)
        assertEquals("Minimum zoom reached.", minimum.detail)
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
    fun `generic subject position without tracking never asks for a face`() {
        val decision = engine.evaluateLocal(input("subject too far left", observation())) as LocalDecision.Advisory

        assertFalse(decision.headline.contains("face", ignoreCase = true))
        assertFalse(decision.detail.contains("person", ignoreCase = true))
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
    fun `explicit color complaints produce a one tap setting`() {
        mapOf(
            "too blue" to CameraAdjustment.WhiteBalance(WhiteBalancePreset.WARMER),
            "too yellow" to CameraAdjustment.WhiteBalance(WhiteBalancePreset.COOLER),
        ).forEach { (complaint, expected) ->
            val decision = engine.evaluateLocal(input(complaint, observation())) as LocalDecision.Recommend
            val recommendation = decision.recommendation

            assertEquals(expected, (recommendation.action as RecommendationAction.ApplySettings).adjustment)
            assertEquals("Apply", recommendation.primaryLabel)
        }
    }

    @Test
    fun `repeated cooler requests advance through three bounded steps`() {
        listOf(0, -1, -2).forEachIndexed { index, currentLevel ->
            val decision = engine.evaluateLocal(
                input(
                    text = "cooler",
                    observation = observation(),
                    telemetry = CameraTelemetry(
                        whiteBalancePreset = whiteBalancePresetForLevel(currentLevel),
                        whiteBalanceLevel = currentLevel,
                    ),
                ),
            ) as LocalDecision.Recommend
            val adjustment = (decision.recommendation.action as RecommendationAction.ApplySettings).adjustment

            assertEquals(
                CameraAdjustment.WhiteBalance(WhiteBalancePreset.COOLER, -(index + 1)),
                adjustment,
            )
        }

        val atLimit = engine.evaluateLocal(
            input(
                text = "cooler",
                observation = observation(),
                telemetry = CameraTelemetry(
                    whiteBalancePreset = WhiteBalancePreset.COOLER,
                    whiteBalanceLevel = -MAX_WHITE_BALANCE_LEVEL,
                ),
            ),
        )

        assertTrue(atLimit is LocalDecision.Advisory)
    }

    @Test
    fun `unsupported white balance never offers a fake apply`() {
        val decision = engine.evaluateLocal(
            input(
                text = "warmer",
                observation = observation(),
                capabilities = capabilities.copy(
                    supportedWhiteBalancePresets = emptySet(),
                    supportedWhiteBalanceLevels = emptySet(),
                ),
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
        val action = recommendation.action as RecommendationAction.ApplySettings

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
    fun `zoom verification uses the camera reported ratio`() {
        val target = VerificationTarget.Zoom(direction = 1, baselineRatio = 1f, targetRatio = 1.25f)

        assertEquals(VerificationResult.Progress, engine.verify(target, observation(zoomRatio = 1.1f)))
        assertEquals(VerificationResult.Satisfied, engine.verify(target, observation(zoomRatio = 1.25f)))
        assertTrue(engine.verify(target, observation()) is VerificationResult.Incomparable)
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
            capabilities.copy(
                supportedWhiteBalancePresets = emptySet(),
                supportedWhiteBalanceLevels = emptySet(),
            ),
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
        zoomRatio: Float? = null,
    ) = FrameObservation(
        id = 1,
        timestampMs = 1,
        meanLuma = luma,
        highlightClipFraction = highlights,
        shadowClipFraction = shadows,
        chromaBlueBias = blueBias,
        faces = faces,
        deviceRollDegrees = rollDegrees,
        zoomRatio = zoomRatio,
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
