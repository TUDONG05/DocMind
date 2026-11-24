package com.example.docmind.model.qa

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.docmind.model.local.entity.DocumentEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

class QaFailure(val code: String, val document: String? = null) : Exception(code)

/** Bounded extraction for QA. Display viewers and their placeholder/error strings are not indexed. */
class DocumentTextExtractor(context: Context) {
    private val context = context.applicationContext

    suspend fun extract(document: DocumentEntity): List<TextChunk> = withContext(Dispatchers.IO) {
        try {
            val path = document.filePath
            val bytes = (if (path.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(path))
                else File(path).inputStream())?.use { readBounded(it, 15 * 1024 * 1024) }
                ?: throw QaFailure("document", document.fileName)
            val extension = document.fileName.substringAfterLast('.', "").lowercase()
            val chunks = when {
                extension == "pdf" || document.fileType == "application/pdf" -> pdf(bytes)
                extension == "docx" -> office(bytes, false)
                extension == "xlsx" -> office(bytes, true)
                extension in setOf("txt", "md") ||
                    document.fileType in setOf("text/plain", "text/markdown", "text/x-markdown") -> {
                    require(bytes.size <= 640_000)
                    RagMath.split(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"), null)
                }
                else -> throw QaFailure("unsupported", document.fileName)
            }
            if (chunks.isEmpty()) throw QaFailure("empty", document.fileName)
            if (chunks.size > 200 || chunks.sumOf { it.text.length } > 200_000) throw QaFailure("large", document.fileName)
            chunks
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is QaFailure) throw QaFailure(error.code, error.document ?: document.fileName)
            throw QaFailure("document", document.fileName)
        }
    }

    private suspend fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw QaFailure("large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun pdf(bytes: ByteArray): List<TextChunk> {
        PDFBoxResourceLoader.init(context)
        return PDDocument.load(bytes).use { pdf ->
            if (pdf.numberOfPages > 300) throw QaFailure("large")
            if (!pdf.currentAccessPermission.canExtractContent()) throw QaFailure("document")
            val result = mutableListOf<TextChunk>()
            val stripper = PDFTextStripper()
            for (page in 1..pdf.numberOfPages) {
                coroutineContext.ensureActive()
                stripper.startPage = page
                stripper.endPage = page
                result += RagMath.split(stripper.getText(pdf), page)
                if (result.size > 200) throw QaFailure("large")
            }
            result
        }
    }

    private suspend fun office(bytes: ByteArray, spreadsheet: Boolean): List<TextChunk> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var count = 0
            var total = 0
            while (true) {
                coroutineContext.ensureActive()
                val entry = zip.nextEntry ?: break
                if (++count > 1024) throw QaFailure("large")
                // Read every entry with a bound so unrelated zip bombs cannot consume unbounded CPU/memory.
                val value = readBounded(zip, 4 * 1024 * 1024)
                total += value.size
                if (total > 16 * 1024 * 1024) throw QaFailure("large")
                if (entry.name == "word/document.xml" || entry.name == "xl/sharedStrings.xml" ||
                    entry.name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml"))) entries[entry.name] = value
                zip.closeEntry()
            }
        }
        val text = StringBuilder()
        fun append(value: String) {
            if (text.length + value.length > 160_000) throw QaFailure("large")
            text.append(value)
        }
        if (!spreadsheet) {
            val xml = entries["word/document.xml"] ?: throw QaFailure("document")
            parse(xml) { parser ->
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "t") append(parser.nextText())
                if (parser.eventType == XmlPullParser.END_TAG && parser.name == "p") append("\n")
            }
        } else {
            val shared = mutableListOf<String>()
            var current = StringBuilder()
            entries["xl/sharedStrings.xml"]?.let { xml ->
                parse(xml) { parser ->
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "si") current = StringBuilder()
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "t") current.append(parser.nextText())
                    if (parser.eventType == XmlPullParser.END_TAG && parser.name == "si") shared += current.toString()
                }
            }
            val sheets = entries.keys.filter { it.startsWith("xl/worksheets/") }.sorted()
            if (sheets.isEmpty()) throw QaFailure("document")
            sheets.forEach { name ->
                append("\n$name\n")
                var type: String? = null
                parse(entries.getValue(name)) { parser ->
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "c" -> type = parser.getAttributeValue(null, "t")
                            "v" -> {
                                val value = parser.nextText()
                                append(if (type == "s") shared.getOrNull(value.toIntOrNull() ?: -1) ?: throw QaFailure("document") else value)
                            }
                            "t" -> append(parser.nextText())
                        }
                    } else if (parser.eventType == XmlPullParser.END_TAG) {
                        if (parser.name == "c") append(" | ")
                        if (parser.name == "row") append("\n")
                    }
                }
            }
            // Sheet labels alone are not document content.
            if (text.toString().replace(Regex("xl/worksheets/sheet[0-9]+\\.xml|[|\\s]"), "").isBlank())
                throw QaFailure("empty")
        }
        return RagMath.split(text.toString().trim(), null)
    }

    private suspend fun parse(bytes: ByteArray, consume: (XmlPullParser) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(bytes.inputStream(), null)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            coroutineContext.ensureActive()
            if (parser.eventType == XmlPullParser.DOCDECL) throw QaFailure("document")
            consume(parser)
            parser.next()
        }
    }
}
