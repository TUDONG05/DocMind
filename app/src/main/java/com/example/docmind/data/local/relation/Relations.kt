package com.example.docmind.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.docmind.data.local.entity.ConversationDocumentEntity
import com.example.docmind.data.local.entity.ConversationEntity
import com.example.docmind.data.local.entity.DocumentChunkEntity
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.data.local.entity.MessageEntity
import com.example.docmind.data.local.entity.UserEntity

/**
 * 1-to-many relationship: User -> Documents
 */
data class UserWithDocuments(
    @Embedded
    val user: UserEntity,
    @Relation(
        parentColumn = "userId",
        entityColumn = "userId"
    )
    val documents: List<DocumentEntity>
)

/**
 * 1-to-many relationship: User -> Conversations
 */
data class UserWithConversations(
    @Embedded
    val user: UserEntity,
    @Relation(
        parentColumn = "userId",
        entityColumn = "userId"
    )
    val conversations: List<ConversationEntity>
)

/**
 * 1-to-many relationship: Document -> DocumentChunks
 */
data class DocumentWithChunks(
    @Embedded
    val document: DocumentEntity,
    @Relation(
        parentColumn = "documentId",
        entityColumn = "documentId"
    )
    val chunks: List<DocumentChunkEntity>
)

/**
 * 1-to-many relationship: Conversation -> Messages
 */
data class ConversationWithMessages(
    @Embedded
    val conversation: ConversationEntity,
    @Relation(
        parentColumn = "conversationId",
        entityColumn = "conversationId"
    )
    val messages: List<MessageEntity>
)

/**
 * Many-to-many relationship: Conversation <-> Documents via ConversationDocuments
 */
data class ConversationWithDocuments(
    @Embedded
    val conversation: ConversationEntity,
    @Relation(
        parentColumn = "conversationId",
        entityColumn = "documentId",
        associateBy = Junction(ConversationDocumentEntity::class)
    )
    val documents: List<DocumentEntity>
)

/**
 * Many-to-many relationship: Document <-> Conversations via ConversationDocuments
 */
data class DocumentWithConversations(
    @Embedded
    val document: DocumentEntity,
    @Relation(
        parentColumn = "documentId",
        entityColumn = "conversationId",
        associateBy = Junction(ConversationDocumentEntity::class)
    )
    val conversations: List<ConversationEntity>
)

/**
 * Complete Conversation including both messages and linked documents
 */
data class CompleteConversation(
    @Embedded
    val conversation: ConversationEntity,
    @Relation(
        parentColumn = "conversationId",
        entityColumn = "conversationId"
    )
    val messages: List<MessageEntity>,
    @Relation(
        parentColumn = "conversationId",
        entityColumn = "documentId",
        associateBy = Junction(ConversationDocumentEntity::class)
    )
    val documents: List<DocumentEntity>
)
