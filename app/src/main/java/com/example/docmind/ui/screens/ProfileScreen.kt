package com.example.docmind.ui.screens

import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.docmind.data.local.entity.UserEntity
import com.example.docmind.ui.auth.ChangePasswordDialog
import com.example.docmind.util.FileUtils

@Composable
fun ProfileScreen(
    user: UserEntity?,
    docCount: Int,
    convCount: Int,
    authLoading: Boolean,
    authError: String?,
    onUpdateProfile: (fullName: String, email: String, phone: String?, avatarUri: String?) -> Unit,
    onChangePassword: (oldPass: String, newPass: String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var showChangePassDialog by remember { mutableStateOf(false) }

    // Form Edit States
    var editFullName by remember(user, isEditing) { mutableStateOf(user?.fullName ?: "") }
    var editEmail by remember(user, isEditing) { mutableStateOf(user?.email ?: "") }
    var editPhone by remember(user, isEditing) { mutableStateOf(user?.phone ?: "") }
    var selectedAvatarUri by remember(user, isEditing) { mutableStateOf(user?.avatarUrl) }

    // Validation error states
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    // Photo Picker launcher for Avatar Image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiedPath = FileUtils.copyUriToInternalStorage(
                context = context,
                uri = uri,
                targetFolder = "avatars",
                preferredFileName = "avatar_user_${user?.userId ?: 0}.jpg"
            )
            selectedAvatarUri = copiedPath ?: uri.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Avatar Box
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = isEditing) {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center
        ) {
            val avatarPath = if (isEditing) selectedAvatarUri else user?.avatarUrl
            if (!avatarPath.isNullOrBlank()) {
                AsyncImage(
                    model = avatarPath,
                    contentDescription = "Ảnh đại diện",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = (user?.fullName?.take(1) ?: "U").uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp
                )
            }

            if (isEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Đổi ảnh",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!isEditing) {
            // VIEW PROFILE MODE
            Text(
                text = user?.fullName ?: "Người dùng DocMind",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = user?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!user?.phone.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SĐT: ${user?.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "Tham gia: ${user?.createdAt?.take(10) ?: "Hôm nay"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { isEditing = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chỉnh sửa thông tin cá nhân")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$docCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tài liệu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$convCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Đoạn chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Actions Menu
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showChangePassDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Đổi mật khẩu",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout Button
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Đăng xuất tài khoản", fontWeight = FontWeight.SemiBold)
            }
        } else {
            // EDIT PROFILE FORM MODE
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Cập nhật hồ sơ cá nhân",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Full Name Field
                    OutlinedTextField(
                        value = editFullName,
                        onValueChange = {
                            editFullName = it
                            fullNameError = if (it.isBlank()) "Họ và tên không được để trống" else null
                        },
                        label = { Text("Họ và tên") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        isError = fullNameError != null,
                        supportingText = {
                            fullNameError?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Email Field
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = {
                            editEmail = it
                            emailError = when {
                                it.isBlank() -> "Email không được để trống"
                                !Patterns.EMAIL_ADDRESS.matcher(it.trim()).matches() -> "Email không đúng định dạng"
                                else -> null
                            }
                        },
                        label = { Text("Email liên hệ") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        isError = emailError != null,
                        supportingText = {
                            emailError?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Phone Field
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = {
                            editPhone = it
                            phoneError = when {
                                it.isNotBlank() && (it.trim().length < 9 || !it.trim().all { char -> char.isDigit() || char == '+' }) ->
                                    "Số điện thoại không hợp lệ (ví dụ: 0912345678)"
                                else -> null
                            }
                        },
                        label = { Text("Số điện thoại / Thông tin liên hệ") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        isError = phoneError != null,
                        supportingText = {
                            phoneError?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Save / Cancel Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isEditing = false
                                fullNameError = null
                                emailError = null
                                phoneError = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Hủy")
                        }

                        Button(
                            onClick = {
                                val hasError = editFullName.isBlank() ||
                                        editEmail.isBlank() ||
                                        !Patterns.EMAIL_ADDRESS.matcher(editEmail.trim()).matches() ||
                                        (editPhone.isNotBlank() && (editPhone.trim().length < 9 || !editPhone.trim().all { char -> char.isDigit() || char == '+' }))

                                if (editFullName.isBlank()) fullNameError = "Họ và tên không được để trống"
                                if (editEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(editEmail.trim()).matches()) {
                                    emailError = "Email không đúng định dạng"
                                }

                                if (!hasError) {
                                    onUpdateProfile(
                                        editFullName.trim(),
                                        editEmail.trim(),
                                        editPhone.trim().ifBlank { null },
                                        selectedAvatarUri
                                    )
                                    isEditing = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Lưu thay đổi", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showChangePassDialog) {
        ChangePasswordDialog(
            isLoading = authLoading,
            errorMessage = authError,
            onDismiss = { showChangePassDialog = false },
            onChangePassword = { oldPass, newPass ->
                onChangePassword(oldPass, newPass)
                showChangePassDialog = false
            }
        )
    }
}
