package com.example.docmind.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GeminiGatewayTest {
    @Test fun embedsWithTaskAndDimensionContract() = runBlocking {
        val tasks = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("test-key",request.headers["x-goog-api-key"])
            assertFalse(request.url.toString().contains("test-key"))
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val entry = body.getValue("requests").jsonArray.single().jsonObject
            tasks += entry.getValue("taskType").jsonPrimitive.content
            assertEquals(768,entry.getValue("outputDimensionality").jsonPrimitive.int)
            val response = buildJsonObject { put("embeddings",buildJsonArray { add(buildJsonObject {
                put("values",buildJsonArray { repeat(768) { add(if(it==0) 1.0 else 0.0) } })
            }) }) }
            respond(response.toString(),headers=headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            val gateway = GeminiGateway(client,"test-key")
            for (task in listOf("RETRIEVAL_DOCUMENT","RETRIEVAL_QUERY")) {
                val result = gateway.embed(Json.parseToJsonElement("""{"texts":["hello"],"task":"$task"}""").jsonObject)
                assertEquals(768,result.getValue("dimensions").jsonPrimitive.int)
                assertTrue(result.getValue("model").jsonPrimitive.content.contains("gemini-embedding-001"))
            }
            assertEquals(listOf("RETRIEVAL_DOCUMENT","RETRIEVAL_QUERY"),tasks)
        } finally { client.close() }
    }

    @Test fun acceptsOnlyCitationsFromRetrievedSources() = runBlocking {
        var sourceId = "invented"
        val client = HttpClient(MockEngine {
            val output = buildJsonObject { put("answer","30 days"); put("grounded",true)
                put("sourceIds",buildJsonArray { add(sourceId) }) }
            val response = buildJsonObject { put("candidates",buildJsonArray { add(buildJsonObject {
                put("content",buildJsonObject { put("parts",buildJsonArray { add(buildJsonObject { put("text",output.toString()) }) }) })
            }) }) }
            respond(response.toString(),headers=headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            val gateway = GeminiGateway(client,"test-key")
            val request = Json.parseToJsonElement("""{"question":"When?","history":[],"sources":[{"id":"d1:c0","text":"30 days"}]}""").jsonObject
            val failure = runCatching { gateway.answer(request) }.exceptionOrNull()
            assertTrue(failure is ApiError)
            sourceId = "d1:c0"
            assertEquals("30 days",gateway.answer(request).getValue("answer").jsonPrimitive.content)
        } finally { client.close() }
    }

    @Test fun quotaErrorsDoNotExposeUpstreamResponse() = runBlocking {
        val client = HttpClient(MockEngine { respond("private upstream body",HttpStatusCode.TooManyRequests) })
        try {
            val result = runCatching {
                GeminiGateway(client,"test-key").embed(Json.parseToJsonElement("""{"texts":["hi"],"task":"RETRIEVAL_QUERY"}""").jsonObject)
            }.exceptionOrNull()
            assertTrue(result is ApiError)
            assertEquals(HttpStatusCode.TooManyRequests,(result as ApiError).status)
            assertFalse(result.message.orEmpty().contains("private"))
        } finally { client.close() }
    }
}
