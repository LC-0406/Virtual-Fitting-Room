package com.example.appuidemo.ui.import

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.appuidemo.data.image.ImageValidator
import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.data.model.LibraryType
import com.example.appuidemo.data.repository.LibraryRepository
import com.example.appuidemo.ui.components.LoadingButton
import com.example.appuidemo.ui.components.MessageDialog
import com.example.appuidemo.ui.components.PermissionDeniedState
import com.example.appuidemo.ui.util.hasCameraPermission
import com.example.appuidemo.ui.util.openAppSettings
import kotlinx.coroutines.launch
import java.io.File

/**
 * 导入抽屉（页面 7）：选择相册或拍摄 → 预览 → 确认导入时做合格检验。
 * 检验不合格则停在抽屉里提示重拍，不写入图库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    type: LibraryType,
    onDismiss: () -> Unit,
    onImported: (LibraryItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // 相机需要先落一个临时文件，再把它的 content:// 交给系统相机写入
    var cameraTargetUri by remember { mutableStateOf<Uri?>(null) }

    fun createCameraTarget(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingUri = cameraTargetUri
            cameraDenied = false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingUri = uri
    }

    fun launchCamera() {
        val target = createCameraTarget()
        cameraTargetUri = target
        cameraLauncher.launch(target)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else cameraDenied = true
    }

    if (dialogMessage != null) {
        MessageDialog(
            title = "照片不合格",
            message = dialogMessage.orEmpty(),
            onDismiss = { dialogMessage = null }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (type == LibraryType.PERSON) "导入人物照片" else "导入衣服照片",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "支持从系统相册选择，或直接拍摄一张",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImportSourceCard(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "相册",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                ImportSourceCard(
                    icon = Icons.Filled.PhotoCamera,
                    label = "拍摄",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (context.hasCameraPermission()) launchCamera()
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            if (cameraDenied) {
                Spacer(Modifier.height(12.dp))
                PermissionDeniedState(
                    message = "相机权限被拒绝，无法拍摄照片。可在系统设置中重新开启。",
                    onOpenSettings = { context.openAppSettings() }
                )
            }

            // 已选好一张：给出预览再确认
            val uri = pendingUri
            if (uri != null) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(type.aspectRatio)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "待导入预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pendingUri = null },
                        enabled = !isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) { Text("重选") }
                    LoadingButton(
                        text = "确认导入",
                        loading = isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = {
                            isImporting = true
                            scope.launch {
                                when (val result = LibraryRepository.importImage(type, uri)) {
                                    is LibraryRepository.ImportResult.Success -> {
                                        isImporting = false
                                        onImported(result.item)
                                    }
                                    is LibraryRepository.ImportResult.Invalid -> {
                                        isImporting = false
                                        dialogMessage = ImageValidator.MESSAGE_INVALID + "\n\n（${result.reason}）"
                                    }
                                    is LibraryRepository.ImportResult.Failed -> {
                                        isImporting = false
                                        dialogMessage = result.message
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportSourceCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(label)
        }
    }
}
