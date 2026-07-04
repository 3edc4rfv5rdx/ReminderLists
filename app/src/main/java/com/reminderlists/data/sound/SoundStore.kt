package com.reminderlists.data.sound

import android.content.Context
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
