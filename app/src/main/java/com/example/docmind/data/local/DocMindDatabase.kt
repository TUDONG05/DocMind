package com.example.docmind.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.docmind.data.local.dao.ConversationDao
import com.example.docmind.data.local.dao.ConversationDocumentDao
import com.example.docmind.data.local.dao.DocumentChunkDao
import com.example.docmind.data.local.dao.DocumentDao
import com.example.docmind.data.local.dao.MessageDao
import com.example.docmind.data.local.dao.UserDao
import com.example.docmind.data.local.entity.ConversationDocumentEntity
import com.example.docmind.data.local.entity.ConversationEntity
import com.example.docmind.data.local.entity.DocumentChunkEntity
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.data.local.entity.MessageEntity
import com.example.docmind.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class,
        ConversationEntity::class,
        ConversationDocumentEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class DocMindDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun documentDao(): DocumentDao
    abstract fun documentChunkDao(): DocumentChunkDao
    abstract fun conversationDao(): ConversationDao
    abstract fun conversationDocumentDao(): ConversationDocumentDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: DocMindDatabase? = null

        fun getInstance(context: Context): DocMindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DocMindDatabase::class.java,
                    "docmind_database.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
