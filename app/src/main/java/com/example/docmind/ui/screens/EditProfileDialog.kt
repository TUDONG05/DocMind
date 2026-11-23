package com.example.docmind.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.docmind.R
import com.example.docmind.data.local.entity.UserEntity
import com.example.docmind.data.local.profile.AvatarStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileAvatar(name: String, source: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(null, source) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                source?.let { AvatarStorage(context).load(it)?.asImageBitmap() }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        }
    }
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, stringResource(R.string.profile_avatar_description),
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.trim().take(1).ifBlank { "U" }.uppercase(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun EditProfileDialog(
    user: UserEntity,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSave: (String, String?, () -> Unit) -> Unit
) {
    var name by rememberSaveable(user.userId) { mutableStateOf(user.fullName) }
    var selectedAvatar by rememberSaveable(user.userId) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedAvatar = uri.toString()
            onClearError()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAvatar(name, selectedAvatar ?: user.avatarUrl, Modifier.size(88.dp))
                TextButton(enabled = !saving, onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.profile_choose_avatar)) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it; onClearError() },
                    label = { Text(stringResource(R.string.full_name)) },
                    singleLine = true, enabled = !saving,
                    isError = name.isBlank(), modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        if (name.isBlank()) Text(stringResource(R.string.error_name_required))
                    }
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && name.isNotBlank() &&
                    (name.trim() != user.fullName || selectedAvatar != null),
                onClick = { onSave(name, selectedAvatar, onDismiss) }
            ) { Text(stringResource(if (saving) R.string.profile_saving else R.string.profile_save)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}
