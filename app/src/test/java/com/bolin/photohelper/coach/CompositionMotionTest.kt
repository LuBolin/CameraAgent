package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.FaceObservation
import com.bolin.photohelper.capture.FrameObservation
import com.bolin.photohelper.capture.gravityRollDegrees
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

/** Independent pinhole projection: fixed world subjects, moving camera, no detector involved. */
class CompositionMotionTest {
    private data class Camera(
        val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0,
        val yaw: Double = 0.0, val pitch: Double = 0.0, val roll: Double = 0.0,
    )

    // World coordinates are right/down/forward; rotations are radians. Fixed focal length = 1.
    private fun observe(camera: Camera, group: Boolean = false): FrameObservation {
        val faces = (if (group) listOf(-.4, .4) else listOf(0.0)).mapIndexed { id, centre ->
            val halfWidth = if (group) .1 else .25
            val corners = listOf(-halfWidth, halfWidth).flatMap { dx ->
                listOf(-.2, .2).map { dy ->
                    val x = centre + dx - camera.x
                    val y = -.2 + dy - camera.y
                    val z = 2.0 - camera.z
                    val rx = cos(camera.yaw) * x - sin(camera.yaw) * z
                    val rz = sin(camera.yaw) * x + cos(camera.yaw) * z
                    val ry = cos(camera.pitch) * y - sin(camera.pitch) * rz
                    val depth = sin(camera.pitch) * y + cos(camera.pitch) * rz
                    val px = cos(camera.roll) * rx + sin(camera.roll) * ry
                    val py = -sin(camera.roll) * rx + cos(camera.roll) * ry
                    (.5 + px / depth).toFloat() to (.5 + py / depth).toFloat()
                }
            }
            FaceObservation(id, corners.minOf { it.first }, corners.minOf { it.second },
                corners.maxOf { it.first }, corners.maxOf { it.second })
        }
        return FrameObservation(1, 0, .5f, 0f, 0f, faces = faces,
            deviceRollDegrees = gravityRollDegrees((-9.8 * sin(camera.roll)).toFloat(),
                (9.8 * cos(camera.roll)).toFloat(), 0), sourceWidth = 1000, sourceHeight = 1000)
    }

    @Test fun `physical motions reduce error while opposite motions increase it`() {
        data class Case(val name: String, val start: Camera, val step: (Camera, Double) -> Camera,
            val expected: Correction, val level: Boolean = false)
        val cases = listOf(
            Case("walk closer", Camera(z = -1.5), { c, s -> c.copy(z = c.z + .1 * s) }, Correction.LARGER),
            Case("step back", Camera(z = .65), { c, s -> c.copy(z = c.z - .1 * s) }, Correction.SMALLER),
            Case("move right", Camera(x = -.35), { c, s -> c.copy(x = c.x + .025 * s) }, Correction.LEFT),
            Case("move left", Camera(x = .35), { c, s -> c.copy(x = c.x - .025 * s) }, Correction.RIGHT),
            Case("lower camera", Camera(y = -.35), { c, s -> c.copy(y = c.y + .025 * s) }, Correction.UP),
            Case("raise camera", Camera(y = .35), { c, s -> c.copy(y = c.y - .025 * s) }, Correction.DOWN),
            Case("aim right", Camera(yaw = -.12), { c, s -> c.copy(yaw = c.yaw + .01 * s) }, Correction.LEFT),
            Case("aim left", Camera(yaw = .12), { c, s -> c.copy(yaw = c.yaw - .01 * s) }, Correction.RIGHT),
            Case("tilt down", Camera(pitch = -.12), { c, s -> c.copy(pitch = c.pitch + .01 * s) }, Correction.UP),
            Case("tilt up", Camera(pitch = .12), { c, s -> c.copy(pitch = c.pitch - .01 * s) }, Correction.DOWN),
            Case("rotate counterclockwise", Camera(roll = .12), { c, s -> c.copy(roll = c.roll - .01 * s) }, Correction.LEVEL_LEFT, true),
            Case("rotate clockwise", Camera(roll = -.12), { c, s -> c.copy(roll = c.roll + .01 * s) }, Correction.LEVEL_RIGHT, true),
        )
        val report = mutableListOf("subjects,movement,prompt,error_before,error_after_step,error_opposite,completion_ms")
        for (group in listOf(false, true)) for (case in cases) {
            val initial = observe(case.start, group)
            val targets = if (case.level) listOf(VerificationTarget.Level())
                else compileComposition(CompositionIntent(), initial.faces).targets
            fun measure(camera: Camera) = measureGuidance(targets, observe(camera, group))!!
            val before = measure(case.start)
            assertEquals(case.name, case.expected, before.correction)
            val next = measure(case.step(case.start, 1.0))
            val opposite = measure(case.step(case.start, -1.0))
            assertTrue(case.name, next.error < before.error)
            assertTrue("opposite ${case.name}", opposite.error > before.error)
            val governor = GuidanceGovernor(0)
            var camera = case.start
            var completedAt: Long? = null
            for (time in 0L..7500L step 250) {
                val measured = measure(camera)
                val output = governor.update(measured, time)
                assertNull("${case.name}: $time", output.failure)
                if (output.complete) { assertTrue(measured.satisfied); completedAt = time; break }
                if (output.correction != Correction.HOLD && !measured.satisfied) {
                    assertEquals("must not reverse: ${case.name}", case.expected, output.correction)
                    val moved = case.step(camera, 1.0)
                    assertNotNull("membership survives ${case.name}",
                        matchMembers(observe(camera, group).faces, observe(moved, group).faces))
                    camera = moved
                }
            }
            assertNotNull("must settle: ${case.name}", completedAt)
            report += "${if (group) "group" else "single"},${case.name},${case.expected.phoneInstruction()},${before.error},${next.error},${opposite.error},$completedAt"
        }
        File("build/reports/composition-motion.csv").apply { parentFile?.mkdirs(); writeText(report.joinToString("\n")) }
    }

    @Test fun `perspective instruction improves with distance rather than a pan`() {
        val targets = listOf(VerificationTarget.StepBack(.29f))
        val start = Camera(z = .65)
        val before = measureGuidance(targets, observe(start))!!
        assertEquals("Move the phone farther away", before.correction.phoneInstruction())
        assertTrue(measureGuidance(targets, observe(start.copy(z = .55)))!!.error < before.error)
        assertTrue(measureGuidance(targets, observe(start.copy(z = .75)))!!.error > before.error)
        assertEquals(before.error, measureGuidance(targets, observe(start.copy(x = .05)))!!.error, .0001f)
        assertTrue(measureGuidance(targets, observe(Camera()))!!.satisfied)
    }

    @Test fun `lateral move and opposite yaw can cancel and must not complete`() {
        val initial = observe(Camera(x = -.35))
        val targets = compileComposition(CompositionIntent(), initial.faces).targets
        val before = measureGuidance(targets, initial)!!
        // Rightward translation helps, but sufficiently strong leftward yaw undoes it.
        val conflicting = measureGuidance(targets, observe(Camera(x = -.25, yaw = -.07)))!!
        assertTrue(conflicting.error > before.error)
        val governor = GuidanceGovernor(0)
        for (time in 0L..1000L step 250) assertFalse(governor.update(conflicting, time).complete)
    }

    @Test fun `move right and counterclockwise roll settle as sequential instructions`() {
        var camera = Camera(x = -.35, roll = .1)
        val targets = compileComposition(CompositionIntent(), observe(camera).faces).targets + VerificationTarget.Level()
        val governor = GuidanceGovernor(0)
        val shown = mutableSetOf<Correction>()
        var completed = false
        for (time in 0L..12000L step 250) {
            val measured = measureGuidance(targets, observe(camera))!!
            val output = governor.update(measured, time)
            assertNull(output.failure)
            if (output.complete) { assertTrue(measured.satisfied); completed = true; break }
            if (measured.satisfied) continue
            when (output.correction) {
                Correction.LEFT -> { camera = camera.copy(x = camera.x + .025); shown += output.correction }
                Correction.LEVEL_LEFT -> { camera = camera.copy(roll = camera.roll - .01); shown += output.correction }
                Correction.HOLD -> Unit
                else -> fail("Unexpected movement: ${output.correction}")
            }
        }
        assertTrue(completed)
        assertEquals(setOf(Correction.LEFT, Correction.LEVEL_LEFT), shown)
    }
}
