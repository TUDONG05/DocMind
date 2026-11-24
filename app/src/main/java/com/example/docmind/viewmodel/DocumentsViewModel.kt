package com.example.docmind.viewmodel

import androidx.lifecycle.ViewModel
import com.example.docmind.model.session.UserSession
import androidx.lifecycle.viewModelScope
import com.example.docmind.R
import com.example.docmind.model.local.entity.*
import com.example.docmind.model.local.relation.*
import com.example.docmind.model.repository.DocMindRepository
import com.example.docmind.model.qa.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DocumentsViewModel(
    private val repository: DocMindRepository,
    private val documentIndexer: DocumentIndexer,
    private val session: UserSession,
    private val messages: UiMessages,
    private val qaCancellation: QaCancellation
) : ViewModel() {
    private val _currentUser get() = session.currentUser
    private val work = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
    private fun message(id: Int, vararg args: Any) = messages.text(id, *args)
    val documentIndexProgress = documentIndexer.progress
    private val indexJobs = mutableMapOf<Long, Job>()
    private val uploadJobs = mutableSetOf<Job>()
    // Documents belonging to the logged-in user
    private val _userDocuments = MutableStateFlow<List<DocumentEntity>>(emptyList())
    val userDocuments = _userDocuments.asStateFlow()

    // Active document view
    private val _selectedDocumentId = MutableStateFlow<Long?>(null)
    val selectedDocumentId: StateFlow<Long?> = _selectedDocumentId.asStateFlow()

    private val _selectedDocumentWithChunks = MutableStateFlow<DocumentWithChunks?>(null)
    val selectedDocumentWithChunks = _selectedDocumentWithChunks.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSessionData() {
        viewModelScope.launch {
            session.identity.flatMapLatest { identity ->
                val userId = identity.userId
                if (userId != null) repository.getDocumentsByUserId(userId).map { identity to it }
                else flowOf(identity to emptyList())
            }.collect { (identity, items) ->
                if (identity.generation == session.generation) _userDocuments.value = items
            }
        }
        viewModelScope.launch {
            combine(session.identity, _selectedDocumentId) { identity, id -> identity to id }
                .flatMapLatest { (identity, id) ->
                    if (id != null && identity.userId != null) repository.getDocumentWithChunks(id).map { detail ->
                        Triple(identity, id, detail?.takeIf { it.document.userId == identity.userId })
                    } else flowOf(Triple(identity, id, null))
                }.collect { (identity, id, detail) ->
                    if (identity.generation == session.generation && _selectedDocumentId.value == id)
                        _selectedDocumentWithChunks.value = detail
                }
        }
    }

    fun selectDocument(id: Long?) {
        _selectedDocumentWithChunks.value = null
        _selectedDocumentId.value = id
    }

    // --- Document Actions ---

    fun uploadDocumentUri(context: android.content.Context, uri: android.net.Uri) {
        val user = _currentUser.value ?: return
        val appContext = context.applicationContext
        lateinit var job: Job
        job = work.launch(start = CoroutineStart.LAZY) {
            var copiedPath: String? = null
            var savedDocument: DocumentEntity? = null
            var completed = false
            try {
                val (metadata, localPath) = withContext(Dispatchers.IO) {
                    val metadata = com.example.docmind.util.FileUtils.getFileMetadata(appContext, uri)
                    val copyContext = coroutineContext
                    val destinationName = "doc_${java.util.UUID.randomUUID()}_${java.io.File(metadata.fileName).name}"
                    copiedPath = java.io.File(appContext.filesDir, "documents/${destinationName}").absolutePath
                    val path = com.example.docmind.util.FileUtils.copyUriToInternalStorage(
                        context = appContext, uri = uri, targetFolder = "documents",
                        preferredFileName = destinationName,
                        checkCancelled = { copyContext.ensureActive() }
                    ) ?: throw java.io.IOException("Cannot copy document")
                    metadata to path
                }

                coroutineContext.ensureActive()
                val document = DocumentEntity(userId = user.userId, fileName = metadata.fileName,
                    filePath = localPath, fileType = metadata.fileType, fileSize = metadata.fileSize,
                    processingStatus = "READY", uploadDate = System.currentTimeMillis())
                withContext(NonCancellable) {
                    savedDocument = document.copy(documentId = repository.insertDocument(document))
                }
                coroutineContext.ensureActive()
                messages.show(message(R.string.upload_success, metadata.fileName))
                startDocumentIndexing(user.userId, requireNotNull(savedDocument).documentId)
                completed = true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                messages.show(message(R.string.upload_failed, e.localizedMessage ?: message(R.string.unknown_error)))
            } finally {
                uploadJobs.remove(job)
                if (!completed) withContext(NonCancellable + Dispatchers.IO) {
                    savedDocument?.let { repository.deleteDocument(it) }
                    copiedPath?.let { java.io.File(it).delete() }
                }
            }
        }
        uploadJobs += job
        job.start()
    }

    fun indexDocument(document: DocumentEntity) {
        val user = _currentUser.value ?: return
        if (document.userId == user.userId) startDocumentIndexing(user.userId, document.documentId)
    }

    private fun startDocumentIndexing(userId: Long, documentId: Long) {
        if (_currentUser.value?.userId != userId || indexJobs[documentId]?.isActive == true) return
        lateinit var job: Job
        job = work.launch(start = CoroutineStart.LAZY) {
            try {
                documentIndexer.ensureIndexed(userId, documentId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // DocumentIndexer exposes the failure on the document card.
            } finally {
                if (indexJobs[documentId] === job) indexJobs.remove(documentId)
            }
        }
        indexJobs[documentId] = job
        job.start()
    }

    fun deleteDocument(document: DocumentEntity) {
        val user = _currentUser.value ?: return
        if (document.userId != user.userId) return
        qaCancellation.cancelAll()
        indexJobs.remove(document.documentId)?.cancel()
        work.launch {
            try {
                repository.deleteDocument(document)
                if (_selectedDocumentId.value == document.documentId) {
                    _selectedDocumentId.value = null
                }
                messages.show(message(R.string.document_deleted, document.fileName))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                messages.show(message(R.string.delete_failed, e.localizedMessage ?: message(R.string.unknown_error)))
            }
        }
    }


    private fun resetSession() {
        _userDocuments.value = emptyList()
        _selectedDocumentWithChunks.value = null
        work.coroutineContext.cancelChildren()
        uploadJobs.toList().forEach { it.cancel() }
        uploadJobs.clear()
        val jobs = indexJobs.values.toList()
        indexJobs.clear()
        jobs.forEach { it.cancel() }
        _selectedDocumentId.value = null
    }

    private val resetSubscription = session.onReset(::resetSession)
    init { observeSessionData() }

    override fun onCleared() {
        resetSubscription.close()
        super.onCleared()
    }
}
