package com.example.docmind.settings.storage

import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.CancellationException

data class StorageUsage(
    val dataBytes: Long,
    val cacheBytes: Long,
    val unreadableEntries: Int = 0
) {
    val totalBytes: Long get() = dataBytes + cacheBytes
}

data class CacheClearResult(val deletedBytes: Long, val failedEntries: Int = 0)

/**
 * Measures app-owned directories and removes only descendants of safe cache roots.
 * Call from a worker thread. Interrupted workers abort with CancellationException.
 * Links are retained and reported as unreadable/failed, never traversed.
 */
class StorageManager(
    dataDirectories: List<File>,
    cacheDirectories: List<File>
) {
    private val dataDirectories = dataDirectories.toList()
    private val cacheDirectories = cacheDirectories.toList()

    fun measure(): StorageUsage {
        checkInterrupted()
        val data = resolve(dataDirectories)
        val cache = resolve(cacheDirectories)
        var failures = data.failures + cache.failures
        val dataResult = measureRoots(deduplicate(data.roots))
        failures += dataResult.failures
        val safeCache = cache.roots.filter { candidate ->
            if (data.failures > 0 || data.roots.any { overlaps(it.path, candidate.path) }) {
                try {
                    if (candidate.file.exists()) failures++
                } catch (_: SecurityException) {
                    failures++
                }
                false
            } else true
        }
        val cacheResult = measureRoots(deduplicate(safeCache))
        return StorageUsage(dataResult.bytes, cacheResult.bytes, failures + cacheResult.failures)
    }

    fun clearCache(): CacheClearResult {
        checkInterrupted()
        val protected = resolve(dataDirectories)
        val caches = resolve(cacheDirectories)
        var failures = caches.failures
        var bytes = 0L
        val safe = caches.roots.filter { candidate ->
            try {
                if (!candidate.file.exists()) {
                    false
                } else if (protected.failures > 0 ||
                    protected.roots.any { overlaps(it.path, candidate.path) }) {
                    failures++
                    false
                } else true
            } catch (_: SecurityException) {
                failures++
                false
            }
        }
        // Preserve every allowlisted directory, including roots nested in another cache root.
        val preserved = safe.map { it.path }.toSet()
        for (root in deduplicate(safe)) {
            val pending = ArrayDeque<Entry>()
            pending.add(Entry(root.file))
            while (pending.isNotEmpty()) {
                checkInterrupted()
                val entry = pending.removeLast()
                val file = entry.file
                try {
                    val path = confinedPath(file, root.path)
                    if (path == null) {
                        failures++
                        continue
                    }
                    if (!file.exists()) continue
                    if (file.isDirectory) {
                        if (!entry.visited) {
                            val children = file.listFiles()
                            if (children == null) {
                                failures++
                                continue
                            }
                            pending.add(Entry(file, true))
                            children.forEach { pending.add(Entry(it)) }
                        } else if (path !in preserved && !file.delete()) {
                            failures++
                        }
                    } else if (file.isFile && path !in preserved) {
                        val length = file.length()
                        checkInterrupted()
                        // Recheck the complete path immediately before mutating it.
                        if (confinedPath(file, root.path) != path || !file.delete()) {
                            failures++
                        } else {
                            bytes += length
                        }
                    } else {
                        failures++
                    }
                } catch (_: IOException) {
                    failures++
                } catch (_: SecurityException) {
                    failures++
                }
            }
        }
        return CacheClearResult(bytes, failures)
    }

    private fun measureRoots(roots: List<Root>): Measurement {
        var bytes = 0L
        var failures = 0
        for (root in roots) {
            val pending = ArrayDeque<File>()
            pending.add(root.file)
            while (pending.isNotEmpty()) {
                checkInterrupted()
                val file = pending.removeLast()
                try {
                    if (confinedPath(file, root.path) == null) {
                        failures++
                        continue
                    }
                    if (!file.exists()) continue
                    if (file.isDirectory) {
                        val children = file.listFiles()
                        if (children == null) failures++
                        else children.forEach { pending.add(it) }
                    } else if (file.isFile && file.canRead()) {
                        bytes += file.length()
                    } else {
                        failures++
                    }
                } catch (_: IOException) {
                    failures++
                } catch (_: SecurityException) {
                    failures++
                }
            }
        }
        return Measurement(bytes, failures)
    }

    private fun resolve(directories: List<File>): Roots {
        var failures = 0
        val roots = mutableListOf<Root>()
        for (file in directories) {
            checkInterrupted()
            try {
                val canonical = file.canonicalFile
                // A canonical mismatch also rejects a link in an ancestor of a root.
                if (canonical != lexicalPath(file)) failures++
                else roots.add(Root(file, canonical))
            } catch (_: IOException) {
                failures++
            } catch (_: SecurityException) {
                failures++
            }
        }
        return Roots(roots, failures)
    }

    private fun deduplicate(roots: List<Root>): List<Root> =
        roots.distinctBy { it.path }.filter { root ->
            roots.none { other -> other.path != root.path && isWithin(root.path, other.path) }
        }

    private fun confinedPath(file: File, root: File): File? {
        val canonical = file.canonicalFile
        return canonical.takeIf { it == lexicalPath(file) && isWithin(it, root) }
    }

    private fun lexicalPath(file: File): File =
        File(file.absoluteFile.toURI().normalize())

    private fun overlaps(first: File, second: File): Boolean =
        isWithin(first, second) || isWithin(second, first)

    private fun isWithin(file: File, root: File): Boolean {
        var current: File? = file
        while (current != null) {
            if (current == root) return true
            current = current.parentFile
        }
        return false
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Storage operation interrupted")
    }

    private data class Root(val file: File, val path: File)
    private data class Roots(val roots: List<Root>, val failures: Int)
    private data class Entry(val file: File, val visited: Boolean = false)
    private data class Measurement(val bytes: Long, val failures: Int)
}
