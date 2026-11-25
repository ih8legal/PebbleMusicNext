package com.example.pebblemusicremote

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

/**
 * MusicNext companion app. Two sections, switched from a bar at the bottom:
 * Main (now playing and watch app settings) and Settings (permissions, about,
 * and the debug tools, which are tucked away in a collapsed panel).
 */
class MainActivity : Activity() {

    private enum class Page { MAIN, SETTINGS }

    private var page = Page.MAIN
    private var debugOpen = false

    private lateinit var pageMain: View
    private lateinit var pageSettings: View
    private lateinit var accessBanner: View
    private lateinit var nowSource: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var themeDark: TextView
    private lateinit var themeLight: TextView
    private lateinit var accessStatus: TextView
    private lateinit var debugPanel: View
    private lateinit var debugChevron: ImageView
    private lateinit var navMainIcon: ImageView
    private lateinit var navMainLabel: TextView
    private lateinit var navSettingsIcon: ImageView
    private lateinit var navSettingsLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setUpSystemBars()

        pageMain = findViewById(R.id.page_main)
        pageSettings = findViewById(R.id.page_settings)
        accessBanner = findViewById(R.id.access_banner)
        nowSource = findViewById(R.id.now_source)
        nowTitle = findViewById(R.id.now_title)
        nowArtist = findViewById(R.id.now_artist)
        themeDark = findViewById(R.id.theme_dark)
        themeLight = findViewById(R.id.theme_light)
        accessStatus = findViewById(R.id.access_status)
        debugPanel = findViewById(R.id.debug_panel)
        debugChevron = findViewById(R.id.debug_chevron)
        navMainIcon = findViewById(R.id.nav_main_icon)
        navMainLabel = findViewById(R.id.nav_main_label)
        navSettingsIcon = findViewById(R.id.nav_settings_icon)
        navSettingsLabel = findViewById(R.id.nav_settings_label)

        findViewById<View>(R.id.nav_main).setOnClickListener { showPage(Page.MAIN) }
        findViewById<View>(R.id.nav_settings).setOnClickListener { showPage(Page.SETTINGS) }

        themeDark.setOnClickListener { setWatchTheme(Config.THEME_DARK) }
        themeLight.setOnClickListener { setWatchTheme(Config.THEME_LIGHT) }

        findViewById<Button>(R.id.btn_grant_access).setOnClickListener { openAccessSettings() }
        findViewById<Button>(R.id.btn_manage_access).setOnClickListener { openAccessSettings() }

        findViewById<View>(R.id.debug_header).setOnClickListener {
            debugOpen = !debugOpen
            applyDebugState()
        }
        findViewById<Button>(R.id.btn_debug_sync).setOnClickListener { WatchLink.sendAll(this) }
        findViewById<Button>(R.id.btn_debug_play).setOnClickListener {
            PebbleListenerService.sendMediaKey(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
        findViewById<TextView>(R.id.about_version).text = getString(R.string.version_format, version)

        savedInstanceState?.let {
            page = if (it.getBoolean("settings")) Page.SETTINGS else Page.MAIN
            debugOpen = it.getBoolean("debug")
        }
        applyDebugState()
        showPage(page)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("settings", page == Page.SETTINGS)
        outState.putBoolean("debug", debugOpen)
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

    // ---------- Layout and navigation ----------

    /** Keeps content clear of the status bar and gesture bar, and matches icon colours. */
    private fun setUpSystemBars() {
        val root = findViewById<View>(R.id.root)
        val navBar = findViewById<View>(R.id.nav_bar)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars: android.graphics.Insets? = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            } else null
            @Suppress("DEPRECATION")
            val top = bars?.top ?: insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = bars?.bottom ?: insets.systemWindowInsetBottom
            @Suppress("DEPRECATION")
            val left = bars?.left ?: insets.systemWindowInsetLeft
            @Suppress("DEPRECATION")
            val right = bars?.right ?: insets.systemWindowInsetRight
            root.setPadding(left, top, right, 0)
            navBar.setPadding(0, 0, 0, bottom)
            insets
        }

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (night) 0 else mask, mask)
        }
    }

    private fun showPage(target: Page) {
        page = target
        pageMain.visibility = if (target == Page.MAIN) View.VISIBLE else View.GONE
        pageSettings.visibility = if (target == Page.SETTINGS) View.VISIBLE else View.GONE

        val active = getColor(R.color.accent)
        val idle = getColor(R.color.text_secondary)
        val mainColor = if (target == Page.MAIN) active else idle
        val settingsColor = if (target == Page.SETTINGS) active else idle
        navMainIcon.setColorFilter(mainColor)
        navMainLabel.setTextColor(mainColor)
        navSettingsIcon.setColorFilter(settingsColor)
        navSettingsLabel.setTextColor(settingsColor)
    }

    private fun applyDebugState() {
        debugPanel.visibility = if (debugOpen) View.VISIBLE else View.GONE
        debugChevron.setColorFilter(getColor(R.color.text_secondary))
        debugChevron.rotation = if (debugOpen) 90f else 0f
    }

    // ---------- Actions ----------

    private fun setWatchTheme(theme: Int) {
        Prefs.setTheme(this, theme)
        WatchLink.sendTheme(this)
        refresh()
    }

    private fun openAccessSettings() {
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
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    // ---------- Showing state ----------

    private fun refresh() {
        val title = NowPlaying.title
        val artist = NowPlaying.artist
        val source = NowPlaying.source

        nowTitle.text = if (title.isEmpty()) getString(R.string.nothing_playing) else title
        nowArtist.text = artist
        nowArtist.visibility = if (title.isEmpty() || artist.isEmpty()) View.GONE else View.VISIBLE
        nowSource.text = source
        nowSource.visibility = if (title.isEmpty() || source.isEmpty()) View.GONE else View.VISIBLE

        val granted = hasNotificationAccess()
        accessBanner.visibility = if (granted) View.GONE else View.VISIBLE
        accessStatus.text = getString(if (granted) R.string.access_granted else R.string.access_not_granted)

        val light = Prefs.getTheme(this) == Config.THEME_LIGHT
        styleSegment(themeDark, !light)
        styleSegment(themeLight, light)
    }

    private fun styleSegment(view: TextView, selected: Boolean) {
        view.setBackgroundResource(if (selected) R.drawable.segment_selected else 0)
        view.setTextColor(getColor(if (selected) R.color.on_accent else R.color.text_secondary))
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
