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

class ChatViewModel(
    private val repository: DocMindRepository,
    private val qaService: DocumentQaService,
    private val session: UserSession,
    private val messages: UiMessages,
    private val qaCancellation: QaCancellation
) : ViewModel() {
    private val _currentUser get() = session.currentUser
    private val sessionGeneration get() = session.generation
    private val work = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
    private fun message(id: Int, vararg args: Any) = messages.text(id, *args)
    private var createConversationJob: Job? = null
    private var scopeJob: Job? = null
    private val qaCoordinator = QaRequestCoordinator(work,
        run = { conversationId, question, retryId, onQuestion, onProgress ->
            val user = _currentUser.value ?: throw QaFailure("configuration")
            qaService.ask(user.userId, conversationId, question, retryId,
                message(R.string.qa_sources), message(R.string.qa_no_evidence), onQuestion, onProgress)
            Unit
        },
        errorMessage = { error -> qaErrorMessage(error) })
    val qaStates: StateFlow<Map<Long, QaUiState>> = qaCoordinator.states

    private fun cancelQa() = qaCoordinator.cancelAll()

    // Conversations belonging to the logged-in user
    private val _userConversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val userConversations = _userConversations.asStateFlow()

    // Active conversation view
    private val _selectedConversationId = MutableStateFlow<Long?>(null)
    val selectedConversationId: StateFlow<Long?> = _selectedConversationId.asStateFlow()

    private val _selectedConversationDetails = MutableStateFlow<CompleteConversation?>(null)
    val selectedConversationDetails = _selectedConversationDetails.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSessionData() {
        viewModelScope.launch {
            session.identity.flatMapLatest { identity ->
                val userId = identity.userId
                if (userId != null) repository.getConversationsByUserId(userId).map { identity to it }
                else flowOf(identity to emptyList())
            }.collect { (identity, items) ->
                if (identity.generation == session.generation) _userConversations.value = items
            }
        }
        viewModelScope.launch {
            combine(session.identity, _selectedConversationId) { identity, id -> identity to id }
                .flatMapLatest { (identity, id) ->
                    if (id != null && identity.userId != null) repository.getCompleteConversation(id).map { detail ->
                        Triple(identity, id, detail?.takeIf { it.conversation.userId == identity.userId })
                    } else flowOf(Triple(identity, id, null))
                }.collect { (identity, id, detail) ->
                    if (identity.generation == session.generation && _selectedConversationId.value == id)
                        _selectedConversationDetails.value = detail
                }
        }
    }

    fun selectConversation(id: Long?) {
        _selectedConversationDetails.value = null
        _selectedConversationId.value = id
    }

    // --- Conversation & Chat Actions ---

    fun renameConversation(conversationId: Long, title: String) {
        val user = _currentUser.value ?: return
        val generation = sessionGeneration
        work.launch {
            try {
                repository.renameConversation(user.userId, conversationId, title)
                if (generation == sessionGeneration && _currentUser.value?.userId == user.userId)
                    messages.show(message(R.string.conversation_renamed))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == sessionGeneration && _currentUser.value?.userId == user.userId)
                    messages.show(message(R.string.conversation_rename_failed))
            }
        }
    }

    fun createConversation(onCreated: (Long) -> Unit = {}) {
        val user = _currentUser.value ?: return
        if (createConversationJob?.isActive == true) return
        val generation = sessionGeneration
        lateinit var job: Job
        job = work.launch(start = CoroutineStart.LAZY) {
            try {
                val convId = repository.createQaConversation(user.userId, message(R.string.new_conversation))

                // Initial greeting message from assistant
                repository.insertMessage(
                    MessageEntity(
                        conversationId = convId,
                        role = "assistant",
                        content = message(R.string.assistant_greeting),
                        createdAt = System.currentTimeMillis()
                    )
                )

                coroutineContext.ensureActive()
                if (_currentUser.value?.userId == user.userId && generation == sessionGeneration) {
                    _selectedConversationId.value = convId
                    messages.show(message(R.string.conversation_created))
                    onCreated(convId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == sessionGeneration) {
                    messages.show(message(R.string.conversation_failed, e.localizedMessage ?: message(R.string.unknown_error)))
                }
            } finally {
                if (createConversationJob === job) createConversationJob = null
            }
        }
        createConversationJob = job
        job.start()
    }

    private val _scopeSaving = MutableStateFlow(false)
    val scopeSaving = _scopeSaving.asStateFlow()
    private val _scopeError = MutableStateFlow<String?>(null)
    val scopeError = _scopeError.asStateFlow()

    fun clearScopeError() { _scopeError.value = null }

    fun updateConversationScope(conversationId: Long, allDocuments: Boolean, documentIds: List<Long>, onSaved: () -> Unit) {
        val user = _currentUser.value ?: return
        if (_scopeSaving.value || qaStates.value[conversationId]?.busy == true) return
        _scopeSaving.value = true
        _scopeError.value = null
        val generation = sessionGeneration
        lateinit var job: Job
        job = work.launch(start = CoroutineStart.LAZY) {
            try {
                qaCoordinator.cancel(conversationId)
                repository.updateQaScope(user.userId, conversationId, allDocuments, documentIds)
                if (generation == sessionGeneration && _currentUser.value?.userId == user.userId) onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == sessionGeneration) _scopeError.value = message(R.string.qa_scope_save_failed)
            } finally {
                if (scopeJob === job) {
                    _scopeSaving.value = false
                    scopeJob = null
                }
            }
        }
        scopeJob = job
        job.start()
    }

    fun sendMessage(conversationId: Long, content: String) {
        if (_scopeSaving.value) return
        startQa(conversationId, content.trim(), null)
    }

    fun retryQa(conversationId: Long) {
        val pending = qaStates.value[conversationId]
        if (pending?.pendingQuestion != null) {
            startQa(conversationId, pending.pendingQuestion, pending.questionId)
            return
        }
        val detail = selectedConversationDetails.value
        if (detail?.conversation?.conversationId != conversationId) return
        val last = detail.messages.maxByOrNull { it.messageId } ?: return
        if (last.role == "user") startQa(conversationId, last.content, last.messageId)
    }

    private fun startQa(conversationId: Long, question: String, retryId: Long?) {
        if (_currentUser.value != null) qaCoordinator.start(conversationId, question, retryId)
    }

    private fun qaErrorMessage(error: Throwable): String {
        val failure = error as? QaFailure
        val resource = when (failure?.code) {
            "configuration" -> R.string.qa_configuration
            "quota" -> R.string.qa_quota
            "empty" -> R.string.qa_empty_document
            "large" -> R.string.qa_large_document
            "unsupported" -> R.string.qa_unsupported
            "documents" -> R.string.qa_scope_no_documents
            "document" -> R.string.qa_document_error
            else -> R.string.qa_request_error
        }
        return message(resource) + (failure?.document?.let { ": $it" } ?: "")
    }

    fun deleteConversation(conversation: ConversationEntity) {
        if (_currentUser.value?.userId != conversation.userId) return
        qaCoordinator.cancel(conversation.conversationId)
        work.launch {
            try {
                repository.deleteConversation(conversation)
                if (_selectedConversationId.value == conversation.conversationId) {
                    _selectedConversationId.value = null
                }
                messages.show(message(R.string.conversation_deleted))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                messages.show(message(R.string.delete_failed, e.localizedMessage ?: message(R.string.unknown_error)))
            }
        }
    }
    private fun resetSession() {
        _userConversations.value = emptyList()
        _selectedConversationDetails.value = null
        work.coroutineContext.cancelChildren()
        createConversationJob?.cancel()
        createConversationJob = null
        scopeJob?.cancel()
        scopeJob = null
        _scopeSaving.value = false
        _scopeError.value = null
        cancelQa()
        _selectedConversationId.value = null
    }

    private val resetSubscription = session.onReset(::resetSession)
    init { observeSessionData() }
    private val qaSubscription = qaCancellation.register(::cancelQa)

    override fun onCleared() {
        resetSubscription.close()
        qaSubscription.close()
        cancelQa()
        super.onCleared()
    }
}
