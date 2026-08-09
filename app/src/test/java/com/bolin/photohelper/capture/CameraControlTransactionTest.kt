package com.bolin.photohelper.capture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraControlTransactionTest {
    @Test
    fun `batch structure rejects empty oversized and duplicate axes before controls run`() {
        assertNotNull(validateAdjustmentBatchStructure(emptyList()))
        assertNotNull(
            validateAdjustmentBatchStructure(
                listOf(
                    CameraAdjustment.ExposureCompensation(1),
                    CameraAdjustment.ZoomRatio(1.25f),
                    CameraAdjustment.WhiteBalance(WhiteBalancePreset.WARMER),
                    CameraAdjustment.ZoomRatio(1.5f),
                ),
            ),
        )
        assertNotNull(
            validateAdjustmentBatchStructure(
                listOf(
                    CameraAdjustment.ExposureCompensation(1),
                    CameraAdjustment.ExposureCompensation(2),
                ),
            ),
        )
        assertNull(
            validateAdjustmentBatchStructure(
                listOf(
                    CameraAdjustment.ExposureCompensation(1),
                    CameraAdjustment.ZoomRatio(1.25f),
                    CameraAdjustment.WhiteBalance(WhiteBalancePreset.WARMER),
                ),
            ),
        )
    }

    @Test
    fun `second control failure rolls back once and reports no partial success`() = runTest {
        val applied = mutableListOf<Int>()
        val rollbackResults = mutableListOf<Boolean>()

        val result = runCameraControlTransaction(
            commands = listOf(1, 2, 3),
            isCurrent = { true },
            applyCommand = { command ->
                applied += command
                if (command == 2) error("second control failed")
            },
            rollback = { true },
            finishRollback = rollbackResults::add,
            commit = {},
        )

        assertEquals(listOf(1, 2), applied)
        assertEquals(listOf(true), rollbackResults)
        assertTrue(result is ApplyResult.Failed)
        assertEquals("second control failed", (result as ApplyResult.Failed).message)
    }

    @Test
    fun `successful transaction commits only after every control`() = runTest {
        val events = mutableListOf<String>()

        val result = runCameraControlTransaction(
            commands = listOf(1, 2, 3),
            isCurrent = { true },
            applyCommand = { events += "apply-$it" },
            rollback = {
                events += "rollback"
                true
            },
            finishRollback = { events += "finish-rollback" },
            commit = { events += "commit" },
        )

        assertEquals(ApplyResult.Applied, result)
        assertEquals(listOf("apply-1", "apply-2", "apply-3", "commit"), events)
    }

    @Test
    fun `cancellation rolls back before it is rethrown`() = runTest {
        val events = mutableListOf<String>()
        var cancelled = false

        try {
            runCameraControlTransaction(
                commands = listOf(1),
                isCurrent = { true },
                applyCommand = {
                    events += "apply"
                    throw CancellationException("backgrounded")
                },
                rollback = {
                    events += "rollback"
                    true
                },
                finishRollback = { events += "finish-$it" },
                commit = { events += "commit" },
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(listOf("apply", "rollback", "finish-true"), events)
    }

    @Test
    fun `cancellation requested after the final control rolls back before commit`() = runTest {
        val events = mutableListOf<String>()

        val transaction = async {
            runCameraControlTransaction(
                commands = listOf(1),
                isCurrent = { true },
                applyCommand = {
                    events += "apply"
                    currentCoroutineContext()[Job]?.cancel(CancellationException("backgrounded"))
                },
                rollback = {
                    events += "rollback"
                    true
                },
                finishRollback = { events += "finish-$it" },
                commit = { events += "commit" },
            )
        }

        runCatching { transaction.await() }

        assertTrue(transaction.isCancelled)
        assertEquals(listOf("apply", "rollback", "finish-true"), events)
    }

    @Test
    fun `rollback failure is never reported as an ordinary adjustment failure`() = runTest {
        val result = runCameraControlTransaction(
            commands = listOf(1, 2),
            isCurrent = { true },
            applyCommand = { command -> if (command == 2) error("driver rejected control") },
            rollback = { false },
            finishRollback = {},
            commit = {},
        )

        assertEquals(
            "Camera controls could not be restored. Retry the camera before shooting.",
            (result as ApplyResult.Failed).message,
        )
    }

    @Test
    fun `session drift stops later controls and requests rollback`() = runTest {
        val applied = mutableListOf<Int>()
        var current = true
        var rollbacks = 0

        val result = runCameraControlTransaction(
            commands = listOf(1, 2, 3),
            isCurrent = { current },
            applyCommand = { command ->
                applied += command
                current = false
            },
            rollback = {
                rollbacks++
                true
            },
            finishRollback = {},
            commit = {},
        )

        assertEquals(listOf(1), applied)
        assertEquals(1, rollbacks)
        assertEquals(
            "Camera session changed. Check the shot and try again.",
            (result as ApplyResult.Failed).message,
        )
    }
}
