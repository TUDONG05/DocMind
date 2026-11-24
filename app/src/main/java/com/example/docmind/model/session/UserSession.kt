package com.example.docmind.model.session

import com.example.docmind.model.local.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared session state. Mutations and reset callbacks run on the main thread. */
class UserSession(
    private val saveUserId: (Long) -> Unit,
    private val clearSavedUser: () -> Unit
) {
    private val user = MutableStateFlow<UserEntity?>(null)
    val currentUser = user.asStateFlow()
    var generation = 0L
        private set
    data class Identity(val userId: Long?, val generation: Long)
    private val sessionIdentity = MutableStateFlow(Identity(null, 0))
    val identity = sessionIdentity.asStateFlow()
    private val resets = linkedSetOf<() -> Unit>()

    fun onReset(callback: () -> Unit): AutoCloseable {
        resets += callback
        return AutoCloseable { resets -= callback }
    }

    fun isCurrent(userId: Long, expectedGeneration: Long) =
        generation == expectedGeneration && user.value?.userId == userId

    fun signIn(value: UserEntity) {
        reset()
        saveUserId(value.userId)
        user.value = value
        sessionIdentity.value = Identity(value.userId, generation)
    }

    fun restore(value: UserEntity?, expectedGeneration: Long) {
        if (generation != expectedGeneration) return
        reset()
        user.value = value
        sessionIdentity.value = Identity(value?.userId, generation)
        if (value == null) clearSavedUser()
    }

    fun updateUser(value: UserEntity, expectedGeneration: Long) {
        if (isCurrent(value.userId, expectedGeneration)) user.value = value
    }

    fun logout() {
        reset()
        clearSavedUser()
        user.value = null
        sessionIdentity.value = Identity(null, generation)
    }

    private fun reset() {
        generation++
        resets.toList().forEach { it() }
    }
}
