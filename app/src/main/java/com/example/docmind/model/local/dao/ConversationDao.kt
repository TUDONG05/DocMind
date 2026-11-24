package com.example.docmind.model.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.docmind.model.local.entity.ConversationEntity
import com.example.docmind.model.local.relation.CompleteConversation
import com.example.docmind.model.local.relation.ConversationWithDocuments
import com.example.docmind.model.local.relation.ConversationWithMessages
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE Conversations SET title = :title WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun renameConversation(userId: Long, conversationId: Long, title: String): Int

    @Query("""
        UPDATE Conversations
        SET scopeRevision = scopeRevision + 1,
            scopeStartMessageId = COALESCE(
                (SELECT MAX(messageId) FROM Messages
                 WHERE Messages.conversationId = Conversations.conversationId), 0)
        WHERE userId = :userId AND (
            allDocuments = 1 OR EXISTS (
                SELECT 1 FROM ConversationDocuments
                WHERE ConversationDocuments.conversationId = Conversations.conversationId
                  AND ConversationDocuments.documentId = :documentId))
    """)
    suspend fun invalidateScopesForDeletedDocument(userId: Long, documentId: Long)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)

    @Query("DELETE FROM Conversations WHERE conversationId = :conversationId")
    suspend fun deleteConversationById(conversationId: Long)

    @Query("SELECT * FROM Conversations WHERE conversationId = :conversationId")
    fun getConversationById(conversationId: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM Conversations WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getConversationsByUserId(userId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM Conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Transaction
    @Query("SELECT * FROM Conversations WHERE conversationId = :conversationId")
    fun getConversationWithMessages(conversationId: Long): Flow<ConversationWithMessages?>

    @Transaction
    @Query("SELECT * FROM Conversations WHERE conversationId = :conversationId")
    fun getConversationWithDocuments(conversationId: Long): Flow<ConversationWithDocuments?>

    @Transaction
    @Query("SELECT * FROM Conversations WHERE conversationId = :conversationId")
    fun getCompleteConversation(conversationId: Long): Flow<CompleteConversation?>
}
