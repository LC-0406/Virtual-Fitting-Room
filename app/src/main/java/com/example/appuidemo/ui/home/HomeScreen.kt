package com.example.appuidemo.ui.home

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.appuidemo.data.image.MediaStoreHelper
import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.ui.components.LoadingButton
import com.example.appuidemo.ui.components.rememberImageModel
import com.example.appuidemo.ui.instructions.InstructionsSheet
import com.example.appuidemo.ui.util.needsLegacyStoragePermission
import kotlinx.coroutines.launch
import java.io.File

/**
 * 主页面（页面 1）。
 *
 * 中央预览图可切换人物 / 衣服 / 预览三路；确认生成 → 结果自动成为「预览」并进历史库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPersonLibrary: () -> Unit,
    onOpenClothingLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenZoom: (LibraryItem) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val person by viewModel.selectedPerson.collectAsStateWithLifecycle()
    val clothing by viewModel.selectedClothing.collectAsStateWithLifecycle()
    val history by viewModel.historyItems.collectAsStateWithLifecycle()

    val latestResult = history.firstOrNull()
    var showInstructions by remember { mutableStateOf(false) }

    // 还没有任何生成结果时，「预览」标签保持空态提示
    val currentItem: LibraryItem? = when (viewModel.previewTab) {
        PreviewTab.PERSON -> person
        PreviewTab.CLOTHING -> clothing
        PreviewTab.PREVIEW -> latestResult
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun startExport() {
        val result = latestResult ?: return
        viewModel.beginExport()
        scope.launch {
            val ok = MediaStoreHelper.saveToAlbum(
                context = context,
                source = File(result.filePath),
                displayName = "VFR_result_${result.id}.jpg"
            )
            viewModel.endExport(ok)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startExport()
        else Toast.makeText(context, "没有存储权限，无法导出到相册", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("虚拟试衣间", fontWeight = FontWeight.SemiBold)
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // ------------------------------------------------ 中央预览图
            // 预览区占满剩余高度，卡片保持 3:4；屏幕偏矮时自动缩小，避免内容被挤出屏幕
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = currentItem != null) {
                            currentItem?.let(onOpenZoom)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (currentItem != null) {
                        AsyncImage(
                            model = rememberImageModel(currentItem.filePath),
                            contentDescription = "当前预览图",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (viewModel.generating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(Modifier.height(12.dp))
                                    Text("正在生成…", color = Color.White)
                                }
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = when (viewModel.previewTab) {
                                    PreviewTab.PERSON -> Icons.Outlined.Person
                                    PreviewTab.CLOTHING -> Icons.Outlined.Checkroom
                                    PreviewTab.PREVIEW -> Icons.Filled.AutoAwesome
                                },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = when (viewModel.previewTab) {
                                    PreviewTab.PERSON -> "请先选择人物"
                                    PreviewTab.CLOTHING -> "请先选择衣服"
                                    PreviewTab.PREVIEW -> "请先生成"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ------------------------------------------------ 三路切换圆形图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewTabChip(
                    label = "人物",
                    icon = Icons.Outlined.Person,
                    item = person,
                    selected = viewModel.previewTab == PreviewTab.PERSON,
                    onClick = { viewModel.selectTab(PreviewTab.PERSON) }
                )
                PreviewTabChip(
                    label = "衣服",
                    icon = Icons.Outlined.Checkroom,
                    item = clothing,
                    selected = viewModel.previewTab == PreviewTab.CLOTHING,
                    onClick = { viewModel.selectTab(PreviewTab.CLOTHING) }
                )
                PreviewTabChip(
                    label = "预览",
                    icon = Icons.Filled.AutoAwesome,
                    item = latestResult,
                    selected = viewModel.previewTab == PreviewTab.PREVIEW,
                    onClick = { viewModel.selectTab(PreviewTab.PREVIEW) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ------------------------------------------------ 生成 / 导出
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 缺项时按钮置灰，但仍要接住点击来提示缺哪一项
                Box(modifier = Modifier.weight(1f)) {
                    LoadingButton(
                        text = "确认生成",
                        loading = viewModel.generating,
                        icon = Icons.Filled.AutoAwesome,
                        onClick = { viewModel.generate() },
                        enabled = person != null && clothing != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )
                    if (person == null || clothing == null) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable { viewModel.generate() }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            if (context.needsLegacyStoragePermission()) {
                                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                startExport()
                            }
                        },
                        enabled = latestResult != null && !viewModel.exporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("导出")
                    }
                    if (latestResult == null) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable { viewModel.notify("还没有生成结果，无法导出") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ------------------------------------------------ 库入口
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryEntry(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = "人物库",
                    count = person?.let { "已选 1 张" } ?: null,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPersonLibrary
                )
                LibraryEntry(
                    icon = Icons.Outlined.Checkroom,
                    label = "衣柜",
                    count = clothing?.let { "已选 1 张" } ?: null,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenClothingLibrary
                )
                LibraryEntry(
                    icon = Icons.Outlined.History,
                    label = "历史",
                    count = if (history.isEmpty()) null else "${history.size} 张",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenHistory
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { showInstructions = true }) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("使用说明")
                }
            }
        }
    }

    if (showInstructions) {
        InstructionsSheet(onDismiss = { showInstructions = false })
    }
}

/** 中央预览的圆形切换图标，已选则直接显示缩略图 */
@Composable
private fun PreviewTabChip(
    label: String,
    icon: ImageVector,
    item: LibraryItem?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (item != null) {
                AsyncImage(
                    model = rememberImageModel(item.filePath),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun LibraryEntry(
    icon: ImageVector,
    label: String,
    count: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (count != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
