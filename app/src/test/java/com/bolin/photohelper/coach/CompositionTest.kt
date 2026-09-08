package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.FaceObservation
import com.bolin.photohelper.capture.FrameObservation
import org.junit.Assert.*
import org.junit.Test

class CompositionTest {
    @Test fun `independent adjustments preserve unchanged axes and route walking`() {
        val subject = face(1, .2f, .12f).copy(top = .4f, bottom = .6f)
        val adjustment = CompositionAdjustment(CompositionProblem.LOOK_ROOM, HorizontalPlacement.RIGHT_THIRD,
            VerticalPlacement.KEEP, CompositionSize.KEEP, CompositionMovement.NONE)
        val plan = compileComposition(CompositionIntent(adjustment = adjustment), listOf(subject))
        assertEquals("Aim the phone slightly left", measureGuidance(plan.targets, frame(subject))!!.correction.phoneInstruction())
        val arrived = subject.copy(left = 2f / 3f - .06f, right = 2f / 3f + .06f)
        assertTrue(measureGuidance(plan.targets, frame(arrived))!!.satisfied)
        val group = listOf(face(1, .15f, .1f), face(2, .35f, .1f))
        val groupPlan = compileComposition(CompositionIntent(adjustment = adjustment), group)
        assertTrue(groupPlan.targets.first() is VerificationTarget.GroupPosition)
        assertEquals(Correction.RIGHT, measureGuidance(groupPlan.targets, frame(*group.toTypedArray()))!!.correction)
        val walk = compileComposition(CompositionIntent(adjustment = adjustment.copy(
            problem = CompositionProblem.SUBJECT_SIZE, horizontal = HorizontalPlacement.KEEP,
            size = CompositionSize.SMALLER, movement = CompositionMovement.WALK)), listOf(subject))
        assertTrue(walk.distanceMovement)
        assertEquals(Correction.STEP_BACK, measureGuidance(walk.targets, frame(subject), distanceMovement = walk.distanceMovement)!!.correction)
        assertFalse(measureGuidance(walk.targets, frame(subject), setOf(Correction.STEP_BACK), true)!!.satisfied)
        val perspective = walk.intent.adjustment!!.copy(problem = CompositionProblem.PERSPECTIVE)
        assertEquals(Correction.STEP_BACK, measureGuidance(
            compileComposition(CompositionIntent(adjustment = perspective), listOf(subject)).targets,
            frame(subject), distanceMovement = true)!!.correction)
    }

    @Test fun `vertical correction preserves size and impossible group targets become advice`() {
        val subject = FaceObservation(1, .32f, .334f, .69f, .625f, 1f)
        val adjustment = CompositionAdjustment(CompositionProblem.HEADROOM, HorizontalPlacement.KEEP,
            VerticalPlacement.UPPER, CompositionSize.KEEP, CompositionMovement.NONE)
        val plan = compileComposition(CompositionIntent(adjustment = adjustment), listOf(subject))
        assertEquals(Correction.UP, measureGuidance(plan.targets, frame(subject))!!.correction)
        val group = listOf(face(1, .15f), face(2, .85f))
        val impossible = adjustment.copy(problem = CompositionProblem.LOOK_ROOM,
            horizontal = HorizontalPlacement.RIGHT_THIRD, vertical = VerticalPlacement.KEEP)
        assertEquals(GuidanceMode.ADVICE_ONLY, compileComposition(CompositionIntent(adjustment = impossible), group).guidanceMode)
        val hold = adjustment.copy(problem = CompositionProblem.NONE, vertical = VerticalPlacement.KEEP)
        assertTrue(compileComposition(CompositionIntent(adjustment = hold), listOf(subject)).targets.isEmpty())
    }

    @Test fun `blocked movement retains other axes and never falsely completes`() {
        val plan = compileComposition(CompositionIntent(), listOf(face(1)))
        val off = face(1, .8f).copy(top = .6f, bottom = .8f)
        val restricted = setOf(Correction.LEFT)
        assertEquals(Correction.UP, measureGuidance(plan.targets, frame(off), restricted)!!.correction)
        val stillRight = measureGuidance(plan.targets, frame(face(1, .8f)), restricted)!!
        assertEquals(Correction.BLOCKED, stillRight.correction)
        assertFalse(stillRight.satisfied)
        assertEquals("movement_blocked", GuidanceGovernor(0).update(stillRight, 0).failure)
        assertEquals(Correction.RIGHT, measureGuidance(plan.targets, frame(face(1, .2f)), restricted)!!.correction)
        val distance = measureGuidance(plan.targets, frame(face(1, .8f, width = .15f)),
            setOf(Correction.STEP_FORWARD), distanceMovement = true)!!
        assertEquals(Correction.LEFT, distance.correction)
        assertFalse(distance.satisfied)
        assertTrue(Correction.STEP_FORWARD.isMovement)
        assertFalse(Correction.LARGER.isMovement)
        assertFalse(Correction.HOLD.isMovement)
        assertFalse(Correction.RECOVER.isMovement)
    }

    @Test fun `phone actions oppose image displacement and preserve perspective advice`() {
        val target = listOf(VerificationTarget.FacePosition(.46f.. .54f, .34f.. .46f))
        val left = measureGuidance(target, frame(face(1, .2f)))!!
        val right = measureGuidance(target, frame(face(1, .8f)))!!
        assertEquals("Aim the phone slightly left", left.correction.phoneInstruction())
        assertEquals("Aim the phone slightly right", right.correction.phoneInstruction())
        assertEquals("Aim the phone slightly right", left.correction.phoneInstruction(mirrored = true))
        assertEquals("Aim the phone slightly left", right.correction.phoneInstruction(mirrored = true))
        val low = face(1).copy(top = .6f, bottom = .8f)
        val high = face(1).copy(top = .05f, bottom = .25f)
        assertEquals("Tilt the phone slightly down", measureGuidance(target, frame(low))!!.correction.phoneInstruction())
        assertEquals("Tilt the phone slightly up", measureGuidance(target, frame(high))!!.correction.phoneInstruction())
        assertTrue(measureGuidance(target, frame(face(1, .4f)))!!.error < left.error)
        assertTrue(measureGuidance(target, frame(face(1, .6f)))!!.error < right.error)
        assertEquals(Correction.STEP_BACK, measureGuidance(listOf(VerificationTarget.StepBack(.2f)), frame(face(1)))!!.correction)
    }

    private fun face(id: Int?, x: Float = .5f, width: Float = .25f, visible: Float = 1f) =
        FaceObservation(id, x - width / 2, .3f, x + width / 2, .5f, visible)
    private fun frame(vararg faces: FaceObservation, time: Long = 0) = FrameObservation(
        id = time, timestampMs = time, meanLuma = .5f, highlightClipFraction = 0f,
        shadowClipFraction = 0f, sourceWidth = 1000, sourceHeight = 1000, faces = faces.toList(),
    )

    @Test fun `compiler never tracks scene advice or absent subjects`() {
        assertEquals(GuidanceMode.ADVICE_ONLY, compileComposition(CompositionIntent(), emptyList()).guidanceMode)
        assertEquals(GuidanceMode.ADVICE_ONLY, compileComposition(
            CompositionIntent(strategy = CompositionStrategy.SYMMETRY), listOf(face(1))).guidanceMode)
        assertEquals(2, compileComposition(CompositionIntent(), listOf(face(1))).targets.size)
        assertTrue(compileComposition(CompositionIntent(), listOf(face(1, .3f), face(2, .7f)))
            .targets.first() is VerificationTarget.GroupPosition)
    }

    @Test fun `completion requires joint targets and visible members`() {
        val plan = compileComposition(CompositionIntent(), listOf(face(1)))
        assertTrue(measureGuidance(plan.targets, frame(face(1)))!!.satisfied)
        assertFalse(measureGuidance(plan.targets, frame(face(1, width = .4f)))!!.satisfied)
        assertFalse(measureGuidance(plan.targets, frame(face(1, visible = .8f)))!!.satisfied)
    }

    @Test fun `members exclude passersby and refuse ambiguous replacement`() {
        val members = listOf(face(1, .3f), face(2, .7f))
        assertEquals(members, matchMembers(members, members + face(3, .5f)))
        assertNull(matchMembers(members, listOf(members.first())))
        assertNull(matchMembers(listOf(face(null)), listOf(face(2, .48f), face(3, .52f))))
        assertEquals(listOf(face(9)), matchMembers(listOf(face(1)), listOf(face(9))))
    }

    @Test fun `group proposal needs persistence and does not discard small children`() {
        val people = arrayOf(face(1, .4f), face(2, .65f, width = .1f))
        assertNull(automaticCompositionMembers(listOf(frame(*people))))
        assertEquals(2, automaticCompositionMembers(listOf(frame(*people), frame(*people, time = 250), frame(*people, time = 500)))!!.size)
    }

    @Test fun `scene proposal cannot ignore newly detected or tiny faces`() {
        assertEquals(emptyList<FaceObservation>(), automaticCompositionMembers(
            listOf(frame(), frame(time = 250), frame(time = 500))))
        assertNull(automaticCompositionMembers(
            listOf(frame(), frame(face(1), time = 250), frame(face(1), time = 500))))
        val tiny = face(1, width = .02f)
        assertNull(automaticCompositionMembers(
            listOf(frame(tiny), frame(tiny, time = 250), frame(tiny, time = 500))))
    }

    @Test fun `governor recovers same attempt and restarts completion dwell`() {
        val governor = GuidanceGovernor(0)
        val hold = GuidanceMeasurement(Correction.HOLD, 0f, true)
        assertFalse(governor.update(hold, 0).complete)
        assertEquals(Correction.RECOVER, governor.update(null, 250).correction)
        assertFalse(governor.update(hold, 500).complete)
        assertTrue(governor.update(hold, 1000).complete)
        val metrics = governor.finish(1000, "completion")!!
        assertEquals(1, metrics.trackingLosses)
        assertNull(governor.finish(1000, "abandonment"))
    }

    @Test fun `minor overshoot after reaching target does not restart completion`() {
        val governor = GuidanceGovernor(0)
        val arrived = GuidanceMeasurement(Correction.HOLD, 0f, true)
        val edgeJitter = GuidanceMeasurement(Correction.UP, .2f, false)

        assertFalse(governor.update(arrived, 0).complete)
        assertEquals(Correction.HOLD, governor.update(edgeJitter, 250).correction)
        assertTrue(governor.update(arrived, 500).complete)
    }

    @Test fun `governor limits noisy direction changes and stalled attempts`() {
        val governor = GuidanceGovernor(0)
        for (time in 0L..3000L step 250) {
            val direction = if (time % 500 == 0L) Correction.LEFT else Correction.RIGHT
            assertEquals(Correction.HOLD, governor.update(GuidanceMeasurement(direction, 1f, false), time).correction)
        }
        for (time in 3250L..7750L step 250) governor.update(GuidanceMeasurement(Correction.LEFT, 1f, false), time)
        assertEquals("no_progress", governor.update(GuidanceMeasurement(Correction.LEFT, 1f, false), 8000).failure)
        val loss = GuidanceGovernor(0)
        loss.update(null, 0)
        assertEquals("tracking_loss", loss.update(null, 2000).failure)
        assertEquals("timeout", GuidanceGovernor(0).update(null, 30_000).failure)
    }

    @Test fun `small initial error still produces correction and gaps cannot complete`() {
        val governor = GuidanceGovernor(0)
        val smallError = GuidanceMeasurement(Correction.LEFT, .1f, false)
        governor.update(smallError, 0)
        assertEquals(Correction.LEFT, governor.update(smallError, 500).correction)
        val hold = GuidanceMeasurement(Correction.HOLD, 0f, true)
        val gap = GuidanceGovernor(0)
        gap.update(hold, 0)
        assertFalse(gap.update(hold, 1000).complete)
        val lost = GuidanceGovernor(0)
        lost.update(null, 0)
        assertEquals("tracking_loss", lost.update(hold, 3000).failure)
    }
}
