package com.bolin.photohelper.voice

import android.Manifest
import androidx.test.filters.RequiresDevice
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class PcmCaptureInstrumentedTest {
    @get:Rule
    val microphonePermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @RequiresDevice
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun microphoneCaptureCanFinishAndStartAgainWithoutLeakingTheRecorder() = runBlocking {
        val capture = PcmCapture()
        try {
            repeat(2) {
                val ready = CompletableDeferred<Unit>()
                val pending = async(Dispatchers.Default) {
                    capture.capture { ready.complete(Unit) }
                }
                withTimeout(5_000) { ready.await() }
                delay(100)
                capture.finish()
                when (val result = withTimeout(5_000) { pending.await() }) {
                    is PcmCaptureResult.Ready -> result.pcm.bytes.fill(0)
                    PcmCaptureResult.NoSpeech -> Unit
                    PcmCaptureResult.Failed -> fail("Microphone capture failed")
                    PcmCaptureResult.Unsupported -> fail("Microphone capture is unsupported")
                }
            }
        } finally {
            capture.close()
        }
    }
}
