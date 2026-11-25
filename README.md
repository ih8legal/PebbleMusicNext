# Pebble Music Remote

Android companion app for a Pebble watch app that controls phone music playback (PebbleKit Android 2).

- Play/pause, previous, next from the watch
- Track title and artist shown on the watch (needs Notification access)
- Long-press Up/Down on the watch to step the phone's music volume; volume bar on the watch
- Light/dark theme setting sent to the watch

The watch app is written in C (CloudPebble). Its `package.json` must declare this app under
`pebble.companionApp.android` with package `com.example.pebblemusicremote`, and its UUID must
match `APP_UUID` in `Config.kt`.
