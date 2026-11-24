package com.example.docmind.viewmodel

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docmind.R
import com.example.docmind.model.local.SessionManager
import com.example.docmind.model.local.entity.UserEntity
import com.example.docmind.model.local.profile.AvatarStorage
import com.example.docmind.model.repository.AuthException
import com.example.docmind.model.repository.DocMindRepository
import com.example.docmind.model.session.UserSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Authentication and account presentation state. Document/chat work belongs to their own ViewModels. */
class DocMindViewModel(
    private val repository: DocMindRepository,
    private val sessionManager: SessionManager,
    private val session: UserSession,
    private val messages: UiMessages,
    private val avatarStorage: AvatarStorage
) : ViewModel() {
    val currentUser = session.currentUser
    val statusMessage = messages.status
    private val _authLoading = MutableStateFlow(false)
    val authLoading = _authLoading.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()
    private val _profileSaving = MutableStateFlow(false)
    val profileSaving = _profileSaving.asStateFlow()
    private val _profileError = MutableStateFlow<String?>(null)
    val profileError = _profileError.asStateFlow()
    private var authJob: Job? = null
    private var profileJob: Job? = null
    private val resetSubscription = session.onReset {
        profileJob?.cancel()
        profileJob = null
        _profileSaving.value = false
        _profileError.value = null
        _authError.value = null
        messages.clear()
    }
    private val restoreJob = viewModelScope.launch {
        val generation = session.generation
        if (currentUser.value == null) {
            val savedId = sessionManager.getLoggedInUserId()
            if (savedId != null) session.restore(repository.findUserById(savedId), generation)
        }
    }

    private fun message(@StringRes id: Int, vararg args: Any) = messages.text(id, *args)
    private fun authMessage(error: Throwable, @StringRes fallback: Int) =
        message((error as? AuthException)?.messageRes ?: fallback)

    fun clearStatusMessage() = messages.clear()
    fun clearAuthError() { _authError.value = null }
    fun clearProfileError() { _profileError.value = null }

    fun register(fullName: String, email: String, password: String, onSuccess: () -> Unit = {}) {
        authenticate(R.string.register_failed, { message(R.string.welcome, it.fullName) }, onSuccess) {
            repository.register(fullName, email, password)
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit = {}) {
        authenticate(R.string.login_failed, { message(R.string.login_success) }, onSuccess) {
            repository.login(email, password)
        }
    }

    private fun authenticate(
        @StringRes fallback: Int,
        successMessage: (UserEntity) -> String,
        onSuccess: () -> Unit,
        request: suspend () -> Result<UserEntity>
    ) {
        if (_authLoading.value) return
        restoreJob.cancel()
        val generation = session.generation
        _authLoading.value = true
        _authError.value = null
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = request()
                ensureActive()
                if (generation != session.generation) return@launch
                result.onSuccess { user ->
                    session.signIn(user)
                    messages.show(successMessage(user))
                    onSuccess()
                }.onFailure { _authError.value = authMessage(it, fallback) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == session.generation) _authError.value = authMessage(error, fallback)
            } finally {
                if (authJob === job) {
                    authJob = null
                    _authLoading.value = false
                }
            }
        }
        authJob = job
        job.start()
    }

    fun updateProfile(fullName: String, avatarUri: String?, onSuccess: () -> Unit) {
        val user = currentUser.value ?: return
        if (_profileSaving.value) return
        if (fullName.isBlank()) {
            _profileError.value = message(R.string.error_name_required)
            return
        }
        val generation = session.generation
        _profileSaving.value = true
        _profileError.value = null
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val updated = withContext(Dispatchers.IO) {
                    val avatar = if (avatarUri != null) avatarStorage.save(user.userId, Uri.parse(avatarUri)) else user.avatarUrl
                    val result = repository.updateProfile(user.userId, fullName, avatar)
                    if (result.isFailure && avatarUri != null) avatarStorage.deleteOwned(avatar)
                    val saved = result.getOrThrow()
                    if (avatarUri != null) avatarStorage.deleteOwned(user.avatarUrl)
                    saved
                }
                ensureActive()
                if (session.isCurrent(user.userId, generation)) {
                    session.updateUser(updated, generation)
                    messages.show(message(R.string.profile_saved))
                    onSuccess()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session.isCurrent(user.userId, generation))
                    _profileError.value = authMessage(error, if (avatarUri != null) R.string.profile_avatar_failed else R.string.profile_save_failed)
            } finally {
                if (profileJob === job) {
                    profileJob = null
                    _profileSaving.value = false
                }
            }
        }
        profileJob = job
        job.start()
    }

    fun logout() {
        restoreJob.cancel()
        authJob?.cancel()
        authJob = null
        _authLoading.value = false
        session.logout()
        messages.show(message(R.string.logout_success))
    }

    fun changePassword(oldPassword: String, newPassword: String, onSuccess: () -> Unit = {}) {
        val user = currentUser.value ?: return
        if (_authLoading.value) return
        val generation = session.generation
        _authLoading.value = true
        _authError.value = null
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = repository.changePassword(user.userId, oldPassword, newPassword)
                ensureActive()
                if (!session.isCurrent(user.userId, generation)) return@launch
                result.onSuccess {
                    messages.show(message(R.string.password_changed))
                    onSuccess()
                }.onFailure { _authError.value = authMessage(it, R.string.password_change_failed) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session.isCurrent(user.userId, generation)) _authError.value = authMessage(error, R.string.password_change_failed)
            } finally {
                if (authJob === job) {
                    authJob = null
                    _authLoading.value = false
                }
            }
        }
        authJob = job
        job.start()
    }

    override fun onCleared() {
        resetSubscription.close()
        super.onCleared()
    }
}
