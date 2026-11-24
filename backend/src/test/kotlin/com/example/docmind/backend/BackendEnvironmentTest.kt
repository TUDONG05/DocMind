package com.example.docmind.backend

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class BackendEnvironmentTest {
    @Test fun loadsBackendEnvFromRepositoryRootAndSupportsQuotes() {
        val root = Files.createTempDirectory("docmind-env").toFile()
        try {
            val backend = root.resolve("backend").apply { mkdir() }
            backend.resolve(".env").writeText("# local configuration\nDOCMIND_ENV_TEST_VALUE=\"sample value\"\n")
            assertEquals("sample value",BackendEnvironment.load(root)("DOCMIND_ENV_TEST_VALUE"))
            assertEquals("sample value",BackendEnvironment.load(backend)("DOCMIND_ENV_TEST_VALUE"))
        } finally { root.deleteRecursively() }
    }

    @Test fun hostEnvironmentOverridesFileAndMissingFileIsOptional() {
        val root = Files.createTempDirectory("docmind-env").toFile()
        try {
            val key = System.getenv().keys.first { it.equals("PATH",ignoreCase=true) }
            root.resolve(".env").writeText("$key=ignored-file-value\n")
            assertEquals(System.getenv(key),BackendEnvironment.load(root)(key))
            root.resolve(".env").delete()
            assertNull(BackendEnvironment.load(root)("DOCMIND_ENV_TEST_VALUE"))
        } finally { root.deleteRecursively() }
    }

    @Test fun malformedFileDoesNotExposeItsContentsInError() {
        val root = Files.createTempDirectory("docmind-env").toFile()
        try {
            root.resolve(".env").writeText("invalid-test-sensitive-line\n")
            val error = runCatching { BackendEnvironment.load(root) }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(error!!.message.orEmpty().contains("invalid-test-sensitive-line"))
            assertNull(error.cause)
        } finally { root.deleteRecursively() }
    }
}
