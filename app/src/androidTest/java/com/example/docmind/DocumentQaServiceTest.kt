package com.example.docmind

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docmind.model.local.DocMindDatabase
import com.example.docmind.model.local.entity.*
import com.example.docmind.model.qa.*
import com.example.docmind.model.repository.DocMindRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentQaServiceTest {
    @Test fun failedGenerationRetriesWithoutDuplicatingAndOnlyUsesLinkedSources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir,"qa-service.txt").apply { writeText("Thời hạn nộp báo cáo là 30 ngày.") }
        val db = Room.inMemoryDatabaseBuilder(context,DocMindDatabase::class.java).build()
        try {
            val repo = DocMindRepository(db)
            val user = repo.insertUser(UserEntity(fullName="QA",email="qa@service.test",passwordHash="x",createdAt="now"))
            val doc = repo.insertDocument(DocumentEntity(userId=user,fileName=file.name,filePath=file.absolutePath,fileType="text/plain",fileSize=file.length(),processingStatus="READY",uploadDate=1))
            val conv = repo.insertConversation(ConversationEntity(userId=user,title="QA",createdAt=1,updatedAt=1))
            repo.linkDocumentToConversation(conv,doc)
            var fail = true
            var documentEmbeds = 0
            val api = object: QaApi {
                override suspend fun embed(texts: List<String>, query: Boolean): EmbeddingBatch {
                    if (!query) documentEmbeds++
                    return EmbeddingBatch("test|2|v1",texts.map { floatArrayOf(1f,0f) })
                }
                override suspend fun answer(question: String, history: List<QaHistory>, sources: List<QaSource>): QaAnswer {
                    assertEquals(1,sources.size)
                    assertTrue(sources.single().text.contains("30 ngày"))
                    if (fail) throw QaFailure("network")
                    return QaAnswer("Thời hạn là 30 ngày. [${sources.single().id}]",listOf(sources.single().id),true)
                }
            }
            val service = DocumentQaService(repo,DocumentTextExtractor(context),api)
            var id: Long? = null
            assertTrue(runCatching {
                service.ask(user,conv,"Thời hạn?",null,"Nguồn","Không có căn cứ",{id=it},{})
            }.isFailure)
            assertNotNull(id)
            fail = false
            val result = service.ask(user,conv,"Thời hạn?",id,"Nguồn","Không có căn cứ",{},{})
            assertTrue(result.contains("30 ngày"))
            assertTrue(result.contains(file.name))
            assertTrue(result.contains("[1]"))
            assertFalse(result.contains("[d$doc:c0]"))
            assertEquals(2,repo.getMessagesByConversationId(conv).first().size)
            assertEquals(1,documentEmbeds)
        } finally { db.close(); file.delete() }
    }
}
