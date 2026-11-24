package com.example.docmind.viewmodel

import com.example.docmind.model.local.entity.DocumentEntity
import com.example.docmind.model.qa.DocumentIndexProgress
import com.example.docmind.model.qa.IndexStage
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentFiltersTest {
    private fun doc(id: Long, name: String, status: String = "READY",
        mime: String = "application/octet-stream") =
        DocumentEntity(id, 1, name, "/test", mime, 1, status, 1)

    @Test fun combinesSearchStatusAndFormatWithoutChangingOriginalTotal() {
        val documents = listOf(doc(1, "Kế hoạch.MD", "INDEXED", "text/plain"),
            doc(2, "Kế hoạch.pdf", "INDEXED"), doc(3, "Khác.md", "INDEXED"),
            doc(4, "Kế hoạch.md", "INDEX_FAILED:network"))
        assertEquals(listOf(1L), filterDocuments(documents, emptyMap(), "ke hoach",
            DocumentStatusFilter.INDEXED, DocumentFormatFilter.MD).map { it.documentId })
        assertEquals(4, documents.size)
        assertEquals(documents, filterDocuments(documents, emptyMap(), "",
            DocumentStatusFilter.ALL, DocumentFormatFilter.ALL))
    }

    @Test fun usesLiveProgressAndTreatsStoredRunningAsInterruptedAfterRestart() {
        val documents = listOf(doc(1, "a.txt", "INDEX_FAILED:network"),
            doc(2, "b.txt", "INDEX_RUNNING"))
        for (stage in listOf(IndexStage.QUEUED, IndexStage.READING, IndexStage.EMBEDDING, IndexStage.SAVING)) {
            assertEquals(listOf(1L), filterDocuments(documents, mapOf(1L to DocumentIndexProgress(stage)),
                "", DocumentStatusFilter.PROCESSING, DocumentFormatFilter.ALL).map { it.documentId })
        }
        assertEquals(listOf(2L), filterDocuments(documents, emptyMap(), "",
            DocumentStatusFilter.INTERRUPTED, DocumentFormatFilter.ALL).map { it.documentId })
        assertEquals(listOf(1L), filterDocuments(documents, emptyMap(), "",
            DocumentStatusFilter.FAILED, DocumentFormatFilter.ALL).map { it.documentId })
        assertEquals(listOf(1L), filterDocuments(documents,
            mapOf(1L to DocumentIndexProgress(IndexStage.INDEXED)), "",
            DocumentStatusFilter.INDEXED, DocumentFormatFilter.ALL).map { it.documentId })
        assertEquals(listOf(3L), filterDocuments(documents + doc(3, "old.txt", "COMPLETED"),
            emptyMap(), "", DocumentStatusFilter.NOT_INDEXED, DocumentFormatFilter.ALL).map { it.documentId })
    }

    @Test fun distinguishesExtensionsBeforeMimeAndSupportsMimeFallback() {
        val documents = listOf(doc(1, "note.MD", mime = "text/plain"),
            doc(2, "notes.TXT"), doc(3, "old.doc"), doc(4, "new.docx"),
            doc(5, "old.xls"), doc(6, "new.xlsx"), doc(7, "scan.PDF"),
            doc(8, "unknown.bin"), doc(9, "no-extension", mime = "text/markdown"))
        val expected = mapOf(DocumentFormatFilter.MD to listOf(1L, 9L),
            DocumentFormatFilter.TXT to listOf(2L), DocumentFormatFilter.DOC to listOf(3L),
            DocumentFormatFilter.DOCX to listOf(4L), DocumentFormatFilter.XLS to listOf(5L),
            DocumentFormatFilter.XLSX to listOf(6L), DocumentFormatFilter.PDF to listOf(7L),
            DocumentFormatFilter.OTHER to listOf(8L))
        expected.forEach { (format, ids) ->
            assertEquals(ids, filterDocuments(documents, emptyMap(), "",
                DocumentStatusFilter.ALL, format).map { it.documentId })
        }
    }

    @Test fun returnsEmptyWhenFiltersHaveNoIntersection() {
        assertEquals(emptyList<DocumentEntity>(), filterDocuments(listOf(doc(1, "a.md", "INDEXED")),
            emptyMap(), "", DocumentStatusFilter.FAILED, DocumentFormatFilter.MD))
    }
}
