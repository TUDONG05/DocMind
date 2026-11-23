package com.example.docmind.data.repository

import com.example.docmind.data.local.DocMindDatabase
import com.example.docmind.data.local.entity.ConversationDocumentEntity
import com.example.docmind.data.local.entity.ConversationEntity
import com.example.docmind.data.local.entity.DocumentChunkEntity
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.data.local.entity.MessageEntity
import com.example.docmind.data.local.entity.UserEntity
import com.example.docmind.data.local.relation.CompleteConversation
import com.example.docmind.data.local.relation.ConversationWithDocuments
import com.example.docmind.data.local.relation.DocumentWithChunks
import com.example.docmind.data.local.relation.DocumentWithConversations
import com.example.docmind.data.local.relation.UserWithConversations
import com.example.docmind.data.local.relation.UserWithDocuments
import kotlinx.coroutines.flow.Flow

class DocMindRepository(private val database: DocMindDatabase) {

    private val userDao = database.userDao()
    private val documentDao = database.documentDao()
    private val chunkDao = database.documentChunkDao()
    private val conversationDao = database.conversationDao()
    private val convDocDao = database.conversationDocumentDao()
    private val messageDao = database.messageDao()

    // --- User & Auth Operations ---
    suspend fun insertUser(user: UserEntity): Long = userDao.insertUser(user)
    fun getAllUsers(): Flow<List<UserEntity>> = userDao.getAllUsers()
    fun getUserById(userId: Long): Flow<UserEntity?> = userDao.getUserById(userId)
    suspend fun findUserById(userId: Long): UserEntity? = userDao.findUserById(userId)
    suspend fun findUserByEmail(email: String): UserEntity? = userDao.findUserByEmail(email)
    fun getUserWithDocuments(userId: Long): Flow<UserWithDocuments?> = userDao.getUserWithDocuments(userId)
    fun getUserWithConversations(userId: Long): Flow<UserWithConversations?> = userDao.getUserWithConversations(userId)

    suspend fun register(fullName: String, email: String, password: String): Result<UserEntity> {
        val trimmedName = fullName.trim()
        val trimmedEmail = email.trim().lowercase()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Họ và tên không được để trống!"))
        }
        if (trimmedEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Email không đúng định dạng!"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự!"))
        }

        val existingUser = userDao.findUserByEmail(trimmedEmail)
        if (existingUser != null) {
            return Result.failure(IllegalStateException("Email '$trimmedEmail' đã tồn tại trong hệ thống!"))
        }

        val passwordHash = com.example.docmind.util.PasswordHasher.hashPassword(password)
        val newUser = UserEntity(
            fullName = trimmedName,
            email = trimmedEmail,
            passwordHash = passwordHash,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        )

        val insertedId = userDao.insertUser(newUser)
        val createdUser = userDao.findUserById(insertedId)
            ?: return Result.failure(IllegalStateException("Không thể tạo người dùng!"))

        return Result.success(createdUser)
    }

    suspend fun login(email: String, password: String): Result<UserEntity> {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập Email!"))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập mật khẩu!"))
        }

        val user = userDao.findUserByEmail(trimmedEmail)
            ?: return Result.failure(IllegalArgumentException("Tài khoản với email này không tồn tại!"))

        val isPasswordCorrect = com.example.docmind.util.PasswordHasher.verifyPassword(password, user.passwordHash)
        if (!isPasswordCorrect) {
            return Result.failure(IllegalArgumentException("Mật khẩu không chính xác!"))
        }

        return Result.success(user)
    }

    suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String): Result<Unit> {
        if (oldPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập mật khẩu cũ!"))
        }
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự!"))
        }
        if (oldPassword == newPassword) {
            return Result.failure(IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại!"))
        }

        val user = userDao.findUserById(userId)
            ?: return Result.failure(IllegalArgumentException("Người dùng không tồn tại!"))

        val isOldPasswordCorrect = com.example.docmind.util.PasswordHasher.verifyPassword(oldPassword, user.passwordHash)
        if (!isOldPasswordCorrect) {
            return Result.failure(IllegalArgumentException("Mật khẩu hiện tại không chính xác!"))
        }

        val newHash = com.example.docmind.util.PasswordHasher.hashPassword(newPassword)
        userDao.updatePassword(userId, newHash)
        return Result.success(Unit)
    }

    // --- Document Operations ---
    suspend fun insertDocument(document: DocumentEntity): Long = documentDao.insertDocument(document)
    suspend fun deleteDocument(document: DocumentEntity) = documentDao.deleteDocument(document)
    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    fun getDocumentsByUserId(userId: Long): Flow<List<DocumentEntity>> = documentDao.getDocumentsByUserId(userId)
    fun getDocumentWithChunks(documentId: Long): Flow<DocumentWithChunks?> = documentDao.getDocumentWithChunks(documentId)
    fun getDocumentWithConversations(documentId: Long): Flow<DocumentWithConversations?> = documentDao.getDocumentWithConversations(documentId)

    // --- Document Chunk Operations ---
    suspend fun insertChunk(chunk: DocumentChunkEntity): Long = chunkDao.insertChunk(chunk)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>): List<Long> = chunkDao.insertChunks(chunks)
    fun getChunksByDocumentId(documentId: Long): Flow<List<DocumentChunkEntity>> = chunkDao.getChunksByDocumentId(documentId)

    // --- Conversation Operations ---
    suspend fun insertConversation(conversation: ConversationEntity): Long = conversationDao.insertConversation(conversation)
    suspend fun deleteConversation(conversation: ConversationEntity) = conversationDao.deleteConversation(conversation)
    fun getAllConversations(): Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
    fun getConversationsByUserId(userId: Long): Flow<List<ConversationEntity>> = conversationDao.getConversationsByUserId(userId)
    fun getCompleteConversation(conversationId: Long): Flow<CompleteConversation?> = conversationDao.getCompleteConversation(conversationId)
    fun getConversationWithDocuments(conversationId: Long): Flow<ConversationWithDocuments?> = conversationDao.getConversationWithDocuments(conversationId)

    // --- Conversation-Document Link Operations (Many-to-Many) ---
    suspend fun linkDocumentToConversation(conversationId: Long, documentId: Long): Long {
        return convDocDao.linkDocumentToConversation(
            ConversationDocumentEntity(
                conversationId = conversationId,
                documentId = documentId,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun unlinkDocumentFromConversation(conversationId: Long, documentId: Long) {
        convDocDao.unlinkDocumentFromConversation(conversationId, documentId)
    }

    fun getDocumentsForConversation(conversationId: Long): Flow<List<DocumentEntity>> =
        convDocDao.getDocumentsForConversation(conversationId)

    // --- Message Operations ---
    suspend fun insertMessage(message: MessageEntity): Long = messageDao.insertMessage(message)
    fun getMessagesByConversationId(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesByConversationId(conversationId)

    /**
     * Seeds dummy/test data to demonstrate the linked database
     */
    suspend fun seedSampleData() {
        // 1. Create a User
        val userId = userDao.insertUser(
            UserEntity(
                fullName = "Nguyen Van A",
                email = "nguyenvana_${System.currentTimeMillis()}@example.com",
                passwordHash = "hashed_pw_123456",
                avatarUrl = "https://example.com/avatar.png",
                createdAt = "2026-11-20T21:00:00Z"
            )
        )

        // 2. Create 2 Documents for this User
        val doc1Id = documentDao.insertDocument(
            DocumentEntity(
                userId = userId,
                fileName = "AI_Research_Report.pdf",
                filePath = "/storage/emulated/0/Documents/AI_Research_Report.pdf",
                fileType = "application/pdf",
                fileSize = 1048576L,
                processingStatus = "COMPLETED",
                uploadDate = System.currentTimeMillis()
            )
        )

        val doc2Id = documentDao.insertDocument(
            DocumentEntity(
                userId = userId,
                fileName = "Kotlin_Architecture_Guide.docx",
                filePath = "/storage/emulated/0/Documents/Kotlin_Architecture_Guide.docx",
                fileType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                fileSize = 524288L,
                processingStatus = "COMPLETED",
                uploadDate = System.currentTimeMillis()
            )
        )

        // 3. Create Document Chunks for Document 1
        chunkDao.insertChunks(
            listOf(
                DocumentChunkEntity(
                    documentId = doc1Id,
                    chunkIndex = 0,
                    content = "Introduction to On-device AI and Jetpack Compose Architecture...",
                    pageNumber = 1,
                    embedding = byteArrayOf(1, 2, 3, 4, 5),
                    embeddingModel = "text-embedding-004"
                ),
                DocumentChunkEntity(
                    documentId = doc1Id,
                    chunkIndex = 1,
                    content = "Detailed analysis of SQLite Room database relations and indexing...",
                    pageNumber = 2,
                    embedding = byteArrayOf(6, 7, 8, 9, 10),
                    embeddingModel = "text-embedding-004"
                )
            )
        )

        // 4. Create a Conversation
        val convId = conversationDao.insertConversation(
            ConversationEntity(
                userId = userId,
                title = "Discussion on AI & Room Architecture",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // 5. Link both Documents to this Conversation (Many-to-Many junction)
        linkDocumentToConversation(convId, doc1Id)
        linkDocumentToConversation(convId, doc2Id)

        // 6. Add Messages to the Conversation
        messageDao.insertMessage(
            MessageEntity(
                conversationId = convId,
                role = "user",
                content = "Can you summarize the AI research report?",
                createdAt = System.currentTimeMillis()
            )
        )
        messageDao.insertMessage(
            MessageEntity(
                conversationId = convId,
                role = "assistant",
                content = "Certainly! The AI Research Report discusses local SQLite Room integration with foreign keys and Jetpack Compose.",
                createdAt = System.currentTimeMillis() + 1000
            )
        )
    }
}
