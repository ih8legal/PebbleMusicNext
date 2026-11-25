package com.example.pebblemusicremote

import android.content.Context
import android.media.AudioManager

/** Reads and changes the phone's music volume. */
object VolumeControl {
    private fun audio(context: Context) =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Current music volume as a percentage from 0 to 100. */
    fun percent(context: Context): Int {
        val am = audio(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current * 100 + max / 2) / max
    }

    /** One step louder or quieter, without showing Android's volume panel. */
    fun step(context: Context, up: Boolean) {
        audio(context).adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
    }
}
