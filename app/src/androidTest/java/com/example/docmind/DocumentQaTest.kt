package com.example.docmind

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docmind.model.local.DocMindDatabase
import com.example.docmind.model.local.entity.*
import com.example.docmind.model.repository.DocMindRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentQaTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun ownedContextAndAtomicIndexEnforceUserBoundary() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DocMindDatabase::class.java).build()
        try {
            val repo = DocMindRepository(db)
            val owner = repo.insertUser(UserEntity(fullName="A",email="a@qa.test",passwordHash="x",createdAt="now"))
            val other = repo.insertUser(UserEntity(fullName="B",email="b@qa.test",passwordHash="x",createdAt="now"))
            val doc = repo.insertDocument(DocumentEntity(userId=owner,fileName="a.txt",filePath="/qa",fileType="text/plain",fileSize=5,processingStatus="READY",uploadDate=1))
            val conv = repo.insertConversation(ConversationEntity(userId=owner,title="QA",createdAt=1,updatedAt=1))
            repo.linkDocumentToConversation(conv,doc)
            assertTrue(runCatching { repo.qaContext(other,conv) }.isFailure)
            repo.replaceQaIndex(owner,doc,listOf(DocumentChunkEntity(documentId=doc,chunkIndex=0,content="Hello",embedding=byteArrayOf(1),embeddingModel="m")))
            assertEquals("Hello",repo.getChunksByDocumentId(doc).first().single().content)
            assertTrue(runCatching { repo.replaceQaIndex(other,doc,emptyList()) }.isFailure)
            assertEquals("Hello",repo.getChunksByDocumentId(doc).first().single().content)
        } finally { db.close() }
    }

    @Test fun retryReusesQuestionAndUpdatesConversationTime() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context,DocMindDatabase::class.java).build()
        try {
            val repo = DocMindRepository(db)
            val user = repo.insertUser(UserEntity(fullName="A",email="a@retry.test",passwordHash="x",createdAt="now"))
            val conv = repo.insertConversation(ConversationEntity(userId=user,title="QA",createdAt=1,updatedAt=1))
            val id = repo.beginQaQuestion(user,conv,"question",null)
            assertEquals(id,repo.beginQaQuestion(user,conv,"question",id))
            assertEquals(1,repo.getMessagesByConversationId(conv).first().size)
            repo.completeQaAnswer(user,conv,id,"answer")
            assertEquals(2,repo.getMessagesByConversationId(conv).first().size)
            assertTrue(runCatching { repo.completeQaAnswer(user,conv,id,"duplicate") }.isFailure)
            assertTrue(repo.qaContext(user,conv).conversation.updatedAt > 1)
        } finally { db.close() }
    }
}
