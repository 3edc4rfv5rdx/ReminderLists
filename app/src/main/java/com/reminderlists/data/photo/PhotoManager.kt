package com.reminderlists.data.photo

import android.content.Context
import java.io.File

// Single shared photo module (TZ 8): system Photo Picker (gallery) + camera via FileProvider
// (no runtime permissions). On import: fix EXIF orientation, resize to ~2048px, JPEG ~85%,
// store in photos/ under a generated name; DB keeps only the file name. Thumbnails via Coil.
// Used by list items, reminders, and notes alike.
object PhotoManager {

    fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    // TODO import(sourceUri): normalize + resize + compress, return stored file name (TZ 8).
    // TODO delete(fileName): remove file from disk (Room CASCADE only clears rows).
    // TODO sweepOrphans(): drop photos/ + sounds/ files with no DB reference (start / post-restore).

    fun fileFor(context: Context, fileName: String): File =
        File(photosDir(context), fileName)
}
