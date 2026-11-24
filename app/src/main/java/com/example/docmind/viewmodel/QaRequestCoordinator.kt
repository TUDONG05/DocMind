package com.example.docmind.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

typealias QaRun = suspend (Long, String, Long?, (Long) -> Unit, (Boolean) -> Unit) -> Unit

/** Confined to the ViewModel's main dispatcher; callbacks from cancelled requests cannot mutate newer requests. */
class QaRequestCoordinator(
    private val scope: CoroutineScope,
    private val run: QaRun,
    private val errorMessage: (Throwable) -> String
) {
    private class Request(val token: Any, val job: Job)
    private val requests = mutableMapOf<Long, Request>()
    private val mutableStates = MutableStateFlow<Map<Long, QaUiState>>(emptyMap())
    val states: StateFlow<Map<Long, QaUiState>> = mutableStates.asStateFlow()

    fun start(id: Long, question: String, retryId: Long?): Boolean {
        if (question.isBlank() || question.length > 4000 || requests[id]?.job?.isActive == true) return false
        val token = Any()
        fun update(transform: (QaUiState) -> QaUiState) {
            if (requests[id]?.token === token) mutableStates.update { states ->
                states[id]?.let { states + (id to transform(it)) } ?: states
            }
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                run(id, question, retryId,
                    { questionId -> update { it.copy(questionId = questionId) } },
                    { indexing -> update { it.copy(indexing = indexing) } })
                if (requests[id]?.token === token) mutableStates.update { it - id }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                update { it.copy(error = errorMessage(error)) }
            } finally {
                update { it.copy(busy = false) }
                if (requests[id]?.token === token) requests.remove(id)
            }
        }
        requests[id] = Request(token, job)
        mutableStates.update { it + (id to QaUiState(busy = true, indexing = true,
            pendingQuestion = question, questionId = retryId)) }
        job.start()
        return true
    }

    fun cancel(id: Long) {
        requests.remove(id)?.job?.cancel()
        mutableStates.update { it - id }
    }

    fun cancelAll() {
        val jobs = requests.values.map { it.job }
        requests.clear()
        mutableStates.value = emptyMap()
        jobs.forEach { it.cancel() }
    }
}
