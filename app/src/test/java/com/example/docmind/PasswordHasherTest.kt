package com.example.docmind

import com.example.docmind.util.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun testHashConsistency() {
        val hash1 = PasswordHasher.hashPassword("mySecurePassword123")
        val hash2 = PasswordHasher.hashPassword("mySecurePassword123")
        assertEquals(hash1, hash2)
    }

    @Test
    fun testDifferentPasswordsProduceDifferentHashes() {
        val hash1 = PasswordHasher.hashPassword("passwordA")
        val hash2 = PasswordHasher.hashPassword("passwordB")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testVerifyPassword() {
        val hash = PasswordHasher.hashPassword("correctPassword")
        assertTrue(PasswordHasher.verifyPassword("correctPassword", hash))
        assertFalse(PasswordHasher.verifyPassword("wrongPassword", hash))
    }

    private fun assertEquals(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
