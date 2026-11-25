package com.example.pebblemusicremote

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Small status and settings screen for the companion app. */
class MainActivity : Activity() {

    private lateinit var accessStatus: TextView
    private lateinit var nowPlayingView: TextView
    private lateinit var themeStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        fun label(text: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad / 2, 0, pad / 4)
        }

        fun button(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }

        root.addView(label("Pebble Music Remote", 24f, true))

        root.addView(label("Now playing", 14f))
        nowPlayingView = label("", 18f, true)
        root.addView(nowPlayingView)

        root.addView(label("Notification access", 14f))
        accessStatus = label("")
        root.addView(accessStatus)
        root.addView(button("Open notification access settings") {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(this@MainActivity, MusicListenerService::class.java).flattenToString(),
                    )
                }
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            startActivity(intent)
        })

        root.addView(label("Watch theme", 14f))
        themeStatus = label("")
        root.addView(themeStatus)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        row.addView(button("Dark") { setAppTheme(Config.THEME_DARK) })
        row.addView(button("Light") { setAppTheme(Config.THEME_LIGHT) })
        root.addView(row)

        root.addView(label("Testing", 14f))
        root.addView(button("Send theme and track to watch") {
            WatchLink.sendAll(this)
        })
        root.addView(button("Test: play / pause") {
            PebbleListenerService.sendMediaKey(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        NowPlaying.onChanged = { runOnUiThread { refresh() } }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        NowPlaying.onChanged = null
    }

    private fun setAppTheme(theme: Int) {
        Prefs.setTheme(this, theme)
        WatchLink.sendTheme(this)
        refresh()
    }

    private fun refresh() {
        val title = NowPlaying.title
        val artist = NowPlaying.artist
        nowPlayingView.text = if (title.isEmpty()) "Nothing playing" else "$title\n$artist"

        accessStatus.text = if (hasNotificationAccess()) "Granted" else
            "Not granted. Track and artist can't be read until you enable it for this app."

        themeStatus.text = if (Prefs.getTheme(this) == Config.THEME_LIGHT) "Light" else "Dark"
    }

    private fun hasNotificationAccess(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        val me = ComponentName(this, MusicListenerService::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            nm.isNotificationListenerAccessGranted(me)
        } else {
            val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?: return false
            enabled.split(":").any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
