package com.example.docmind

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docmind.model.local.entity.DocumentEntity
import com.example.docmind.model.qa.DocumentTextExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@RunWith(AndroidJUnit4::class)
class DocumentExtractionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun doc(file: File) = DocumentEntity(documentId=1,userId=1,fileName=file.name,filePath=file.absolutePath,
        fileType="application/octet-stream",fileSize=file.length(),processingStatus="READY",uploadDate=1)
    @Test fun extractsUtf8AndRejectsEmptyFiles() = runBlocking {
        val file = File(context.cacheDir,"qa-extract.txt")
        try {
            file.writeText("DocMind hỗ trợ hỏi đáp tài liệu.")
            val chunks = DocumentTextExtractor(context).extract(doc(file))
            assertEquals("DocMind hỗ trợ hỏi đáp tài liệu.",chunks.single().text)
            assertNull(chunks.single().page)
            file.writeText("  ")
            assertTrue(runCatching { DocumentTextExtractor(context).extract(doc(file)) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun extractsMarkdownAsUtf8WithGenericOrMarkdownMimeTypes() = runBlocking {
        val file = File(context.cacheDir, "qa-extract.MD")
        val markdown = "# Hướng dẫn DocMind\n\n- **Thời hạn**: 30 ngày.\n\n`mã ví dụ`"
        try {
            file.writeText("\uFEFF" + markdown)
            for (mime in listOf("application/octet-stream", "text/markdown", "text/x-markdown")) {
                val chunks = DocumentTextExtractor(context).extract(doc(file).copy(fileType = mime))
                assertEquals(markdown, chunks.single().text)
                assertNull(chunks.single().page)
            }
            file.writeText(" \n ")
            val error = runCatching { DocumentTextExtractor(context).extract(doc(file)) }.exceptionOrNull()
            assertEquals("empty", (error as com.example.docmind.model.qa.QaFailure).code)
            assertEquals(file.name, error.document)
        } finally { file.delete() }
    }

    @Test fun corruptedSpreadsheetReportsFailingFileInsteadOfPartialContent() = runBlocking {
        val file = File(context.cacheDir,"qa-corrupt.xlsx")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write("""<worksheet><sheetData><row><c t="inlineStr"><is><t>Valid cell</t></is></c><c t="s"><v>999</v></c></row></sheetData></worksheet>""".toByteArray())
                zip.closeEntry()
            }
            val error = runCatching { DocumentTextExtractor(context).extract(doc(file)) }.exceptionOrNull()
            assertTrue(error is com.example.docmind.model.qa.QaFailure)
            assertEquals(file.name,(error as com.example.docmind.model.qa.QaFailure).document)
        } finally { file.delete() }
    }

    @Test fun emptySpreadsheetReportsFailingFilename() = runBlocking {
        val file = File(context.cacheDir,"qa-empty.xlsx")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write("<worksheet><sheetData/></worksheet>".toByteArray())
                zip.closeEntry()
            }
            val error = runCatching { DocumentTextExtractor(context).extract(doc(file)) }.exceptionOrNull()
            assertEquals(file.name,(error as com.example.docmind.model.qa.QaFailure).document)
        } finally { file.delete() }
    }

    @Test fun extractsDocxTextWithoutInventingPages() = runBlocking {
        val file = File(context.cacheDir,"qa-extract.docx")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write("""<w:document xmlns:w="urn:word"><w:body><w:p><w:r><w:t>Thời hạn là 30 ngày.</w:t></w:r></w:p></w:body></w:document>""".toByteArray())
                zip.closeEntry()
            }
            val chunks = DocumentTextExtractor(context).extract(doc(file))
            assertTrue(chunks.single().text.contains("30 ngày"))
            assertNull(chunks.single().page)
        } finally { file.delete() }
    }
}
