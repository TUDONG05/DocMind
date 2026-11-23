package com.example.docmind.ui

import android.content.Intent
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
import androidx.compose.runtime.collectAsState
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
import com.example.docmind.settings.SettingsActivity
import com.example.docmind.ui.auth.LoginScreen
import com.example.docmind.ui.auth.RegisterScreen
import com.example.docmind.ui.screens.ChatDetailScreen
import com.example.docmind.ui.screens.ConversationsScreen
import com.example.docmind.ui.screens.DocumentsScreen
import com.example.docmind.ui.screens.ProfileScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocMindApp(
    viewModel: DocMindViewModel = viewModel()
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val userDocs by viewModel.userDocuments.collectAsState()
    val userConvs by viewModel.userConversations.collectAsState()
    val selectedConvId by viewModel.selectedConversationId.collectAsState()
    val selectedConvDetails by viewModel.selectedConversationDetails.collectAsState()

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
            conversationDetails = selectedConvDetails,
            onBack = { viewModel.selectConversation(null) },
            onSendMessage = { text ->
                viewModel.sendMessage(selectedConvId!!, text)
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
                    documents = userDocs,
                    onSelectConversation = { convId ->
                        viewModel.selectConversation(convId)
                    },
                    onCreateConversation = { title, docIds ->
                        viewModel.createConversation(title, docIds) { }
                    },
                    onDeleteConversation = { conv ->
                        viewModel.deleteConversation(conv)
                    }
                )

                1 -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    DocumentsScreen(
                        documents = userDocs,
                        onUploadDocumentUri = { uri ->
                            viewModel.uploadDocumentUri(context, uri)
                        },
                        onDeleteDocument = { doc ->
                            viewModel.deleteDocument(doc)
                        }
                    )
                }

                2 -> ProfileScreen(
                    user = currentUser,
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
