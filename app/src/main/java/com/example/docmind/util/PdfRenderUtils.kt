package com.example.docmind.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfPageBitmap(
    val pageIndex: Int,
    val bitmap: Bitmap
)

object PdfRenderUtils {

    /**
     * Renders all pages of a PDF file (via Uri or File Path) into a list of Bitmaps
     */
    suspend fun renderPdfPages(
        context: Context,
        filePathOrUri: String,
        maxWidth: Int = 1080
    ): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        val bitmaps = mutableListOf<Bitmap>()

        try {
            val uri = Uri.parse(filePathOrUri)
            pfd = if (filePathOrUri.startsWith("content://") || filePathOrUri.startsWith("file://")) {
                context.contentResolver.openFileDescriptor(uri, "r")
            } else {
                val file = File(filePathOrUri)
                if (file.exists()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    null
                }
            }

            if (pfd == null) {
                return@withContext Result.failure(IllegalArgumentException("Không thể mở tập tin PDF hoặc tập tin không tồn tại."))
            }

            pdfRenderer = PdfRenderer(pfd)
            val pageCount = pdfRenderer.pageCount

            for (i in 0 until pageCount) {
                pdfRenderer.openPage(i).use { page ->
                    val aspectRatio = page.height.toFloat() / page.width.toFloat()
                    val width = maxWidth.coerceAtMost(page.width * 2)
                    val height = (width * aspectRatio).toInt()

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // Render white background
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                }
            }

            Result.success(bitmaps)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                pdfRenderer?.close()
                pfd?.close()
            } catch (_: Exception) {}
        }
    }
}
