package com.example.docmind.model.qa

import org.junit.Assert.*
import org.junit.Test

class DocumentIndexProgressTest {
    @Test fun progressCountsSuccessfulChunksAndOnlyCommittedIndexIs100Percent() {
        assertEquals(40,DocumentIndexProgress(IndexStage.EMBEDDING,16,40).percent)
        assertEquals(99,DocumentIndexProgress(IndexStage.EMBEDDING,40,40).percent)
        assertEquals(99,DocumentIndexProgress(IndexStage.SAVING,40,40).percent)
        assertEquals(100,DocumentIndexProgress(IndexStage.INDEXED).percent)
        assertNull(DocumentIndexProgress(IndexStage.READING).percent)
        assertTrue(DocumentIndexProgress(IndexStage.QUEUED).busy)
    }

    @Test fun restartDoesNotClaimOldRunningOrSampleStatusIsComplete() {
        assertEquals(IndexStage.INTERRUPTED,DocumentIndexProgress.fromStored("INDEX_RUNNING").stage)
        assertEquals(IndexStage.INDEXED,DocumentIndexProgress.fromStored("INDEXED").stage)
        assertEquals(IndexStage.NOT_INDEXED,DocumentIndexProgress.fromStored("COMPLETED").stage)
        assertEquals(IndexStage.NOT_INDEXED,DocumentIndexProgress.fromStored("READY").stage)
        val failure = DocumentIndexProgress.fromStored("INDEX_FAILED:quota")
        assertEquals(IndexStage.FAILED,failure.stage)
        assertEquals("quota",failure.errorCode)
    }
}
