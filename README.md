# ReminderLists

A local, offline-first Android app for lists, reminders and notes — all in a single SQLite database, with no cloud, accounts or internet.

## Why

These three related things usually live in separate apps, half of which sync your data to the cloud. ReminderLists keeps everything on the device in one place and sends nothing anywhere: your data never leaves the phone, and privacy is handled by a PIN gate on individual lists and notes.

## Three modules (bottom-bar tabs)

- **Lists** — checklists with folders (one level), per-item photos, an autocomplete dictionary, PIN protection, a large-font mode, sharing and zip backup. A whole line can be added at once: "Add from a line" splits `bread, milk 2/l` on commas, semicolons or line breaks into items and amounts, and shows what it parsed before anything is added.
- **Reminders** — time-triggered entries, sorted automatically into type folders (Once / Daily / Periods / Monthly / Yearly / Intervals) from the form fields rather than by hand. Intervals repeat every N minutes/hours/days/weeks/months from a start moment. Tags, priority, full-screen alert with Postpone, repeats, and a Timer mode — a "+N min/hours" countdown that fires once and is stored nowhere. Engine is `AlarmManager` (exact alarms), handling missed fires and timezone/DST changes.
- **Notes** — long-lived note cards that never fire; built like Lists, with reminder-style fields and PIN.

## Settings

Theme and font size, the interface language (English / Russian / Ukrainian), the dictionary,
a master switch for reminders, keeping the screen on in large-font mode, a log file, the three time
presets, the default alert sound with its duration and level, the default PIN, and backups — made
by hand or once a calendar day by the shared `../backups` module, the newest three kept, into
`Documents/ReminderLists/`. The last row is the switch for the update check.

## About and updates

The **i** button in the Settings tab's top bar opens the shared About dialog: what the app is, the
version, the build date, the GitHub page, the mailbox, and a button that asks for a newer build
there and then. Beyond that the app
asks GitHub for the newest release of this repository at launch, at most every six hours, and
offers the split built for the device's own ABI out of the `latest.json` that `23-ToUpdate.sh`
uploads beside the APKs. That check is the one thing the app sends over the network, and the
Settings switch turns it off at launch; the About button stays.

## Shared modules

Three folders beside this one are compiled into the app from source rather than pulled in as
modules or AARs — one copy of each serves every project here (the `sourceSets` block in
`app/build.gradle.kts` wires them):

- `../updater` — the update check, its configuration and its dialogs
- `../about` — the About dialog, drawn in code so the resource shrinker cannot drop its strings
- `../backups` — when a daily copy is made, what it is named, where it lands and how many survive;
  this app only fills the archive

## Tech

Kotlin + Jetpack Compose, Room (SQLite), single-Activity. Pure AOSP/Jetpack only — no Google Play Services, Firebase or FCM. Minimum API 33 (Android 13+). Backup is local, to `Documents/ReminderLists/`.

## Build

Release-only workflow, driven by the numbered scripts at the repository root:

| Script | What it does |
|---|---|
| `./00-MakeAll.sh` | release, both installs and the `OUT/` link in one run |
| `./10-MakeRelease.sh` | signed release with ABI splits, raising the build number |
| `./11-EmulRELEASE.sh`, `./12-SamsRELEASE.sh` | install the release on the emulator / the phone |
| `./02-DebugWiFiConn.sh` | connect `adb` to the phone over Wi-Fi |
| `./05-Lint.sh`, `./06-Test.sh` | Android Lint and the JVM unit tests, findings as plain text |
| `./19-LinkOut.sh` | hard-link this build's APKs into `OUT/` |
| `./20-MakeTag.sh`, `./21-PushTag.sh` | the release tag and its push |
| `./22-RelUpload.sh` | the GitHub Release for that tag, with the APKs |
| `./23-ToUpdate.sh` | `latest.json` for that release, so the in-app updater can find it |
| `./99-CopyToAPKX.sh` | the arm64 APK under an `.apkx` name, for messengers that mangle `.apk` |

Release signing reads `~/.my-safe/key.properties`; the version and the build number live in
`build_number.txt`, and `10-MakeRelease.sh` raises the line itself when the changelog has an `N`
entry waiting under `Unreleased`. The full spec lives in `ReminderLists-TZ.txt`.
