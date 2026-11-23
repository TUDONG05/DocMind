package com.example.docmind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.data.local.relation.DocumentWithChunks
import com.example.docmind.data.local.relation.DocumentWithConversations
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<DocumentEntity>): List<Long>

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM Documents WHERE documentId = :documentId")
    suspend fun deleteDocumentById(documentId: Long)

    @Query("SELECT * FROM Documents WHERE documentId = :documentId")
    fun getDocumentById(documentId: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM Documents WHERE userId = :userId ORDER BY uploadDate DESC")
    fun getDocumentsByUserId(userId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM Documents ORDER BY uploadDate DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Transaction
    @Query("SELECT * FROM Documents WHERE documentId = :documentId")
    fun getDocumentWithChunks(documentId: Long): Flow<DocumentWithChunks?>

    @Transaction
    @Query("SELECT * FROM Documents WHERE documentId = :documentId")
    fun getDocumentWithConversations(documentId: Long): Flow<DocumentWithConversations?>
}
