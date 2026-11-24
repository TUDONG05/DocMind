package com.example.docmind.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

class ApiError(val status: HttpStatusCode, val code: String) : Exception(code)
interface Gateway {
    suspend fun embed(request: JsonObject): JsonObject
    suspend fun answer(request: JsonObject): JsonObject
}

fun main() {
    val environment = BackendEnvironment.load()
    val port = environment("PORT")?.toIntOrNull() ?: 8080
    require(port in 1..65535)
    embeddedServer(Netty, host = "127.0.0.1", port = port) { qaModule(environment = environment) }.start(wait = true)
}

fun Application.qaModule(gateway: Gateway? = null, environment: (String) -> String? = BackendEnvironment.load()) {
    val client = if (gateway == null) HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 10_000 }
        followRedirects = false
    } else null
    val service = gateway ?: GeminiGateway(client!!, environment("GEMINI_API_KEY").orEmpty(),
        environment("GEMINI_MODEL")?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash-lite",
        environment("GEMINI_EMBEDDING_MODEL")?.takeIf { it.isNotBlank() } ?: "gemini-embedding-001")
    monitor.subscribe(ApplicationStopped) { client?.close() }
    install(StatusPages) {
        exception<ApiError> { call, error -> call.respondJson(buildJsonObject { put("code", error.code) }, error.status) }
        exception<NoSuchElementException> { call, _ ->
            call.respondJson(buildJsonObject { put("code", "invalid_request") }, HttpStatusCode.BadRequest)
        }
        exception<IllegalArgumentException> { call, _ ->
            call.respondJson(buildJsonObject { put("code", "invalid_request") }, HttpStatusCode.BadRequest)
        }
        exception<SerializationException> { call, _ ->
            call.respondJson(buildJsonObject { put("code", "invalid_request") }, HttpStatusCode.BadRequest)
        }
        exception<Throwable> { call, error ->
            if (error is CancellationException) throw error
            // Never return/log upstream exceptions: they can contain request details.
            call.respondJson(buildJsonObject { put("code", "upstream") }, HttpStatusCode.BadGateway)
        }
    }
    val permits = Semaphore(4)
    routing {
        get("/health") { call.respondJson(buildJsonObject { put("status", "ok") }) }
        post("/embeddings") {
            if (!permits.tryAcquire()) throw ApiError(HttpStatusCode.TooManyRequests, "busy")
            try {
                val body = call.smallJson()
                val texts = body.getValue("texts").jsonArray
                val task = body.requiredText("task")
                require(task in setOf("RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY"))
                require(texts.size in 1..16 && (task != "RETRIEVAL_QUERY" || texts.size == 1))
                val maxLength = if (task == "RETRIEVAL_QUERY") 4000 else 1200
                require(texts.all { it is JsonPrimitive && it.isString && it.content.isNotBlank() && it.content.length <= maxLength })
                call.respondJson(service.embed(body))
            } finally { permits.release() }
        }
        post("/answer") {
            if (!permits.tryAcquire()) throw ApiError(HttpStatusCode.TooManyRequests, "busy")
            try {
                val body = call.smallJson()
                val question = body.requiredText("question")
                val sources = body.getValue("sources").jsonArray
                val history = body.getValue("history").jsonArray
                require(question.isNotBlank() && question.length <= 4000 && sources.size in 1..6 && history.size <= 8)
                val ids = sources.map { it.jsonObject.requiredText("id") }
                require(ids.distinct().size == ids.size && ids.all { it.matches(Regex("[A-Za-z0-9:_-]{1,80}")) })
                require(sources.all { it.jsonObject.requiredText("text").let { text -> text.isNotBlank() && text.length <= 1200 } })
                require(history.all { item ->
                    val entry = item.jsonObject
                    entry.requiredText("role") in setOf("user", "assistant") &&
                        entry.requiredText("content").length <= 4000
                })
                call.respondJson(service.answer(body))
            } finally { permits.release() }
        }
    }
}

private fun JsonObject.requiredText(key: String): String {
    val value = get(key) as? JsonPrimitive
    require(value != null && value.isString)
    return value.content
}

private suspend fun ApplicationCall.smallJson(): JsonObject {
    val channel = receiveChannel()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = channel.readAvailable(buffer, 0, buffer.size)
        if (count < 0) break
        if (output.size() + count > 262_144) throw ApiError(HttpStatusCode.PayloadTooLarge, "large")
        output.write(buffer, 0, count)
    }
    return Json.parseToJsonElement(output.toString("UTF-8")).jsonObject
}

private suspend fun ApplicationCall.respondJson(value: JsonObject, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(value.toString(), ContentType.Application.Json, status)
}
