package com.example.docmind.ui.screens

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docmind.data.local.entity.DocumentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentsScreen(
    documents: List<DocumentEntity>,
    onUploadDocument: (fileName: String, fileType: String, fileSize: Long, sampleContent: String) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit
) {
    var showUploadDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showUploadDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm tài liệu")
            }
        }
    ) { innerPadding ->
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                        text = "Kho tài liệu đang trống",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tải lên tài liệu PDF hoặc văn bản để DocMind phân tích và trả lời câu hỏi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showUploadDialog = true }) {
                        Text("+ Thêm tài liệu mới")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(documents) { doc ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isPdf = doc.fileType.contains("pdf", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isPdf) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                val sizeMb = (doc.fileSize * 10 / (1024 * 1024)) / 10.0
                                val sizeFormatted = if (doc.fileSize >= 1024 * 1024) {
                                    "${sizeMb} MB"
                                } else {
                                    "${doc.fileSize / 1024} KB"
                                }

                                Text(
                                    text = "$sizeFormatted • ${dateFormat.format(Date(doc.uploadDate))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = doc.processingStatus,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { onDeleteDocument(doc) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Xóa tài liệu",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        UploadDocumentDialog(
            onDismiss = { showUploadDialog = false },
            onConfirm = { fileName, fileType, size, content ->
                onUploadDocument(fileName, fileType, size, content)
                showUploadDialog = false
            }
        )
    }
}

@Composable
fun UploadDocumentDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, fileType: String, fileSize: Long, sampleContent: String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("application/pdf") }
    var sampleContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Thêm tài liệu mới", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Tên tài liệu") },
                    placeholder = { Text("Ví dụ: Ke_Hoach_Kinh_Doanh.pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Định dạng tài liệu:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == "application/pdf",
                        onClick = { selectedType = "application/pdf" },
                        label = { Text("PDF") }
                    )
                    FilterChip(
                        selected = selectedType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        onClick = { selectedType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document" },
                        label = { Text("Word (DOCX)") }
                    )
                    FilterChip(
                        selected = selectedType == "text/plain",
                        onClick = { selectedType = "text/plain" },
                        label = { Text("TXT") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = sampleContent,
                    onValueChange = { sampleContent = it },
                    label = { Text("Nội dung tóm tắt / Đoạn trích") },
                    placeholder = { Text("Nhập nội dung mẫu của tài liệu...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank()) {
                        val extension = when (selectedType) {
                            "application/pdf" -> if (fileName.endsWith(".pdf", ignoreCase = true)) "" else ".pdf"
                            "text/plain" -> if (fileName.endsWith(".txt", ignoreCase = true)) "" else ".txt"
                            else -> if (fileName.endsWith(".docx", ignoreCase = true)) "" else ".docx"
                        }
                        val finalName = "$fileName$extension"
                        onConfirm(finalName, selectedType, 524288L, sampleContent)
                    }
                },
                enabled = fileName.isNotBlank()
            ) {
                Text("Tải lên")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
