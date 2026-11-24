package com.example.docmind.model.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.docmind.model.local.dao.ConversationDao
import com.example.docmind.model.local.dao.ConversationDocumentDao
import com.example.docmind.model.local.dao.DocumentChunkDao
import com.example.docmind.model.local.dao.DocumentDao
import com.example.docmind.model.local.dao.MessageDao
import com.example.docmind.model.local.dao.UserDao
import com.example.docmind.model.local.entity.ConversationDocumentEntity
import com.example.docmind.model.local.entity.ConversationEntity
import com.example.docmind.model.local.entity.DocumentChunkEntity
import com.example.docmind.model.local.entity.DocumentEntity
import com.example.docmind.model.local.entity.MessageEntity
import com.example.docmind.model.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class,
        ConversationEntity::class,
        ConversationDocumentEntity::class,
        MessageEntity::class,
    ],
    version = 2,
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
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Conversations ADD COLUMN allDocuments INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Conversations ADD COLUMN scopeRevision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Conversations ADD COLUMN scopeStartMessageId INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile
        private var INSTANCE: DocMindDatabase? = null

        fun getInstance(context: Context): DocMindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DocMindDatabase::class.java,
                    "docmind_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
