package com.example.docmind.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class FileMetadata(
    val fileName: String,
    val fileSize: Long,
    val fileType: String,
    val uriString: String
)

object FileUtils {

    /**
     * Extracts metadata (name, size, mime type) from a Content URI
     */
    fun getFileMetadata(context: Context, uri: Uri): FileMetadata {
        var fileName = "Document_${System.currentTimeMillis()}.pdf"
        var fileSize = 0L
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/pdf"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        fileName = name
                    }
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        // Try taking persistable URI permission if applicable
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
            // Safe fallback if URI is not persistable
        }

        return FileMetadata(
            fileName = fileName,
            fileSize = fileSize,
            fileType = mimeType,
            uriString = uri.toString()
        )
    }

    /**
     * Copies a file from Uri to internal app storage
     */
    fun copyUriToInternalStorage(context: Context, uri: Uri, targetFolder: String, preferredFileName: String,
        checkCancelled: () -> Unit = {}): String? {
        return try {
            val folder = File(context.filesDir, targetFolder)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val destinationFile = File(folder, preferredFileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        checkCancelled()
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (destinationFile.exists() && destinationFile.length() > 0) {
                destinationFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            null
        }
    }

    /**
     * Normalizes Vietnamese text by removing diacritics, lowercasing, and replacing 'đ'/'Đ' with 'd'.
     */
    fun normalizeVietnamese(input: String): String {
        if (input.isBlank()) return ""
        val nfdNormalizedString = Normalizer.normalize(input, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        val withoutDiacritics = pattern.matcher(nfdNormalizedString).replaceAll("")
        return withoutDiacritics
            .lowercase(Locale.getDefault())
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val sizeMb = bytes / (1024.0 * 1024.0)
        return if (sizeMb >= 1.0) {
            String.format(Locale.getDefault(), "%.1f MB", sizeMb)
        } else {
            val sizeKb = bytes / 1024
            "$sizeKb KB"
        }
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
