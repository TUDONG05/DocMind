package com.example.docmind.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.docmind.viewmodel.QaUiState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.docmind.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docmind.model.local.relation.CompleteConversation
import com.example.docmind.model.local.entity.DocumentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationDetails: CompleteConversation?,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    qaState: QaUiState,
    onRetry: () -> Unit,
    availableDocuments: List<DocumentEntity>,
    scopeSaving: Boolean,
    scopeError: String?,
    onClearScopeError: () -> Unit,
    onUpdateScope: (Boolean, List<Long>, () -> Unit) -> Unit
) {
    var inputText by rememberSaveable(conversationDetails?.conversation?.conversationId) { mutableStateOf("") }
    var showScopeDialog by rememberSaveable(conversationDetails?.conversation?.conversationId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val messages = conversationDetails?.messages ?: emptyList()
    val documents = conversationDetails?.documents ?: emptyList()

    if (showScopeDialog && conversationDetails != null) {
        ConversationScopeDialog(
            conversationId = conversationDetails.conversation.conversationId,
            allDocuments = conversationDetails.conversation.allDocuments,
            selectedIds = documents.map { it.documentId },
            documents = availableDocuments, saving = scopeSaving, error = scopeError,
            onDismiss = { showScopeDialog = false },
            onApply = { all, ids ->
                onUpdateScope(all, ids) { showScopeDialog = false }
            })
    }

    // Auto scroll to latest message when messages list updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = conversationDetails?.conversation?.title ?: stringResource(R.string.nav_chat),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (documents.isNotEmpty()) stringResource(R.string.attached_documents, documents.size) else stringResource(R.string.ai_questions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth().imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= 4000) inputText = it },
                        enabled = !qaState.busy && !scopeSaving && documents.isNotEmpty(),
                        placeholder = { Text(stringResource(R.string.ask_document)) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        enabled = !qaState.busy && !scopeSaving && inputText.isNotBlank() && documents.isNotEmpty(),
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TextButton(
                enabled = conversationDetails != null && !qaState.busy && !scopeSaving,
                onClick = { onClearScopeError(); showScopeDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(
                    if (conversationDetails?.conversation?.allDocuments == true)
                        R.string.qa_scope_all else R.string.qa_scope_selected, documents.size))
            }
            HorizontalDivider()

            if (qaState.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(if (qaState.indexing) R.string.qa_indexing else R.string.qa_answering),
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (documents.isEmpty()) {
                Text(stringResource(R.string.qa_scope_no_documents), modifier = Modifier.padding(12.dp))
            }
            qaState.error?.let { error ->
                if (qaState.questionId == null && qaState.pendingQuestion != null) {
                    Text(qaState.pendingQuestion, modifier = Modifier.padding(horizontal = 12.dp))
                }
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            if (!qaState.busy && !scopeSaving && documents.isNotEmpty() &&
                (qaState.pendingQuestion != null || messages.maxByOrNull { it.messageId }?.role == "user")) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.qa_retry)) }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.messageId }) { msg ->
                    val isUser = msg.role.equals("user", ignoreCase = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (!isUser) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column(
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Card(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SelectionContainer { Text(
                                        text = msg.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeFormat.format(Date(msg.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
