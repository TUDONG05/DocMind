package com.example.docmind.backend

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.DotenvException
import java.io.File

object BackendEnvironment {
    /** Supports Gradle/IDE launches from backend/ and launches from the repository root. */
    fun load(workingDirectory: File = File(".")): (String) -> String? {
        val directory = workingDirectory.resolve("backend").takeIf { it.isDirectory } ?: workingDirectory
        val dotenv = try {
            Dotenv.configure().directory(directory.absolutePath).ignoreIfMissing().load()
        } catch (_: DotenvException) {
            // Parser exceptions may contain the offending line, including credentials.
            throw IllegalArgumentException("Cannot load backend .env; check KEY=value syntax.")
        }
        // dotenv-java gives the host environment priority and does not mutate System.getenv().
        return { key -> dotenv.get(key) }
    }
}
