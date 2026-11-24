package com.example.docmind.model.qa

import org.junit.Assert.*
import org.junit.Test

class RagMathTest {
    @Test fun chunksPreserveContentAndOverlap() {
        val input = "abcdefghij".repeat(40)
        val chunks = RagMath.split(input, 3, size = 100, overlap = 20)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 100 && it.page == 3 })
        assertEquals(chunks[0].text.takeLast(20), chunks[1].text.take(20))
        assertEquals(input, chunks.first().text + chunks.drop(1).joinToString("") { it.text.drop(20) })
    }
    @Test fun vectorRoundTripAndCosineRanking() {
        val vector = floatArrayOf(0.1f, -0.2f, 0.3f)
        assertArrayEquals(vector, RagMath.decode(RagMath.encode(vector)), 0f)
        val sources = listOf(
            VectorChunk("a", "wrong", floatArrayOf(0f, 1f), "m"),
            VectorChunk("b", "right", floatArrayOf(1f, 0f), "m"),
            VectorChunk("c", "old model", floatArrayOf(1f, 0f), "old")
        )
        assertEquals(listOf("b"), RagMath.retrieve(floatArrayOf(1f, 0f), "m", sources).map { it.id })
    }
    @Test fun rejectsNonFiniteZeroAndMalformedVectors() {
        assertFalse(RagMath.valid(floatArrayOf(Float.NaN)))
        assertFalse(RagMath.valid(floatArrayOf(0f, 0f)))
        assertFalse(RagMath.valid(floatArrayOf(Float.POSITIVE_INFINITY)))
        assertTrue(runCatching { RagMath.decode(byteArrayOf(1, 2, 3)) }.isFailure)
    }
    @Test fun blankTextProducesNoChunksAndInvalidOverlapFails() {
        assertTrue(RagMath.split("   ", null).isEmpty())
        assertTrue(runCatching { RagMath.split("abc", null, 10, 10) }.isFailure)
    }
}
