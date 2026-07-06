package com.reminderlists.data.sound

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Single shared sound store (TZ 8): user-attached notification sounds live in internal
// storage sounds/; the DB keeps only the file name (TZ 6.2 sound_uri, null = Default).
object SoundStore {

    fun soundsDir(context: Context): File =
        File(context.filesDir, "sounds").apply { mkdirs() }

    fun fileFor(context: Context, fileName: String): File =
        File(soundsDir(context), fileName)

    fun listSounds(context: Context): List<String> =
        soundsDir(context).listFiles()?.map { it.name }?.sorted().orEmpty()

    // The single place a stored soundUri becomes a playable Uri (TZ 6.2), shared by the
    // scheduler's SoundService and the editor/Settings previews (TZ 8, no duplication).
    // A system Uri plays as-is; anything else is a user file in sounds/.
    fun mediaUri(context: Context, name: String): Uri? =
        if (name.startsWith("content://") || name.startsWith("android.resource://")) {
            Uri.parse(name)
        } else {
            fileFor(context, name).takeIf { it.exists() }?.let { Uri.fromFile(it) }
        }

    // The device's default alarm ringtone — used when no custom/default sound is set (TZ 4.10).
    fun systemDefaultAlarm(context: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    // Copy a picked audio file into sounds/ under its display name so the dropdown shows
    // a readable entry (TZ 4.2 j). Returns the stored file name, null on failure.
    suspend fun importSound(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val displayName = resolver.query(source, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: "sound_${System.currentTimeMillis()}"
            var target = fileFor(context, displayName)
            if (target.exists()) {
                target = fileFor(context, "${System.currentTimeMillis()}_$displayName")
            }
            resolver.openInputStream(source)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            target.name
        } catch (_: Exception) {
            null
        }
    }
}
