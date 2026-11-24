package com.example.docmind.viewmodel

/** Main-thread cancellation signal shared by document and chat features. */
class QaCancellation {
    private val subscribers = linkedSetOf<() -> Unit>()
    fun register(cancel: () -> Unit): AutoCloseable {
        subscribers += cancel
        return AutoCloseable { subscribers -= cancel }
    }
    fun cancelAll() = subscribers.toList().forEach { it() }
}
