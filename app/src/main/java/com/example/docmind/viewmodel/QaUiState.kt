package com.example.docmind.viewmodel

data class QaUiState(
    val busy: Boolean = false, val indexing: Boolean = false, val error: String? = null,
    val pendingQuestion: String? = null, val questionId: Long? = null
)
