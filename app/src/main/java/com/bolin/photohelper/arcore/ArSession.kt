package com.bolin.photohelper.arcore

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ArAvailability { SUPPORTED, UNSUPPORTED, CHECKING }

/**
 * Lifecycle-aware wrapper around an ARCore [Session]. The session is created
 * when the lifecycle reaches RESUMED and torn down on PAUSED, matching the
 * camera lifecycle so the two never fight over the sensor.
 *
 * When ARCore is unavailable the session stays null and every feature that
 * depends on it degrades gracefully — auto-capture simply requires a manual tap.
 */
class ArSessionManager(private val context: Context) : DefaultLifecycleObserver {

    private var session: Session? = null
    private val _availability = MutableStateFlow(ArAvailability.CHECKING)
    val availability: StateFlow<ArAvailability> = _availability.asStateFlow()

    private val _latestFrame = MutableStateFlow<Frame?>(null)
    val latestFrame: StateFlow<Frame?> = _latestFrame.asStateFlow()

    fun checkAvailability() {
        val result = try {
            ArCoreApk.getInstance().checkAvailability(context)
        } catch (_: Exception) {
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
        }
        _availability.value = when {
            result.isSupported -> ArAvailability.SUPPORTED
            result.isTransient -> ArAvailability.CHECKING
            else -> ArAvailability.UNSUPPORTED
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (_availability.value != ArAvailability.SUPPORTED) return
        try {
            val s = session ?: Session(context).also { newSession ->
                newSession.configure(
                    Config(newSession).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                    },
                )
            }
            s.resume()
            session = s
        } catch (_: UnavailableException) {
            _availability.value = ArAvailability.UNSUPPORTED
            session = null
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        session?.pause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        session?.close()
        session = null
        _latestFrame.value = null
    }

    /**
     * Called from the render/update loop. Returns the latest [Frame] if the
     * session is active, or null. The frame is also published to [latestFrame].
     */
    fun update(): Frame? {
        val s = session ?: return null
        return try {
            s.update().also { _latestFrame.value = it }
        } catch (_: Exception) {
            null
        }
    }
}
