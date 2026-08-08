package com.bolin.photohelper.capture

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class WhiteBalanceModesTest {
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
