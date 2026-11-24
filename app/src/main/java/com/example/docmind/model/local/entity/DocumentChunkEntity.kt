package com.example.docmind.model.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "DocumentChunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"])
    ]
)
data class DocumentChunkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chunkId")
    val chunkId: Long = 0,

    @ColumnInfo(name = "documentId")
    val documentId: Long,

    @ColumnInfo(name = "chunkIndex")
    val chunkIndex: Int,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "pageNumber")
    val pageNumber: Int? = null,

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,

    @ColumnInfo(name = "embeddingModel")
    val embeddingModel: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocumentChunkEntity

        if (chunkId != other.chunkId) return false
        if (documentId != other.documentId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (content != other.content) return false
        if (pageNumber != other.pageNumber) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (embeddingModel != other.embeddingModel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + content.hashCode()
        result = 31 * result + (pageNumber ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + (embeddingModel?.hashCode() ?: 0)
        return result
    }
}
