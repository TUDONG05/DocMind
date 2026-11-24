package com.example.docmind.model.qa

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class TextChunk(val text: String, val page: Int?)
class VectorChunk(val id: String, val text: String, val vector: FloatArray, val model: String)

object RagMath {
    fun split(text: String, page: Int?, size: Int = 1200, overlap: Int = 180): List<TextChunk> {
        require(size > 0 && overlap in 0 until size)
        if (text.isBlank()) return emptyList()
        return buildList {
            var start = 0
            while (start < text.length) {
                val end = minOf(start + size, text.length)
                val part = text.substring(start, end)
                if (part.isNotBlank()) add(TextChunk(part, page))
                if (end == text.length) break
                start = end - overlap
            }
        }
    }

    fun valid(vector: FloatArray): Boolean =
        vector.isNotEmpty() && vector.all { it.isFinite() } && vector.any { it != 0f }

    fun encode(vector: FloatArray): ByteArray {
        require(valid(vector))
        return ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            .apply { vector.forEach { putFloat(it) } }.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.isNotEmpty() && bytes.size % 4 == 0 && bytes.size <= 4 * 3072)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }.also { require(valid(it)) }
    }

    fun retrieve(query: FloatArray, model: String, chunks: List<VectorChunk>, limit: Int = 6): List<VectorChunk> {
        require(valid(query) && limit > 0)
        return chunks.asSequence()
            .filter { it.model == model && it.vector.size == query.size && valid(it.vector) }
            .map { chunk ->
                var dot = 0.0
                var left = 0.0
                var right = 0.0
                query.indices.forEach { i ->
                    dot += query[i].toDouble() * chunk.vector[i]
                    left += query[i].toDouble() * query[i]
                    right += chunk.vector[i].toDouble() * chunk.vector[i]
                }
                chunk to dot / sqrt(left * right)
            }
            .filter { it.second >= 0.25 }
            .sortedByDescending { it.second }
            .take(limit).map { it.first }.toList()
    }
}
