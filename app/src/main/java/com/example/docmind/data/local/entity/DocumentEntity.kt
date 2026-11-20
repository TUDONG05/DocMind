package com.example.docmind.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Documents",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "documentId")
    val documentId: Long = 0,

    @ColumnInfo(name = "userId")
    val userId: Long,

    @ColumnInfo(name = "fileName")
    val fileName: String,

    @ColumnInfo(name = "filePath")
    val filePath: String,

    @ColumnInfo(name = "fileType")
    val fileType: String,

    @ColumnInfo(name = "fileSize")
    val fileSize: Long,

    @ColumnInfo(name = "processingStatus")
    val processingStatus: String,

    @ColumnInfo(name = "uploadDate")
    val uploadDate: Long
)
