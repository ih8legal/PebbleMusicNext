# Pebble Music Remote

Android companion app for a Pebble watch app that controls phone music playback (PebbleKit Android 2).

- Play/pause, previous, next from the watch
- Track title and artist shown on the watch (needs Notification access)
- Long-press Up/Down on the watch to step the phone's music volume; volume bar on the watch
- Light/dark theme setting sent to the watch

The watch app is written in C (CloudPebble). Its `package.json` must declare this app under
`pebble.companionApp.android` with package `com.example.pebblemusicremote`, and its UUID must
match `APP_UUID` in `Config.kt`.

## Layout

- `app/` – Android companion app (`./gradlew assembleDebug`)
- `watch/` – Pebble watch app in C (`pebble build`)
- `.github/workflows/` – CI builds both; the APK and `.pbw` are uploaded as artifacts on every push.
  Push a tag like `v1.0.0` to attach the `.pbw` to a GitHub release.

## MusicNext app

Two sections, switched from the bar at the bottom:

- **Main** – what's playing (and in which app), and the watch app's theme.
- **Settings** – notification access, about, and a collapsed "Debug tools" panel.
