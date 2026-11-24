package com.example.docmind.viewmodel

import com.example.docmind.model.settings.storage.AndroidStorageDirectories
import com.example.docmind.model.settings.storage.CacheClearResult
import com.example.docmind.model.settings.storage.StorageUsage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class StorageUiState(
    val usage: StorageUsage? = null,
    val busy: Boolean = false,
    val clearing: Boolean = false,
    val clearResult: CacheClearResult? = null,
    val failed: Boolean = false
)

/** IO survives Activity recreation; LiveData only updates active screens. */
class StorageViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = AndroidStorageDirectories.create(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val cleared = AtomicBoolean(false)
    private val mutableState = MutableLiveData(StorageUiState())
    val state: LiveData<StorageUiState> = mutableState

    init {
        refresh()
    }

    fun refresh() = runOperation(clearCache = false)

    fun clearCache() = runOperation(clearCache = true)

    // Invoked on the UI thread, including the busy check to prevent duplicate requests.
    private fun runOperation(clearCache: Boolean) {
        if (cleared.get() || mutableState.value?.busy == true) return
        val previous = mutableState.value ?: StorageUiState()
        mutableState.value = previous.copy(
            busy = true, clearing = clearCache, clearResult = null, failed = false
        )
        executor.execute {
            try {
                val result = if (clearCache) manager.clearCache() else null
                val usage = manager.measure()
                if (!cleared.get()) mutableState.postValue(
                    StorageUiState(usage = usage, clearResult = result)
                )
            } catch (_: CancellationException) {
                // Cancellation happens when the ViewModel is permanently removed.
            } catch (_: Exception) {
                if (!cleared.get()) mutableState.postValue(StorageUiState(failed = true))
            }
        }
    }

    override fun onCleared() {
        cleared.set(true)
        executor.shutdownNow()
        super.onCleared()
    }
}
