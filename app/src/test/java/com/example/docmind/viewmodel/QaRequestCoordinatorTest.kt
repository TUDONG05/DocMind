package com.example.docmind.viewmodel

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class QaRequestCoordinatorTest {
    @Test fun cancelledRequestCannotUnlockNewRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val oldFinish = CompletableDeferred<Unit>()
        val newFinish = CompletableDeferred<Unit>()
        var count = 0
        val coordinator = QaRequestCoordinator(scope, { _,_,_,_,_ ->
            if (++count == 1) {
                withContext(NonCancellable) { oldFinish.await() }
                throw CancellationException()
            } else newFinish.await()
        }, { "failed" })
        try {
            assertTrue(coordinator.start(1,"old",null))
            coordinator.cancelAll()
            assertTrue(coordinator.start(1,"new",null))
            oldFinish.complete(Unit)
            yield()
            assertTrue(coordinator.states.value.getValue(1).busy)
            assertFalse(coordinator.start(1,"duplicate",null))
            newFinish.complete(Unit)
            yield()
            assertFalse(coordinator.states.value[1]?.busy ?: false)
        } finally { scope.cancel() }
    }

    @Test fun prePersistenceFailureKeepsQuestionForRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = QaRequestCoordinator(scope, { _,_,_,_,_ -> error("not saved") }, { "failed" })
        try {
            assertTrue(coordinator.start(2,"recover me",null))
            val state = coordinator.states.value.getValue(2)
            assertEquals("recover me",state.pendingQuestion)
            assertNull(state.questionId)
            assertEquals("failed",state.error)
            assertFalse(state.busy)
        } finally { scope.cancel() }
    }
}
