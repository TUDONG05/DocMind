package com.example.docmind.model.settings.storage

import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException

class StorageManagerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun measuresNestedDataAndCacheBytes() {
        val data = temporary.newFolder("data")
        val cache = temporary.newFolder("cache")
        File(data, "one").writeBytes(ByteArray(7))
        File(data, "nested").mkdir()
        File(data, "nested/two").writeBytes(ByteArray(11))
        File(cache, "temporary").writeBytes(ByteArray(5))

        val usage = StorageManager(listOf(data), listOf(cache)).measure()

        assertEquals(18L, usage.dataBytes)
        assertEquals(5L, usage.cacheBytes)
        assertEquals(23L, usage.totalBytes)
        assertEquals(0, usage.unreadableEntries)
    }

    @Test fun emptyAndMissingDirectoriesHaveNoUsageOrFailures() {
        val empty = temporary.newFolder("empty")
        val missing = File(temporary.root, "missing")
        val manager = StorageManager(listOf(empty, missing), listOf(missing))
        assertEquals(StorageUsage(0, 0), manager.measure())
        assertEquals(CacheClearResult(0), manager.clearCache())
    }

    @Test fun clearDeletesNestedCacheButPreservesRootAndData() {
        val data = temporary.newFolder("data")
        val cache = temporary.newFolder("cache")
        val original = File(data, "original").apply { writeText("keep") }
        File(cache, "nested").mkdir()
        File(cache, "nested/a").writeBytes(ByteArray(13))
        File(cache, "b").writeBytes(ByteArray(3))
        val manager = StorageManager(listOf(data), listOf(cache))

        assertEquals(CacheClearResult(16), manager.clearCache())
        assertTrue(cache.isDirectory)
        assertTrue(cache.listFiles()!!.isEmpty())
        assertEquals("keep", original.readText())
        assertEquals(CacheClearResult(0), manager.clearCache())
    }

    @Test fun duplicateAndNestedRootsAreCountedAndClearedOnce() {
        val cache = temporary.newFolder("cache")
        val nested = File(cache, "nested").apply { mkdir() }
        File(nested, "one").writeBytes(ByteArray(9))
        val manager = StorageManager(emptyList(), listOf(nested, cache, cache))
        assertEquals(9L, manager.measure().cacheBytes)
        assertEquals(CacheClearResult(9), manager.clearCache())
        assertTrue(cache.isDirectory)
    }

    @Test fun overlappingDataRootsAreCountedOnce() {
        val data = temporary.newFolder("data")
        val nested = File(data, "nested").apply { mkdir() }
        File(nested, "one").writeBytes(ByteArray(4))
        assertEquals(4L, StorageManager(listOf(nested, data, data), emptyList()).measure().dataBytes)
    }

    @Test fun refusesCacheEqualToData() {
        val data = temporary.newFolder("data")
        val original = File(data, "one").apply { writeText("keep") }
        assertTrue(StorageManager(listOf(data), listOf(data)).clearCache().failedEntries > 0)
        assertTrue(original.exists())
    }

    @Test fun refusesCacheContainingProtectedData() {
        val cache = temporary.newFolder("cache")
        val data = File(cache, "data").apply { mkdir() }
        val original = File(data, "one").apply { writeText("keep") }
        assertTrue(StorageManager(listOf(data), listOf(cache)).clearCache().failedEntries > 0)
        assertTrue(original.exists())
    }

    @Test fun refusesCacheInsideProtectedData() {
        val data = temporary.newFolder("data")
        val cache = File(data, "cache").apply { mkdir() }
        val original = File(cache, "one").apply { writeText("keep") }
        assertTrue(StorageManager(listOf(data), listOf(cache)).clearCache().failedEntries > 0)
        assertTrue(original.exists())
    }

    @Test fun externalSymbolicLinkIsNotFollowedOrDeleted() {
        val data = temporary.newFolder("outside")
        val cache = temporary.newFolder("cache")
        val original = File(data, "one").apply { writeBytes(ByteArray(17)) }
        val link = File(cache, "link")
        try {
            Files.createSymbolicLink(link.toPath(), data.toPath())
        } catch (error: IOException) {
            assumeNoException(error)
        } catch (error: UnsupportedOperationException) {
            assumeNoException(error)
        } catch (error: SecurityException) {
            assumeNoException(error)
        }
        val manager = StorageManager(emptyList(), listOf(cache))
        assertEquals(0L, manager.measure().cacheBytes)
        val result = manager.clearCache()
        assertEquals(0L, result.deletedBytes)
        assertTrue(result.failedEntries > 0)
        assertTrue(original.exists())
        assertTrue(Files.isSymbolicLink(link.toPath()))
    }

    @Test fun unreadableDirectoryIsReported() {
        val directory = temporary.newFolder("unreadable")
        val unreadable = object : File(directory.absolutePath) {
            override fun listFiles(): Array<File>? = null
        }
        val manager = StorageManager(emptyList(), listOf(unreadable))
        assertTrue(manager.measure().unreadableEntries > 0)
        assertTrue(manager.clearCache().failedEntries > 0)
    }

    @Test fun unsuccessfulDeleteDoesNotClaimDeletedBytes() {
        val directory = temporary.newFolder("cache")
        val file = File(directory, "retained").apply { writeBytes(ByteArray(19)) }
        val undeletable = object : File(file.absolutePath) {
            override fun delete(): Boolean = false
        }
        val root = object : File(directory.absolutePath) {
            override fun listFiles(): Array<File> = arrayOf(undeletable)
        }
        val result = StorageManager(emptyList(), listOf(root)).clearCache()
        assertEquals(0L, result.deletedBytes)
        assertTrue(result.failedEntries > 0)
        assertTrue(file.exists())
    }

    @Test fun interruptedOperationStopsBeforeDeleting() {
        val cache = temporary.newFolder("cache")
        val file = File(cache, "retained").apply { writeText("keep") }
        Thread.currentThread().interrupt()
        try {
            StorageManager(emptyList(), listOf(cache)).clearCache()
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(file.exists())
        } finally {
            Thread.interrupted()
        }
    }
}
