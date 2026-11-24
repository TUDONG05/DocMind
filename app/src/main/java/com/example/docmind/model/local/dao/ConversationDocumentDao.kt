package com.example.docmind.model.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.docmind.model.local.entity.ConversationDocumentEntity
import com.example.docmind.model.local.entity.ConversationEntity
import com.example.docmind.model.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDocumentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkDocumentToConversation(conversationDocument: ConversationDocumentEntity): Long

    @Delete
    suspend fun unlinkDocument(conversationDocument: ConversationDocumentEntity)

    @Query("DELETE FROM ConversationDocuments WHERE conversationId = :conversationId")
    suspend fun clearScopeLinks(conversationId: Long)

    @Query("DELETE FROM ConversationDocuments WHERE conversationId = :conversationId AND documentId = :documentId")
    suspend fun unlinkDocumentFromConversation(conversationId: Long, documentId: Long)

    @Query("""
        SELECT d.* FROM Documents d
        INNER JOIN ConversationDocuments cd ON d.documentId = cd.documentId
        WHERE cd.conversationId = :conversationId
        ORDER BY cd.addedAt DESC
    """)
    fun getDocumentsForConversation(conversationId: Long): Flow<List<DocumentEntity>>

    @Query("""
        SELECT c.* FROM Conversations c
        INNER JOIN ConversationDocuments cd ON c.conversationId = cd.conversationId
        WHERE cd.documentId = :documentId
        ORDER BY cd.addedAt DESC
    """)
    fun getConversationsForDocument(documentId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM ConversationDocuments WHERE conversationId = :conversationId")
    fun getLinksForConversation(conversationId: Long): Flow<List<ConversationDocumentEntity>>
}
