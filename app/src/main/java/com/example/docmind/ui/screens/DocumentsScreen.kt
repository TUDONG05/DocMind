package com.example.docmind.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.docmind.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.util.FileUtils

@Composable
fun DocumentsScreen(
    documents: List<DocumentEntity>,
    onUploadDocumentUri: (Uri) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val unsupportedPreviewMessage = stringResource(R.string.unsupported_preview)
    var searchQuery by remember { mutableStateOf("") }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToViewPdf by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToViewWord by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToViewExcel by remember { mutableStateOf<DocumentEntity?>(null) }

    // Storage Access Framework Document Picker Launcher supporting PDF, Word, Excel
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onUploadDocumentUri(uri)
        }
    }

    // Filter documents by search keyword with Vietnamese diacritic-insensitive normalization
    val filteredDocuments = remember(documents, searchQuery) {
        if (searchQuery.isBlank()) {
            documents
        } else {
            val normalizedQuery = FileUtils.normalizeVietnamese(searchQuery)
            documents.filter { doc ->
                val normalizedFileName = FileUtils.normalizeVietnamese(doc.fileName)
                val normalizedFileType = FileUtils.normalizeVietnamese(doc.fileType)
                normalizedFileName.contains(normalizedQuery) || normalizedFileType.contains(normalizedQuery)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    safPickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "text/plain",
                            "*/*"
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_document))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.document_search_hint)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (documents.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.empty_documents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.study_documents_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = {
                            safPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "*/*"
                                )
                            )
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.pick_document))
                        }
                    }
                }
            } else if (filteredDocuments.isEmpty()) {
                // No Search Results State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.no_search_results),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.try_other_keywords),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDocuments, key = { it.documentId }) { doc ->
                        DocumentItemCard(
                            document = doc,
                            onView = {
                                val name = doc.fileName.lowercase()
                                val type = doc.fileType.lowercase()
                                Log.d("DocMindDebug", "DocumentsScreen - onView clicked: fileName=${doc.fileName}, fileType=${doc.fileType}")
                                when {
                                    name.endsWith(".pdf") || type.contains("pdf") -> {
                                        Log.d("DocMindDebug", "DocumentsScreen - Dispatched to PDF Viewer")
                                        documentToViewPdf = doc
                                    }
                                    name.endsWith(".xlsx") || name.endsWith(".xls") || type.contains("sheet") || type.contains("excel") || type.contains("spreadsheet") -> {
                                        Log.d("DocMindDebug", "DocumentsScreen - Dispatched to Excel Viewer")
                                        documentToViewExcel = doc
                                    }
                                    name.endsWith(".docx") || name.endsWith(".doc") || type.contains("word") || type.contains("msword") || type.contains("document") -> {
                                        Log.d("DocMindDebug", "DocumentsScreen - Dispatched to Word Viewer")
                                        documentToViewWord = doc
                                    }
                                    else -> {
                                        Log.w("DocMindDebug", "DocumentsScreen - Unsupported format: name=$name, type=$type")
                                        Toast.makeText(context, unsupportedPreviewMessage, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDelete = {
                                documentToDelete = doc
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    val targetToDelete: DocumentEntity? = documentToDelete
    if (targetToDelete != null) {
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = {
                Text(stringResource(R.string.delete_document_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(stringResource(R.string.delete_document_message, targetToDelete.fileName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDocument(targetToDelete)
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.delete_document))
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    // Viewers
    documentToViewPdf?.let { doc ->
        PdfViewerDialog(document = doc, onDismiss = { documentToViewPdf = null })
    }

    documentToViewWord?.let { doc ->
        WordViewerDialog(document = doc, onDismiss = { documentToViewWord = null })
    }

    documentToViewExcel?.let { doc ->
        ExcelViewerDialog(document = doc, onDismiss = { documentToViewExcel = null })
    }
}

@Composable
fun DocumentItemCard(
    document: DocumentEntity,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    val name = document.fileName.lowercase()
    val type = document.fileType.lowercase()

    val isPdf = name.endsWith(".pdf") || type.contains("pdf")
    val isExcel = name.endsWith(".xlsx") || name.endsWith(".xls") || type.contains("sheet") || type.contains("excel") || type.contains("spreadsheet")
    val isWord = name.endsWith(".docx") || name.endsWith(".doc") || type.contains("word") || type.contains("msword") || type.contains("document")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isPdf -> MaterialTheme.colorScheme.errorContainer
                                isWord -> MaterialTheme.colorScheme.primaryContainer
                                isExcel -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isPdf -> Icons.Default.PictureAsPdf
                            isWord -> Icons.Default.Description
                            isExcel -> Icons.Default.GridOn
                            else -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = when {
                            isPdf -> MaterialTheme.colorScheme.error
                            isWord -> MaterialTheme.colorScheme.primary
                            isExcel -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${FileUtils.formatFileSize(document.fileSize)} • ${FileUtils.formatDate(document.uploadDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onView,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.view_document))
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_document),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}