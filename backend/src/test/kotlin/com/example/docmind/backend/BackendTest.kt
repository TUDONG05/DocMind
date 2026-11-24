package com.example.docmind.backend

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import org.junit.Assert.*
import org.junit.Test

class BackendTest {
    @Test fun rejectsMissingFieldsAndNonStringQuestions() = testApplication {
        application { qaModule(object: Gateway {
            override suspend fun embed(request: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject = error("must not call")
            override suspend fun answer(request: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject = error("must not call")
        }) }
        assertEquals(HttpStatusCode.BadRequest, client.post("/embeddings") { setBody("{}") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/answer") {
            setBody("""{"question":123,"history":[],"sources":[{"id":"s1","text":"hello"}]}""")
        }.status)
    }
    @Test fun rejectsOversizedBody() = testApplication {
        application { qaModule() }
        assertEquals(HttpStatusCode.PayloadTooLarge,client.post("/embeddings") { setBody(" ".repeat(262145)) }.status)
    }

    @Test fun healthDoesNotNeedApiKey() = testApplication {
        application { qaModule() }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }
    @Test fun rejectsInvalidEmbeddingBeforeCallingGemini() = testApplication {
        application { qaModule() }
        val response = client.post("/embeddings") {
            contentType(ContentType.Application.Json)
            setBody("""{"texts":[],"task":"RETRIEVAL_QUERY"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
    @Test fun rejectsUnsupportedTaskAndTooManySources() = testApplication {
        application { qaModule() }
        assertEquals(HttpStatusCode.BadRequest, client.post("/embeddings") {
            setBody("""{"texts":["hello"],"task":"UNKNOWN"}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/answer") {
            setBody("""{"question":"hello","sources":[],"history":[]}""")
        }.status)
    }
}
