package com.example.docmind.viewmodel

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class QaCancellationTest {
    @Test fun documentDeletionCancelsActiveQaAndClearsItsState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = QaRequestCoordinator(scope, { _, _, _, _, _ -> awaitCancellation() }, { "failed" })
        val cancellation = QaCancellation()
        val subscription = cancellation.register(requests::cancelAll)
        try {
            requests.start(1, "question", null)
            assertTrue(requests.states.value.getValue(1).busy)
            cancellation.cancelAll()
            assertTrue(requests.states.value.isEmpty())
            assertTrue(requests.start(1, "next", null))
        } finally {
            subscription.close()
            scope.cancel()
        }
    }

    @Test fun disposedSubscriberDoesNotReceiveCancellation() {
        val cancellation = QaCancellation()
        var called = 0
        val subscription = cancellation.register { called++ }
        subscription.close()
        cancellation.cancelAll()
        assertEquals(0, called)
    }
}
