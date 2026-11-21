package com.example.docmind

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.docmind.data.local.DocMindDatabase
import com.example.docmind.data.local.dao.ConversationDao
import com.example.docmind.data.local.dao.ConversationDocumentDao
import com.example.docmind.data.local.dao.DocumentChunkDao
import com.example.docmind.data.local.dao.DocumentDao
import com.example.docmind.data.local.dao.MessageDao
import com.example.docmind.data.local.dao.UserDao
import com.example.docmind.data.local.entity.ConversationDocumentEntity
import com.example.docmind.data.local.entity.ConversationEntity
import com.example.docmind.data.local.entity.DocumentChunkEntity
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.data.local.entity.MessageEntity
import com.example.docmind.data.local.entity.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DocMindDatabaseTest {

    private lateinit var db: DocMindDatabase
    private lateinit var userDao: UserDao
    private lateinit var documentDao: DocumentDao
    private lateinit var chunkDao: DocumentChunkDao
    private lateinit var conversationDao: ConversationDao
    private lateinit var convDocDao: ConversationDocumentDao
    private lateinit var messageDao: MessageDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DocMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
        documentDao = db.documentDao()
        chunkDao = db.documentChunkDao()
        conversationDao = db.conversationDao()
        convDocDao = db.conversationDocumentDao()
        messageDao = db.messageDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testDatabaseRelationsAndLinking() = runBlocking {
        // 1. Create User
        val userId = userDao.insertUser(
            UserEntity(
                fullName = "Le Van B",
                email = "levanb@test.com",
                passwordHash = "hash9876",
                avatarUrl = "https://example.com/avatar.jpg",
                createdAt = "2026-11-20T21:30:00Z"
            )
        )
        assertTrue(userId > 0)

        // 2. Create Document for User (1-to-many: Users -> Documents)
        val docId = documentDao.insertDocument(
            DocumentEntity(
                userId = userId,
                fileName = "DocMind_Specification.pdf",
                filePath = "/data/docs/DocMind_Specification.pdf",
                fileType = "application/pdf",
                fileSize = 2048L,
                processingStatus = "READY",
                uploadDate = System.currentTimeMillis()
            )
        )
        assertTrue(docId > 0)

        // 3. Create DocumentChunk for Document (1-to-many: Documents -> DocumentChunks)
        val chunkId = chunkDao.insertChunk(
            DocumentChunkEntity(
                documentId = docId,
                chunkIndex = 0,
                content = "Room SQLite Database implementation with Foreign Keys...",
                pageNumber = 1,
                embedding = byteArrayOf(10, 20, 30),
                embeddingModel = "gemini-nano-embed"
            )
        )
        assertTrue(chunkId > 0)

        // 4. Create Conversation for User (1-to-many: Users -> Conversations)
        val convId = conversationDao.insertConversation(
            ConversationEntity(
                userId = userId,
                title = "Room Database Q&A",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        assertTrue(convId > 0)

        // 5. Link Document to Conversation via Junction (Many-to-Many: Conversations <-> Documents)
        val linkId = convDocDao.linkDocumentToConversation(
            ConversationDocumentEntity(
                conversationId = convId,
                documentId = docId,
                addedAt = System.currentTimeMillis()
            )
        )
        assertTrue(linkId > 0)

        // 6. Create Message for Conversation (1-to-many: Conversations -> Messages)
        val msgId = messageDao.insertMessage(
            MessageEntity(
                conversationId = convId,
                role = "user",
                content = "How are foreign keys structured?",
                createdAt = System.currentTimeMillis()
            )
        )
        assertTrue(msgId > 0)

        // --- VERIFY RELATIONS ---

        // Verify UserWithDocuments
        val userWithDocs = userDao.getUserWithDocuments(userId).first()
        assertNotNull(userWithDocs)
        assertEquals(1, userWithDocs?.documents?.size)
        assertEquals("DocMind_Specification.pdf", userWithDocs?.documents?.first()?.fileName)

        // Verify DocumentWithChunks
        val docWithChunks = documentDao.getDocumentWithChunks(docId).first()
        assertNotNull(docWithChunks)
        assertEquals(1, docWithChunks?.chunks?.size)
        assertEquals(0, docWithChunks?.chunks?.first()?.chunkIndex)

        // Verify ConversationWithDocuments (Many-to-Many)
        val convWithDocs = conversationDao.getConversationWithDocuments(convId).first()
        assertNotNull(convWithDocs)
        assertEquals(1, convWithDocs?.documents?.size)
        assertEquals("DocMind_Specification.pdf", convWithDocs?.documents?.first()?.fileName)

        // Verify CompleteConversation
        val completeConv = conversationDao.getCompleteConversation(convId).first()
        assertNotNull(completeConv)
        assertEquals(1, completeConv?.messages?.size)
        assertEquals(1, completeConv?.documents?.size)
        assertEquals("How are foreign keys structured?", completeConv?.messages?.first()?.content)

        // --- TEST CASCADE DELETE ---
        // Deleting conversation should cascade-delete messages and junction links
        conversationDao.deleteConversationById(convId)
        val messagesAfterDelete = messageDao.getMessagesByConversationId(convId).first()
        assertTrue(messagesAfterDelete.isEmpty())

        // The document itself should still exist!
        val docStillExists = documentDao.getDocumentById(docId).first()
        assertNotNull(docStillExists)
    }

    @Test
    fun testAuthenticationFlow() = runBlocking {
        val repo = com.example.docmind.data.repository.DocMindRepository(db)

        // 1. Register new user
        val regResult = repo.register("Tran Thi C", "tranthic@example.com", "secret123")
        assertTrue(regResult.isSuccess)
        val user = regResult.getOrThrow()
        assertEquals("Tran Thi C", user.fullName)
        assertEquals("tranthic@example.com", user.email)

        // 2. Register duplicate email should fail
        val dupResult = repo.register("Another Person", "tranthic@example.com", "another_pass")
        assertTrue(dupResult.isFailure)

        // 3. Login with wrong password should fail
        val wrongLogin = repo.login("tranthic@example.com", "wrong_password")
        assertTrue(wrongLogin.isFailure)

        // 4. Login with correct password should succeed
        val correctLogin = repo.login("tranthic@example.com", "secret123")
        assertTrue(correctLogin.isSuccess)

        // 5. Change password with wrong old password should fail
        val wrongChange = repo.changePassword(user.userId, "bad_old_pass", "new_secret_456")
        assertTrue(wrongChange.isFailure)

        // 6. Change password with correct old password should succeed
        val correctChange = repo.changePassword(user.userId, "secret123", "new_secret_456")
        assertTrue(correctChange.isSuccess)

        // 7. Old password no longer works
        val oldLogin = repo.login("tranthic@example.com", "secret123")
        assertTrue(oldLogin.isFailure)

        // 8. New password works
        val newLogin = repo.login("tranthic@example.com", "new_secret_456")
        assertTrue(newLogin.isSuccess)
    }
}
