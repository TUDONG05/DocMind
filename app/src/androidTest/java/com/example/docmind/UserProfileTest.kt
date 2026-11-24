package com.example.docmind

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.docmind.model.local.DocMindDatabase
import com.example.docmind.model.local.entity.UserEntity
import com.example.docmind.model.local.profile.AvatarStorage
import com.example.docmind.model.repository.DocMindRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UserProfileTest {
    private lateinit var db: DocMindDatabase
    private lateinit var repository: DocMindRepository
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DocMindDatabase::class.java).build()
        repository = DocMindRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        files.forEach { it.delete() }
    }

    @Test
    fun updatingProfileChangesOnlyNameAndAvatarOfSelectedUser() = runBlocking {
        val original = UserEntity(
            fullName = "Old name", email = "profile@example.com",
            passwordHash = "existing-hash", avatarUrl = null, createdAt = "2026-11-23"
        )
        val id = repository.insertUser(original)
        val otherId = repository.insertUser(original.copy(email = "other@example.com"))
        val result = repository.updateProfile(id, "  Nguyễn Văn An  ", "/avatars/new.jpg")
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(original.copy(userId = id, fullName = "Nguyễn Văn An", avatarUrl = "/avatars/new.jpg"), updated)
        assertEquals(updated, repository.findUserById(id))
        assertEquals(original.copy(userId = otherId, email = "other@example.com"), repository.findUserById(otherId))
    }

    @Test
    fun blankNameDoesNotModifyStoredProfile() = runBlocking {
        val original = UserEntity(fullName = "Original", email = "blank@example.com", passwordHash = "hash", createdAt = "2026-11-23")
        val id = repository.insertUser(original)
        assertTrue(repository.updateProfile(id, "  \n ", "/avatars/new.jpg").isFailure)
        assertEquals(original.copy(userId = id), repository.findUserById(id))
    }

    @Test
    fun missingUserCannotBeUpdated() = runBlocking {
        assertTrue(repository.updateProfile(999L, "New name", null).isFailure)
    }

    @Test
    fun avatarIsResizedAndRemainsReadableWithoutOriginalFile() {
        val source = File.createTempFile("profile-source", ".png", context.cacheDir).also(files::add)
        val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val storage = AvatarStorage(context)
        val result = runCatching { storage.save(42L, Uri.fromFile(source)) }
        assertTrue(result.isSuccess)
        val saved = File(result.getOrThrow()).also(files::add)
        assertTrue(saved.canonicalPath.startsWith(File(context.filesDir, "avatars").canonicalPath + File.separator))
        assertTrue(source.delete())
        val decoded = storage.load(saved.absolutePath)
        assertNotNull(decoded)
        assertTrue(decoded!!.width <= 512 && decoded.height <= 512)
        assertEquals(2f, decoded.width.toFloat() / decoded.height, 0.05f)
        decoded.recycle()
    }

    @Test
    fun corruptImageIsRejectedBeforeSavingAvatar() {
        val source = File.createTempFile("profile-invalid", ".jpg", context.cacheDir).also(files::add)
        source.writeText("not an image")
        assertTrue(runCatching { AvatarStorage(context).save(42L, Uri.fromFile(source)) }.isFailure)
    }
}
