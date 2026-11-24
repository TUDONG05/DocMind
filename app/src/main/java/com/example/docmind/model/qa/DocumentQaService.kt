package com.example.docmind.model.qa

import com.example.docmind.model.repository.DocMindRepository
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class DocumentQaService(
    private val repository: DocMindRepository,
    extractor: DocumentTextExtractor,
    private val api: QaApi,
    private val indexer: DocumentIndexer = DocumentIndexer(repository, extractor, api)
) {
    suspend fun ask(userId: Long, conversationId: Long, question: String, retryId: Long?, sourcesTitle: String,
        noEvidence: String, onQuestion: (Long) -> Unit, onProgress: (Boolean) -> Unit): String {
        val context = repository.qaContext(userId, conversationId)
        if (context.documents.isEmpty()) throw QaFailure("documents")
        val questionId = repository.beginQaQuestion(userId, conversationId, question, retryId, context.conversation.scopeRevision)
        onQuestion(questionId)
        onProgress(true)
        val query = api.embed(listOf(question), query = true)
        val queryVector = query.vectors.single()
        val model = query.model + "|chunk-v1"
        val candidates = mutableListOf<VectorChunk>()
        val sourcesById = mutableMapOf<String, QaSource>()
        for (doc in context.documents) {
            coroutineContext.ensureActive()
            val chunks = indexer.ensureIndexed(userId, doc.documentId, query.model, queryVector.size)
            chunks.forEach { chunk ->
                val id = "d${doc.documentId}:c${chunk.chunkIndex}"
                candidates += VectorChunk(id, chunk.content, RagMath.decode(chunk.embedding!!), model)
                sourcesById[id] = QaSource(id,
                    doc.fileName + (chunk.pageNumber?.let { " (p. $it)" } ?: ""), chunk.content)
            }
            val top = RagMath.retrieve(queryVector, model, candidates)
            candidates.clear()
            candidates.addAll(top)
            val retained = top.map { it.id }.toSet()
            sourcesById.keys.retainAll(retained)
        }
        val selected = RagMath.retrieve(queryVector, model, candidates).map { sourcesById.getValue(it.id) }
        onProgress(false)
        val history = context.messages.filter { it.messageId != retryId && it.messageId > context.conversation.scopeStartMessageId }.sortedBy { it.messageId }.takeLast(8)
            .map { QaHistory(it.role, it.content.take(4000)) }
        val result = if (selected.isEmpty()) QaAnswer(noEvidence, emptyList(), false)
            else api.answer(question, history, selected)
        require(result.sourceIds.all { id -> selected.any { it.id == id } })
        if (result.grounded && result.sourceIds.isEmpty()) throw QaFailure("response")
        val answer = if (!result.grounded) noEvidence else {
            val cited = result.sourceIds.distinct().map { id -> sourcesById.getValue(id) }
            var answerText = result.answer
            cited.forEachIndexed { index, source -> answerText = answerText.replace("[${source.id}]", "[${index + 1}]") }
            answerText + "\n\n" + sourcesTitle + ":\n" + cited.mapIndexed { index, source ->
                "[${index + 1}] ${source.label}\n${source.text.take(280)}"
            }.joinToString("\n\n")
        }
        coroutineContext.ensureActive()
        val current = repository.qaContext(userId, conversationId)
        require(current.conversation.scopeRevision == context.conversation.scopeRevision)
        require(current.documents.map { it.documentId }.toSet().containsAll(context.documents.map { it.documentId }))
        repository.completeQaAnswer(userId, conversationId, questionId, answer,
            context.conversation.scopeRevision, context.documents.map { it.documentId }.toSet())
        return answer
    }
}
