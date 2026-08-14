package com.bolin.photohelper.capture

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class WhiteBalanceModesTest {
    @Test
    fun `native white balance modes form three bounded steps per direction`() {
        val allModes = setOf(
            CaptureRequest.CONTROL_AWB_MODE_AUTO,
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
            CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT,
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            CaptureRequest.CONTROL_AWB_MODE_SHADE,
        )

        assertEquals(CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, awbModeForLevel(allModes, -1))
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT, awbModeForLevel(allModes, -2))
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, awbModeForLevel(allModes, -3))
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, awbModeForLevel(allModes, 1))
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, awbModeForLevel(allModes, 2))
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_SHADE, awbModeForLevel(allModes, 3))
        assertEquals((-3..3).toSet(), whiteBalanceLevelsForModes(allModes))
    }

    @Test
    fun `only presets backed by native AWB modes are advertised`() {
        assertEquals(
            WhiteBalancePreset.entries.toSet(),
            whiteBalancePresetsForModes(
                setOf(
                    CaptureRequest.CONTROL_AWB_MODE_AUTO,
                    CaptureRequest.CONTROL_AWB_MODE_SHADE,
                    CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                ),
            ),
        )
        assertEquals(
            setOf(WhiteBalancePreset.AUTO, WhiteBalancePreset.WARMER),
            whiteBalancePresetsForModes(
                setOf(
                    CaptureRequest.CONTROL_AWB_MODE_AUTO,
                    CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
                ),
            ),
        )
        assertEquals(emptySet<WhiteBalancePreset>(), whiteBalancePresetsForModes(emptySet()))
    }
}
