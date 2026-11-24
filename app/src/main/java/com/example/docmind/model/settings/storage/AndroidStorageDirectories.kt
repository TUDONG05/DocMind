package com.example.docmind.model.settings.storage

import android.content.Context
import java.io.File

/** App content only: APK and codeCacheDir are deliberately excluded. */
object AndroidStorageDirectories {
    fun create(context: Context): StorageManager {
        val app = context.applicationContext
        val data = buildList {
            add(app.filesDir)
            app.getDatabasePath("storage-directory-probe").parentFile?.let { add(it) }
            add(File(app.applicationInfo.dataDir, "shared_prefs"))
            add(app.noBackupFilesDir)
            addAll(app.getExternalFilesDirs(null).filterNotNull())
        }
        val cache = buildList {
            add(app.cacheDir)
            addAll(app.externalCacheDirs.filterNotNull())
        }
        return StorageManager(data.map(::normalizePlatformParent), cache.map(::normalizePlatformParent))
    }

    private fun normalizePlatformParent(directory: File): File {
        // Android may expose /data/user/0 through a platform alias. Resolve only the
        // parent: a symbolic link replacing the cache directory itself stays visible
        // to StorageManager and is refused rather than becoming an allowed target.
        return try {
            directory.parentFile?.canonicalFile?.let { File(it, directory.name) } ?: directory
        } catch (_: java.io.IOException) {
            directory
        } catch (_: SecurityException) {
            directory
        }
    }
}
