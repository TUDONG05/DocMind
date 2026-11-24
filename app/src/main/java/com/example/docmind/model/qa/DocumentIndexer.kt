package com.example.docmind.model.qa

import com.example.docmind.model.local.entity.DocumentChunkEntity
import com.example.docmind.model.repository.DocMindRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Shared by upload/retry/chat. Percent counts successful chunks; 100 means the Room transaction committed. */
class DocumentIndexer(
    private val repository: DocMindRepository,
    private val extractor: DocumentTextExtractor,
    private val api: QaApi
) {
    private val mutableProgress = MutableStateFlow<Map<Long, DocumentIndexProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, DocumentIndexProgress>> = mutableProgress.asStateFlow()
    private val documentLocks = ConcurrentHashMap<Long, Mutex>()
    private val permits = Semaphore(1)

    suspend fun ensureIndexed(userId: Long, documentId: Long, expectedModel: String? = null,
        expectedDimensions: Int? = null): List<DocumentChunkEntity> =
        documentLocks.getOrPut(documentId) { Mutex() }.withLock {
            val document = repository.ownedDocument(userId, documentId)
            val previous = repository.getChunksByDocumentId(documentId).first()
            val model = expectedModel?.plus("|chunk-v1") ?: previous.firstOrNull()?.embeddingModel
            val dimensions = expectedDimensions ?: previous.firstOrNull()?.embedding?.let {
                runCatching { RagMath.decode(it).size }.getOrNull()
            }
            val failedAttempt = document.processingStatus.startsWith("INDEX_FAILED:") ||
                document.processingStatus in setOf("INDEX_RUNNING","INDEX_INTERRUPTED")
            val compatible = !failedAttempt && model?.endsWith("|chunk-v1") == true && dimensions != null &&
                previous.isNotEmpty() && previous.size <= 200 && previous.all { chunk ->
                    chunk.embeddingModel == model && chunk.content.isNotBlank() && chunk.content.length <= 1200 &&
                        chunk.embedding?.let { runCatching { RagMath.decode(it).size == dimensions }.getOrDefault(false) } == true
                }
            fun report(value: DocumentIndexProgress) { mutableProgress.update { it + (documentId to value) } }
            if (compatible) {
                repository.updateIndexStatus(userId, documentId, "INDEXED")
                report(DocumentIndexProgress(IndexStage.INDEXED, previous.size, previous.size))
                return@withLock previous
            }
            try {
                repository.updateIndexStatus(userId, documentId, "INDEX_RUNNING")
                report(DocumentIndexProgress(IndexStage.QUEUED))
                permits.withPermit {
                    currentCoroutineContext().ensureActive()
                    report(DocumentIndexProgress(IndexStage.READING))
                    val extracted = extractor.extract(document)
                    val indexed = mutableListOf<DocumentChunkEntity>()
                    var contractModel = expectedModel
                    var contractDimensions = expectedDimensions
                    for (batch in extracted.chunked(16)) {
                        report(DocumentIndexProgress(IndexStage.EMBEDDING, indexed.size, extracted.size))
                        val embedding = api.embed(batch.map { it.text }, query = false)
                        val size = embedding.vectors.firstOrNull()?.size
                        if (contractModel == null) contractModel = embedding.model
                        if (contractDimensions == null) contractDimensions = size
                        if (embedding.model != contractModel || embedding.vectors.size != batch.size ||
                            embedding.vectors.any { it.size != contractDimensions || !RagMath.valid(it) })
                            throw QaFailure("response",document.fileName)
                        batch.forEachIndexed { index, chunk ->
                            indexed += DocumentChunkEntity(documentId = documentId, chunkIndex = indexed.size,
                                content = chunk.text, pageNumber = chunk.page,
                                embedding = RagMath.encode(embedding.vectors[index]),
                                embeddingModel = contractModel + "|chunk-v1")
                        }
                        report(DocumentIndexProgress(IndexStage.EMBEDDING, indexed.size, extracted.size))
                    }
                    currentCoroutineContext().ensureActive()
                    report(DocumentIndexProgress(IndexStage.SAVING, indexed.size, indexed.size))
                    repository.replaceQaIndex(userId, documentId, indexed)
                    report(DocumentIndexProgress(IndexStage.INDEXED, indexed.size, indexed.size))
                    indexed
                }
            } catch (error: Exception) {
                val interrupted = error is CancellationException
                val code = (error as? QaFailure)?.code ?: "network"
                withContext(NonCancellable) {
                    // Deleted documents must stay deleted; a failed status write never recreates a row.
                    runCatching { repository.updateIndexStatus(userId, documentId,
                        if (interrupted) "INDEX_INTERRUPTED" else "INDEX_FAILED:$code") }
                }
                report(DocumentIndexProgress(if (interrupted) IndexStage.INTERRUPTED else IndexStage.FAILED, errorCode = code))
                if (interrupted) throw error
                throw QaFailure(code, document.fileName)
            }
        }
}
