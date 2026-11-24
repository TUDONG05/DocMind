package com.example.docmind.di

import com.example.docmind.viewmodel.ChatViewModel
import com.example.docmind.viewmodel.DocMindViewModel
import com.example.docmind.viewmodel.DocumentsViewModel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.docmind.di.DocMindContainer

class DocMindViewModelFactory(application: Application) : ViewModelProvider.Factory {
    private val dependencies = DocMindContainer.getInstance(application)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val instance: ViewModel = with(dependencies) {
            when (modelClass) {
                DocMindViewModel::class.java -> DocMindViewModel(repository, sessionManager, session, messages, avatarStorage)
                ChatViewModel::class.java -> ChatViewModel(repository, qaService, session, messages, qaCancellation)
                DocumentsViewModel::class.java -> DocumentsViewModel(repository, indexer, session, messages, qaCancellation)
                else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
            }
        }
        @Suppress("UNCHECKED_CAST")
        return instance as T
    }
}
