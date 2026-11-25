package com.example.pebblemusicremote

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID

/** Receives button presses from the watch and turns them into media key events. */
class PebbleListenerService : BasePebbleListenerService() {

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: WatchIdentifier,
    ): ReceiveResult {
        if (watchappUUID != Config.APP_UUID) {
            Log.w(TAG, "Ignoring message from unexpected watchapp $watchappUUID")
            return ReceiveResult.Ack
        }

        val item = data[Config.KEY_COMMAND]
        val command = item?.asInt()
        Log.d(TAG, "Command from watch: $command")

        when (command) {
            Config.CMD_PREV -> sendMediaKey(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Config.CMD_PLAY_PAUSE -> sendMediaKey(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Config.CMD_NEXT -> sendMediaKey(this, KeyEvent.KEYCODE_MEDIA_NEXT)
            Config.CMD_SYNC -> WatchLink.sendAll(this)
            Config.CMD_VOL_UP -> {
                VolumeControl.step(this, up = true)
                WatchLink.sendVolume(this)
            }
            Config.CMD_VOL_DOWN -> {
                VolumeControl.step(this, up = false)
                WatchLink.sendVolume(this)
            }
            else -> Log.w(TAG, "Unknown or missing command: $item")
        }
        return ReceiveResult.Ack
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        if (watchappUUID == Config.APP_UUID) {
            WatchLink.sendAll(this)
        }
    }

    override fun onAppClosed(watchappUUID: UUID, watch: WatchIdentifier) {}

    /**
     * The Pebble app delivers small numbers as different integer types (the log
     * showed UInt32 even though the watch sends one byte), so accept any of them.
     */
    private fun PebbleDictionaryItem.asInt(): Int? = when (this) {
        is PebbleDictionaryItem.UInt8 -> value.toInt()
        is PebbleDictionaryItem.UInt32 -> value.toInt()
        // Other integer types: read the number out of the item's text form,
        // e.g. "UInt16(value=3)".
        else -> Regex("""[A-Za-z0-9]+\(value=(-?\d+)\)""").matchEntire(toString())
            ?.groupValues?.get(1)?.toIntOrNull()
    }

    companion object {
        private const val TAG = "PebbleListener"

        /** Acts like a headset button: sent to whichever player is currently active. */
        fun sendMediaKey(context: Context, keyCode: Int) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }
}
