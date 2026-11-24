package com.example.docmind

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docmind.model.local.DocMindDatabase
import com.example.docmind.model.local.entity.*
import com.example.docmind.model.qa.*
import com.example.docmind.model.repository.DocMindRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentIndexerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun withDocument(text: String, block: suspend (DocMindRepository,Long,Long) -> Unit) {
        val file = File(context.cacheDir,"index-${UUID.randomUUID()}.txt").apply { writeText(text) }
        val db = Room.inMemoryDatabaseBuilder(context,DocMindDatabase::class.java).build()
        try {
            val repository = DocMindRepository(db)
            val user = repository.insertUser(UserEntity(fullName="Index",email="index@test.local",passwordHash="x",createdAt="now"))
            val doc = repository.insertDocument(DocumentEntity(userId=user,fileName=file.name,filePath=file.absolutePath,
                fileType="text/plain",fileSize=file.length(),processingStatus="READY",uploadDate=1))
            block(repository,user,doc)
        } finally { db.close(); file.delete() }
    }

    private fun api(embed: suspend (List<String>) -> EmbeddingBatch) = object: QaApi {
        override suspend fun embed(texts: List<String>, query: Boolean) = embed(texts)
        override suspend fun answer(question: String, history: List<QaHistory>, sources: List<QaSource>): QaAnswer = error("not used")
    }

    @Test fun showsSuccessfulChunkProgressAndCommits100OnlyWithSavedIndex() = runBlocking {
        withDocument("The report deadline is 30 days. ".repeat(800)) { repo,user,doc ->
            lateinit var indexer: DocumentIndexer
            var completed = 0
            indexer = DocumentIndexer(repo,DocumentTextExtractor(context),api { texts ->
                val progress = indexer.progress.value.getValue(doc)
                assertEquals(IndexStage.EMBEDDING,progress.stage)
                assertEquals(completed,progress.completed)
                assertTrue(progress.percent!! < 100)
                assertTrue(repo.getChunksByDocumentId(doc).first().isEmpty())
                completed += texts.size
                EmbeddingBatch("test|2|v1",texts.map { floatArrayOf(1f,0f) })
            })
            val chunks = indexer.ensureIndexed(user,doc)
            assertTrue(chunks.size > 16)
            assertEquals(chunks.size,completed)
            assertEquals(100,indexer.progress.value.getValue(doc).percent)
            assertEquals("INDEXED",repo.ownedDocument(user,doc).processingStatus)
            assertEquals(chunks.size,repo.getChunksByDocumentId(doc).first().size)
        }
    }

    @Test fun failurePreservesOldIndexAndRetryReplacesIt() = runBlocking {
        withDocument("The report deadline is 30 days. ".repeat(800)) { repo,user,doc ->
            repo.replaceQaIndex(user,doc,listOf(DocumentChunkEntity(documentId=doc,chunkIndex=0,
                content="old content",embedding=RagMath.encode(floatArrayOf(1f,0f)),embeddingModel="old|2|v1|chunk-v1")))
            var fail = true
            var calls = 0
            val indexer = DocumentIndexer(repo,DocumentTextExtractor(context),api { texts ->
                if (++calls == 2 && fail) throw QaFailure("quota")
                EmbeddingBatch("test|2|v1",texts.map { floatArrayOf(1f,0f) })
            })
            assertTrue(runCatching { indexer.ensureIndexed(user,doc,"test|2|v1",2) }.isFailure)
            assertEquals("old content",repo.getChunksByDocumentId(doc).first().single().content)
            assertEquals("INDEX_FAILED:quota",repo.ownedDocument(user,doc).processingStatus)
            assertEquals(IndexStage.FAILED,indexer.progress.value.getValue(doc).stage)
            fail = false; calls = 0
            assertTrue(indexer.ensureIndexed(user,doc,"test|2|v1",2).size > 16)
            assertEquals("INDEXED",repo.ownedDocument(user,doc).processingStatus)
        }
    }

    @Test fun uploadAndChatShareAnIndexWithoutDuplicateEmbeddingCalls() = runBlocking {
        withDocument("The report deadline is 30 days.") { repo,user,doc ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val indexer = DocumentIndexer(repo,DocumentTextExtractor(context),api { texts ->
                calls++; entered.complete(Unit); release.await()
                EmbeddingBatch("test|2|v1",texts.map { floatArrayOf(1f,0f) })
            })
            coroutineScope {
                val upload = async { indexer.ensureIndexed(user,doc) }
                entered.await()
                val chat = async { indexer.ensureIndexed(user,doc,"test|2|v1",2) }
                release.complete(Unit)
                assertEquals(upload.await().size,chat.await().size)
            }
            assertEquals(1,calls)
        }
    }

    @Test fun cancellationMarksInterruptedInsteadOfReady() = runBlocking {
        withDocument("The report deadline is 30 days.") { repo,user,doc ->
            val entered = CompletableDeferred<Unit>()
            val indexer = DocumentIndexer(repo,DocumentTextExtractor(context),api {
                entered.complete(Unit); awaitCancellation()
            })
            val job = launch { indexer.ensureIndexed(user,doc) }
            entered.await(); job.cancelAndJoin()
            assertEquals("INDEX_INTERRUPTED",repo.ownedDocument(user,doc).processingStatus)
            assertEquals(IndexStage.INTERRUPTED,indexer.progress.value.getValue(doc).stage)
            assertTrue(repo.getChunksByDocumentId(doc).first().isEmpty())
        }
    }
}
