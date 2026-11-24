package com.example.docmind

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docmind.model.local.DocMindDatabase
import com.example.docmind.model.local.entity.*
import com.example.docmind.model.repository.DocMindRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ConversationScopeTest {
    @Test fun deletingSourceResetsHistoryAndRejectsTheOldScopeRevision() = runBlocking {
        withIndexedDocuments(2) { repo,user,conv,ids ->
            val old = repo.qaContext(user,conv).conversation.scopeRevision
            repo.insertMessage(MessageEntity(conversationId=conv,role="user",content="Old question",createdAt=1))
            repo.insertMessage(MessageEntity(conversationId=conv,role="assistant",content="Deleted evidence",createdAt=2))
            repo.updateIndexStatus(user,ids.first(),"INDEX_FAILED:network")
            repo.deleteDocument(repo.ownedDocument(user,ids.first()))
            val updated = repo.qaContext(user,conv)
            assertTrue(updated.conversation.scopeRevision > old)
            val api = api { history,sources ->
                assertTrue("Deleted document history must not reach Gemini",history.isEmpty())
                com.example.docmind.model.qa.QaAnswer("Answer",listOf(sources.first().id),true)
            }
            val service = com.example.docmind.model.qa.DocumentQaService(repo,
                com.example.docmind.model.qa.DocumentTextExtractor(InstrumentationRegistry.getInstrumentation().targetContext),api)
            service.ask(user,conv,"New question",null,"Sources","None",{},{})
            assertEquals(listOf(ids.last()),repo.qaContext(user,conv).documents.map { it.documentId })
        }
    }

    @Test fun migrationFromVersionOnePreservesUsersDocumentsLinksAndMessages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "scope-migration-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, DocMindDatabase::class.java, name).build()
        try {
            var repo = DocMindRepository(db)
            val user = repo.insertUser(UserEntity(fullName="Saved user",email="migration@test.com",passwordHash="hash",createdAt="now"))
            val doc = repo.insertDocument(DocumentEntity(userId=user,fileName="saved.md",filePath="/saved",
                fileType="text/markdown",fileSize=1,processingStatus="INDEXED",uploadDate=1))
            val conv = repo.insertConversation(ConversationEntity(userId=user,title="Saved chat",createdAt=1,updatedAt=1))
            repo.linkDocumentToConversation(conv,doc)
            repo.insertMessage(MessageEntity(conversationId=conv,role="user",content="Saved question",createdAt=1))
            db.close()
            // Reconstruct the exact v1 table shape by removing only v2's added columns.
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath,
                null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
                sqlite.execSQL("ALTER TABLE Conversations DROP COLUMN allDocuments")
                sqlite.execSQL("ALTER TABLE Conversations DROP COLUMN scopeRevision")
                sqlite.execSQL("ALTER TABLE Conversations DROP COLUMN scopeStartMessageId")
                sqlite.version = 1
            }
            db = Room.databaseBuilder(context,DocMindDatabase::class.java,name)
                .addMigrations(DocMindDatabase.MIGRATION_1_2).build()
            repo = DocMindRepository(db)
            assertEquals("Saved user",repo.findUserById(user)!!.fullName)
            val saved = repo.qaContext(user,conv)
            assertEquals("Saved chat",saved.conversation.title)
            assertFalse(saved.conversation.allDocuments)
            assertEquals(0L,saved.conversation.scopeRevision)
            assertEquals(listOf(doc),saved.documents.map { it.documentId })
            assertEquals("Saved question",saved.messages.single().content)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private suspend fun withIndexedDocuments(count: Int, run: suspend (DocMindRepository, Long, Long, List<Long>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DocMindDatabase::class.java).build()
        try {
            val repo = DocMindRepository(db)
            val user = repo.insertUser(UserEntity(fullName="QA",email="scope-service@test.com",passwordHash="x",createdAt="now"))
            val ids = (1..count).map { number ->
                val id = repo.insertDocument(DocumentEntity(userId=user,fileName="file-$number.md",
                    filePath="/not-needed",fileType="text/markdown",fileSize=1,processingStatus="READY",uploadDate=number.toLong()))
                repo.replaceQaIndex(user,id,listOf(DocumentChunkEntity(documentId=id,chunkIndex=0,
                    content="Evidence $number",embedding=com.example.docmind.model.qa.RagMath.encode(
                        if (number == count) floatArrayOf(1f,0f) else floatArrayOf(1f,1f)),
                    embeddingModel="test|2|v1|chunk-v1")))
                id
            }
            run(repo,user,repo.createQaConversation(user,"All"),ids)
        } finally { db.close() }
    }

    private fun api(answer: suspend (List<com.example.docmind.model.qa.QaHistory>, List<com.example.docmind.model.qa.QaSource>) -> com.example.docmind.model.qa.QaAnswer) =
        object : com.example.docmind.model.qa.QaApi {
            override suspend fun embed(texts: List<String>, query: Boolean): com.example.docmind.model.qa.EmbeddingBatch {
                assertTrue("Indexed documents must reuse stored vectors", query)
                return com.example.docmind.model.qa.EmbeddingBatch("test|2|v1",listOf(floatArrayOf(1f,0f)))
            }
            override suspend fun answer(question: String, history: List<com.example.docmind.model.qa.QaHistory>,
                sources: List<com.example.docmind.model.qa.QaSource>) = answer(history,sources)
        }

    @Test fun allScopeRetrievesAcrossMoreThanTenFilesAndOnlySendsBestSixSources() = runBlocking {
        withIndexedDocuments(12) { repo,user,conv,ids ->
            val api = api { _, sources ->
                assertEquals(6,sources.size)
                assertEquals("Evidence 12",sources.first().text)
                assertTrue(sources.first().id.startsWith("d${ids.last()}:"))
                com.example.docmind.model.qa.QaAnswer("Answer",listOf(sources.first().id),true)
            }
            val service = com.example.docmind.model.qa.DocumentQaService(repo,
                com.example.docmind.model.qa.DocumentTextExtractor(InstrumentationRegistry.getInstrumentation().targetContext),api)
            assertTrue(service.ask(user,conv,"Question",null,"Sources","None",{},{ }).contains("file-12.md"))
        }
    }

    @Test fun scopeChangeRejectsStaleAnswerEvenWhenSelectedFileIdsAreTheSame() = runBlocking {
        withIndexedDocuments(2) { repo,user,conv,ids ->
            val api = api { _, sources ->
                repo.updateQaScope(user,conv,false,ids)
                com.example.docmind.model.qa.QaAnswer("Stale",listOf(sources.first().id),true)
            }
            val service = com.example.docmind.model.qa.DocumentQaService(repo,
                com.example.docmind.model.qa.DocumentTextExtractor(InstrumentationRegistry.getInstrumentation().targetContext),api)
            assertTrue(runCatching { service.ask(user,conv,"Question",null,"Sources","None",{},{}) }.isFailure)
            assertEquals(listOf("user"),repo.getMessagesByConversationId(conv).first().map { it.role })
        }
    }

    @Test fun changingScopeDoesNotSendPreviousScopeHistoryToGemini() = runBlocking {
        withIndexedDocuments(2) { repo,user,conv,ids ->
            repo.insertMessage(MessageEntity(conversationId=conv,role="user",content="Old question",createdAt=1))
            repo.insertMessage(MessageEntity(conversationId=conv,role="assistant",content="Old private evidence",createdAt=2))
            repo.updateQaScope(user,conv,false,listOf(ids.first()))
            val api = api { history,sources ->
                assertTrue(history.isEmpty())
                com.example.docmind.model.qa.QaAnswer("Answer",listOf(sources.first().id),true)
            }
            val service = com.example.docmind.model.qa.DocumentQaService(repo,
                com.example.docmind.model.qa.DocumentTextExtractor(InstrumentationRegistry.getInstrumentation().targetContext),api)
            service.ask(user,conv,"New question",null,"Sources","None",{},{})
        }
    }

    @Test fun allScopeIsDynamicOwnedAndOnlyIndexedWhileCustomScopeNeverBroadens() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DocMindDatabase::class.java).build()
        try {
            val repo = DocMindRepository(db)
            val owner = repo.insertUser(UserEntity(fullName="A",email="scope-a@test.com",passwordHash="x",createdAt="now"))
            val other = repo.insertUser(UserEntity(fullName="B",email="scope-b@test.com",passwordHash="x",createdAt="now"))
            suspend fun doc(user: Long, name: String, status: String) =
                repo.insertDocument(DocumentEntity(userId=user,fileName=name,filePath="/test",fileType="text/plain",
                    fileSize=1,processingStatus=status,uploadDate=1))
            val first = doc(owner,"first.txt","INDEXED")
            val pending = doc(owner,"pending.txt","READY")
            val foreign = doc(other,"foreign.txt","INDEXED")
            val conv = repo.createQaConversation(owner,"New")
            assertEquals(setOf(first),repo.qaContext(owner,conv).documents.map { it.documentId }.toSet())
            assertEquals(setOf(first),repo.getCompleteConversation(conv).first()!!.documents.map { it.documentId }.toSet())
            val later = doc(owner,"later.txt","INDEXED")
            assertEquals(setOf(first,later),repo.qaContext(owner,conv).documents.map { it.documentId }.toSet())
            assertTrue(runCatching { repo.updateQaScope(other,conv,true,emptyList()) }.isFailure)
            assertTrue(runCatching { repo.updateQaScope(owner,conv,false,listOf(foreign)) }.isFailure)
            assertTrue(runCatching { repo.updateQaScope(owner,conv,false,listOf(pending)) }.isFailure)
            assertTrue(runCatching { repo.updateQaScope(owner,conv,false,emptyList()) }.isFailure)
            repo.updateQaScope(owner,conv,false,listOf(first))
            assertEquals(listOf(first),repo.qaContext(owner,conv).documents.map { it.documentId })
            repo.deleteDocument(repo.ownedDocument(owner,first))
            assertTrue(repo.qaContext(owner,conv).documents.isEmpty())
            repo.updateQaScope(owner,conv,true,emptyList())
            assertEquals(listOf(later),repo.qaContext(owner,conv).documents.map { it.documentId })
            val legacy = repo.insertConversation(ConversationEntity(userId=owner,title="Legacy",createdAt=1,updatedAt=1))
            repo.linkDocumentToConversation(legacy,pending)
            assertEquals(listOf(pending),repo.qaContext(owner,legacy).documents.map { it.documentId })
        } finally { db.close() }
    }
}
