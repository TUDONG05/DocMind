package com.example.docmind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.docmind.data.local.entity.DocumentChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: DocumentChunkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>): List<Long>

    @Update
    suspend fun updateChunk(chunk: DocumentChunkEntity)

    @Delete
    suspend fun deleteChunk(chunk: DocumentChunkEntity)

    @Query("SELECT * FROM DocumentChunks WHERE documentId = :documentId ORDER BY chunkIndex ASC")
    fun getChunksByDocumentId(documentId: Long): Flow<List<DocumentChunkEntity>>

    @Query("DELETE FROM DocumentChunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: Long)
}
