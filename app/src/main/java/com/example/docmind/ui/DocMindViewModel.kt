package com.example.docmind.ui

import android.app.Application
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.example.docmind.R
import com.example.docmind.data.repository.AuthException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.docmind.data.local.DocMindDatabase
import com.example.docmind.data.local.SessionManager
import com.example.docmind.data.local.entity.ConversationEntity
import com.example.docmind.data.local.entity.DocumentChunkEntity
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.data.local.entity.MessageEntity
import com.example.docmind.data.local.entity.UserEntity
import com.example.docmind.data.local.relation.CompleteConversation
import com.example.docmind.data.local.relation.DocumentWithChunks
import com.example.docmind.data.repository.DocMindRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DocMindViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocMindRepository(DocMindDatabase.getInstance(application))
    private val sessionManager = SessionManager(application)

    // AppCompat locales also need to be applied to application resources on API < 33.
    private fun message(@StringRes id: Int, vararg args: Any): String {
        val app = getApplication<Application>()
        val configuration = Configuration(app.resources.configuration)
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) configuration.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
        return app.createConfigurationContext(configuration).getString(id, *args)
    }

    private fun authMessage(error: Throwable, @StringRes fallback: Int): String =
        message((error as? AuthException)?.messageRes ?: fallback)

    // Current logged-in user state
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Restore session if user was previously logged in
        viewModelScope.launch {
            val savedUserId = sessionManager.getLoggedInUserId()
            if (savedUserId != null) {
                val user = repository.findUserById(savedUserId)
                _currentUser.value = user
            }
        }
    }

    // Documents belonging to the logged-in user
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val userDocuments: StateFlow<List<DocumentEntity>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getDocumentsByUserId(user.userId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Conversations belonging to the logged-in user
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val userConversations: StateFlow<List<ConversationEntity>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getConversationsByUserId(user.userId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active conversation view
    private val _selectedConversationId = MutableStateFlow<Long?>(null)
    val selectedConversationId: StateFlow<Long?> = _selectedConversationId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedConversationDetails: StateFlow<CompleteConversation?> = _selectedConversationId
        .flatMapLatest { id ->
            if (id != null) repository.getCompleteConversation(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Active document view
    private val _selectedDocumentId = MutableStateFlow<Long?>(null)
    val selectedDocumentId: StateFlow<Long?> = _selectedDocumentId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedDocumentWithChunks: StateFlow<DocumentWithChunks?> = _selectedDocumentId
        .flatMapLatest { id ->
            if (id != null) repository.getDocumentWithChunks(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectConversation(id: Long?) {
        _selectedConversationId.value = id
    }

    fun selectDocument(id: Long?) {
        _selectedDocumentId.value = id
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearAuthError() {
        _authError.value = null
    }

    // --- Authentication Actions ---

    fun register(fullName: String, email: String, password: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val result = repository.register(fullName, email, password)
            _authLoading.value = false
            result.onSuccess { user ->
                _currentUser.value = user
                sessionManager.saveLoggedInUserId(user.userId)
                _statusMessage.value = message(R.string.welcome, user.fullName)
                onSuccess()
            }.onFailure { error ->
                _authError.value = authMessage(error, R.string.register_failed)
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val result = repository.login(email, password)
            _authLoading.value = false
            result.onSuccess { user ->
                _currentUser.value = user
                sessionManager.saveLoggedInUserId(user.userId)
                _statusMessage.value = message(R.string.login_success)
                onSuccess()
            }.onFailure { error ->
                _authError.value = authMessage(error, R.string.login_failed)
            }
        }
    }

    fun quickDemoLogin() {
        viewModelScope.launch {
            val demoEmail = "demo@docmind.ai"
            var user = repository.findUserByEmail(demoEmail)
            if (user == null) {
                val reg = repository.register("Người Dùng Trải Nghiệm", demoEmail, "123456")
                user = reg.getOrNull()
                // Seed initial documents for this user
                if (user != null) {
                    val docId = repository.insertDocument(
                        DocumentEntity(
                            userId = user.userId,
                            fileName = "Bao_Cao_AI_DocMind.pdf",
                            filePath = "/storage/emulated/0/Documents/Bao_Cao_AI_DocMind.pdf",
                            fileType = "application/pdf",
                            fileSize = 1048576L,
                            processingStatus = "READY",
                            uploadDate = System.currentTimeMillis()
                        )
                    )
                    repository.insertChunks(
                        listOf(
                            DocumentChunkEntity(
                                documentId = docId,
                                chunkIndex = 0,
                                content = "DocMind là ứng dụng trợ lý thông minh trên Android với Jetpack Compose & SQLite Room.",
                                pageNumber = 1,
                                embedding = byteArrayOf(1, 2, 3, 4),
                                embeddingModel = "text-embedding-004"
                            ),
                            DocumentChunkEntity(
                                documentId = docId,
                                chunkIndex = 1,
                                content = "Hệ thống hỗ trợ đọc hiểu tài liệu, trích xuất tóm tắt và hỏi đáp văn bản bằng AI.",
                                pageNumber = 2,
                                embedding = byteArrayOf(5, 6, 7, 8),
                                embeddingModel = "text-embedding-004"
                            )
                        )
                    )

                    val convId = repository.insertConversation(
                        ConversationEntity(
                            userId = user.userId,
                            title = "Hỏi đáp tài liệu Báo Cáo AI",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    repository.linkDocumentToConversation(convId, docId)
                    repository.insertMessage(
                        MessageEntity(
                            conversationId = convId,
                            role = "assistant",
                            content = "Xin chào! Tôi là trợ lý AI DocMind. Tôi đã sẵn sàng trả lời các câu hỏi về tài liệu của bạn.",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            if (user != null) {
                _currentUser.value = user
                sessionManager.saveLoggedInUserId(user.userId)
                _statusMessage.value = message(R.string.demo_login_success)
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
        _currentUser.value = null
        _selectedConversationId.value = null
        _selectedDocumentId.value = null
        _statusMessage.value = message(R.string.logout_success)
    }

    fun changePassword(oldPassword: String, newPassword: String, onSuccess: () -> Unit = {}) {
        val user = _currentUser.value
        if (user == null) {
            _authError.value = message(R.string.not_logged_in)
            return
        }

        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val result = repository.changePassword(user.userId, oldPassword, newPassword)
            _authLoading.value = false
            result.onSuccess {
                _currentUser.value = repository.findUserById(user.userId)
                _statusMessage.value = message(R.string.password_changed)
                onSuccess()
            }.onFailure { error ->
                _authError.value = authMessage(error, R.string.password_change_failed)
            }
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            try {
                repository.seedSampleData()
                _statusMessage.value = message(R.string.sample_loaded)
            } catch (e: Exception) {
                _statusMessage.value = message(R.string.sample_failed, e.localizedMessage ?: message(R.string.unknown_error))
            }
        }
    }

    // --- Document Actions ---

    fun uploadDocument(
        fileName: String,
        fileType: String,
        fileSize: Long,
        sampleContent: String
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val docId = repository.insertDocument(
                    DocumentEntity(
                        userId = user.userId,
                        fileName = fileName.trim(),
                        filePath = "/documents/user_${user.userId}/${fileName.trim()}",
                        fileType = fileType,
                        fileSize = fileSize,
                        processingStatus = "READY",
                        uploadDate = System.currentTimeMillis()
                    )
                )

                // Generate a chunk with vector embedding representation
                repository.insertChunk(
                    DocumentChunkEntity(
                        documentId = docId,
                        chunkIndex = 0,
                        content = sampleContent.ifBlank { message(R.string.document_ready_content, fileName) },
                        pageNumber = 1,
                        embedding = byteArrayOf(10, 20, 30, 40),
                        embeddingModel = "text-embedding-004"
                    )
                )

                _statusMessage.value = message(R.string.upload_success, fileName)
            } catch (e: Exception) {
                _statusMessage.value = message(R.string.upload_failed, e.localizedMessage ?: message(R.string.unknown_error))
            }
        }
    }

    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            try {
                repository.deleteDocument(document)
                if (_selectedDocumentId.value == document.documentId) {
                    _selectedDocumentId.value = null
                }
                _statusMessage.value = message(R.string.document_deleted, document.fileName)
            } catch (e: Exception) {
                _statusMessage.value = message(R.string.delete_failed, e.localizedMessage ?: message(R.string.unknown_error))
            }
        }
    }

    // --- Conversation & Chat Actions ---

    fun createConversation(
        title: String,
        linkedDocIds: List<Long>,
        onCreated: (Long) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val convTitle = title.trim().ifBlank { message(R.string.new_conversation) }
                val convId = repository.insertConversation(
                    ConversationEntity(
                        userId = user.userId,
                        title = convTitle,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )

                linkedDocIds.forEach { docId ->
                    repository.linkDocumentToConversation(convId, docId)
                }

                // Initial greeting message from assistant
                repository.insertMessage(
                    MessageEntity(
                        conversationId = convId,
                        role = "assistant",
                        content = message(R.string.assistant_greeting),
                        createdAt = System.currentTimeMillis()
                    )
                )

                _selectedConversationId.value = convId
                _statusMessage.value = message(R.string.conversation_created)
                onCreated(convId)
            } catch (e: Exception) {
                _statusMessage.value = message(R.string.conversation_failed, e.localizedMessage ?: message(R.string.unknown_error))
            }
        }
    }

    fun sendMessage(conversationId: Long, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try {
                // 1. Insert user message
                repository.insertMessage(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "user",
                        content = content.trim(),
                        createdAt = System.currentTimeMillis()
                    )
                )

                // 2. Simulate smart AI response from DocMind
                delay(600)
                val reply = message(R.string.assistant_reply, content.trim())
                repository.insertMessage(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "assistant",
                        content = reply,
                        createdAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                _statusMessage.value = message(R.string.send_failed, e.localizedMessage ?: message(R.string.unknown_error))
            }
        }
    }

    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversation)
                if (_selectedConversationId.value == conversation.conversationId) {
                    _selectedConversationId.value = null
                }
                _statusMessage.value = message(R.string.conversation_deleted)
            } catch (e: Exception) {
                _statusMessage.value = message(R.string.delete_failed, e.localizedMessage ?: message(R.string.unknown_error))
            }
        }
    }
}
