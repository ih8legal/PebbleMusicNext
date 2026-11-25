package com.example.pebblemusicremote

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import android.service.notification.NotificationListenerService

/**
 * Reads what is playing on the phone. Android only lets an app see other apps'
 * media sessions if it has "Notification access", which is why this is a
 * NotificationListenerService. Notifications themselves are not read.
 */
class MusicListenerService : NotificationListenerService() {

    private val TAG = "MusicListener"
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    // Keeps the watch's volume bar in step when the volume is changed on the phone.
    private var volumeReceiverRegistered = false
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (stream == AudioManager.STREAM_MUSIC) {
                WatchLink.sendVolume(context)
            }
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attach(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        Log.d(TAG, "Listener connected")
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager = manager
        val me = ComponentName(this, MusicListenerService::class.java)
        manager.addOnActiveSessionsChangedListener(sessionsListener, me)
        val sessions = manager.getActiveSessions(me)
        Log.d(TAG, "Active sessions: ${sessions.size}")
        attach(sessions)

        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(volumeReceiver, filter)
            }
            volumeReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Volume receiver not registered", e)
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        controller?.unregisterCallback(controllerCallback)
        controller = null
        if (volumeReceiverRegistered) {
            unregisterReceiver(volumeReceiver)
            volumeReceiverRegistered = false
        }
    }

    private fun attach(controllers: List<MediaController>) {
        controller?.unregisterCallback(controllerCallback)
        // Prefer the player that is actually playing; otherwise the most recent one.
        controller = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        controller?.registerCallback(controllerCallback)
        refresh()
    }

    /** Human-readable name of the app that owns the media session. */
    private fun appLabel(packageName: String?): String {
        if (packageName.isNullOrEmpty()) return ""
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            Log.w(TAG, "No label for $packageName", e)
            packageName
        }
    }

    private fun refresh() {
        val c = controller
        Log.d(TAG, "refresh: pkg=${c?.packageName} state=${c?.playbackState?.state} title=${c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
        val state = c?.playbackState?.state
        val inactive = c == null || state == null ||
            state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED

        val metadata = c?.metadata
        val title = if (inactive) "" else metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = if (inactive) "" else
            (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()

        val source = if (inactive) "" else appLabel(c?.packageName)

        if (title == NowPlaying.title && artist == NowPlaying.artist && source == NowPlaying.source) {
            return
        }
        NowPlaying.title = title
        NowPlaying.artist = artist
        NowPlaying.source = source
        NowPlaying.onChanged?.invoke()
        WatchLink.sendNowPlaying(this)
    }
}
