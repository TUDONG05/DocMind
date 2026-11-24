package com.example.docmind.model.repository

import androidx.annotation.StringRes
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import com.example.docmind.R
import com.example.docmind.model.local.DocMindDatabase
import com.example.docmind.model.local.entity.ConversationDocumentEntity
import com.example.docmind.model.local.entity.ConversationEntity
import com.example.docmind.model.local.entity.DocumentChunkEntity
import com.example.docmind.model.local.entity.DocumentEntity
import com.example.docmind.model.local.entity.MessageEntity
import com.example.docmind.model.local.entity.UserEntity
import com.example.docmind.model.local.relation.CompleteConversation
import com.example.docmind.model.local.relation.ConversationWithDocuments
import com.example.docmind.model.local.relation.DocumentWithChunks
import com.example.docmind.model.local.relation.DocumentWithConversations
import com.example.docmind.model.local.relation.UserWithConversations
import com.example.docmind.model.local.relation.UserWithDocuments
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf

class AuthException(@StringRes val messageRes: Int) : IllegalArgumentException()

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
            return Result.failure(AuthException(R.string.error_name_required))
        }
        if (trimmedEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(AuthException(R.string.error_invalid_email))
        }
        if (password.length < 6) {
            return Result.failure(AuthException(R.string.error_password_short))
        }

        val existingUser = userDao.findUserByEmail(trimmedEmail)
        if (existingUser != null) {
            return Result.failure(AuthException(R.string.error_email_exists))
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
            ?: return Result.failure(AuthException(R.string.error_create_user))

        return Result.success(createdUser)
    }

    suspend fun login(email: String, password: String): Result<UserEntity> {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isBlank()) {
            return Result.failure(AuthException(R.string.error_email_required))
        }
        if (password.isBlank()) {
            return Result.failure(AuthException(R.string.error_password_required))
        }

        val user = userDao.findUserByEmail(trimmedEmail)
            ?: return Result.failure(AuthException(R.string.error_account_missing))

        val isPasswordCorrect = com.example.docmind.util.PasswordHasher.verifyPassword(password, user.passwordHash)
        if (!isPasswordCorrect) {
            return Result.failure(AuthException(R.string.error_wrong_password))
        }

        return Result.success(user)
    }

    suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String): Result<Unit> {
        if (oldPassword.isBlank()) {
            return Result.failure(AuthException(R.string.error_old_password_required))
        }
        if (newPassword.length < 6) {
            return Result.failure(AuthException(R.string.error_new_password_short))
        }
        if (oldPassword == newPassword) {
            return Result.failure(AuthException(R.string.error_password_same))
        }

        val user = userDao.findUserById(userId)
            ?: return Result.failure(AuthException(R.string.error_user_missing))

        val isOldPasswordCorrect = com.example.docmind.util.PasswordHasher.verifyPassword(oldPassword, user.passwordHash)
        if (!isOldPasswordCorrect) {
            return Result.failure(AuthException(R.string.error_wrong_old_password))
        }

        val newHash = com.example.docmind.util.PasswordHasher.hashPassword(newPassword)
        userDao.updatePassword(userId, newHash)
        return Result.success(Unit)
    }

    suspend fun updateProfile(userId: Long, fullName: String, avatarUrl: String?): Result<UserEntity> {
        val name = fullName.trim()
        if (name.isBlank()) return Result.failure(AuthException(R.string.error_name_required))
        return try {
            Result.success(database.withTransaction {
                if (userDao.updateProfile(userId, name, avatarUrl) == 0) {
                    throw AuthException(R.string.error_user_missing)
                }
                userDao.findUserById(userId) ?: throw AuthException(R.string.error_user_missing)
            })
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    suspend fun createQaConversation(userId: Long, title: String): Long {
        require(userDao.findUserById(userId) != null)
        val now = System.currentTimeMillis()
        return insertConversation(ConversationEntity(userId = userId, title = title,
            createdAt = now, updatedAt = now, allDocuments = true))
    }

    suspend fun updateQaScope(userId: Long, conversationId: Long, allDocuments: Boolean, documentIds: List<Long>) {
        database.withTransaction {
            val context = conversationDao.getCompleteConversation(conversationId).first()
            require(context != null && context.conversation.userId == userId)
            require(context.documents.all { it.userId == userId })
            val ids = documentIds.distinct()
            require(if (allDocuments) ids.isEmpty() else ids.isNotEmpty())
            if (!allDocuments) ids.forEach { id ->
                val doc = ownedDocument(userId, id)
                require(doc.processingStatus == "INDEXED")
            }
            if (context.conversation.allDocuments == allDocuments &&
                (allDocuments || context.documents.map { it.documentId }.toSet() == ids.toSet())) return@withTransaction
            convDocDao.clearScopeLinks(conversationId)
            ids.forEach { linkDocumentToConversation(conversationId, it) }
            conversationDao.updateConversation(context.conversation.copy(allDocuments = allDocuments,
                scopeRevision = context.conversation.scopeRevision + 1,
                scopeStartMessageId = context.messages.maxOfOrNull { it.messageId } ?: 0,
                updatedAt = System.currentTimeMillis()))
        }
    }

    private fun resolveQaScope(context: CompleteConversation, owned: List<DocumentEntity>): CompleteConversation {
        require(context.documents.all { it.userId == context.conversation.userId })
        return if (context.conversation.allDocuments) context.copy(
            documents = owned.filter { it.processingStatus == "INDEXED" }) else context
    }

    suspend fun qaContext(userId: Long, conversationId: Long): CompleteConversation = database.withTransaction {
        val context = conversationDao.getCompleteConversation(conversationId).first()
        require(context != null && context.conversation.userId == userId)
        resolveQaScope(context, documentDao.getDocumentsByUserId(userId).first())
    }

    suspend fun replaceQaIndex(userId: Long, documentId: Long, chunks: List<DocumentChunkEntity>) {
        database.withTransaction {
            require(documentDao.getDocumentById(documentId).first()?.userId == userId)
            require(chunks.isNotEmpty() && chunks.all { it.documentId == documentId })
            chunkDao.deleteChunksByDocumentId(documentId)
            chunkDao.insertChunks(chunks)
            require(documentDao.updateIndexStatus(userId, documentId, "INDEXED") == 1)
        }
    }

    suspend fun beginQaQuestion(userId: Long, conversationId: Long, question: String, retryId: Long?, expectedScopeRevision: Long? = null): Long =
        database.withTransaction {
            val context = qaContext(userId, conversationId)
            require(expectedScopeRevision == null || context.conversation.scopeRevision == expectedScopeRevision)
            require(question.isNotBlank() && question.length <= 4000)
            if (retryId != null) {
                val last = context.messages.maxByOrNull { it.messageId }
                require(last?.messageId == retryId && last.role == "user" && last.content == question)
                retryId
            } else {
                val now = System.currentTimeMillis()
                val id = messageDao.insertMessage(MessageEntity(conversationId = conversationId,
                    role = "user", content = question, createdAt = now))
                conversationDao.updateConversation(context.conversation.copy(updatedAt = now))
                id
            }
        }

    suspend fun completeQaAnswer(userId: Long, conversationId: Long, questionId: Long, answer: String,
        expectedScopeRevision: Long? = null, expectedDocumentIds: Set<Long>? = null) {
        database.withTransaction {
            val context = qaContext(userId, conversationId)
            val last = context.messages.maxByOrNull { it.messageId }
            require(expectedScopeRevision == null || context.conversation.scopeRevision == expectedScopeRevision)
            require(expectedDocumentIds == null || context.documents.map { it.documentId }.toSet().containsAll(expectedDocumentIds))
            require(last?.messageId == questionId && last.role == "user" && answer.isNotBlank())
            val now = System.currentTimeMillis()
            messageDao.insertMessage(MessageEntity(conversationId = conversationId,
                role = "assistant", content = answer, createdAt = now))
            conversationDao.updateConversation(context.conversation.copy(updatedAt = now))
        }
    }

    suspend fun ownedDocument(userId: Long, documentId: Long): DocumentEntity {
        val document = documentDao.getDocumentById(documentId).first()
        require(document != null && document.userId == userId)
        return document
    }

    suspend fun updateIndexStatus(userId: Long, documentId: Long, status: String) {
        require(documentDao.updateIndexStatus(userId, documentId, status) == 1)
    }

    // --- Document Operations ---
    suspend fun insertDocument(document: DocumentEntity): Long = documentDao.insertDocument(document)
    suspend fun deleteDocument(document: DocumentEntity) = database.withTransaction {
        val stored = ownedDocument(document.userId, document.documentId)
        conversationDao.invalidateScopesForDeletedDocument(stored.userId, stored.documentId)
        documentDao.deleteDocument(stored)
    }
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

    suspend fun renameConversation(userId: Long, conversationId: Long, title: String) {
        val name = title.trim()
        require(name.isNotEmpty() && name.length <= 100)
        require(conversationDao.renameConversation(userId, conversationId, name) == 1)
    }
    suspend fun deleteConversation(conversation: ConversationEntity) = conversationDao.deleteConversation(conversation)
    fun getAllConversations(): Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
    fun getConversationsByUserId(userId: Long): Flow<List<ConversationEntity>> = conversationDao.getConversationsByUserId(userId)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getCompleteConversation(conversationId: Long): Flow<CompleteConversation?> =
        conversationDao.getCompleteConversation(conversationId).flatMapLatest { context ->
            if (context == null) flowOf(null)
            else documentDao.getDocumentsByUserId(context.conversation.userId).map { owned ->
                resolveQaScope(context, owned)
            }
        }
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
