# Source audit findings — 2026-09-04 (tofix2)

Each item is a self-contained prompt for an LLM. Verify against current code before fixing
(file paths and line references are from the audit date). Fix one item per commit, with a
CHANGELOG.md entry in the same commit.

---

## 1. MEDIUM — A restore can run while the daily backup is still reading the data, producing a corrupt archive

Found while wiring the shared daily-backup module (`PRJ/backups`) in; deliberately left
unfixed at the time, because the fix belongs to this app and not to the module.

`MainActivity.onCreate` calls `Backups.checkOnStart(this, BackupManager.backupsConfig(this))`
(`app/src/main/java/com/reminderlists/MainActivity.kt`). When the day has no copy yet, that
starts a worker thread which calls `BackupManager.writeArchive(context, out)` — it
checkpoints the WAL and then reads `reminderlists.db`, `photos/`, `sounds/` and `logs/`
into a zip. Building the archive takes as long as the photo folder is big.

`BackupManager.restore(context, src)`
(`app/src/main/java/com/reminderlists/data/backup/BackupManager.kt`) has no interlock with
it. After validating the staged copy it calls `AppDatabase.closeInstance()`, copies the new
`.db` over the live one, deletes the `-wal`/`-shm` sidecars and then `replaceTree()`s
`photos/` and `sounds/` — `replaceTree` starts with `to.deleteRecursively()`.

Failure scenario: the user launches the app on a day with no backup yet, goes straight to
Settings → Restore and confirms while the backup worker is still zipping. The worker is
mid-`putTree(photos)` when `deleteRecursively()` removes the files under it, or mid-`putFile`
on the database while it is being overwritten. The zip either throws (the copy fails, is
discarded, retried in an hour — harmless) or completes over a half-replaced data set and is
published as a valid-looking backup of nothing coherent. The restore itself still succeeds:
this costs an archive, never the live data. The window is the few seconds of a launch that
happens to owe a backup, so it is rare, not impossible.

Task: serialize the two. The straightforward version is a single app-wide gate that both
`writeArchive` and `restore` take — BikeTracker solves the same problem with
`DatabaseMaintenance.withMaintenance(operation)`
(`PRJ/BikeTracker/app/src/main/java/xx/biketracker/data/DatabaseMaintenance.kt`), which is
worth reading before inventing another. A restore must not be silently dropped because a
backup holds the gate: let it wait, or refuse with a message, but never return as if it had
run.

Constraints not to break:
- The daily check must stay silent and must not block the UI thread — it runs on the
  module's own worker (`PRJ/backups/android/src/dev/backups/Backups.kt`), which already
  serializes its own writes with a semaphore. Do not move backup work onto the main thread
  to make the locking simpler.
- `Backups.runNow` (the Create backup button) goes through the same `writeArchive`, so
  whatever gate is added covers the button too, and must not deadlock when the button is
  pressed while the daily copy is running: the module's semaphore already blocks there, and
  a second lock taken in the opposite order would hang the settings screen.
- Restore ends by restarting the app; do not add a gate that outlives the process in a
  file, or a crash mid-restore will lock the app out of its own data.

Tests: a unit or instrumented test that starts `writeArchive` on one thread and `restore` on
another and asserts they do not overlap (a flag set/cleared around each, or the gate's own
state). Manually: launch on a day with no copy, immediately restore a large backup, and
verify the resulting archive in `Documents/ReminderLists` opens as a zip and its
`manifest.json` and `reminderlists.db` are both present.
