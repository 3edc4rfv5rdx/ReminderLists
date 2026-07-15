# Source audit findings — 2026-07 (tofix1)

Each item is a self-contained prompt for an LLM. Verify against current code before fixing
(file paths and line references are from the audit date). Fix one item per commit, with a
CHANGELOG.md entry in the same commit.

---

## 1. CRITICAL — Editing a reminder silently deletes its photos and event history (REPLACE-upsert cascade)

`RemindersDao.upsert` is declared with `@Insert(onConflict = OnConflictStrategy.REPLACE)`
(`app/src/main/java/com/reminderlists/data/db/dao/RemindersDao.kt:79-80`) and
`RemindersRepository.save()` calls it for BOTH create and edit
(`app/src/main/java/com/reminderlists/data/reminders/RemindersRepository.kt:34-52`).

In SQLite, `INSERT OR REPLACE` on an existing primary key executes DELETE + INSERT. Room
enables foreign keys, so the DELETE fires the children's `ON DELETE CASCADE`:
`reminder_photos`, `reminder_events`, `reminder_times`, `reminder_tags` (see
`data/db/entity/ReminderEntities.kt`). Times and tags are re-inserted by `save()` right
after, but:
- all `reminder_photos` rows of the reminder are wiped on every edit — the editor re-attaches
  only NEW photos (`ReminderEditorViewModel.save()`: `photos.filter { it.entity == null }`),
  so previously attached photos vanish from the DB, and the startup orphan sweep
  (`PhotoManager.sweepOrphans`) then deletes the files permanently;
- all `reminder_events` history is wiped, which also breaks the deferred auto-remove query
  `RemindersDao.autoRemovableBefore` (its `MAX(firedAt)` subquery becomes NULL).

Task: stop using REPLACE-insert for updates. Either split into `@Insert` for new rows +
`@Update` for existing rows (branch on `reminder.id == 0L` inside the repository
transaction), or use Room's `@Upsert` annotation (which does insert-or-update without the
delete). Keep the whole save in one transaction. Write/adjust a test or manually verify:
edit an existing reminder that has photos and fired events → photos and events must survive.

## 2. CRITICAL — Same REPLACE-upsert bug for notes: editing a note deletes its photos

`NotesDao.upsert` uses `OnConflictStrategy.REPLACE`
(`app/src/main/java/com/reminderlists/data/db/dao/NotesDao.kt:74-75`) and
`NotesRepository.save()` uses it for edits
(`app/src/main/java/com/reminderlists/data/notes/NotesRepository.kt:68-80`).
Editing an existing note DELETE+INSERTs the `notes` row, cascading deletion of its
`note_photos` rows (and `note_tags`, which are re-added). Existing photos disappear from the
note and their files are later removed by the orphan sweep.

Task: same fix as item 1 — `@Update` for existing notes (or `@Upsert`), keep the
transaction. Verify by editing a note that has photos.

## 3. CRITICAL — Renaming a folder (or editing its comment) moves all its lists/notes to root

`ListsDao.upsertFolder` and `NotesDao.upsertFolder` use `OnConflictStrategy.REPLACE`
(`ListsDao.kt:34-35`, `NotesDao.kt:48-49`). `ListsRepository.renameFolder`,
`ListsRepository.updateFolderComment`, `NotesRepository.renameFolder`,
`NotesRepository.updateFolderComment` call them with an existing folder id.
REPLACE deletes + re-inserts the folder row; the children's FK is `ON DELETE SET NULL`
(`lists.folderId`, `notes.folderId`), so every list/note in that folder is kicked to root
whenever the folder is renamed or its comment is edited.

Task: add `@Update` DAO methods for folders and use them for rename/comment-edit; keep the
REPLACE/plain insert only for creation. Verify: rename a folder containing lists → the lists
must stay inside it. Same for note folders.

## 4. HIGH — Backup manifest DB version is out of sync with the Room schema version

`BackupManager.CURRENT_DB_VERSION = 1`
(`app/src/main/java/com/reminderlists/data/backup/BackupManager.kt:31`) but the Room
database is already `version = 2` (`data/db/AppDatabase.kt:57`). Consequences: every new
backup's `manifest.json` claims `db_version: 1` while the packed DB file is schema v2, and
the restore guard `version > CURRENT_DB_VERSION` ("backup from a newer app") can never work
correctly because the writer never bumps the constant.

Task: remove the hand-maintained constant. Expose the schema version in one place (e.g.
`const val DB_VERSION = 2` in `AppDatabase.companion` used both by the `@Database(version=)`
annotation value and by `BackupManager`), so backup manifest and restore validation always
match the real Room version.

## 5. HIGH — SoundService leaks the previous MediaPlayer when a second reminder fires while sound is playing

`SoundService.onStartCommand` → `begin()` creates a new `MediaPlayer` and overwrites the
`player` field without stopping/releasing the previous one; it also overwrites `timeout`
without cancelling the old Job
(`app/src/main/java/com/reminderlists/reminders/SoundService.kt:38-111`). Same-minute
reminders are staggered by 3 s (ReminderScheduler), so overlap is a normal scenario. Effects:
- the first MediaPlayer keeps looping and is never released — `onDestroy` releases only the
  latest player, so the first sound can keep playing even after the service stops;
- the stale `timeout` Job from the first fire still calls `stopSelf()`, cutting the second
  sound short.

Task: at the top of `begin()` (or in `onStartCommand` before launching it) stop and release
the existing player and cancel the existing timeout Job, then proceed. Verify with two
reminders armed one minute apart (or same minute) with a long loop duration.

## 6. MEDIUM — Notification channel names are hardcoded English strings

`NotificationChannels.ensure` creates channels with literal names `"Reminders"` and
`"Service"` (`app/src/main/java/com/reminderlists/reminders/NotificationChannels.kt:19-26`).
These names are user-visible in the system notification settings. Project rule: all
UI-facing strings must come from string resources (base English + ru + uk).

Task: add string resources for both channel names (and translate to ru/uk in the same
change, per project rule), pass `context.getString(...)` when creating the channels.
`ensure()` runs on every app start, so re-creating the channel updates its name after a
locale change automatically.

## 7. MEDIUM — Weekday compact string "mtwt-ss" is hardcoded Latin letters shown in the UI

`Weekdays.compact()` builds the card's weekday line from the hardcoded constant
`LETTERS = "mtwtfss"` (`app/src/main/java/com/reminderlists/util/Weekdays.kt:18-22`) and it
is rendered on reminder cards (`RemindersScreen.kt` — Daily/Period cards). Russian/Ukrainian
users see English letters; this violates the "no hardcoded locale strings" rule.

Task: source the seven one-letter day markers from a string resource (e.g. one 7-char string
per locale, or reuse/derive from the existing `day_mo..day_su` resources' first letters) and
build the compact form from that. Keep the "active letter / dash" pattern. Translate ru/uk
in the same change.

## 8. MEDIUM — Editor Save pressed before async load finishes creates a duplicate record

`ReminderEditorViewModel`, `NoteEditorViewModel` and `ItemEditorViewModel` load the existing
record asynchronously in `init { viewModelScope.launch { ... existing = it ... } }`. Their
`save()` builds the entity with `id = base?.id ?: 0` (or branches on `existing == null`).
If the user taps Save before the load completes (slow storage, heavy DB), `existing` is
still null and a NEW record is inserted — a duplicate of the one being edited, and the edits
are applied to the duplicate.

Task: block saving while an edit target is not yet loaded — e.g. disable the Save action
when `isEdit && existing == null` (expose a `loaded` state), or make `save()` early-return
in that state. Apply the same guard in all three editors.

## 9. MEDIUM — Settings sound sliders write to the DB on every drag frame

In `SettingsScreen`, the "Sound duration" and "Sound level" sliders call
`vm.setSoundDuration(...)` / `vm.setSoundLevel(...)` from `onValueChange`
(`SettingsScreen.kt:203-215`), producing a DB write per drag tick. The font-scale slider
just above already does it correctly (local state + `onValueChangeFinished`).

Task: mirror the font-scale pattern for both sound sliders: hold the dragged value in local
`remember` state keyed on the flow value, persist once in `onValueChangeFinished`.

## 10. LOW — Camera capture temp files are never deleted

`PhotoManager.newCameraCaptureFile` creates `cache/camera/capture_<ts>.jpg`
(`PhotoManager.kt:37-40`); the comment says "imported and discarded after", but nothing ever
deletes the capture file — neither after a successful import (`AddPhotoButton` in
`PhotoComponents.kt` calls `onPicked` and forgets the file) nor when the camera returns
`success == false`. Files accumulate in the cache dir until the OS purges it.

Task: delete the capture file after `importPhoto` completes (success or failure) and on the
cancelled-capture path in `AddPhotoButton`. Alternatively sweep `cache/camera/` at app start.

## 11. LOW — Imported sound file name is not sanitized (path traversal / collision hardening)

`SoundStore.importSound` uses the content provider's `DISPLAY_NAME` directly as a file name
under `sounds/` (`SoundStore.kt:41-59`). A display name containing `/` or `..` (a hostile or
buggy provider can return anything) would escape the sounds directory.

Task: sanitize the display name before use — strip path separators and control chars (e.g.
`name.substringAfterLast('/')` plus a filter), fall back to the timestamp name if the result
is empty. Single-user app, so this is hardening, not an active exploit.

## 12. LOW — Stale TODO: "Backup/Restore once that feature exists"

`ListsScreen.kt:132` still carries
`// TODO menu item Backup/Restore (TZ 3.8) once that feature exists.` — the feature exists
(Settings → Backup/Restore section). Decide: either the Lists-tab menu should get the item
(then add it navigating to the same actions), or the TODO is obsolete — remove the comment.

## 13. LOW — Stale comment: sound duration range says "0..180" but the limit is 90

`SettingsKeys.kt:23` comment `// seconds, range 0..180` contradicts
`Limits.MAX_SOUND_DURATION = 90` used by the Settings slider. Fix the comment (or the limit,
if 180 was intended — confirm with TZ 5 / the user before changing behavior).

## 14. LOW — SoundField preview uses synchronous MediaPlayer.prepare() on the main thread

`SoundField.preview()` calls `prepare()` + `start()` directly in the click handler
(`ui/components/SoundField.kt:85-99`). For a large user file this blocks the UI thread.
Task: switch to `prepareAsync()` with `setOnPreparedListener { start() }` (the same pattern
SoundService already uses), keeping the try/catch and stopPreview semantics.

## 15. INFO — Welcome dialog (TZ 4.8) is not implemented

CLAUDE.md states the welcome screen (TZ 4.8) is implemented as a `WelcomeDialog`, but no
such composable exists in the source tree (`grep WelcomeDialog` finds nothing). Not a bug —
an unimplemented TZ item. Confirm with the user whether it is still planned; if yes,
implement per TZ 4.8 as a dialog shown on first launch (persist a "welcome_shown" flag in
the settings table).
