package com.example.docmind.util

import java.security.MessageDigest

object PasswordHasher {
    private const val SALT = "DocMind_Secure_Salt_2026"

    /**
     * Hashes password using SHA-256 with project salt
     */
    fun hashPassword(password: String): String {
        val bytes = "$SALT$password".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies if raw password matches the stored password hash
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        return hashPassword(password).equals(storedHash, ignoreCase = true)
    }
}
