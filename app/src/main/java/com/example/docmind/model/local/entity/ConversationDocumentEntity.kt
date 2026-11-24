package com.example.docmind.model.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ConversationDocuments",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["conversationId"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["conversationId"]),
        Index(value = ["conversationId", "documentId"], unique = true)
    ]
)
data class ConversationDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "conversationDocumentId")
    val conversationDocumentId: Long = 0,

    @ColumnInfo(name = "documentId")
    val documentId: Long,

    @ColumnInfo(name = "conversationId")
    val conversationId: Long,

    @ColumnInfo(name = "addedAt")
    val addedAt: Long
)
