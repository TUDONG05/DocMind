package com.example.docmind.model.session

import com.example.docmind.model.local.entity.UserEntity
import org.junit.Assert.*
import org.junit.Test

class UserSessionTest {
    private fun user(id: Long) = UserEntity(id, "User $id", "$id@example.com", "hash", createdAt = "today")

    @Test fun logoutCancelsFeaturesBeforeExposingNextSession() {
        var saved: Long? = null
        val session = UserSession({ saved = it }, { saved = null })
        session.signIn(user(1))
        val oldGeneration = session.generation
        var resetUser: Long? = null
        val subscription = session.onReset { resetUser = session.currentUser.value?.userId }
        session.logout()
        assertEquals(1L, resetUser)
        assertNull(saved)
        assertNull(session.currentUser.value)
        assertFalse(session.isCurrent(1, oldGeneration))
        subscription.close()
    }

    @Test fun staleRestoreAndProfileResultCannotReplaceNewLogin() {
        val session = UserSession({}, {})
        val restoreGeneration = session.generation
        session.signIn(user(1))
        val profileGeneration = session.generation
        session.logout()
        session.signIn(user(2))
        session.restore(user(1), restoreGeneration)
        session.updateUser(user(1).copy(fullName = "Old result"), profileGeneration)
        assertEquals(user(2), session.currentUser.value)
    }

    @Test fun profileUpdateKeepsGenerationAndDoesNotResetFeatures() {
        val session = UserSession({}, {})
        session.signIn(user(1))
        val generation = session.generation
        var resets = 0
        val subscription = session.onReset { resets++ }
        session.updateUser(user(1).copy(fullName = "New name"), generation)
        assertEquals("New name", session.currentUser.value?.fullName)
        assertEquals(generation, session.generation)
        assertEquals(0, resets)
        subscription.close()
        session.logout()
        assertEquals(0, resets)
    }
}
