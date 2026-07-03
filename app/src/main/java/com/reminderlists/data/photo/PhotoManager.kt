package com.reminderlists.data.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.reminderlists.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

// Single shared photo module (TZ 8): system Photo Picker (gallery) + camera via FileProvider
// (no runtime permissions). On import: fix EXIF orientation, resize to ~2048px, JPEG ~85%,
// store in photos/ under a generated name; DB keeps only the file name. Thumbnails via Coil.
// Used by list items, reminders, and notes alike.
object PhotoManager {

    private const val MAX_SIDE = 2048
    private const val JPEG_QUALITY = 85

    fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    fun fileFor(context: Context, fileName: String): File =
        File(photosDir(context), fileName)

    // Camera capture target in cache/camera (matches file_paths.xml); imported and discarded after.
    fun newCameraCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // Import a picked/captured image: downsample + resize to MAX_SIDE, bake in the EXIF
    // orientation, save as JPEG into photos/. Returns the stored file name, null on failure.
    suspend fun importPhoto(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            // Bounds-only decode intentionally returns null — do not chain ?: off its result.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(source) ?: return@withContext null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = resolver.openInputStream(source)
                ?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: return@withContext null

            val maxSide = max(bitmap.width, bitmap.height)
            if (maxSide > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / maxSide
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
            }

            val orientation = resolver.openInputStream(source)?.use { stream ->
                ExifInterface(stream)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            bitmap = applyOrientation(bitmap, orientation)

            val fileName = "photo_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            FileOutputStream(fileFor(context, fileName)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            fileName
        } catch (_: Exception) {
            null
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // Copy a stored photo into the system gallery (Pictures/<app>) via MediaStore —
    // no permissions needed for own inserts (TZ 8: delete dialog "save to gallery" checkbox).
    suspend fun exportToGallery(context: Context, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = fileFor(context, fileName)
                if (!file.exists()) return@withContext false
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/" + context.getString(R.string.app_name),
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: return@withContext false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } catch (_: Exception) {
                false
            }
        }

    // Room CASCADE only clears rows — files are removed here (TZ 8).
    fun delete(context: Context, fileName: String) {
        fileFor(context, fileName).delete()
    }

    fun deleteAll(context: Context, fileNames: Collection<String>) {
        fileNames.forEach { delete(context, it) }
    }

    // Drop photos/ files with no DB reference — app start and post-restore (TZ 8).
    fun sweepOrphans(context: Context, referenced: Set<String>) {
        photosDir(context).listFiles()?.forEach { file ->
            if (file.name !in referenced) file.delete()
        }
    }
}
