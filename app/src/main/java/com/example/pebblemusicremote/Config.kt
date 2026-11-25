package com.example.pebblemusicremote

import android.content.Context
import java.util.UUID

object Config {
    // TODO: replace with your watch app's UUID (CloudPebble: Settings > UUID).
    // The watch app and this app only talk to each other if these match.
    val APP_UUID: UUID = UUID.fromString("55299c65-7599-4d66-9887-8394c435cfef")

    // AppMessage keys. These must match the keys in the watch project.
    const val KEY_COMMAND = 0u
    const val KEY_THEME = 1u
    const val KEY_ARTIST = 2u
    const val KEY_TITLE = 3u
    const val KEY_VOLUME = 4u
    const val KEY_SOURCE = 5u

    // Values of COMMAND sent by the watch.
    const val CMD_PREV = 1
    const val CMD_PLAY_PAUSE = 2
    const val CMD_NEXT = 3
    const val CMD_SYNC = 4
    const val CMD_VOL_UP = 5
    const val CMD_VOL_DOWN = 6

    const val THEME_DARK = 0
    const val THEME_LIGHT = 1

    // Pebble text values are kept short; the watch buffer is 128 bytes.
    const val MAX_TEXT = 100
}

object Prefs {
    private const val FILE = "prefs"
    private const val KEY_THEME = "theme"

    fun getTheme(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, Config.THEME_DARK)

    fun setTheme(context: Context, theme: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME, theme).apply()
    }
}

/** The track currently playing on the phone, shared between the services and the UI. */
object NowPlaying {
    @Volatile var title: String = ""
    @Volatile var artist: String = ""
    @Volatile var source: String = ""
    @Volatile var onChanged: (() -> Unit)? = null
}
