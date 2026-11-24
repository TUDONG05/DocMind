package com.example.docmind.model.qa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.coroutineContext

class QaApiClient(private val baseUrl: String, private val debug: Boolean) : QaApi {
    override suspend fun embed(texts: List<String>, query: Boolean): EmbeddingBatch {
        val result = post("/embeddings", JSONObject()
            .put("texts", JSONArray(texts))
            .put("task", if (query) "RETRIEVAL_QUERY" else "RETRIEVAL_DOCUMENT"))
        try {
            val model = result.getString("model")
            val dimensions = result.getInt("dimensions")
            val items = result.getJSONArray("vectors")
            require(model.isNotBlank() && model.length <= 200 && dimensions in 1..3072 && items.length() == texts.size)
            val vectors = (0 until items.length()).map { index ->
                val values = items.getJSONArray(index)
                require(values.length() == dimensions)
                FloatArray(dimensions) { values.getDouble(it).toFloat() }.also { require(RagMath.valid(it)) }
            }
            return EmbeddingBatch(model, vectors)
        } catch (_: Exception) { throw QaFailure("response") }
    }

    override suspend fun answer(question: String, history: List<QaHistory>, sources: List<QaSource>): QaAnswer {
        val result = post("/answer", JSONObject().put("question", question)
            .put("history", JSONArray(history.map { JSONObject().put("role", it.role).put("content", it.content) }))
            .put("sources", JSONArray(sources.map { JSONObject().put("id", it.id).put("text", it.text) })))
        try {
            val answer = result.getString("answer")
            val ids = result.getJSONArray("sourceIds")
            val sourceIds = (0 until ids.length()).map { ids.getString(it) }.distinct()
            val grounded = result.getBoolean("grounded")
            require(answer.isNotBlank() && answer.length <= 16000)
            require(sourceIds.all { id -> sources.any { it.id == id } })
            require(if (grounded) sourceIds.isNotEmpty() else sourceIds.isEmpty())
            return QaAnswer(answer, sourceIds, grounded)
        } catch (_: Exception) { throw QaFailure("response") }
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(baseUrl.trimEnd('/') + path) }.getOrNull()
            ?: throw QaFailure("configuration")
        if (uri.userInfo != null || uri.fragment != null || uri.host == null ||
            !(uri.scheme == "https" || (debug && uri.scheme == "http" && uri.host in listOf("127.0.0.1", "localhost"))))
            throw QaFailure("configuration")
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 90_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = ByteArrayOutputStream()
            input?.use {
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = it.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > 1_048_576) throw QaFailure("response")
                    bytes.write(buffer, 0, count)
                }
            }
            coroutineContext.ensureActive()
            if (status !in 200..299) {
                throw QaFailure(when (status) { 429 -> "quota"; 503 -> "configuration"; else -> "network" })
            }
            try { JSONObject(bytes.toString("UTF-8")) } catch (_: Exception) { throw QaFailure("response") }
        } catch (error: java.io.IOException) {
            throw QaFailure("network")
        } finally { connection.disconnect() }
    }
}
