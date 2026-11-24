package com.example.docmind.di

import com.example.docmind.BuildConfig

import android.app.Application
import com.example.docmind.model.local.DocMindDatabase
import com.example.docmind.model.local.SessionManager
import com.example.docmind.model.local.profile.AvatarStorage
import com.example.docmind.model.qa.*
import com.example.docmind.model.repository.DocMindRepository
import com.example.docmind.model.session.UserSession
import com.example.docmind.viewmodel.QaCancellation
import com.example.docmind.viewmodel.UiMessages

/** Application-lifetime dependencies; no Activity, Composable or ViewModel references. */
class DocMindContainer private constructor(application: Application) {
    val repository = DocMindRepository(DocMindDatabase.getInstance(application))
    val sessionManager = SessionManager(application)
    val session = UserSession(sessionManager::saveLoggedInUserId, sessionManager::clearSession)
    val messages = UiMessages(application)
    val avatarStorage = AvatarStorage(application)
    val qaCancellation = QaCancellation()
    private val extractor = DocumentTextExtractor(application)
    private val api = QaApiClient(BuildConfig.QA_BASE_URL, BuildConfig.DEBUG)
    val indexer = DocumentIndexer(repository, extractor, api)
    val qaService = DocumentQaService(repository, extractor, api, indexer)

    companion object {
        @Volatile private var instance: DocMindContainer? = null
        fun getInstance(application: Application): DocMindContainer =
            instance ?: synchronized(this) {
                instance ?: DocMindContainer(application).also { instance = it }
            }
    }
}
