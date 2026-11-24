package com.example.docmind

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.docmind.model.qa.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.io.Closeable

@RunWith(AndroidJUnit4::class)
class QaApiClientTest {
    private class LocalResponse(status: Int, body: String): Closeable {
        private val server = ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${server.localPort}"
        init {
            server.soTimeout = 10000
            executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break }
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            }
        }
        override fun close() { server.close(); executor.shutdownNow() }
    }

    @Test fun readsEmbeddingContractOverRealHttp() = runBlocking {
        LocalResponse(200,"""{"model":"test|2|v1","dimensions":2,"vectors":[[1.0,0.0]]}""").use {
            val embedding = QaApiClient(it.url,true).embed(listOf("hello"),true)
            assertEquals("test|2|v1",embedding.model)
            assertArrayEquals(floatArrayOf(1f,0f),embedding.vectors.single(),0f)
        }
    }

    @Test fun refusesInventedSourcesAndMapsMissingConfiguration() = runBlocking {
        LocalResponse(200,"""{"answer":"hello","sourceIds":["invented"],"grounded":true}""").use {
            val error = runCatching { QaApiClient(it.url,true).answer("hi",emptyList(),listOf(QaSource("s1","file","hello"))) }.exceptionOrNull()
            assertEquals("response",(error as QaFailure).code)
        }
        LocalResponse(503,"""{"code":"configuration"}""").use {
            val error = runCatching { QaApiClient(it.url,true).embed(listOf("hi"),true) }.exceptionOrNull()
            assertEquals("configuration",(error as QaFailure).code)
        }
        val error = runCatching { QaApiClient("http://127.0.0.1:8080",false).embed(listOf("hi"),true) }.exceptionOrNull()
        assertEquals("configuration",(error as QaFailure).code)
    }
}
