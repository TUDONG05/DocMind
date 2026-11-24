package com.example.docmind.view

import com.example.docmind.di.DocMindViewModelFactory
import com.example.docmind.viewmodel.ChatViewModel
import com.example.docmind.viewmodel.DocMindViewModel
import com.example.docmind.viewmodel.DocumentsViewModel
import com.example.docmind.viewmodel.QaUiState

import android.content.Intent
import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.docmind.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.docmind.view.settings.SettingsActivity
import com.example.docmind.view.auth.LoginScreen
import com.example.docmind.view.auth.RegisterScreen
import com.example.docmind.view.screens.ChatDetailScreen
import com.example.docmind.view.screens.ConversationsScreen
import com.example.docmind.view.screens.DocumentsScreen
import com.example.docmind.view.screens.ProfileScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocMindApp() {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { DocMindViewModelFactory(application) }
    val viewModel: DocMindViewModel = viewModel(factory = factory)
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val documentsViewModel: DocumentsViewModel = viewModel(factory = factory)
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val authLoading by viewModel.authLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val profileSaving by viewModel.profileSaving.collectAsStateWithLifecycle()
    val profileError by viewModel.profileError.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val userDocs by documentsViewModel.userDocuments.collectAsStateWithLifecycle()
    val indexProgress by documentsViewModel.documentIndexProgress.collectAsStateWithLifecycle()
    val userConvs by chatViewModel.userConversations.collectAsStateWithLifecycle()
    val selectedConvId by chatViewModel.selectedConversationId.collectAsStateWithLifecycle()
    val qaStates by chatViewModel.qaStates.collectAsStateWithLifecycle()
    val scopeSaving by chatViewModel.scopeSaving.collectAsStateWithLifecycle()
    val scopeError by chatViewModel.scopeError.collectAsStateWithLifecycle()
    val selectedConvDetails by chatViewModel.selectedConversationDetails.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isRegistering by rememberSaveable { mutableStateOf(false) }
    var selectedBottomNavIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // --- 1. AUTH FLOW (Khi chưa đăng nhập) ---
    if (currentUser == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = isRegistering,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "AuthScreenTransition"
                ) { registering ->
                    if (registering) {
                        RegisterScreen(
                            isLoading = authLoading,
                            errorMessage = authError,
                            onRegister = { fullName, email, password ->
                                viewModel.register(fullName, email, password)
                            },
                            onNavigateToLogin = {
                                viewModel.clearAuthError()
                                isRegistering = false
                            }
                        )
                    } else {
                        LoginScreen(
                            isLoading = authLoading,
                            errorMessage = authError,
                            onLogin = { email, password ->
                                viewModel.login(email, password)
                            },
                            onNavigateToRegister = {
                                viewModel.clearAuthError()
                                isRegistering = true
                            }
                        )
                    }
                }
            }
        }
        return
    }

    // --- 2. CHAT DETAIL SCREEN (Khi đang mở một đoạn chat) ---
    if (selectedConvId != null) {
        ChatDetailScreen(
            conversationDetails = selectedConvDetails?.takeIf { it.conversation.conversationId == selectedConvId },
            qaState = qaStates[selectedConvId] ?: QaUiState(),
            availableDocuments = userDocs,
            scopeSaving = scopeSaving,
            scopeError = scopeError,
            onClearScopeError = chatViewModel::clearScopeError,
            onUpdateScope = { all, ids, onSaved ->
                chatViewModel.updateConversationScope(selectedConvId!!, all, ids, onSaved)
            },
            onRetry = { selectedConvId?.let(chatViewModel::retryQa) },
            onBack = { chatViewModel.selectConversation(null) },
            onSendMessage = { text ->
                chatViewModel.sendMessage(selectedConvId!!, text)
            }
        )
        return
    }

    // --- 3. MAIN CONSUMER APP (Khi đã đăng nhập) ---
    val navTitles = listOf(stringResource(R.string.nav_chat), stringResource(R.string.nav_documents), stringResource(R.string.nav_account))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedBottomNavIndex) {
                            0 -> stringResource(R.string.chat_title)
                            1 -> stringResource(R.string.documents_title)
                            else -> stringResource(R.string.profile_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 0,
                    onClick = { selectedBottomNavIndex = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedBottomNavIndex == 0) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                            contentDescription = stringResource(R.string.nav_chat)
                        )
                    },
                    label = { Text(navTitles[0]) }
                )

                NavigationBarItem(
                    selected = selectedBottomNavIndex == 1,
                    onClick = { selectedBottomNavIndex = 1 },
                    icon = {
                        Icon(
                            imageVector = if (selectedBottomNavIndex == 1) Icons.Default.Folder else Icons.Default.FolderOpen,
                            contentDescription = stringResource(R.string.nav_documents)
                        )
                    },
                    label = { Text(navTitles[1]) }
                )

                NavigationBarItem(
                    selected = selectedBottomNavIndex == 2,
                    onClick = { selectedBottomNavIndex = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedBottomNavIndex == 2) Icons.Default.Person else Icons.Default.PersonOutline,
                            contentDescription = stringResource(R.string.nav_account)
                        )
                    },
                    label = { Text(navTitles[2]) }
                )

                NavigationBarItem(
                    selected = false,
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    icon = {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedBottomNavIndex) {
                0 -> ConversationsScreen(
                    conversations = userConvs,
                    onSelectConversation = { convId ->
                        chatViewModel.selectConversation(convId)
                    },
                    onCreateConversation = { chatViewModel.createConversation() },
                    onRenameConversation = chatViewModel::renameConversation,
                    onDeleteConversation = { conv ->
                        chatViewModel.deleteConversation(conv)
                    }
                )

                1 -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    DocumentsScreen(
                        indexProgress = indexProgress,
                        onIndexDocument = documentsViewModel::indexDocument,
                        documents = userDocs,
                        onUploadDocumentUri = { uri ->
                            documentsViewModel.uploadDocumentUri(context, uri)
                        },
                        onDeleteDocument = { doc ->
                            documentsViewModel.deleteDocument(doc)
                        }
                    )
                }

                2 -> ProfileScreen(
                    user = currentUser,
                    profileSaving = profileSaving,
                    profileError = profileError,
                    onSaveProfile = viewModel::updateProfile,
                    onClearProfileError = viewModel::clearProfileError,
                    docCount = userDocs.size,
                    convCount = userConvs.size,
                    authLoading = authLoading,
                    authError = authError,
                    onChangePassword = { oldPass, newPass ->
                        viewModel.changePassword(oldPass, newPass)
                    },
                    onLogout = {
                        viewModel.logout()
                    }
                )
            }
        }
    }
}
