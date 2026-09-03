package com.bolin.photohelper.capture

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

enum class AudioCue { SHUTTER, CHIME }

interface AudioCuePlayer {
    fun play(cue: AudioCue)
    fun release()
}

/**
 * Low-latency audio feedback using [SoundPool]. Bundled sounds in `res/raw/`.
 * Falls back silently when assets are missing or playback fails.
 */
class SoundPoolCuePlayer(context: Context) : AudioCuePlayer {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val shutterSoundId: Int
    private val chimeSoundId: Int

    init {
        val res = context.resources
        val pkg = context.packageName
        shutterSoundId = loadRawOrZero(res, pkg, "cue_shutter")
        chimeSoundId = loadRawOrZero(res, pkg, "cue_chime")
    }

    override fun play(cue: AudioCue) {
        val id = when (cue) {
            AudioCue.SHUTTER -> shutterSoundId
            AudioCue.CHIME -> chimeSoundId
        }
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    override fun release() = pool.release()

    private fun loadRawOrZero(res: android.content.res.Resources, pkg: String, name: String): Int {
        val resId = res.getIdentifier(name, "raw", pkg)
        return if (resId != 0) pool.load(res.openRawResourceFd(resId), 1) else 0
    }
}
