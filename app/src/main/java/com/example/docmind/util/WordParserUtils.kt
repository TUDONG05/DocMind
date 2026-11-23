package com.example.docmind.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

sealed class WordContent {
    data class Paragraph(val text: String, val isHeading: Boolean = false) : WordContent()
    data class Table(val rows: List<List<String>>) : WordContent()
}

object WordParserUtils {

    suspend fun parseDocx(context: Context, filePathOrUri: String): Result<List<WordContent>> = withContext(Dispatchers.IO) {
        try {
            Log.d("DocMindDebug", "WordParserUtils - parseDocx started for path: $filePathOrUri")
            val inputStream: InputStream? = if (filePathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(filePathOrUri))
            } else {
                val file = File(filePathOrUri)
                if (file.exists()) file.inputStream() else null
            }

            if (inputStream == null) {
                Log.e("DocMindDebug", "WordParserUtils - inputStream is null for path: $filePathOrUri")
                return@withContext Result.failure(IllegalArgumentException("Không thể mở tập tin Word (File không tồn tại hoặc không có quyền đọc)."))
            }

            val contents = mutableListOf<WordContent>()
            val zipInputStream = ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry

            var documentXmlBytes: ByteArray? = null
            while (entry != null) {
                Log.d("DocMindDebug", "WordParserUtils - ZipEntry: ${entry.name}")
                if (entry.name == "word/document.xml") {
                    documentXmlBytes = zipInputStream.readBytes()
                    break
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (documentXmlBytes == null) {
                Log.e("DocMindDebug", "WordParserUtils - document.xml not found in docx zip")
                return@withContext Result.failure(IllegalArgumentException("File DOCX không hợp lệ (thiếu tệp word/document.xml bên trong cấu trúc nén)."))
            }

            Log.d("DocMindDebug", "WordParserUtils - document.xml loaded, size: ${documentXmlBytes.size} bytes")

            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val parser = factory.newPullParser()
            parser.setInput(documentXmlBytes.inputStream(), "UTF-8")

            var eventType = parser.eventType
            val currentParagraphText = StringBuilder()
            var inParagraph = false
            var inTable = false
            val currentTableRows = mutableListOf<List<String>>()
            val currentRowCells = mutableListOf<String>()
            val currentCellText = StringBuilder()
            var inCell = false

            var pCount = 0
            var tCount = 0
            var tableCount = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawTag = parser.name ?: ""
                val tagName = if (rawTag.contains(":")) rawTag.substringAfter(":") else rawTag

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "p" -> {
                                inParagraph = true
                                currentParagraphText.clear()
                                pCount++
                            }
                            "tbl" -> {
                                inTable = true
                                currentTableRows.clear()
                                tableCount++
                            }
                            "tr" -> {
                                if (inTable) {
                                    currentRowCells.clear()
                                }
                            }
                            "tc" -> {
                                if (inTable) {
                                    inCell = true
                                    currentCellText.clear()
                                }
                            }
                            "t" -> {
                                tCount++
                                // Read text content safely
                                val text = parser.nextText() ?: ""
                                if (text.isNotEmpty()) {
                                    if (inCell) {
                                        currentCellText.append(text)
                                    } else if (inParagraph) {
                                        currentParagraphText.append(text)
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "p" -> {
                                if (inParagraph) {
                                    inParagraph = false
                                    val pText = currentParagraphText.toString().trim()
                                    if (pText.isNotEmpty()) {
                                        val isHead = pText.length < 80 && (pText == pText.uppercase() || pText.startsWith("Phần") || pText.startsWith("Chương") || pText.startsWith("Bài") || pText.startsWith("I.") || pText.startsWith("1."))
                                        contents.add(WordContent.Paragraph(pText, isHeading = isHead))
                                    }
                                }
                            }
                            "tc" -> {
                                if (inCell) {
                                    inCell = false
                                    currentRowCells.add(currentCellText.toString().trim())
                                }
                            }
                            "tr" -> {
                                if (inTable && currentRowCells.isNotEmpty()) {
                                    currentTableRows.add(currentRowCells.toList())
                                }
                            }
                            "tbl" -> {
                                if (inTable) {
                                    inTable = false
                                    if (currentTableRows.isNotEmpty()) {
                                        contents.add(WordContent.Table(currentTableRows.toList()))
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            Log.d("DocMindDebug", "WordParserUtils - Stats: paragraphs=$pCount, textTags=$tCount, tables=$tableCount, extractedItems=${contents.size}")
            if (contents.isEmpty()) {
                contents.add(WordContent.Paragraph("Tài liệu Word trống hoặc không trích xuất được đoạn văn bản nào."))
            }

            Result.success(contents)
        } catch (e: Exception) {
            Log.e("DocMindDebug", "WordParserUtils - Exception in parseDocx: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}
