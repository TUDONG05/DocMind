package com.example.docmind

import com.example.docmind.di.DocMindViewModelFactory

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docmind.model.local.DocMindDatabase
import com.example.docmind.model.local.entity.*
import com.example.docmind.model.qa.*
import com.example.docmind.model.repository.DocMindRepository
import com.example.docmind.model.session.UserSession
import com.example.docmind.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MvvmSessionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val noNetwork = object : QaApi {
        override suspend fun embed(texts: List<String>, query: Boolean): EmbeddingBatch = error("Unexpected network")
        override suspend fun answer(question: String, history: List<QaHistory>, sources: List<QaSource>): QaAnswer = error("Unexpected network")
    }

    @Test fun logoutImmediatelyClearsCachedDocumentsAndConversations() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, DocMindDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val repo = DocMindRepository(db)
            val ownerId = repo.insertUser(UserEntity(fullName = "A", email = "a@mvvm.test", passwordHash = "x", createdAt = "now"))
            val owner = requireNotNull(repo.findUserById(ownerId))
            repo.insertDocument(DocumentEntity(userId = ownerId, fileName = "a.txt", filePath = "/unused",
                fileType = "text/plain", fileSize = 1, processingStatus = "READY", uploadDate = 1))
            val conversationId = repo.createQaConversation(ownerId, "My chat")
            withContext(Dispatchers.Main) {
                val session = UserSession({}, {})
                val messages = UiMessages(context)
                val cancellation = QaCancellation()
                val extractor = DocumentTextExtractor(context)
                val indexer = DocumentIndexer(repo, extractor, noNetwork)
                val documents = DocumentsViewModel(repo, indexer, session, messages, cancellation)
                val chat = ChatViewModel(repo, DocumentQaService(repo, extractor, noNetwork, indexer), session, messages, cancellation)
                store.put("documents", documents)
                store.put("chat", chat)
                session.signIn(owner)
                coroutineScope {
                    val docsCollector = launch { documents.userDocuments.collect() }
                    val chatCollector = launch { chat.userConversations.collect() }
                    try {
                        withTimeout(5000) { documents.userDocuments.first { it.isNotEmpty() } }
                        withTimeout(5000) { chat.userConversations.first { it.isNotEmpty() } }
                        chat.selectConversation(conversationId)
                        docsCollector.cancelAndJoin()
                        chatCollector.cancelAndJoin()
                        // Reproduce a background screen whose Room subscriptions have stopped.
                        delay(5200)
                        session.logout()
                        assertTrue("Old account documents must clear synchronously", documents.userDocuments.value.isEmpty())
                        assertTrue("Old account chats must clear synchronously", chat.userConversations.value.isEmpty())
                        assertNull(chat.selectedConversationId.value)
                        session.signIn(owner)
                        withTimeout(5000) { documents.userDocuments.first { it.isNotEmpty() } }
                        withTimeout(5000) { chat.userConversations.first { it.isNotEmpty() } }
                    } finally {
                        docsCollector.cancel()
                        chatCollector.cancel()
                    }
                }
            }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            db.close()
        }
    }

    @Test fun logoutCancelsIndexAndQaWithoutCallbacksFromCompose() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, DocMindDatabase::class.java).build()
        val store = ViewModelStore()
        val file = java.io.File.createTempFile("mvvm-index", ".txt", context.cacheDir)
        file.writeText("A document whose embedding request is in progress.")
        val indexStarted = CompletableDeferred<Unit>()
        val queryStarted = CompletableDeferred<Unit>()
        val indexCancelled = CompletableDeferred<Unit>()
        val queryCancelled = CompletableDeferred<Unit>()
        val api = object : QaApi {
            override suspend fun embed(texts: List<String>, query: Boolean): EmbeddingBatch {
                (if (query) queryStarted else indexStarted).complete(Unit)
                try { awaitCancellation() }
                finally { (if (query) queryCancelled else indexCancelled).complete(Unit) }
            }
            override suspend fun answer(question: String, history: List<QaHistory>, sources: List<QaSource>): QaAnswer = error("Unexpected answer")
        }
        try {
            val repo = DocMindRepository(db)
            val userId = repo.insertUser(UserEntity(fullName = "A", email = "cancel@mvvm.test", passwordHash = "x", createdAt = "now"))
            val owner = requireNotNull(repo.findUserById(userId))
            val readyId = repo.insertDocument(DocumentEntity(userId = userId, fileName = "ready.txt", filePath = "/unused",
                fileType = "text/plain", fileSize = 1, processingStatus = "INDEXED", uploadDate = 1))
            repo.replaceQaIndex(userId, readyId, listOf(DocumentChunkEntity(documentId = readyId, chunkIndex = 0,
                content = "Ready", embedding = RagMath.encode(floatArrayOf(1f, 0f)), embeddingModel = "test|chunk-v1")))
            val indexingId = repo.insertDocument(DocumentEntity(userId = userId, fileName = file.name, filePath = file.absolutePath,
                fileType = "text/plain", fileSize = file.length(), processingStatus = "READY", uploadDate = 1))
            val indexingDocument = repo.ownedDocument(userId, indexingId)
            val conversationId = repo.createQaConversation(userId, "Cancel test")
            withContext(Dispatchers.Main) {
                val session = UserSession({}, {})
                val messages = UiMessages(context)
                val cancellation = QaCancellation()
                val extractor = DocumentTextExtractor(context)
                val indexer = DocumentIndexer(repo, extractor, api)
                val documents = DocumentsViewModel(repo, indexer, session, messages, cancellation)
                val chat = ChatViewModel(repo, DocumentQaService(repo, extractor, api, indexer), session, messages, cancellation)
                store.put("documents", documents)
                store.put("chat", chat)
                session.signIn(owner)
                documents.indexDocument(indexingDocument)
                chat.sendMessage(conversationId, "Question")
                withTimeout(5000) { indexStarted.await(); queryStarted.await() }
                session.logout()
                assertTrue(chat.qaStates.value.isEmpty())
                withTimeout(5000) {
                    indexCancelled.await()
                    queryCancelled.await()
                    indexer.progress.first { it[indexingId]?.stage == IndexStage.INTERRUPTED }
                }
                assertNull(messages.status.value)
            }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            file.delete()
            db.close()
        }
    }

    @Test fun factoryCreatesEachFeatureWithoutDependingOnCreationOrder() = runBlocking<Unit> {
        val store = ViewModelStore()
        val secondStore = ViewModelStore()
        try {
            withContext(Dispatchers.Main) {
                val app = context.applicationContext as Application
                val provider = ViewModelProvider(store, DocMindViewModelFactory(app))
                assertNotNull(provider[DocumentsViewModel::class.java])
                assertNotNull(provider[ChatViewModel::class.java])
                val account = provider[DocMindViewModel::class.java]
                val recreated = ViewModelProvider(secondStore, DocMindViewModelFactory(app))[DocMindViewModel::class.java]
                assertSame(account.currentUser, recreated.currentUser)
            }
        } finally {
            withContext(Dispatchers.Main) {
                store.clear()
                secondStore.clear()
            }
        }
    }
}
