package com.example.docmind.viewmodel

import com.example.docmind.model.local.entity.DocumentEntity
import com.example.docmind.model.qa.DocumentIndexProgress
import com.example.docmind.model.qa.IndexStage
import com.example.docmind.util.FileUtils
import java.util.Locale

enum class DocumentStatusFilter { ALL, NOT_INDEXED, PROCESSING, INDEXED, FAILED, INTERRUPTED }
enum class DocumentFormatFilter { ALL, PDF, DOC, DOCX, XLS, XLSX, TXT, MD, OTHER }

fun filterDocuments(
    documents: List<DocumentEntity>,
    progress: Map<Long, DocumentIndexProgress>,
    query: String,
    status: DocumentStatusFilter,
    format: DocumentFormatFilter
): List<DocumentEntity> {
    val keyword = FileUtils.normalizeVietnamese(query)
    return documents.filter { doc ->
        val state = progress[doc.documentId] ?: DocumentIndexProgress.fromStored(doc.processingStatus)
        val matchesStatus = when (status) {
            DocumentStatusFilter.ALL -> true
            DocumentStatusFilter.NOT_INDEXED -> state.stage == IndexStage.NOT_INDEXED
            DocumentStatusFilter.PROCESSING -> state.busy
            DocumentStatusFilter.INDEXED -> state.stage == IndexStage.INDEXED
            DocumentStatusFilter.FAILED -> state.stage == IndexStage.FAILED
            DocumentStatusFilter.INTERRUPTED -> state.stage == IndexStage.INTERRUPTED
        }
        matchesStatus && (format == DocumentFormatFilter.ALL || documentFormat(doc) == format) &&
            (keyword.isBlank() || FileUtils.normalizeVietnamese(doc.fileName).contains(keyword) ||
                FileUtils.normalizeVietnamese(doc.fileType).contains(keyword))
    }
}

private fun documentFormat(document: DocumentEntity): DocumentFormatFilter {
    val extension = document.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val byExtension = when (extension) {
        "pdf" -> DocumentFormatFilter.PDF
        "doc" -> DocumentFormatFilter.DOC
        "docx" -> DocumentFormatFilter.DOCX
        "xls" -> DocumentFormatFilter.XLS
        "xlsx" -> DocumentFormatFilter.XLSX
        "txt" -> DocumentFormatFilter.TXT
        "md" -> DocumentFormatFilter.MD
        else -> null
    }
    return byExtension ?: when (document.fileType.substringBefore(';').trim().lowercase(Locale.ROOT)) {
        "application/pdf" -> DocumentFormatFilter.PDF
        "application/msword" -> DocumentFormatFilter.DOC
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DocumentFormatFilter.DOCX
        "application/vnd.ms-excel" -> DocumentFormatFilter.XLS
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> DocumentFormatFilter.XLSX
        "text/plain" -> DocumentFormatFilter.TXT
        "text/markdown", "text/x-markdown" -> DocumentFormatFilter.MD
        else -> DocumentFormatFilter.OTHER
    }
}
