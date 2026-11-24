package com.example.docmind.model.qa

class EmbeddingBatch(val model: String, val vectors: List<FloatArray>)
data class QaSource(val id: String, val label: String, val text: String)
data class QaHistory(val role: String, val content: String)
data class QaAnswer(val answer: String, val sourceIds: List<String>, val grounded: Boolean)

interface QaApi {
    suspend fun embed(texts: List<String>, query: Boolean): EmbeddingBatch
    suspend fun answer(question: String, history: List<QaHistory>, sources: List<QaSource>): QaAnswer
}
