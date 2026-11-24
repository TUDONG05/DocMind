package com.example.docmind.view.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.docmind.R
import com.example.docmind.model.local.entity.DocumentEntity

@Composable
fun ConversationScopeDialog(
    conversationId: Long,
    allDocuments: Boolean,
    selectedIds: List<Long>,
    documents: List<DocumentEntity>,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onApply: (Boolean, List<Long>) -> Unit
) {
    var all by rememberSaveable(conversationId) { mutableStateOf(allDocuments) }
    var selected by rememberSaveable(conversationId) { mutableStateOf(selectedIds) }
    val ready = documents.filter { it.processingStatus == "INDEXED" }
    val readyIds = ready.map { it.documentId }.toSet()
    val effectiveIds = selected.filter { it in readyIds }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.qa_scope_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { all = true },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = all, onClick = { all = true }, enabled = !saving)
                    Text(stringResource(R.string.qa_scope_all_option), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { all = false },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !all, onClick = { all = false }, enabled = !saving)
                    Text(stringResource(R.string.qa_scope_custom_option), Modifier.weight(1f))
                }
                Text(stringResource(R.string.qa_scope_hint), style = MaterialTheme.typography.bodySmall)
                if (!all) {
                    if (ready.isEmpty()) Text(stringResource(R.string.qa_scope_no_ready))
                    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(ready, key = { it.documentId }) { document ->
                            val checked = document.documentId in selected
                            fun toggle() {
                                selected = if (checked) selected - document.documentId else selected + document.documentId
                            }
                            Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { toggle() },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, onCheckedChange = { toggle() }, enabled = !saving)
                                Text(document.fileName, Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.qa_scope_history_hint), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && (all || effectiveIds.isNotEmpty()),
                onClick = { onApply(all, if (all) emptyList() else effectiveIds) }) {
                Text(stringResource(if (saving) R.string.qa_scope_saving else R.string.qa_scope_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}
