package com.example.docmind.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.util.WordContent
import com.example.docmind.util.WordParserUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordViewerDialog(
    document: DocumentEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wordContents by remember { mutableStateOf<List<WordContent>>(emptyList()) }

    LaunchedEffect(document.filePath) {
        val file = File(document.filePath)
        Log.d("DocMindDebug", "WordViewerDialog - LaunchedEffect: name=${document.fileName}, path=${document.filePath}, exists=${file.exists()}, length=${file.length()}")
        isLoading = true
        errorMessage = null
        if (document.fileName.endsWith(".doc", ignoreCase = true) && !document.fileName.endsWith(".docx", ignoreCase = true)) {
            isLoading = false
            errorMessage = "Định dạng file Word cũ (.doc) chưa được hỗ trợ trực tiếp. Vui lòng sử dụng file định dạng .docx."
            return@LaunchedEffect
        }

        val result = WordParserUtils.parseDocx(context, document.filePath)
        isLoading = false
        result.onSuccess { contents ->
            Log.d("DocMindDebug", "WordViewerDialog - Success parsing DOCX: ${contents.size} items")
            wordContents = contents
        }.onFailure { err ->
            Log.e("DocMindDebug", "WordViewerDialog - Error parsing DOCX: ${err.localizedMessage}", err)
            errorMessage = err.localizedMessage ?: "Không thể đọc nội dung file Word này."
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = document.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Tài liệu Word (DOCX)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Đang đọc nội dung Word...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Không thể xem tài liệu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "Lỗi không xác định",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(wordContents) { content ->
                            when (content) {
                                is WordContent.Paragraph -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Text(
                                            text = content.text,
                                            style = if (content.isHeading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (content.isHeading) FontWeight.Bold else FontWeight.Normal,
                                            color = if (content.isHeading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                                is WordContent.Table -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(8.dp)
                                        ) {
                                            content.rows.forEachIndexed { rowIndex, row ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    row.forEach { cell ->
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = if (rowIndex == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                            modifier = Modifier.padding(2.dp)
                                                        ) {
                                                            Text(
                                                                text = cell.ifBlank { " " },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (rowIndex < content.rows.size - 1) {
                                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
