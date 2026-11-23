package com.example.docmind.data.local.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.annotation.WorkerThread
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Keeps a small, app-owned image so the profile does not depend on gallery permissions. */
class AvatarStorage(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "avatars")

    @WorkerThread
    fun save(userId: Long, uri: Uri): String {
        val bitmap = load(uri.toString()) ?: throw IOException("Invalid avatar image")
        var destination: File? = null
        try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create avatar directory")
            val file = File(directory, "user_${userId}_${UUID.randomUUID()}.jpg")
            destination = file
            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                    throw IOException("Cannot save avatar image")
                }
            }
            return file.absolutePath
        } catch (error: Exception) {
            destination?.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    @WorkerThread
    fun load(source: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = open(source) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        var bitmap = open(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val orientation = try {
            open(source)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        val longest = max(bitmap.width, bitmap.height)
        if (longest > 512) {
            val factor = 512f / longest
            val resized = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * factor).roundToInt().coerceAtLeast(1),
                (bitmap.height * factor).roundToInt().coerceAtLeast(1), true
            )
            if (resized !== bitmap) bitmap.recycle()
            bitmap = resized
        }
        return bitmap
    }

    @WorkerThread
    fun deleteOwned(path: String?) {
        if (path == null) return
        try {
            val file = File(path).canonicalFile
            if (file.parentFile == directory.canonicalFile) file.delete()
        } catch (_: IOException) {
            // A failed cleanup must not turn a successful profile update into an error.
        } catch (_: SecurityException) {
            // Only best-effort cleanup of an obsolete app-owned avatar.
        }
    }

    private fun open(source: String): InputStream? {
        val uri = Uri.parse(source)
        return when (uri.scheme) {
            "content", "file" -> appContext.contentResolver.openInputStream(uri)
            null -> File(source).takeIf { it.isFile }?.inputStream()
            else -> null
        }
    }
}
