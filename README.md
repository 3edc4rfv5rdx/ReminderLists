# ReminderLists

A local, offline-first Android app for lists, reminders and notes — all in a single SQLite database, with no cloud, accounts or internet.

## Why

These three related things usually live in separate apps, half of which sync your data to the cloud. ReminderLists keeps everything on the device in one place and sends nothing anywhere: your data never leaves the phone, and privacy is handled by a PIN gate on individual lists and notes.

## Three modules (bottom-bar tabs)

- **Lists** — checklists with folders (one level), per-item photos, an autocomplete dictionary, PIN protection, a large-font mode, sharing and zip backup.
- **Reminders** — time-triggered entries, sorted automatically into type folders (Once / Daily / Periods / Monthly / Yearly) from the form fields rather than by hand. Tags, priority, full-screen alert with Postpone, repeats. Engine is `AlarmManager` (exact alarms), handling missed fires and timezone/DST changes.
- **Notes** — long-lived note cards that never fire; built like Lists, with reminder-style fields and PIN.

## Tech

Kotlin + Jetpack Compose, Room (SQLite), single-Activity. Pure AOSP/Jetpack only — no Google Play Services, Firebase or FCM. Minimum API 33 (Android 13+). Backup is local, to `Documents/ReminderLists/`.

## Build

From the command line, no Android Studio:

```
./gradlew assembleDebug     # or assembleRelease
```

Release/install scripts: `10-MakeRelease.sh`, `11-EmulRELEASE.sh`, `12-SamsRELEASE.sh`. The full spec lives in `ReminderLists-TZ.txt`.
