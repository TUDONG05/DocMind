package com.example.docmind.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.docmind.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.docmind.data.local.entity.DocumentEntity
import com.example.docmind.util.ExcelParserUtils
import com.example.docmind.util.ExcelSheetData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewerDialog(
    document: DocumentEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val openFailureMessage = stringResource(R.string.excel_open_failed)
    val legacyFormatMessage = stringResource(R.string.excel_legacy_unsupported)
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var excelSheets by remember { mutableStateOf<List<ExcelSheetData>>(emptyList()) }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(document.filePath) {
        isLoading = true
        errorMessage = null
        if (document.fileName.endsWith(".xls", ignoreCase = true) && !document.fileName.endsWith(".xlsx", ignoreCase = true)) {
            isLoading = false
            errorMessage = legacyFormatMessage
            return@LaunchedEffect
        }

        val result = ExcelParserUtils.parseXlsx(context, document.filePath)
        isLoading = false
        result.onSuccess { sheets ->
            excelSheets = sheets
        }.onFailure { err ->
            errorMessage = err.localizedMessage ?: openFailureMessage
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                Column {
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
                                    text = stringResource(R.string.excel_document),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Sheet Tabs Row if multiple sheets available
                    if (excelSheets.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = selectedSheetIndex,
                            containerColor = MaterialTheme.colorScheme.surface,
                            edgePadding = 16.dp
                        ) {
                            excelSheets.forEachIndexed { index, sheet ->
                                Tab(
                                    selected = selectedSheetIndex == index,
                                    onClick = { selectedSheetIndex = index },
                                    text = { Text(sheet.sheetName, fontWeight = FontWeight.Medium) }
                                )
                            }
                        }
                    }
                }
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
                        Text(stringResource(R.string.excel_loading), style = MaterialTheme.typography.bodyMedium)
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
                            text = stringResource(R.string.preview_failed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: stringResource(R.string.unknown_preview_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val currentSheet = excelSheets.getOrNull(selectedSheetIndex) ?: excelSheets.firstOrNull()
                    if (currentSheet == null || currentSheet.rows.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.sheet_empty))
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    itemsIndexed(currentSheet.rows) { rowIndex, row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            row.forEach { cell ->
                                                Surface(
                                                    shape = RoundedCornerShape(2.dp),
                                                    color = if (rowIndex == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                                    modifier = Modifier.padding(1.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                                            .width(120.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        Text(
                                                            text = cell,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            color = if (rowIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
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
}
