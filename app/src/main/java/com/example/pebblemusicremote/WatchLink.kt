package com.example.pebblemusicremote

import android.content.Context
import android.util.Log
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Sends messages from the phone to the watch app. */
object WatchLink {
    private const val TAG = "WatchLink"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun trim(text: String): String =
        if (text.length <= Config.MAX_TEXT) text else text.take(Config.MAX_TEXT - 1) + "…"

    fun sendTheme(context: Context) {
        val theme = Prefs.getTheme(context)
        send(context, mapOf(Config.KEY_THEME to PebbleDictionaryItem.UInt8(theme.toUByte())))
    }

    /** Music volume as a percentage (0 to 100), shown as the bar on the watch. */
    fun sendVolume(context: Context) {
        send(
            context,
            mapOf(Config.KEY_VOLUME to PebbleDictionaryItem.UInt8(VolumeControl.percent(context).toUByte())),
        )
    }

    fun sendNowPlaying(context: Context) {
        send(
            context,
            mapOf(
                Config.KEY_TITLE to PebbleDictionaryItem.Text(trim(NowPlaying.title)),
                Config.KEY_ARTIST to PebbleDictionaryItem.Text(trim(NowPlaying.artist)),
                Config.KEY_SOURCE to PebbleDictionaryItem.Text(trim(NowPlaying.source)),
            ),
        )
    }

    /** What the watch needs right after it opens: theme and the current track. */
    fun sendAll(context: Context) {
        send(
            context,
            mapOf(
                Config.KEY_THEME to PebbleDictionaryItem.UInt8(Prefs.getTheme(context).toUByte()),
                Config.KEY_TITLE to PebbleDictionaryItem.Text(trim(NowPlaying.title)),
                Config.KEY_ARTIST to PebbleDictionaryItem.Text(trim(NowPlaying.artist)),
                Config.KEY_SOURCE to PebbleDictionaryItem.Text(trim(NowPlaying.source)),
                Config.KEY_VOLUME to PebbleDictionaryItem.UInt8(VolumeControl.percent(context).toUByte()),
            ),
        )
    }

    private fun send(context: Context, items: Map<UInt, PebbleDictionaryItem>) {
        val appContext = context.applicationContext
        scope.launch {
            val sender = DefaultPebbleSender(appContext)
            try {
                val result = sender.sendDataToPebble(Config.APP_UUID, items)
                Log.d(TAG, "sendDataToPebble result: $result")
            } catch (e: Exception) {
                Log.w(TAG, "Could not send to watch", e)
            } finally {
                sender.close()
            }
        }
    }
}
