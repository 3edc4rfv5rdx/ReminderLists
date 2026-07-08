package com.reminderlists.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Local backup/restore (TZ 3.8). A backup zip holds reminderlists.db + photos/ + sounds/ +
// logs/ + manifest.json. Restore is atomic-ish: everything is validated in a temp dir first
// (zip structure, DB integrity, schema version) and only then swapped in; logs are not
// restored (current device logs are kept). No cloud — the destination/source is a user-picked
// document Uri (TZ 9). The same converter output (IMPEX) is a valid backup zip.
object BackupManager {

    private const val DB_ENTRY = "reminderlists.db"
    private const val MANIFEST = "manifest.json"
    private const val CURRENT_DB_VERSION = 1

    class RestoreException(message: String) : Exception(message)

    // Default backup location (TZ 3.8): Documents/ReminderLists/.
    const val BACKUP_DIR = "Documents/ReminderLists"

    private fun soundsDir(context: Context) = File(context.filesDir, "sounds")
    private fun logsDir(context: Context) = File(context.filesDir, "logs")

    private fun backupFileName(): String {
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        return "backup_$ts.zip"
    }

    // --- Backup ------------------------------------------------------------------------

    // Default backup: writes to Documents/ReminderLists/ via MediaStore (no storage permission,
    // no picker — TZ 3.8 / 9). Returns the created file's display name.
    suspend fun backupToDocuments(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = backupFileName()
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/ReminderLists")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("Cannot create backup file")
            try {
                backup(context, uri).getOrThrow()
            } catch (e: Exception) {
                resolver.delete(uri, null, null) // don't leave a half-written pending file
                throw e
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            name
        }
    }

    suspend fun backup(context: Context, dest: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Checkpoint the WAL so the plain .db file is complete before we copy it.
            AppDatabase.get(context).query("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            val out = context.contentResolver.openOutputStream(dest)
                ?: error("Cannot open destination")
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                putText(zip, MANIFEST, manifestJson().toString())
                val db = AppDatabase.databaseFile(context)
                if (db.exists()) putFile(zip, db, DB_ENTRY)
                putTree(zip, PhotoManager.photosDir(context), "photos")
                putTree(zip, soundsDir(context), "sounds")
                putTree(zip, logsDir(context), "logs")
            }
            Logger.i("Backup written")
        }
    }

    private fun manifestJson() = JSONObject().apply {
        put("app", "ReminderLists")
        put("db_version", CURRENT_DB_VERSION)
    }

    // --- Restore -----------------------------------------------------------------------

    suspend fun restore(context: Context, src: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                unzip(context, src, staging)

                val db = File(staging, DB_ENTRY)
                if (!db.exists()) throw RestoreException("Backup has no database")
                validateManifest(File(staging, MANIFEST))
                validateDb(db)

                // Point of no return: swap the DB file and media in, then the app restarts.
                AppDatabase.closeInstance()
                val target = AppDatabase.databaseFile(context)
                target.parentFile?.mkdirs()
                // Drop the old WAL/SHM so they can't shadow the restored main file.
                File(target.parentFile, "${target.name}-wal").delete()
                File(target.parentFile, "${target.name}-shm").delete()
                db.copyTo(target, overwrite = true)

                replaceTree(File(staging, "photos"), PhotoManager.photosDir(context))
                replaceTree(File(staging, "sounds"), soundsDir(context))
                // logs/ is intentionally not restored (TZ 3.8).
                Logger.i("Restore applied")
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    private fun validateManifest(manifest: File) {
        if (!manifest.exists()) throw RestoreException("Backup has no manifest")
        val version = runCatching { JSONObject(manifest.readText()).optInt("db_version", -1) }
            .getOrDefault(-1)
        if (version <= 0) throw RestoreException("Backup manifest is invalid")
        // A newer schema than this app can read cannot be downgraded (TZ 3.8).
        if (version > CURRENT_DB_VERSION) throw RestoreException("Backup is from a newer app version")
    }

    private fun validateDb(db: File) {
        val handle = try {
            SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw RestoreException("Database is unreadable")
        }
        handle.use {
            val ok = it.rawQuery("PRAGMA integrity_check", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else "no result"
            }
            if (ok != "ok") throw RestoreException("Database integrity check failed")
        }
    }

    // --- Zip helpers -------------------------------------------------------------------

    private fun putText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }

    private fun putFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun putTree(zip: ZipOutputStream, dir: File, prefix: String) {
        if (!dir.isDirectory) return
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.isFile) putFile(zip, file, "$prefix/${file.name}")
        }
    }

    private fun unzip(context: Context, src: Uri, dest: File) {
        val input = context.contentResolver.openInputStream(src) ?: error("Cannot open backup")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Guard against path traversal in a malformed zip.
                val target = File(dest, entry.name).canonicalFile
                if (!target.path.startsWith(dest.canonicalPath + File.separator)) {
                    throw RestoreException("Unsafe entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    // Replace a media directory wholesale with the backup's copy (empty backup dir clears it).
    private fun replaceTree(from: File, to: File) {
        to.deleteRecursively()
        to.mkdirs()
        if (from.isDirectory) {
            from.listFiles()?.forEach { it.copyTo(File(to, it.name), overwrite = true) }
        }
    }
}
