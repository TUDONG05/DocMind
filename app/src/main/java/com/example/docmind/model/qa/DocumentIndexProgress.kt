package com.example.docmind.model.qa

enum class IndexStage { NOT_INDEXED, QUEUED, READING, EMBEDDING, SAVING, INDEXED, FAILED, INTERRUPTED }

data class DocumentIndexProgress(
    val stage: IndexStage = IndexStage.NOT_INDEXED,
    val completed: Int = 0,
    val total: Int = 0,
    val errorCode: String? = null
) {
    val percent: Int? get() = when (stage) {
        IndexStage.INDEXED -> 100
        IndexStage.SAVING -> 99
        IndexStage.EMBEDDING -> if (total > 0) (completed.toLong() * 100 / total).toInt().coerceIn(0,99) else 0
        else -> null
    }
    val busy: Boolean get() = stage in setOf(IndexStage.QUEUED,IndexStage.READING,IndexStage.EMBEDDING,IndexStage.SAVING)
    companion object {
        fun fromStored(status: String): DocumentIndexProgress = when {
            status == "INDEXED" -> DocumentIndexProgress(IndexStage.INDEXED)
            status == "INDEX_RUNNING" || status == "INDEX_INTERRUPTED" -> DocumentIndexProgress(IndexStage.INTERRUPTED)
            status.startsWith("INDEX_FAILED:") -> DocumentIndexProgress(IndexStage.FAILED,errorCode=status.substringAfter(':'))
            else -> DocumentIndexProgress()
        }
    }
}
