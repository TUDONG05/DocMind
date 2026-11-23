package com.example.docmind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.docmind.data.local.entity.UserEntity
import com.example.docmind.data.local.relation.UserWithConversations
import com.example.docmind.data.local.relation.UserWithDocuments
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("SELECT * FROM Users WHERE userId = :userId")
    fun getUserById(userId: Long): Flow<UserEntity?>

    @Query("SELECT * FROM Users WHERE email = :email LIMIT 1")
    fun getUserByEmail(email: String): Flow<UserEntity?>

    @Query("SELECT * FROM Users WHERE email = :email LIMIT 1")
    suspend fun findUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM Users WHERE userId = :userId LIMIT 1")
    suspend fun findUserById(userId: Long): UserEntity?

    @Query("UPDATE Users SET passwordHash = :passwordHash WHERE userId = :userId")
    suspend fun updatePassword(userId: Long, passwordHash: String)

    @Query("UPDATE Users SET fullName = :fullName, avatarUrl = :avatarUrl WHERE userId = :userId")
    suspend fun updateProfile(userId: Long, fullName: String, avatarUrl: String?): Int

    @Query("SELECT * FROM Users ORDER BY userId DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Transaction
    @Query("SELECT * FROM Users WHERE userId = :userId")
    fun getUserWithDocuments(userId: Long): Flow<UserWithDocuments?>

    @Transaction
    @Query("SELECT * FROM Users WHERE userId = :userId")
    fun getUserWithConversations(userId: Long): Flow<UserWithConversations?>
}
